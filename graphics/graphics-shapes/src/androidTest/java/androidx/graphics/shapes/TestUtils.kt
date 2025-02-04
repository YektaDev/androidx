/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.graphics.shapes

import android.graphics.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

private val Epsilon = 1e-4f

// Test equality within Epsilon
fun assertPointsEqualish(expected: PointF, actual: PointF) {
    assertEquals(expected.x, actual.x, Epsilon)
    assertEquals(expected.y, actual.y, Epsilon)
}

fun assertCubicsEqua1ish(expected: Cubic, actual: Cubic) {
    assertPointsEqualish(PointF(expected.anchorX0, expected.anchorY0),
        PointF(actual.anchorX0, actual.anchorY0))
    assertPointsEqualish(PointF(expected.controlX0, expected.controlY0),
        PointF(actual.controlX0, actual.controlY0))
    assertPointsEqualish(PointF(expected.controlX1, expected.controlY1),
        PointF(actual.controlX1, actual.controlY1))
    assertPointsEqualish(PointF(expected.anchorX1, expected.anchorY1),
        PointF(actual.anchorX1, actual.anchorY1))
}

fun assertPointGreaterish(expected: PointF, actual: PointF) {
    assertTrue(actual.x >= expected.x - Epsilon)
    assertTrue(actual.y >= expected.y - Epsilon)
}

fun assertPointLessish(expected: PointF, actual: PointF) {
    assertTrue(actual.x <= expected.x + Epsilon)
    assertTrue(actual.y <= expected.y + Epsilon)
}

fun assertEqualish(expected: Float, actual: Float, message: String? = null) {
    assertEquals(message ?: "", expected, actual, Epsilon)
}

fun assertInBounds(shape: CubicShape, minPoint: PointF, maxPoint: PointF) {
    val cubics = shape.cubics
    for (cubic in cubics) {
        assertPointGreaterish(minPoint, PointF(cubic.anchorX0, cubic.anchorY0))
        assertPointLessish(maxPoint, PointF(cubic.anchorX0, cubic.anchorY0))
        assertPointGreaterish(minPoint, PointF(cubic.controlX0, cubic.controlY0))
        assertPointLessish(maxPoint, PointF(cubic.controlX0, cubic.controlY0))
        assertPointGreaterish(minPoint, PointF(cubic.controlX1, cubic.controlY1))
        assertPointLessish(maxPoint, PointF(cubic.controlX1, cubic.controlY1))
        assertPointGreaterish(minPoint, PointF(cubic.anchorX1, cubic.anchorY1))
        assertPointLessish(maxPoint, PointF(cubic.anchorX1, cubic.anchorY1))
    }
}
