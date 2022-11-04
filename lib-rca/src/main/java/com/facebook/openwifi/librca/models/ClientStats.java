package com.facebook.openwifi.librca.models;

import java.util.List;

/** Statistic Model for each client. Maily handle KPI and metric calculations */
public class ClientStats {
    /** Unique ID for each client. */
    String station;
    
    /** LinkStats that are of the same station(client). Because of disconnection issues, a client might be connected to several BSSIDs. */
    List<LinkStats> connections;
}
