# OpenWiFi RRM Service
[See here](owrrm/README.md) for details.

## Requirements
* **Running:** JRE 11.
* **Building:** JDK 11 and [Apache Maven].

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
[Spotless]: https://github.com/diffplug/spotless
