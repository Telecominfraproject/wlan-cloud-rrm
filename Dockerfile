FROM maven:3-jdk-11 as build
WORKDIR /usr/src/java
COPY . .
RUN mvn clean package

FROM openjdk:11
WORKDIR /usr/src/java
COPY --from=build /usr/src/java/target/openwifi-rrm.jar /usr/local/bin/
ENTRYPOINT ["java", "-jar", "/usr/local/bin/openwifi-rrm.jar", "run"]
