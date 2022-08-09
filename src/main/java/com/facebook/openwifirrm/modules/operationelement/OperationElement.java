package com.facebook.openwifirrm.modules.operationelement;

/**
 * Represents an Operation Element (in IEEE 802.11-2020)./**
 */
public interface OperationElement {

	boolean matchesForAggregation(OperationElement otherOper);
}