package com.facebook.openwifirrm.modules.aggregators;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class MeanAggregatorTest {
	@Test
	public void testEmptyAndNonEmptyAndReset() {
		final double eps = 0.000001;

		MeanAggregator agg = new MeanAggregator();

		// default mean is 0
		assertEquals(0, agg.getAggregate(), eps);

		// adding 0 (the mean) does not change the mean
		agg.addValue(0.0);
		assertEquals(0, agg.getAggregate(), eps);

		// add an "int"
		agg.addValue(1.0);
		assertEquals(0.5, agg.getAggregate(), eps);

		// add a double
		agg.addValue(3.5);
		assertEquals(1.5, agg.getAggregate(), eps);

		// add a negative number
		agg.addValue(-0.5);
		assertEquals(1.0, agg.getAggregate(), eps);

		// adding the mean does not change the mean
		agg.addValue(1.0);
		assertEquals(1.0, agg.getAggregate(), eps);

		// test reset
		agg.reset();
		assertEquals(0, agg.getAggregate(), eps);
	}
}
