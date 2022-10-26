FROM maven:3-eclipse-temurin-11 as build
WORKDIR /usr/src/java
COPY . .
RUN mvn clean package -pl owrrm -am -DappendVersionString="$(./scripts/get_build_version.sh)"

FROM ibm-semeru-runtimes:open-11-jre
RUN apt-get update && apt-get install -y gettext-base
ADD https://raw.githubusercontent.com/Telecominfraproject/wlan-cloud-ucentral-deploy/main/docker-compose/certs/restapi-ca.pem \
  /usr/local/share/ca-certificates/restapi-ca-selfsigned.pem
RUN mkdir /owrrm-data
WORKDIR /usr/src/java
COPY docker-entrypoint.sh /
COPY runner.sh /
COPY --from=build /usr/src/java/owrrm/target/openwifi-rrm.jar /usr/local/bin/
EXPOSE 16789 16790
ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["/runner.sh", "/usr/local/bin/openwifi-rrm.jar", \
    "--config-env", \
    "-t", "/owrrm-data/topology.json", \
    "-d", "/owrrm-data/device_config.json"]
