package com.facebook.openwifirrm.modules.aggregators;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class MeanAggregatorTest {

	private static double eps;

	@BeforeAll
	public static void setup(TestInfo testInfo) {
		eps = 0.000001;
	}

	@Test
	public void testEmptyAndFull() {
		MeanAggregator agg = new MeanAggregator();

		// default mean is 0
		assertEquals(0, agg.getAggregate(), eps);

		// adding 0 (the mean) does not change the mean
		agg.addValue(0.0);
		assertEquals(0, agg.getAggregate(), eps);

		// add an "int"
		agg.addValue(1.0);
		assertEquals(0.5, agg.getAggregate(), eps);

		// add a "float"
		agg.addValue(3.5);
		assertEquals(1.5, agg.getAggregate(), eps);

		// add a negative number
		agg.addValue(-0.5);
		assertEquals(1.0, agg.getAggregate(), eps);

		// adding the mean does not change the mean
		agg.addValue(1.0);
		assertEquals(1.0, agg.getAggregate(), eps);
	}
}
