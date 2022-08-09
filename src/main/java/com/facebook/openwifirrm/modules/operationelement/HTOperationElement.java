package com.facebook.openwifirrm.modules.operationelement;

import java.util.Arrays;
import java.util.Objects;

import org.apache.commons.codec.binary.Base64;

/**
 * High Throughput (HT) Operation Element, which are potentially present in
 * wifiscan entries. Introduced in 802.11n (2009).
 */
public class HTOperationElement implements OperationElement {

	private final byte primaryChannel;
	private final byte secondaryChannelOffset;
	private final boolean staChannelWidth;
	private final boolean rifsMode;
	private final byte htProtection;
	private final boolean nongreenfieldHtStasPresent;
	private final boolean obssNonHtStasPresent;
	private final byte channelCenterFrequencySegment2;
	private final boolean dualBeacon;
	private final boolean dualCtsProtection;
	private final boolean stbcBeacon;
	private final byte[] basicHtMcsSet;

	/**
	 * Constructs an {@code HTOperationElement} using the given field values. See
	 * 802.11 for more details.
	 *
	 * @param primaryChannel                 channel number of the primary channel
	 * @param secondaryChannelOffset         Indicates the offset of the secondary
	 *                                       channel relative to the primary
	 *                                       channel. A 1 indicates that the
	 *                                       secondary channel is above the primary
	 *                                       channel. A 3 indicates that the
	 *                                       secondary channel is below the primary
	 *                                       channel. A 0 indicates that there is no
	 *                                       secondary channel present. The value 2
	 *                                       is reserved.
	 * @param staChannelWidth                Defines the channel widths that can be
	 *                                       used to transmit to the STA. With
	 *                                       exceptions, false allows a 20 MHz
	 *                                       channel width. True allows use of any
	 *                                       channel width in the supported channel
	 *                                       width set. See 802.11 for exceptions.
	 * @param rifsMode                       True if RIFS is permitted; false
	 *                                       otherwise.
	 * @param htProtection                   A 0 indicates no protection mode. A 1
	 *                                       indicates nonmember protection mode. A
	 *                                       2 indicates 20 MHz protection mode. A 3
	 *                                       indicates non-HT mixed mode
	 * @param nongreenfieldHtStasPresent     False if all HT STAs that are
	 *                                       associated are HT-greenfield capable or
	 *                                       all HT peer mesh STAs are HT-greenfield
	 *                                       capable; true otherwise.
	 * @param obssNonHtStasPresent           Indicates if the use of protection for
	 *                                       non-HT STAs by overlapping BSSs is
	 *                                       determined to be desirable. See 802.11
	 *                                       for details.
	 * @param channelCenterFrequencySegment2 Defines the channel center frequency
	 *                                       for a 160 or 80+80 MHz BSS bandwidth
	 *                                       with NSS support less than Max VHT NSS.
	 *                                       See 802.11 for details.
	 * @param dualBeacon                     False if no STBC beacon is transmitted;
	 *                                       true otherwise.
	 * @param dualCtsProtection              False if dual CTS protection is not
	 *                                       required; true otherwise.
	 * @param stbcBeacon                     False in a primary beacon. True in an
	 *                                       STBC beacon.
	 */
	public HTOperationElement(byte primaryChannel, byte secondaryChannelOffset, boolean staChannelWidth,
			boolean rifsMode, byte htProtection, boolean nongreenfieldHtStasPresent, boolean obssNonHtStasPresent,
			byte channelCenterFrequencySegment2, boolean dualBeacon, boolean dualCtsProtection, boolean stbcBeacon) {
		/*
		 * XXX some combinations of these parameters may be invalid as defined by
		 * 802.11-2020, but this is not checked here. If fidelity to 802.11 is required,
		 * the caller of this method must make sure to pass in valid parameters. The
		 * 802.11-2020 specification has more details about the parameters.
		 */
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

	/** Default constructor. */
	public HTOperationElement() {
		this((byte) 1, (byte) 0, false, false, (byte) 0, true, false, (byte) 0, false, false, false);
	}

	/**
	 * Constructs an {@code HTOperationElement} by decoding {@code htOperString}.
	 *
	 * @param htOper must be a String representing a base64 encoded properly
	 *               formatted ht operation element (see 802.11)
	 */
	public HTOperationElement(String htOper) {
		byte[] bytes = Base64.decodeBase64(htOper);
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