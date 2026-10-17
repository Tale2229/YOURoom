package com.example.youroom

import com.example.youroom.domain.ThemeWaveMesh
import org.junit.Assert.*
import org.junit.Test

class ThemeWaveMeshTest {
    @Test fun oneVertexPerGridIntersectionIsReusedAndBothEndsAreUndeformed() {
        val mesh = ThemeWaveMesh(720f, 1280f, 120f)
        val vertices = mesh.vertices; val colors = mesh.colors
        mesh.update(0f, 8f)
        val initial = vertices.copyOf()
        assertEquals((mesh.columns + 1) * (mesh.rows + 1) * 2, vertices.size)
        assertTrue(colors.all { it ushr 24 == 0 })
        mesh.update(.5f, 8f)
        assertSame(vertices, mesh.vertices); assertSame(colors, mesh.colors)
        assertTrue(vertices.indices.any { initial[it] != vertices[it] })
        mesh.update(1f, 8f)
        assertArrayEquals(initial, vertices, 0f)
        assertTrue(colors.all { it ushr 24 == 255 })
    }

    @Test fun everyBoundaryStaysCoveredAndNoCellFoldsAtAnyPhaseOrAspectRatio() {
        for ((width, height) in listOf(319f to 900f, 720f to 1280f, 1440f to 2560f, 1600f to 320f, 1f to 1f)) {
            val mesh = ThemeWaveMesh(width, height, 120f)
            for (step in 0..100) {
                mesh.update(step / 100f, 80f)
                for (row in 0..mesh.rows) for (col in 0..mesh.columns) {
                    val i = (row * (mesh.columns + 1) + col) * 2
                    val x = mesh.vertices[i]; val y = mesh.vertices[i + 1]
                    assertTrue(x in 0f..width && y in 0f..height)
                    if (row == 0) assertEquals(0f, y, 0f)
                    if (row == mesh.rows) assertEquals(height, y, .0001f)
                    if (col == 0) assertEquals(0f, x, 0f)
                    if (col == mesh.columns) assertEquals(width, x, .0001f)
                    if (row < mesh.rows) assertTrue(y < mesh.vertices[i + (mesh.columns + 1) * 2 + 1])
                }
            }
        }
    }

    @Test fun colourFrontTravelsFromTopLeftAndIncludesTheArtworkAreaWithoutAMask() {
        val mesh = ThemeWaveMesh(720f, 1280f, 120f)
        mesh.update(.5f, 8f)
        assertEquals(255, mesh.colors.first() ushr 24)
        assertEquals(0, mesh.colors.last() ushr 24)
        // Grid intersections in the former 90dp preview region now deform normally.
        val i = (3 * (mesh.columns + 1) + 3) * 2 + 1
        assertTrue(mesh.vertices[i] < 3 * mesh.height / mesh.rows)
    }

    @Test fun invalidGeometryIsRejectedInsteadOfAllocatingABrokenMesh() {
        for ((w, h, side) in listOf(Triple(0f, 12f, 2f), Triple(12f, Float.NaN, 2f), Triple(12f, 12f, 0f))) {
            assertThrows(IllegalArgumentException::class.java) { ThemeWaveMesh(w, h, side) }
        }
    }
}
