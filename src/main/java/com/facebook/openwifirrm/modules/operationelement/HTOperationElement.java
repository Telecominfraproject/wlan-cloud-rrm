package com.facebook.openwifirrm.modules.operationelement;

import java.util.Arrays;
import java.util.Objects;

import org.apache.commons.codec.binary.Base64;

import com.facebook.openwifirrm.Utils;

/**
 * High Throughput (HT) Operation Element, which are potentially present in
 * wifiscan entries. Details in IEEE 802.11-2020 standard.
 */
public class HTOperationElement implements OperationElement {

	protected final byte primaryChannel;
	protected final byte secondaryChannelOffset;
	protected final boolean staChannelWidth;
	protected final boolean rifsMode;
	protected final byte htProtection;
	protected final boolean nongreenfieldHtStasPresent;
	protected final boolean obssNonHtStasPresent;
	protected final byte channelCenterFrequencySegment2;
	protected final boolean dualBeacon;
	protected final boolean dualCtsProtection;
	protected final boolean stbcBeacon;
	protected final byte[] basicHtMcsSet;

	/**
	 * XXX some combinations of these parameters may be invalid as defined by
	 * 802.11-2020, but this is not checked here. If fidelity to 802.11 is required,
	 * the caller of this method must make sure to pass in valid parameters. The
	 * 802.11-2020 specification has more details about the parameters.
	 *
	 * @param primaryChannel                 channel index
	 * @param secondaryChannelOffset
	 * @param staChannelWidth
	 * @param rifsMode
	 * @param htProtection
	 * @param nongreenfieldHtStasPresent
	 * @param obssNonHtStasPresent
	 * @param channelCenterFrequencySegment2
	 * @param dualBeacon
	 * @param dualCtsProtection
	 * @param stbcBeacon
	 * @return base64 encoded ht operator as a String
	 */
	public HTOperationElement(byte primaryChannel, byte secondaryChannelOffset, boolean staChannelWidth,
			boolean rifsMode, byte htProtection, boolean nongreenfieldHtStasPresent, boolean obssNonHtStasPresent,
			byte channelCenterFrequencySegment2, boolean dualBeacon, boolean dualCtsProtection, boolean stbcBeacon) {
		this.primaryChannel = primaryChannel;
		this.secondaryChannelOffset = secondaryChannelOffset;
		this.staChannelWidth = staChannelWidth;
		this.rifsMode = rifsMode;
		this.htProtection = htProtection;
		this.nongreenfieldHtStasPresent = nongreenfieldHtStasPresent;
		this.obssNonHtStasPresent = obssNonHtStasPresent;
		this.channelCenterFrequencySegment2 = channelCenterFrequencySegment2;
		this.dualBeacon = dualBeacon;
		this.dualCtsProtection = dualCtsProtection;
		this.stbcBeacon = stbcBeacon;
		// the next 16 bytes are for the basic HT-MCS set
		// a default is chosen; if needed, we can add a parameter to set these
		this.basicHtMcsSet = new byte[16];
	}

	public HTOperationElement() {
		this((byte) 1, (byte) 0, false, false, (byte) 0, true, false, (byte) 0, false, false, false);
	}

	/**
	 *
	 * @param htOperString must be a String representing a base64 encoded properly
	 *                     formatted ht operation element (see 802.11-2020 standard)
	 */
	public HTOperationElement(String htOperString) {
		byte[] bytes = Base64.decodeBase64(htOperString);
		this.primaryChannel = bytes[0];
		this.secondaryChannelOffset = (byte) (bytes[1] >>> 6);
		this.staChannelWidth = ((bytes[1] & 0b00100000) >>> 5) == 1;
		this.rifsMode = ((bytes[1] & 0b00010000) >>> 4) == 1;
		this.htProtection = (byte) (bytes[2] >>> 6);
		this.nongreenfieldHtStasPresent = ((bytes[2] & 0b00100000) >>> 5) == 1;
		this.obssNonHtStasPresent = ((bytes[2] & 0b00001000) >>> 4) == 1;
		this.channelCenterFrequencySegment2 = (byte) (((bytes[2] & 0b00000111) << 5)
				| ((bytes[3] & 0b11111000) >>> 3));
		this.dualBeacon = ((bytes[4] & 0b00000010) >>> 1) == 1;
		this.dualCtsProtection = (bytes[4] & 0b00000001) == 1;
		this.stbcBeacon = (bytes[5] & 0b10000000 >>> 7) == 1;
		byte[] basicHtMcsSet = new byte[16];
		for (int i = 0; i < basicHtMcsSet.length; i++) {
			basicHtMcsSet[i] = bytes[6 + i];
		}
		this.basicHtMcsSet = basicHtMcsSet;
	}

	@Override
	public String getAsBase64String() {
		byte[] htOper = new byte[22];
		htOper[0] = primaryChannel;
		htOper[1] = (byte) (secondaryChannelOffset << 6 | Utils.boolToInt(staChannelWidth) << 5
				| Utils.boolToInt(rifsMode) << 4);
		htOper[2] = (byte) (htProtection << 6 | Utils.boolToInt(nongreenfieldHtStasPresent) << 5
				| Utils.boolToInt(obssNonHtStasPresent) << 3 | channelCenterFrequencySegment2 >>> 5);
		htOper[3] = (byte) (channelCenterFrequencySegment2 << 5);
		htOper[4] = (byte) (Utils.boolToInt(dualBeacon) << 1 | Utils.boolToInt(dualCtsProtection));
		htOper[5] = (byte) (Utils.boolToInt(stbcBeacon) << 7);
		for (int i = 0; i < basicHtMcsSet.length; i++) {
			htOper[6 + i] = basicHtMcsSet[i];
		}
		return Base64.encodeBase64String(htOper);
	}

	@Override
	public boolean matchesForAggregation(OperationElement otherOper) {
		// check everything except basicHtMcsSet
		if (otherOper == null || getClass() == otherOper.getClass()) {
			return false;
		}
		HTOperationElement other = (HTOperationElement) otherOper;
		return channelCenterFrequencySegment2 == other.channelCenterFrequencySegment2
				&& dualBeacon == other.dualBeacon && dualCtsProtection == other.dualCtsProtection
				&& htProtection == other.htProtection
				&& nongreenfieldHtStasPresent == other.nongreenfieldHtStasPresent
				&& obssNonHtStasPresent == other.obssNonHtStasPresent && primaryChannel == other.primaryChannel
				&& rifsMode == other.rifsMode && secondaryChannelOffset == other.secondaryChannelOffset
				&& staChannelWidth == other.staChannelWidth && stbcBeacon == other.stbcBeacon;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(basicHtMcsSet);
		result = prime * result + Objects.hash(channelCenterFrequencySegment2, dualBeacon, dualCtsProtection,
				htProtection, nongreenfieldHtStasPresent, obssNonHtStasPresent, primaryChannel, rifsMode,
				secondaryChannelOffset, staChannelWidth, stbcBeacon);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		HTOperationElement other = (HTOperationElement) obj;
		return Arrays.equals(basicHtMcsSet, other.basicHtMcsSet)
				&& channelCenterFrequencySegment2 == other.channelCenterFrequencySegment2
				&& dualBeacon == other.dualBeacon && dualCtsProtection == other.dualCtsProtection
				&& htProtection == other.htProtection
				&& nongreenfieldHtStasPresent == other.nongreenfieldHtStasPresent
				&& obssNonHtStasPresent == other.obssNonHtStasPresent && primaryChannel == other.primaryChannel
				&& rifsMode == other.rifsMode && secondaryChannelOffset == other.secondaryChannelOffset
				&& staChannelWidth == other.staChannelWidth && stbcBeacon == other.stbcBeacon;
	}
}