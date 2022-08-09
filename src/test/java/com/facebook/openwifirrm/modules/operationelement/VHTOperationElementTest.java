package com.facebook.openwifirrm.modules.operationelement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.facebook.openwifirrm.ChannelWidth;

/** Test the Very High Throughput (VHT) Operation Element */
public class VHTOperationElementTest {
	// TODO even in the 5G band, the VHT operation is determined by the ht oper and
	// vht oper. Test that for a 5G channel, ht oper is also correct.

	@Test
	void testGetVhtOper() {
		ChannelWidth channelWidth = ChannelWidth.MHz_20;
		byte channel1 = (byte) 36;
		byte channel2 = (byte) 0;
		byte[] vhtMcsForNss = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0 };
		VHTOperationElement vhtOperObj = new VHTOperationElement(channelWidth, channel1, channel2, vhtMcsForNss);
		String vhtOper = vhtOperObj.getAsBase64String();
		String expectedVhtOperString = "ACQAAAA=";
		assertEquals(expectedVhtOperString, vhtOper);
		VHTOperationElement recreatedVhtOper = new VHTOperationElement(vhtOper);
		assertEquals(recreatedVhtOper, vhtOperObj);
		assertEquals(expectedVhtOperString, recreatedVhtOper.getAsBase64String());

		channelWidth = ChannelWidth.MHz_80;
		channel1 = 58;
		channel2 = 0;
		vhtMcsForNss = new byte[] { 1, 1, 0, 0, 0, 0, 0, 1 };
		vhtOperObj = new VHTOperationElement(channelWidth, channel1, channel2, vhtMcsForNss);
		vhtOper = vhtOperObj.getAsBase64String();
		expectedVhtOperString = "AToAUAE=";
		assertEquals(expectedVhtOperString, vhtOper);
		recreatedVhtOper = new VHTOperationElement(vhtOper);
		assertEquals(recreatedVhtOper, vhtOperObj);
		assertEquals(expectedVhtOperString, recreatedVhtOper.getAsBase64String());

		channelWidth = ChannelWidth.MHz_160;
		channel1 = 42;
		channel2 = 50;
		vhtOperObj = new VHTOperationElement(channelWidth, channel1, channel2, vhtMcsForNss);
		vhtOper = vhtOperObj.getAsBase64String();
		expectedVhtOperString = "ASoyUAE=";
		assertEquals(expectedVhtOperString, vhtOper);
		recreatedVhtOper = new VHTOperationElement(vhtOper);
		assertEquals(recreatedVhtOper, vhtOperObj);
		assertEquals(expectedVhtOperString, recreatedVhtOper.getAsBase64String());
	}
}
