FROM maven:3-jdk-11 as build
WORKDIR /usr/src/java
COPY . .
RUN mvn clean package -DappendVersionString="$(./scripts/get_build_version.sh)"

FROM adoptopenjdk/openjdk11-openj9:latest
RUN apt-get update && apt-get install -y gettext-base wget
RUN wget https://raw.githubusercontent.com/Telecominfraproject/wlan-cloud-ucentral-deploy/main/docker-compose/certs/restapi-ca.pem \
    -O /usr/local/share/ca-certificates/restapi-ca-selfsigned.pem
RUN mkdir /owrrm-data
WORKDIR /usr/src/java
COPY docker-entrypoint.sh /
COPY --from=build /usr/src/java/target/openwifi-rrm.jar /usr/local/bin/
EXPOSE 16789
ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["java", "-XX:+IdleTuningGcOnIdle", "-Xtune:virtualized", \
     "-jar", "/usr/local/bin/openwifi-rrm.jar", \
     "run", "--config-env", \
     "-t", "/owrrm-data/topology.json", \
     "-d", "/owrrm-data/device_config.json"]
