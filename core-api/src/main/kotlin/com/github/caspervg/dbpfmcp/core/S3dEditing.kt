package com.github.caspervg.dbpfmcp.core

import kotlinx.serialization.Serializable

@Serializable
data class S3dVector(val x: Float, val y: Float, val z: Float)

@Serializable
data class S3dBounds(val min: S3dVector, val max: S3dVector)

/** Pivot for rotation about the vertical (Y) axis. */
@Serializable
sealed interface S3dPivot {
    /** The model origin, which is also the game's placement anchor. */
    @Serializable
    data object Origin : S3dPivot

    /** The XZ centre of the source model's bounding box. */
    @Serializable
    data object Center : S3dPivot

    /** An explicit XZ point in source model coordinates. */
    @Serializable
    data class Point(val x: Float, val z: Float) : S3dPivot
}

/**
 * Vertex-only transformation in model coordinates: rotate about the Y axis around [pivot],
 * then scale about the origin, then translate.
 */
@Serializable
data class TransformS3dRequest(
    val path: String,
    val tgi: Tgi,
    val outputPath: String,
    val outputTgi: Tgi? = null,
    val scale: S3dVector = S3dVector(1f, 1f, 1f),
    val translation: S3dVector = S3dVector(0f, 0f, 0f),
    /** Degrees about +Y by the right-hand rule: x' = x·cos + z·sin, z' = −x·sin + z·cos. */
    val rotationDegrees: Float = 0f,
    val pivot: S3dPivot = S3dPivot.Origin,
    val compressed: Boolean = true,
)

@Serializable
data class TransformS3dResult(
    val outputPath: String,
    val tgi: Tgi,
    val vertexCount: Int,
    val before: S3dBounds,
    val after: S3dBounds,
    /** XZ pivot actually used for rotation, in source model coordinates. */
    val pivot: S3dVector,
    val bytesWritten: Long,
    val warnings: List<String>,
)
