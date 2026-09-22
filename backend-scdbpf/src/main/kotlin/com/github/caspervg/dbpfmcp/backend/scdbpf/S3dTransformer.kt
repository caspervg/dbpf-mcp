package com.github.caspervg.dbpfmcp.backend.scdbpf

import com.github.caspervg.dbpfmcp.core.InputError
import com.github.caspervg.dbpfmcp.core.S3dBounds
import com.github.caspervg.dbpfmcp.core.S3dPivot
import com.github.caspervg.dbpfmcp.core.S3dVector
import com.github.caspervg.dbpfmcp.core.TransformS3dRequest
import io.github.memo33.scdbpf.S3d
import scala.jdk.javaapi.CollectionConverters

/** Rotate (Y axis), scale and translate vertices; keep topology, UVs, materials, animation, properties and registration sections untouched. */
internal object S3dTransformer {
    fun transform(source: S3d, request: TransformS3dRequest): S3d {
        val scale = request.scale
        val translation = request.translation
        requireInput(listOf(scale.x, scale.y, scale.z).all { it.isFinite() && it > 0f },
            "scale components must be finite and greater than zero; reflection is not supported")
        requireInput(listOf(translation.x, translation.y, translation.z).all { it.isFinite() },
            "translation components must be finite")
        requireInput(request.rotationDegrees.isFinite(), "rotationDegrees must be finite")
        validate(source)
        val pivot = pivot(source, request.pivot)
        val (cos, sin) = rotation(request.rotationDegrees)
        val groups = CollectionConverters.asJava(source.vert()).map { group ->
            val vertices = CollectionConverters.asJava(group).map { vertex ->
                val dx = vertex.x() - pivot.x
                val dz = vertex.z() - pivot.z
                val rx = pivot.x + dx * cos + dz * sin
                val rz = pivot.z - dx * sin + dz * cos
                S3d.Vert(
                    rx * scale.x + translation.x,
                    vertex.y() * scale.y + translation.y,
                    rz * scale.z + translation.z,
                    vertex.u(),
                    vertex.v(),
                )
            }
            S3d.VertGroup(CollectionConverters.asScala(vertices).toIndexedSeq())
        }
        return source.copy(
            CollectionConverters.asScala(groups).toIndexedSeq(),
            source.indx(), source.prim(), source.mats(), source.anim(), source.prop(), source.regp(),
        ).also(::validate)
    }

    /** Resolve the XZ rotation pivot in source model coordinates (y is always 0). */
    fun pivot(source: S3d, pivot: S3dPivot): S3dVector = when (pivot) {
        S3dPivot.Origin -> S3dVector(0f, 0f, 0f)
        S3dPivot.Center -> bounds(source).let { S3dVector((it.min.x + it.max.x) / 2, 0f, (it.min.z + it.max.z) / 2) }
        is S3dPivot.Point -> {
            requireInput(pivot.x.isFinite() && pivot.z.isFinite(), "pivot components must be finite")
            S3dVector(pivot.x, 0f, pivot.z)
        }
    }

    /** Exact cosine/sine for quarter turns so 90° rotations do not introduce float noise. */
    private fun rotation(degrees: Float): Pair<Float, Float> {
        val normalized = ((degrees.toDouble() % 360.0) + 360.0) % 360.0
        return when (normalized) {
            0.0 -> 1f to 0f
            90.0 -> 0f to 1f
            180.0 -> -1f to 0f
            270.0 -> 0f to -1f
            else -> Math.toRadians(normalized).let { Math.cos(it).toFloat() to Math.sin(it).toFloat() }
        }
    }

    fun bounds(model: S3d): S3dBounds {
        val vertices = CollectionConverters.asJava(model.vert()).flatMap { CollectionConverters.asJava(it) }
        requireInput(vertices.isNotEmpty(), "S3D must contain vertices")
        return S3dBounds(
            S3dVector(vertices.minOf { it.x() }, vertices.minOf { it.y() }, vertices.minOf { it.z() }),
            S3dVector(vertices.maxOf { it.x() }, vertices.maxOf { it.y() }, vertices.maxOf { it.z() }),
        )
    }

    private fun validate(model: S3d) {
        val verts = CollectionConverters.asJava(model.vert())
        val indices = CollectionConverters.asJava(model.indx())
        val primitives = CollectionConverters.asJava(model.prim())
        val materials = CollectionConverters.asJava(model.mats())
        requireInput(verts.any { it.size() > 0 }, "S3D must contain vertices")
        verts.forEachIndexed { groupIndex, group ->
            CollectionConverters.asJava(group).forEach { vertex ->
                requireInput(listOf(vertex.x(), vertex.y(), vertex.z(), vertex.u(), vertex.v()).all { it.isFinite() },
                    "S3D vertex group $groupIndex contains non-finite coordinates or UVs")
            }
        }
        val frames = model.anim().numFrames().toInt() and 0xFFFF
        CollectionConverters.asJava(model.anim().groups()).forEachIndexed { meshIndex, mesh ->
            fun blocks(values: scala.collection.immutable.IndexedSeq<Any>, size: Int): List<Int> {
                val result = CollectionConverters.asJava(values).map { (it as Number).toInt() }
                requireInput(result.size == frames, "S3D mesh $meshIndex has inconsistent frame block counts")
                requireInput(result.all { it in 0 until size }, "S3D mesh $meshIndex references a missing group")
                return result
            }
            val vb = blocks(mesh.vertBlock(), verts.size)
            val ib = blocks(mesh.indxBlock(), indices.size)
            val pb = blocks(mesh.primBlock(), primitives.size)
            blocks(mesh.matsBlock(), materials.size)
            repeat(frames) { frame ->
                val indexGroup = CollectionConverters.asJava(indices[ib[frame]])
                requireInput(indexGroup.all { (it as Number).toInt() in 0 until verts[vb[frame]].size() },
                    "S3D mesh $meshIndex has an out-of-range vertex index")
                CollectionConverters.asJava(primitives[pb[frame]]).forEach { primitive ->
                    requireInput(primitive.firstIndx() >= 0 && primitive.numIndxs() >= 0 &&
                        primitive.firstIndx().toLong() + primitive.numIndxs() <= indexGroup.size,
                        "S3D mesh $meshIndex has an out-of-range primitive")
                }
            }
        }
    }

    private fun requireInput(condition: Boolean, message: String) {
        if (!condition) throw InputError(message)
    }
}
