/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public abstract class IEUtils {
	public static Byte parseOptionalByteField(
		JsonObject contents,
		String fieldName
	) {
		JsonElement element = contents.get(fieldName);
		if (element == null) {
			return null;
		}
		return element.getAsByte();
	}

	public static Short parseOptionalShortField(
		JsonObject contents,
		String fieldName
	) {
		JsonElement element = contents.get(fieldName);
		if (element == null) {
			return null;
		}
		return element.getAsShort();
	}

	public static Integer parseOptionalIntField(
		JsonObject contents,
		String fieldName
	) {
		JsonElement element = contents.get(fieldName);
		if (element == null) {
			return null;
		}
		return element.getAsInt();
	}

	public static String parseOptionalStringField(
		JsonObject contents,
		String fieldName
	) {
		JsonElement element = contents.get(fieldName);
		if (element == null) {
			return null;
		}
		return element.getAsString();
	}
}
