/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#include "TouchesHelper.h"

#include <stdexcept>

#include "WritableNativeArray.h"
#include "WritableNativeMap.h"

using namespace facebook::jni;

namespace facebook::react {

namespace {

// Must match TouchesHelper.POINTER_FIELDS and the order documented on
// TouchesHelper.nativeCreateTouchPayload.
constexpr jint kPointerFields = 8;

folly::dynamic createTouch(const double* fields) {
  folly::dynamic touch = folly::dynamic::object();
  touch.insert("pageX", fields[0]);
  touch.insert("pageY", fields[1]);
  touch.insert("locationX", fields[2]);
  touch.insert("locationY", fields[3]);
  touch.insert("targetSurface", static_cast<int>(fields[4]));
  touch.insert("target", static_cast<int>(fields[5]));
  touch.insert("timestamp", fields[6]);
  touch.insert("identifier", fields[7]);
  return touch;
}

local_ref<WritableNativeMap::jhybridobject> createTouchPayload(
    alias_ref<jclass> /*unused*/,
    alias_ref<JArrayDouble> pointerData,
    jint eventPointer,
    jint changedPointers,
    jint touchPointers) {
  auto length = static_cast<jint>(pointerData->size());
  jint pointerCount = length / kPointerFields;
  // Pointers are selected by bits of the jint masks.
  if (length % kPointerFields != 0 || pointerCount >= 32 || eventPointer < 0 ||
      eventPointer >= pointerCount) {
    throw std::invalid_argument("Invalid touch pointer data");
  }
  auto values = pointerData->getRegion(0, length);

  folly::dynamic changedTouches = folly::dynamic::array();
  folly::dynamic touches = folly::dynamic::array();
  for (jint index = 0; index < pointerCount; index++) {
    const double* fields = values.get() + index * kPointerFields;
    if ((changedPointers & (1 << index)) != 0) {
      changedTouches.push_back(createTouch(fields));
    }
    if ((touchPointers & (1 << index)) != 0) {
      touches.push_back(createTouch(fields));
    }
  }

  auto jPayload = WritableNativeMap::newObjectJavaArgs();
  auto* payload = jPayload->cthis();
  const double* fields = values.get() + eventPointer * kPointerFields;
  payload->putDouble("pageX", fields[0]);
  payload->putDouble("pageY", fields[1]);
  payload->putDouble("locationX", fields[2]);
  payload->putDouble("locationY", fields[3]);
  payload->putInt("targetSurface", static_cast<int>(fields[4]));
  payload->putInt("target", static_cast<int>(fields[5]));
  payload->putDouble("timestamp", fields[6]);
  payload->putDouble("identifier", fields[7]);
  WritableNativeArray changedTouchesArray(std::move(changedTouches));
  payload->putNativeArray("changedTouches", &changedTouchesArray);
  WritableNativeArray touchesArray(std::move(touches));
  payload->putNativeArray("touches", &touchesArray);
  return jPayload;
}

} // namespace

void TouchesHelper::registerNatives() {
  javaClassLocal()->registerNatives({
      makeNativeMethod("nativeCreateTouchPayload", createTouchPayload),
  });
}

} // namespace facebook::react
