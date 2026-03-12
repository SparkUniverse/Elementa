package gg.essential.elementa.unstable.state.v2.combinators

import gg.essential.elementa.unstable.state.v2.State

@Deprecated("Exists primarily for easier migration from State v1. Prefer using [State] lambda (with `memo` where necessary) instead.",
    replaceWith = ReplaceWith("State { this().contains(other(), ignoreCase = ignoreCase) }"))
@Suppress("DEPRECATION")
fun State<String>.contains(other: State<String>, ignoreCase: Boolean = false) =
    zip(other) { a, b -> a.contains(b, ignoreCase) }

@Deprecated("Exists primarily for easier migration from State v1. Prefer using [State] lambda (with `memo` where necessary) instead.",
    replaceWith = ReplaceWith("State { this().isEmpty() }"))
@Suppress("DEPRECATION")
fun State<String>.isEmpty() = map { it.isEmpty() }

@Deprecated("Exists primarily for easier migration from State v1. Prefer using [State] lambda (with `memo` where necessary) instead.",
    replaceWith = ReplaceWith("State { this().isNotEmpty() }"))
@Suppress("DEPRECATION")
fun State<String>.isNotEmpty() = map { it.isNotEmpty() }
