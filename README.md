# OpenWiFi RRM Service
[See here](owrrm/README.md) for details.

## Project Structure
This is an [Apache Maven] project with the following modules:
* `lib-cloudsdk` - OpenWiFi CloudSDK Java Library
* `owrrm` - OpenWiFi RRM Service

## Requirements
* **Running:** JRE 11.
* **Building:** JDK 11 and [Apache Maven].

## Building
```
$ mvn package [-DskipTests]
```
This will build a runnable JAR located at `owrrm/target/openwifi-rrm.jar`.

Alternatively, Docker builds can be launched using the provided
[Dockerfile](Dockerfile).

## Testing
```
$ mvn test
```
Unit tests are written using [JUnit 5].

## Code Style
Code is auto-formatted using [Spotless] with a custom Eclipse style config (see
[spotless/eclipse-java-formatter.xml](spotless/eclipse-java-formatter.xml)).
This can be applied via Maven (but is *not* enforced at build time):
```
$ mvn spotless:apply
```

## License
See [LICENSE](LICENSE).


[Apache Maven]: https://maven.apache.org/
[JUnit 5]: https://junit.org/junit5/
[Spotless]: https://github.com/diffplug/spotless
