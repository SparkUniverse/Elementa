package gg.essential.elementa.renderer.impl

import gg.essential.universal.UGraphics
import gg.essential.universal.render.UGpuSampler
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState

internal val PIPELINE_COLOR = URenderPipeline.builderWithDefaultShader(
    "elementa:renderer/color",
    UGraphics.DrawMode.QUADS,
    UGraphics.CommonVertexFormats.POSITION_COLOR,
).apply {
    blendState = BlendState.ALPHA
}.build()

internal val PIPELINE_TEXTURE = URenderPipeline.builderWithDefaultShader(
    "elementa:renderer/texture",
    UGraphics.DrawMode.QUADS,
    UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR,
).apply {
    blendState = BlendState.ALPHA
}.build()

internal val PIPELINE_TEXTURE_PREMULTIPLIED_ALPHA = URenderPipeline.builderWithDefaultShader(
    "elementa:renderer/texture_premultiplied_alpha",
    UGraphics.DrawMode.QUADS,
    UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR,
).apply {
    blendState = BlendState.PREMULTIPLIED_ALPHA
}.build()

internal val UGpuSampler.Companion.NEAREST: UGpuSampler
    get() = UGpuSampler(
        UGpuSampler.AddressMode.CLAMP_TO_EDGE,
        UGpuSampler.AddressMode.CLAMP_TO_EDGE,
        UGpuSampler.FilterMode.NEAREST,
        UGpuSampler.FilterMode.NEAREST,
        false,
    )
