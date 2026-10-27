/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#include "WritableNativeMap.h"

#include <stdexcept>

using namespace facebook::jni;

namespace facebook::react {

WritableNativeMap::WritableNativeMap() : HybridBase(folly::dynamic::object()) {}

WritableNativeMap::WritableNativeMap(folly::dynamic&& val)
    : HybridBase(std::move(val)) {
  if (!map_.isObject()) {
    throw std::runtime_error("WritableNativeMap value must be an object.");
  }
}

void WritableNativeMap::initHybrid(alias_ref<jhybridobject> jobj) {
  setCxxInstance(jobj);
}

void WritableNativeMap::putNull(std::string key) {
  throwIfConsumed();
  map_.insert(std::move(key), nullptr);
}

void WritableNativeMap::putBoolean(std::string key, bool val) {
  throwIfConsumed();
  map_.insert(std::move(key), val);
}

void WritableNativeMap::putDouble(std::string key, double val) {
  throwIfConsumed();
  map_.insert(std::move(key), val);
}

void WritableNativeMap::putInt(std::string key, int val) {
  throwIfConsumed();
  map_.insert(std::move(key), val);
}

void WritableNativeMap::putLong(std::string key, jlong val) {
  throwIfConsumed();
  map_.insert(std::move(key), val);
}

void WritableNativeMap::putString(std::string key, alias_ref<jstring> val) {
  if (!val) {
    putNull(std::move(key));
    return;
  }
  throwIfConsumed();
  map_.insert(std::move(key), val->toStdString());
}

void WritableNativeMap::putNativeArray(
    std::string key,
    ReadableNativeArray* otherArray) {
  if (otherArray == nullptr) {
    putNull(std::move(key));
    return;
  }
  throwIfConsumed();
  map_.insert(key, otherArray->consume());
}

void WritableNativeMap::putNativeMap(
    std::string key,
    ReadableNativeMap* otherMap) {
  if (otherMap == nullptr) {
    putNull(std::move(key));
    return;
  }
  throwIfConsumed();
  map_.insert(std::move(key), otherMap->consume());
}

void WritableNativeMap::mergeNativeMap(ReadableNativeMap* other) {
  throwIfConsumed();
  other->throwIfConsumed();

  for (const auto& sourceIt : other->map_.items()) {
    map_[sourceIt.first] = sourceIt.second;
  }
}

void WritableNativeMap::putTouchPayload(
    alias_ref<JArrayDouble> pointerData,
    jint eventPointer,
    jint changedPointers,
    jint touchPointers) {
  throwIfConsumed();
  // Must match TouchesHelper.POINTER_FIELDS and the order documented on
  // WritableNativeMap.putTouchPayload.
  constexpr jint kPointerFields = 8;
  auto length = static_cast<jint>(pointerData->size());
  jint pointerCount = length / kPointerFields;
  if (length % kPointerFields != 0 || eventPointer < 0 ||
      eventPointer >= pointerCount) {
    throw std::invalid_argument("Invalid touch pointer data");
  }
  auto values = pointerData->getRegion(0, length);
  auto pointer = [&](jint index) {
    const double* fields = values.get() + index * kPointerFields;
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
  };

  folly::dynamic changedTouches = folly::dynamic::array();
  folly::dynamic touches = folly::dynamic::array();
  for (jint index = 0; index < pointerCount; index++) {
    if ((changedPointers & (1 << index)) != 0) {
      changedTouches.push_back(pointer(index));
    }
    if ((touchPointers & (1 << index)) != 0) {
      touches.push_back(pointer(index));
    }
  }
  map_ = pointer(eventPointer);
  map_.insert("changedTouches", std::move(changedTouches));
  map_.insert("touches", std::move(touches));
}

void WritableNativeMap::registerNatives() {
  registerHybrid({
      makeNativeMethod("putTouchPayload", WritableNativeMap::putTouchPayload),
      makeNativeMethod("putNull", WritableNativeMap::putNull),
      makeNativeMethod("putBoolean", WritableNativeMap::putBoolean),
      makeNativeMethod("putDouble", WritableNativeMap::putDouble),
      makeNativeMethod("putInt", WritableNativeMap::putInt),
      makeNativeMethod("putLong", WritableNativeMap::putLong),
      makeNativeMethod("putString", WritableNativeMap::putString),
      makeNativeMethod("putNativeArray", WritableNativeMap::putNativeArray),
      makeNativeMethod("putNativeMap", WritableNativeMap::putNativeMap),
      makeNativeMethod("mergeNativeMap", WritableNativeMap::mergeNativeMap),
      makeNativeMethod("initHybrid", WritableNativeMap::initHybrid),
  });
}

} // namespace facebook::react
