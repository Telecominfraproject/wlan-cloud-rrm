# OpenWiFi RRM Service
OpenWiFi uCentral-based radio resource management (RRM) service, providing a
cloud-based Wi-Fi RRM layer for APs running the OpenWiFi SDK.

This service collects data from OpenWiFi APs (e.g. Wi-Fi scans, stats,
capabilities) via the uCentral Gateway and Kafka, and integrates with the
OpenWiFi Provisioning service to perform optimization across configured
"venues". It pushes new device configuration parameters to APs after RRM
algorithms are run (manually or periodically).

## Requirements
* **Running:** JRE 11.
* **Building:** JDK 11 and [Apache Maven].

## Building
```
$ mvn package [-DskipTests]
```
This will build a runnable JAR located at `target/openwifi-rrm.jar`.

## Testing
```
$ mvn test
```
Unit tests are written using [JUnit 5].

## Usage
```
$ java -jar openwifi-rrm.jar [-h]
```

To start the service, use the `run` command while providing configuration via
either environment variables (`--config-env`) or a static JSON file
(`--config-file`, default `settings.json`). The following data is *required*:
* Service configuration
    * Env: `SERVICECONFIG_PRIVATEENDPOINT`, `SERVICECONFIG_PUBLICENDPOINT`
    * JSON: `serviceConfig` structure
* Kafka broker URL
    * Env: `KAFKACONFIG_BOOTSTRAPSERVER`
    * JSON: `kafkaConfig` structure
* MySQL database credentials
    * Env: `DATABASECONFIG_SERVER`, `DATABASECONFIG_USER`, `DATABASECONFIG_PASSWORD`
    * JSON: `databaseConfig` structure

## Docker
Docker builds can be launched using the provided [Dockerfile](Dockerfile).

## OpenAPI
This service provides an OpenAPI HTTP interface on the port specified in the
service configuration (`moduleConfig.apiServerParams`). An auto-generated
OpenAPI 3.0 document is hosted at the endpoints `/openapi.{yaml,json}` and is
written to `openapi.yaml` in the project root during the Maven "compile" phase.

## Implementation
See [IMPLEMENTATION.md](IMPLEMENTATION.md) for service architecture details and
[ALGORITHMS.md](ALGORITHMS.md) for descriptions of the RRM algorithms.

## License
See [LICENSE](LICENSE).


[Apache Maven]: https://maven.apache.org/
[JUnit 5]: https://junit.org/junit5/
