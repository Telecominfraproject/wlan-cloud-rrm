package com.facebook.openwifirrm.modules.aggregators;

public interface Aggregator<T> {

	public void addValue(T value);

	public T getAggregate();
}
