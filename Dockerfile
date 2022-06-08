FROM maven:3-jdk-11 as build
WORKDIR /usr/src/java
COPY . .
RUN mvn clean package

FROM openjdk:11
RUN apt-get update && apt-get install -y gettext-base
RUN wget https://raw.githubusercontent.com/Telecominfraproject/wlan-cloud-ucentral-deploy/main/docker-compose/certs/restapi-ca.pem \
    -O /usr/local/share/ca-certificates/restapi-ca-selfsigned.pem
WORKDIR /usr/src/java
RUN mkdir /openwifi
COPY settings.json.tmpl /
COPY docker-entrypoint.sh /
COPY --from=build /usr/src/java/target/openwifi-rrm.jar /usr/local/bin/
EXPOSE 16789
ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["java", "-jar", "/usr/local/bin/openwifi-rrm.jar", "run", "-c", "/openwifi/settings.json"]
