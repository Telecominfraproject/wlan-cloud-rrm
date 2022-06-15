# Implementation
This document provides high-level implementation details of the RRM service.

## Framework
`Launcher` is the main class, which creates *clients* and then passes them to
the `RRM` class to run all *modules* (details below). Many of these will run in
their own threads and implement the standard `Runnable` interface.

The service configuration model is specified in the `RRMConfig` class, which can
be provided either via environment variables or as a JSON-serialized file. When
using the static file option, any new/missing fields are appended automatically.
All fields are documented in Javadoc.

The device topology, `DeviceTopology`, is specified as groupings of APs into
disjoint "RF zones" (default `topology.json`). For example:
```JSON
{
  "<zone-name>": ["<serialNumber1>", "<serialNumber2>"],
  "building-A": ["aaaaaaaaaa01", "aaaaaaaaaa02"]
}
```

Device configuration is defined in `DeviceConfig` and applied in a layered
manner via `DeviceLayeredConfig` (default `device_config.json`), merging the
following layers from least to greatest precedence:
* Base/default AP config (`DeviceConfig.createDefault()`)
* Network-wide config (`networkConfig`)
* Per-zone config (`zoneConfig`)
* Per-AP config (`apConfig`)

Logging is handled using [SLF4J]/[Log4j] and configured in
`src/main/resources/log4j.properties`.

## Clients
The *clients* implement connections to external services.

### uCentral Client
`UCentralClient` implements OpenAPI HTTP client calls to the [uCentralGw] and
[uCentralSec] services using [Unirest]. Where possible, request/response models
are defined in the package `com.facebook.openwifirrm.ucentral.gw.models` and
serialized/deserialized using [Gson].

### uCentral Kafka Consumer
`UCentralKafkaConsumer` implements the [Apache Kafka] consumer for uCentral
topics, and passes data to other modules via listener interfaces. This is
wrapped by `KafkaConsumerRunner` to handle graceful shutdown.

### Database Client
`DatabaseManager` handles JDBC connection details for the RRM database and
exposes methods for specific database operations. It uses the
[MySQL Connector/J] driver and [HikariCP] for connection pooling.

## Modules
The *modules* implement the service's application logic.

### Data Collector
`DataCollector` collects data from all OpenWiFi devices as follows:
* Issues WiFi scan commands periodically and handles responses
* Registers Kafka listeners to write records into the RRM database
* Registers config listeners to configure the stats interval in OpenWiFi devices

### Config Manager
`ConfigManager` periodically sends config changes to OpenWiFi devices. Any
desired config changes are applied via listener interfaces, including the output
of RRM algorithms.

### Modeler
`Modeler` subscribes to uCentral device state and wifi scan data, then prepares
it for use by an optimizer.

### API Server
`ApiServer` is an OpenAPI HTTP server written using [Spark], exposing the
following endpoints:
* `/` - Static resources from [Swagger UI] for visualizing and interacting with
  API endpoints
* `/openapi.{yaml,json}` - OpenAPI 3.0 document generated from source code using
  [Swagger Core]
* `/api/v1/<method>` - RRM API methods

## Optimizers
The *optimizers* implement the RRM algorithms, which are described in
[ALGORITHMS.md](ALGORITHMS.md).


[SLF4J]: http://www.slf4j.org/
[Log4j]: https://logging.apache.org/log4j/
[uCentralGw]: https://github.com/Telecominfraproject/wlan-cloud-ucentralgw
[uCentralSec]: https://github.com/Telecominfraproject/wlan-cloud-ucentralsec
[Unirest]: https://github.com/kong/unirest-java
[Gson]: https://github.com/google/gson
[Apache Kafka]: https://kafka.apache.org/
[MySQL Connector/J]: https://dev.mysql.com/doc/connector-j/8.0/en/
[HikariCP]: https://github.com/brettwooldridge/HikariCP
[Spark]: https://sparkjava.com/
[Swagger UI]: https://swagger.io/tools/swagger-ui/
[Swagger Core]: https://github.com/swagger-api/swagger-core
