/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Utility functions for dealing with IEs
 */
public abstract class IEUtils {
	/**
	 * Try to get a json object as a byte
	 *
	 * @param contents the JSON object to try to parse
	 * @param fieldName the field name
	 * @return the field as a byte or null
	 */
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

	/**
	 * Try to get a json object as a short
	 *
	 * @param contents the JSON object to try to parse
	 * @param fieldName the field name
	 * @return the field as a short or null
	 */
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

	/**
	 * Try to get a json object as a int
	 *
	 * @param contents the JSON object to try to parse
	 * @param fieldName the field name
	 * @return the field as a int or null
	 */
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

	/**
	 * Try to get a json object as a int
	 *
	 * @param contents the JSON object to try to parse
	 * @param fieldName the field name
	 * @return the field as a int (0 if key not present)
	 */
	public static Integer parseIntField(
		JsonObject contents,
		String fieldName
	) {
		JsonElement element = contents.get(fieldName);
		if (element == null) {
			return 0;
		}
		return element.getAsInt();
	}

	/**
	 * Try to get a json object as a string
	 *
	 * @param contents the JSON object to try to parse
	 * @param fieldName the field name
	 * @return the field as a string or null
	 */
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

	/**
	 * Try to get a json object as a boolean when represented as a number (0, 1)
	 *
	 * @param contents the JSON object to try to parse
	 * @param fieldName the field name
	 * @return the field as a boolean (false if key not present)
	 */
	public static boolean parseBooleanNumberField(
		JsonObject contents,
		String fieldName
	) {
		JsonElement element = contents.get(fieldName);
		if (element == null) {
			return false;
		}
		return element.getAsInt() > 0;
	}
}
