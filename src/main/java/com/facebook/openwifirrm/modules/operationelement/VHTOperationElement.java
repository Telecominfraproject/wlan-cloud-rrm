package com.facebook.openwifirrm.modules.operationelement;

import java.util.Arrays;
import java.util.Objects;

import org.apache.commons.codec.binary.Base64;

import com.facebook.openwifirrm.ChannelWidth;
import com.facebook.openwifirrm.Utils;

/**
 * Very High Throughput (VHT) Operation Element, which are potentially present
 * in wifiscan entries. Details in IEEE 802.11-2020 standard.
 */
public class VHTOperationElement implements OperationElement {

	protected final boolean channelWidthIndicator;
	protected final byte channel1;
	protected final byte channel2;
	protected final byte[] vhtMcsForNss;

	/**
	 *
	 * XXX some combinations of channelWidth, channel, channel2, and vhtMcsAtNss are
	 * invalid as defined by 802.11-2020, but this is not checked here. If fidelity
	 * to 802.11 is required, the caller of this method must make sure to pass in
	 * valid parameters.
	 *
	 * @param channelWidth
	 * @param channel1     If the channel is 20 MHz, 40 MHz, or 80 MHz wide, this
	 *                     parameter should be the channel index. E.g., channel 36
	 *                     is the channel centered at 5180 MHz. For a 160 MHz wide
	 *                     channel, this parameter should be the channel index of
	 *                     the 80MHz channel that contains the primary channel. For
	 *                     a 80+80 MHz wide channel, this parameter should be the
	 *                     channel index of the primary channel.
	 * @param channel2     This should be zero unless the channel is 160MHz or 80+80
	 *                     MHz wide. If the channel is 160 MHz wide, this parameter
	 *                     should contain the channel index of the 160 MHz wide
	 *                     channel. If the channel is 80+80 MHz wide, it should be
	 *                     the channel index of the secondary 80 MHz wide channel.
	 * @param vhtMcsForNss An 8-element array where each element is between 0 and 4
	 *                     inclusive. MCS means Modulation and Coding Scheme. NSS
	 *                     means Number of Spatial Streams. There can be 1, 2, ...,
	 *                     or 8 spatial streams. For each NSS, the corresponding
	 *                     element in the array should specify which MCSs are
	 *                     supported for that NSS in the following manner: 0
	 *                     indicates support for VHT-MCS 0-7, 1 indicates support
	 *                     for VHT-MCS 0-8, 2 indicates support for VHT-MCS 0-9, and
	 *                     3 indicates that no VHT-MCS is supported for that NSS.
	 *                     For the specifics of what each VHT-MCS is, see IEEE
	 *                     802.11-2020, Table "21-29" through Table "21-60".
	 * @return base64 encoded vht operator as a String
	 */
	public VHTOperationElement(ChannelWidth channelWidth, byte channel1, byte channel2, byte[] vhtMcsForNss) {
		this.channelWidthIndicator = !(channelWidth == ChannelWidth.MHz_20 || channelWidth == ChannelWidth.MHz_40);
		this.channel1 = channel1;
		this.channel2 = channel2;
		this.vhtMcsForNss = vhtMcsForNss;
	}

	public VHTOperationElement() {
		this(ChannelWidth.MHz_20, (byte) 36, (byte) 0, new byte[] { 0, 0, 0, 0, 0, 0, 0, 0 });
	}

	/**
	 *
	 * @param vhtOperString must be a properly formatted vht operation element (see
	 *                      802.11-2020 standard) string
	 */
	public VHTOperationElement(String vhtOperString) {
		byte[] bytes = Base64.decodeBase64(vhtOperString);
		this.channelWidthIndicator = bytes[0] == 1;
		this.channel1 = bytes[1];
		this.channel2 = bytes[2];
		byte[] vhtMcsForNss = new byte[8];
		vhtMcsForNss[0] = (byte) (bytes[3] >>> 6);
		vhtMcsForNss[1] = (byte) ((bytes[3] & 0b00110000) >>> 4);
		vhtMcsForNss[2] = (byte) ((bytes[3] & 0b00001100) >>> 2);
		vhtMcsForNss[3] = (byte) (bytes[3] & 0b00000011);
		vhtMcsForNss[4] = (byte) (bytes[4] >>> 6);
		vhtMcsForNss[5] = (byte) ((bytes[4] & 0b00110000) >>> 4);
		vhtMcsForNss[6] = (byte) ((bytes[4] & 0b00001100) >>> 2);
		vhtMcsForNss[7] = (byte) (bytes[4] & 0b00000011);
		this.vhtMcsForNss = vhtMcsForNss;
	}

	@Override
	public String getAsBase64String() {
		byte[] vhtOper = new byte[5];
		// overflow shouldn't matter, we only care about the raw bit representation
		byte channelCenterFrequencySegment0 = channel1;
		byte channelCenterFrequencySegment1 = channel2;

		vhtOper[0] = (byte) (Utils.boolToInt(channelWidthIndicator));
		vhtOper[1] = channelCenterFrequencySegment0;
		vhtOper[2] = channelCenterFrequencySegment1;
		vhtOper[3] = (byte) (vhtMcsForNss[0] << 6 | vhtMcsForNss[1] << 4 | vhtMcsForNss[2] << 2 | vhtMcsForNss[3]);
		vhtOper[4] = (byte) (vhtMcsForNss[4] << 6 | vhtMcsForNss[5] << 4 | vhtMcsForNss[6] << 2 | vhtMcsForNss[7]);
		return Base64.encodeBase64String(vhtOper);
	}

	@Override
	public boolean matchesForAggregation(OperationElement otherOper) {
		// check everything except vhtMcsForNss
		if (otherOper == null || getClass() == otherOper.getClass()) {
			return false;
		}
		VHTOperationElement other = (VHTOperationElement) otherOper;
		return channel1 == other.channel1 && channel2 == other.channel2
				&& channelWidthIndicator == other.channelWidthIndicator;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(vhtMcsForNss);
		result = prime * result + Objects.hash(channel1, channel2, channelWidthIndicator);
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
		VHTOperationElement other = (VHTOperationElement) obj;
		return channel1 == other.channel1 && channel2 == other.channel2
				&& channelWidthIndicator == other.channelWidthIndicator
				&& Arrays.equals(vhtMcsForNss, other.vhtMcsForNss);
	}
}