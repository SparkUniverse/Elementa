package gg.essential.elementa.unstable.state.v2

import java.time.Duration
import java.time.Instant

class StateScheduler(val time: State<Instant>) {
    /**
     * Subscribes the given observer to be re-evaluated once the given [targetTime] is reached.
     *
     * If the given time has already been reached, the observer will not be re-evaluated and `true` will be returned.
     */
    fun Observer.observe(targetTime: Instant): Boolean {
        // Fast-path for when called after target time has already been reached
        if (!targetTime.isAfter(time.getUntracked())) {
            return true
        }
        // Wrapped with `memo` because observeRoughly may wake up multiple times,
        // but we want the caller to only wake up once we're actually at the target time.
        return memo { observeRoughly(targetTime) }.invoke()
    }

    private val triggerCache: Array<Pair<Instant, State<Boolean>>?> = arrayOfNulls(Long.SIZE_BITS)

    /**
     * Subscribes the given observer to be re-evaluated, potentially multiple times, until [targetTime] is
     * reached, at which point `true` is returned.
     * If the target time has already passed, `true` is returned immediately.
     */
    private fun Observer.observeRoughly(targetTime: Instant): Boolean {
        val now = time.getUntracked()
        val delay = Duration.between(now, targetTime)
        if (delay.isNegative || delay.isZero) {
            // target time has already passed, we're good to go
            return true
        } else if (delay <= Duration.ofMillis(8)) {
            // target time is very close, just subscribe to the main State directly, we're likely going to hit it by the
            // next update anyway
            time()
            return false
        }

        // To avoid subscribing (and therefore having to re-compute on each update) thousands of `State` directly to the
        // main State, we'll have up to 64 different intermediate states at exponentially far distances in the future,
        // and we'll subscribe to one of those instead of the main state. That way the main state should at no point
        // have more than 64 state (plus however many short-lived ones were subscribed directly above) to update on each
        // tick.
        // Once the time of an intermediate state has come, it'll trigger all states subscribed to it, which will all
        // then re-sort themselves into closer intermediate states, which should overall result in O(log(n))
        // amortized runtime cost each tick instead of the O(n) cost a naive implementation would have.

        // For managing the intermediate states, this implementation is looking at how many shared leading bits there
        // are between the current timestamp and the target timestamp (the more shared bits, the closer they are), and
        // then picking the intermediate state based on that value from an array of length 64.
        val nowMs = now.toEpochMilli()
        val targetMs = targetTime.toEpochMilli()
        val bitsMatching = nowMs.xor(targetMs).countLeadingZeroBits()
        val nextWakeupTime = Instant.ofEpochMilli(targetMs.and((-1L).ushr(bitsMatching + 1).inv()))

        var trigger = triggerCache[bitsMatching]
        if (trigger == null || trigger.first != nextWakeupTime) {
            trigger = Pair(nextWakeupTime, memo {
                // FIXME States are currently not un-registered when they no longer have any subscribers, only once
                //  garbage collections deletes them, this is usually good enough but here (and especially during tests)
                //  it can result in a lot more than 64 states being registered to the root time source state. To avoid
                //  that, we skip subscribing to the root time source state altogether when our target time has been
                //  reached, thereby explicitly removing the subscription of this memo from the root time source state.
                //  Should the State implementation ever be optimized to handle this itself, this can be simplified to:
                //    timeSource() >= nextWakeupTime
                if (time.getUntracked() >= nextWakeupTime) {
                    true
                } else {
                    time()
                    false
                }
            }.let {
                // FIXME while a single `memo` is functionality sufficient to decouple evaluation of the subscribers
                //  from the time source State, a second `memo` is required due to current implementation details of
                //  State, otherwise the runtime of updating the current time will still be O(n).
                memo { it() }
            })
            triggerCache[bitsMatching] = trigger
        }
        trigger.second.invoke()
        return false
    }

    companion object {
        private val systemTime = mutableStateOf(Instant.EPOCH)

        /**
         * Scheduler for the current system time as reported by [Instant.now].
         *
         * Needs to be regularly updated on the main thread via [updateSystemTime].
         */
        @JvmStatic
        val forSystemTime: StateScheduler = StateScheduler(systemTime)

        @JvmStatic
        fun updateSystemTime(now: Instant = Instant.now()) {
            systemTime.set(now)
        }
    }
}

/**
 * Returns the time of the given [scheduler] as an [ObservedInstant] which will track operations applied to it and
 * subscribe the [Observer] to be re-evaluated when the result of any of these operations changes.
 *
 * Note that the [ObservedInstant] wraps the [Observer] and as such the same life-time restrictions apply to it.
 * In particular that means that the [ObservedInstant] or any [ObservedValue] derived from it MUST NOT become the
 * value of the [State], only concrete types, e.g. as returned by [ObservedValue.getValue], may.
 *
 * When performing more complex operations on the returned value, using [withSystemTime] may be more efficient.
 */
fun Observer.systemTime(scheduler: StateScheduler = StateScheduler.forSystemTime): ObservedInstant =
    withSystemTime(scheduler) { it }

/**
 * Runs the given [block] with the time of the given [scheduler] as an [ObservedInstant] which will track operations
 * applied to it and subscribe the [Observer] to be re-evaluated when the result of any of these operations changes.
 *
 * Note that the [ObservedInstant] wraps the [Observer] and as such the same life-time restrictions apply to it.
 * In particular that means that the [ObservedInstant] or any [ObservedValue] derived from it MUST NOT become the
 * value of the [State], only concrete types, e.g. as returned by [ObservedValue.getValue], may.
 */
fun <T> Observer.withSystemTime(
    scheduler: StateScheduler = StateScheduler.forSystemTime,
    block: (ObservedInstant) -> T,
): T {
    val now = scheduler.time.getUntracked()

    var delayedRegistration = true
    var nextWakeupTime = Instant.MAX
    val observableInstant = ObservedInstant(now) { wakeupTime ->
        if (wakeupTime <= now) return@ObservedInstant

        if (wakeupTime > nextWakeupTime) return@ObservedInstant
        nextWakeupTime = wakeupTime

        if (delayedRegistration) return@ObservedInstant
        with(scheduler) { observe(nextWakeupTime) }
    }

    val result = block(observableInstant)

    delayedRegistration = false
    if (nextWakeupTime != Instant.MAX) {
        with(scheduler) { observe(nextWakeupTime) }
    }

    return result
}

/** @see withSystemTime */
fun <T> stateUsingSystemTime(block: Observer.(ObservedInstant) -> T) = State { withSystemTime { block(it) } }

/**
 * Wraps a value (e.g. a [Long]) and tracks all operations applied.
 * This allows the original owner of the value to know how it is used and, crucially, whether a different value would
 * give different results.
 *
 * Implementations will provide various utility methods to operate on the contained value in a tracked way.
 * Any time such an operation is performed on the value, [changesAt] is called with the nearest value(s) that
 * would give a different result.
 * E.g. If the value of a [ObservedLong] is 5 and the user calls `lessOrEqual(7)`, that call will return `true`
 *      and `changesAt(8)` is called because 8 would be the closest value to return `false`.
 *      If `lessOrEqual(3)` is called, `false` is returned and `changesAt(3)` is called because 3 would be the
 *      closest value to return `true`.
 *      If `toString()` is called, `"5"` is returned and `changesAt` is called once with 4 and once with 6 because
 *      any change in either direction will give a different result.
 *      Note that while for these simple examples, `changesAt` is only called exactly as often as necessary and with
 *      the exact value at which the change happens, this is not a strict requirement; it may be called multiple times
 *      and/or with values closer than the next change; the only requirement is that at least one call must be at or
 *      closer than the point at which the change happens, such that if we always re-evaluate a computation at the
 *      closest point, we won't miss the change.
 *
 * To get the underlying value, one may call [getValue]. Since the returned value can not be tracked any further from
 * that point on, this will cause the computation to be re-evaluated if the returned value changes in any way.
 * To get the value without subscribing to all changes, use [untracked] and manually call [changesAt] as appropriate.
 */
interface ObservedValue<T> {
    val untracked: T
    val changesAt: (T) -> Unit

    fun getValue(): T
}

/** An [ObservedValue] for [Instant]. */
class ObservedInstant(override val untracked: Instant, override val changesAt: (Instant) -> Unit) : ObservedValue<Instant> {
    override fun getValue(): Instant {
        if (untracked != Instant.MIN) changesAt(untracked.minusNanos(1))
        if (untracked != Instant.MAX) changesAt(untracked.plusNanos(1))
        return untracked
    }

    override fun toString(): String = getValue().toString()

    fun toEpochMillis() = ObservedLong(untracked.toEpochMilli()) { changesAt(Instant.ofEpochMilli(it)) }

    fun isBefore(other: Instant) = ObservedDuration.between(this, other).isPositive
    fun isAfter(other: Instant) = ObservedDuration.between(other, this).isPositive

    fun since(startInclusive: Instant): ObservedDuration = ObservedDuration.between(startInclusive, this)
    fun until(endExclusive: Instant): ObservedDuration = ObservedDuration.between(this, endExclusive)
}

/** An [ObservedValue] for [Duration]. */
class ObservedDuration(override val untracked: Duration, override val changesAt: (Duration) -> Unit) : ObservedValue<Duration> {
    override fun getValue(): Duration {
        if (untracked != MIN_DURATION) changesAt(untracked.minusNanos(1))
        if (untracked != MAX_DURATION) changesAt(untracked.plusNanos(1))
        return untracked
    }

    override fun toString(): String = getValue().toString()

    fun toMillis(): ObservedLong =
        ObservedLong(untracked.toMillis()) { changesAt(Duration.ofMillis(it)) }

    val isNegative
        get() = untracked.isNegative.also { changesAt(if (it) Duration.ZERO else SMALLEST_NEGATIVE_DURATION) }

    val isPositive
        get() = (!untracked.isNegative && !untracked.isZero).also { changesAt(if (it) Duration.ZERO else SMALLEST_POSITIVE_DURATION) }

    val isZero
        get() = !isNegative && !isPositive

    companion object {
        private val MIN_DURATION = Duration.ofSeconds(Long.MIN_VALUE, 0)
        private val MAX_DURATION = Duration.ofSeconds(Long.MAX_VALUE, 999999999)
        private val SMALLEST_POSITIVE_DURATION = Duration.ofNanos(1)
        private val SMALLEST_NEGATIVE_DURATION = Duration.ofNanos(-1)

        fun between(startInclusive: Instant, endExclusive: Instant) =
            ObservedDuration(Duration.between(startInclusive, endExclusive)) {}

        fun between(startInclusive: ObservedInstant, endExclusive: Instant) =
            ObservedDuration(Duration.between(startInclusive.untracked, endExclusive)) { duration ->
                startInclusive.changesAt(endExclusive.minus(duration))
            }

        fun between(startInclusive: Instant, endExclusive: ObservedInstant) =
            ObservedDuration(Duration.between(startInclusive, endExclusive.untracked)) { duration ->
                endExclusive.changesAt(startInclusive.plus(duration))
            }

        fun between(startInclusive: ObservedInstant, endExclusive: ObservedInstant) =
            ObservedDuration(Duration.between(startInclusive.untracked, endExclusive.untracked)) { duration ->
                startInclusive.changesAt(endExclusive.untracked.minus(duration))
                endExclusive.changesAt(startInclusive.untracked.plus(duration))
            }
    }
}

/** An [ObservedValue] for [Long]. */
class ObservedLong(override val untracked: Long, override val changesAt: (Long) -> Unit) : ObservedValue<Long> {
    override fun getValue(): Long {
        changesAt(untracked - 1)
        changesAt(untracked + 1)
        return untracked
    }

    override fun toString(): String = getValue().toString()

    // Note: Intentionally not implementing `compareTo` because that has two cutoff points and so we'll likely
    //       unnecessarily re-evaluate just 1 before we actually need to re-evaluate.
    fun equal(other: Long) =
        (untracked == other).also { if (it) getValue() else changesAt(other) }
    fun less(other: Long) =
        (untracked < other).also { changesAt(if (it) other else other - 1) }
    fun lessOrEqual(other: Long) =
        (untracked <= other).also { changesAt(if (it) other + 1 else other) }
    fun greater(other: Long) =
        !lessOrEqual(other)
    fun greaterOrEqual(other: Long) =
        !less(other)

    operator fun unaryMinus(): ObservedLong =
        ObservedLong(-untracked) { changesAt(-it) }

    operator fun plus(other: Long): ObservedLong =
        ObservedLong(untracked + other) { changesAt(it - other) }
    operator fun minus(other: Long): ObservedLong = plus(-other)

    operator fun times(other: Long): ObservedLong {
        if (other == 0L) return ObservedLong(0) {}
        if (other < 0) return -times(-other)
        val oldResult = untracked * other
        return ObservedLong(oldResult) { newResult ->
            if (newResult > oldResult) changesAt((newResult + (other - 1)).floorDiv(other))
            if (newResult < oldResult) changesAt(newResult.floorDiv(other))
        }
    }
    operator fun div(other: Long): ObservedLong {
        if (other < 0) return -(this / -other)
        if (untracked < 0) return -(-this / other)
        val oldResult = untracked / other
        return ObservedLong(oldResult) { newResult ->
            if (newResult > oldResult) changesAt(newResult * other)
            if (newResult < oldResult) changesAt((newResult + 1) * other - 1)
        }
    }

    private fun both(a: ObservedLong, b: ObservedLong): ObservedLong {
        assert(a.untracked == b.untracked)
        return ObservedLong(a.untracked) { a.changesAt(it); b.changesAt(it) }
    }

    operator fun plus(other: ObservedLong): ObservedLong =
        both(this.plus(other.untracked), other.plus(this.untracked))
    operator fun minus(other: ObservedLong): ObservedLong =
        plus(-other)
    operator fun times(other: ObservedLong): ObservedLong =
        both(this.times(other.untracked), other.times(this.untracked))
}

/**
 * Explores all possible return values of the given [func] when called with values from the range given by [bounds]
 * provided [observed] constructs an observed type like [ObservedLong].
 */
@ForTestingOnly
fun <T : Comparable<T>, OT : ObservedValue<T>, R> explore(
    bounds: ClosedRange<T>,
    observed: (T, (T) -> Unit) -> OT,
    func: (OT) -> R,
): List<R> {
    fun eval(arg: T): Triple<R, T, T> {
        var lowerNext = bounds.start
        var upperNext = bounds.endInclusive
        val observedArg = observed(arg) { next ->
            if (next < arg) {
                if (next > lowerNext) {
                    lowerNext = next
                }
            } else if (next > arg) {
                if (next < upperNext) {
                    upperNext = next
                }
            }
        }
        val value = try {
            func(observedArg)
        } catch (e: Exception) {
            throw AssertionError("Failed to evaluate func with $arg", e)
        }
        return Triple(value, lowerNext, upperNext)
    }

    var prev = bounds.start
    var prevValue = func(observed(prev) {})
    val results = mutableListOf(prevValue)
    var curr = bounds.start
    while (true) {
        val (value, lowerNext, upperNext) = eval(curr)
        if (value != prevValue) {
            assert(prev <= lowerNext) {
                "When ran with $curr, func returned $value and " +
                        "reported its closest lower change point to be $lowerNext, " +
                        "however when run with $prev it produces $prevValue, which contradicts this."
            }
            results.add(value)
            val (lowerValue, _, _) = eval(lowerNext)
            assert(lowerValue == prevValue) {
                "When ran with $prev, func returned $prevValue and " +
                        "reported its closest upper change point to be $curr, " +
                        "however when run with $lowerNext it produces $lowerValue, which contradicts this."
            }
        }
        if (curr == bounds.endInclusive) {
            break
        }
        prev = curr
        prevValue = value
        curr = upperNext
    }
    return results
}

/** Annotated members are meant for use in unit tests and provide no API/ABI stability guarantees. */
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
annotation class ForTestingOnly
