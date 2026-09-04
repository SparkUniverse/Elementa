package gg.essential.elementa.utils

import gg.essential.universal.render.UGpuSampler

internal val UGpuSampler.Companion.NEAREST: UGpuSampler
    get() = UGpuSampler(
        UGpuSampler.AddressMode.CLAMP_TO_EDGE,
        UGpuSampler.AddressMode.CLAMP_TO_EDGE,
        UGpuSampler.FilterMode.NEAREST,
        UGpuSampler.FilterMode.NEAREST,
        false,
    )
