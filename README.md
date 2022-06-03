# OpenWiFi RRM Service
OpenWiFi uCentral-based radio resource management service.

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
The command-line interface is implemented using [picocli].

Service configuration is done via a JSON file (default `settings.json`) and will
require the following data:
* uCentral credentials (`uCentralConfig`)
* Kafka broker URL (`kafkaConfig`)
* MySQL database credentials (`databaseConfig`)

## Docker
Docker builds can be launched using the provided [Dockerfile](Dockerfile).

## OpenAPI
This service provides an OpenAPI HTTP interface on the port specified in the
service configuration (`moduleConfig.apiServerParams`). An auto-generated
OpenAPI 3.0 document is hosted at the endpoints `/openapi.{yaml,json}`.

## Implementation
See [IMPLEMENTATION.md](IMPLEMENTATION.md) for service architecture details and
[ALGORITHMS.md](ALGORITHMS.md) for descriptions of the RRM algorithms.

## License
See [LICENSE](LICENSE).


[Apache Maven]: https://maven.apache.org/
[JUnit 5]: https://junit.org/junit5/
[picocli]: https://picocli.info/
