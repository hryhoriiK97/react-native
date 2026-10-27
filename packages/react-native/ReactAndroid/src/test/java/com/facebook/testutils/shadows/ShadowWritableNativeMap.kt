/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.testutils.shadows

import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.WritableNativeMap
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@Implements(WritableNativeMap::class)
class ShadowWritableNativeMap : ShadowReadableNativeMap(), WritableMap {

  @Implementation override fun putNull(key: String): Unit = backingMap.putNull(key)

  @Implementation
  override fun putBoolean(key: String, value: Boolean): Unit = backingMap.putBoolean(key, value)

  @Implementation
  override fun putDouble(key: String, value: Double): Unit = backingMap.putDouble(key, value)

  @Implementation override fun putInt(key: String, value: Int): Unit = backingMap.putInt(key, value)

  @Implementation
  override fun putLong(key: String, value: Long): Unit = backingMap.putLong(key, value)

  @Implementation
  override fun putString(key: String, value: String?): Unit = backingMap.putString(key, value)

  @Implementation
  override fun putArray(key: String, value: ReadableArray?): Unit = backingMap.putArray(key, value)

  @Implementation
  override fun putMap(key: String, value: ReadableMap?): Unit = backingMap.putMap(key, value)

  @Implementation override fun merge(source: ReadableMap): Unit = backingMap.merge(source)

  @Implementation override fun copy(): WritableMap = backingMap.copy()

  /** Builds the same payload as the native WritableNativeMap::putTouchPayload. */
  @Implementation
  fun putTouchPayload(
      pointerData: DoubleArray,
      eventPointer: Int,
      changedPointers: Int,
      touchPointers: Int,
  ) {
    val pointerCount = pointerData.size / TOUCH_POINTER_FIELDS
    val changedTouches = JavaOnlyArray()
    val touches = JavaOnlyArray()
    for (index in 0 until pointerCount) {
      if (changedPointers and (1 shl index) != 0) {
        changedTouches.pushMap(touch(pointerData, index))
      }
      if (touchPointers and (1 shl index) != 0) {
        touches.pushMap(touch(pointerData, index))
      }
    }
    backingMap =
        touch(pointerData, eventPointer).apply {
          putArray("changedTouches", changedTouches)
          putArray("touches", touches)
        }
  }

  private fun touch(pointerData: DoubleArray, index: Int): JavaOnlyMap {
    val field = index * TOUCH_POINTER_FIELDS
    return JavaOnlyMap().apply {
      putDouble("pageX", pointerData[field])
      putDouble("pageY", pointerData[field + 1])
      putDouble("locationX", pointerData[field + 2])
      putDouble("locationY", pointerData[field + 3])
      putInt("targetSurface", pointerData[field + 4].toInt())
      putInt("target", pointerData[field + 5].toInt())
      putDouble("timestamp", pointerData[field + 6])
      putDouble("identifier", pointerData[field + 7])
    }
  }

  private companion object {
    private const val TOUCH_POINTER_FIELDS = 8
  }
}
