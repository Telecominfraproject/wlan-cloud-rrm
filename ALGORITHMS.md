# Algorithms
This document describes the RRM algorithms implemented by this service.

## Channel Optimization
`ChannelOptimizer` and its subclasses implement various channel optimization
algorithms, with the goal of minimizing co-channel interference.

### `RandomChannelInitializer`
This algorithm randomly selects a channel, and then assigns all APs to that
selected channel. This is only for testing and re-initialization.

Parameters:
* `mode`: "random"

### `LeastUsedChannelOptimizer`
This algorithm assigns the channel of the OWF APs based on the following logic:
1. If no other APs are on the same channel as the OWF AP, then the algorithm
   keeps the OWF AP on the same channel.
2. If any other APs are on the same channel as the OWF AP, and the OWF AP scan
   results indicate unused channels in its RF vicinity, then the algorithm
   randomly assigns one of those channels to the AP.
3. If any other APs are on the same channel as this AP and the OWF AP scan
   results indicate all available channels are occupied locally, then the
   algorithm assigns the channel with the least number of APs in its WiFi scan
   result.

Parameters:
* `mode`: "least_used"

### `UnmanagedApAwareChannelOptimizer`
Building on the least used channel assignment algorithm, this algorithm can
additionally (1) prioritize non-OWF ("unmanaged") APs over OWF APs and (2) keep
track of the current assignment. This algorithm will try to avoid assigning the
OWF APs to a channel with many non-OWF APs and prevent assigning subsequent OWF
APs to the same channel as previously-assigned OWF APs. The assignment decisions
are based on the following logic:
1. If no other APs are on the same channel as the OWF AP, then the algorithm
   keeps the OWF AP on the same channel.
2. If any other APs are on the same channel as the OWF AP, and the OWF AP scan
   results indicate unused channels in its RF vicinity, then the algorithm
   randomly assigns one of those channels to the AP.
3. If any other APs are on the same channel as this AP and the OWF AP scan
   results indicate all available channels are occupied locally, then the
   algorithm assigns the channel with the least channel weight (*W*):
   $$ W = (D \times N) + (1 \times M) $$
   where *D > 1* is the default weight, *N* is the number of non-OWF APs, and
   *M* is the number of OWF APs.

Parameters:
* `mode`: "unmanaged_aware"

## Transmit Power Control
`TPC` and its subclasses implement various transmit power control algorithms,
with the goal of minimizing interference while avoiding coverage holes.

### `RandomTxPowerinitializer`
This algorithm randomly selects a Tx power value, and then assigns all APs to
the value. This is only for testing and re-initialization.

Parameters:
* `mode`: "random"

### `MeasurementBasedApClientTPC`
This algorithm tries to assign the Tx power of the OWF APs based on the
associated clients of each AP. The strategy is described in the steps below (for
each AP):
1. Determine the operating SNR & MCS on the client side.
2. If this SNR is greater than the minimum required SNR to achieve this MCS,
   reduce the Tx power. Otherwise, no change is made.
3. If there are multiple clients, the above decision is made based on the client
   with lowest SNR.

Parameters:
* `mode`: "measure_ap_client"

### `MeasurementBasedApApTPC`
This algorithm tries to assign the Tx power of the OWF APs by getting a set of
APs to transmit at a power level to minimize overlapping coverage. The power
levels of these APs will be determined by the following steps:
1. Through WiFi scans, collect the list of RSSIs (from *N-1* APs) reported by
   every AP.
2. For each AP, find the lowest RSSI in its list. If this is higher than
   `RSSI_threshold`, decrease the Tx power of this AP by
   `(RSSI_lowest - RSSI_threshold)`.
3. If this is lower than `RSSI_threshold`, increase the Tx power of this AP by
   `(RSSI_threshold - RSSI_lowest)`.

Parameters:
* `mode`: "measure_ap_ap"
