#!/usr/bin/env bash
set -e

if [ "$SELFSIGNED_CERTS" = 'true' ]; then
    update-ca-certificates
fi

if [[ "$TEMPLATE_CONFIG" = 'true' && ! -f /openwifi/settings.json ]]; then
  UCENTRALCONFIG_USER=${UCENTRALCONFIG_USER:-"tip@ucentral.com"} \
  UCENTRALCONFIG_PASSWORD=${UCENTRALCONFIG_PASSWORD:-"openwifi"} \
  UCENTRALCONFIG_UCENTRALSECHOST=${UCENTRALCONFIG_UCENTRALSECHOST:-"127.0.0.1"} \
  UCENTRALCONFIG_UCENTRALSECPORT=${UCENTRALCONFIG_UCENTRALSECPORT:-"16001"} \
  UCENTRALSOCKETPARAMS_CONNECTTIMEOUTMS=${UCENTRALSOCKETPARAMS_CONNECTTIMEOUTMS:-"2000"} \
  UCENTRALSOCKETPARAMS_SOCKETTIMEOUTMS=${UCENTRALSOCKETPARAMS_SOCKETTIMEOUTMS:-"15000"} \
  UCENTRALSOCKETPARAMS_WIFISCANTIMEOUTMS=${UCENTRALSOCKETPARAMS_WIFISCANTIMEOUTMS:-"45000"} \
  KAFKACONFIG_BOOTSTRAPSERVER=${KAFKACONFIG_BOOTSTRAPSERVER:-"127.0.0.1:9093"} \
  KAFKACONFIG_STATETOPIC=${KAFKACONFIG_STATETOPIC:-"state"} \
  KAFKACONFIG_WIFISCANTOPIC=${KAFKACONFIG_WIFISCANTOPIC:-"wifiscan"} \
  KAFKACONFIG_GROUPID=${KAFKACONFIG_GROUPID:-"rrm-service"} \
  KAFKACONFIG_AUTOOFFSETRESET=${KAFKACONFIG_AUTOOFFSETRESET:-"latest"} \
  DATABASECONFIG_SERVER=${DATABASECONFIG_SERVER:-"127.0.0.1:3306"} \
  DATABASECONFIG_USER=${DATABASECONFIG_USER:-"owrrm"} \
  DATABASECONFIG_PASSWORD=${DATABASECONFIG_PASSWORD:-"openwifi"} \
  DATABASECONFIG_DBNAME=${DATABASECONFIG_DBNAME:-"owrrm"} \
  DATABASECONFIG_DATARETENTIONINTERVALDAYS=${DATABASECONFIG_DATARETENTIONINTERVALDAYS:-"14"} \
  DATACOLLECTORPARAMS_UPDATEINTERVALMS=${DATACOLLECTORPARAMS_UPDATEINTERVALMS:-"5000"} \
  DATACOLLECTORPARAMS_DEVICESTATSINTERVALSEC=${DATACOLLECTORPARAMS_DEVICESTATSINTERVALSEC:-"60"} \
  DATACOLLECTORPARAMS_WIFISCANINTERVALSEC=${DATACOLLECTORPARAMS_WIFISCANINTERVALSEC:-"60"} \
  DATACOLLECTORPARAMS_CAPABILITIESINTERVALSEC=${DATACOLLECTORPARAMS_CAPABILITIESINTERVALSEC:-"3600"} \
  DATACOLLECTORPARAMS_EXECUTORTHREADCOUNT=${DATACOLLECTORPARAMS_EXECUTORTHREADCOUNT:-"3"} \
  CONFIGMANAGERPARAMS_UPDATEINTERVALMS=${CONFIGMANAGERPARAMS_UPDATEINTERVALMS:-"60000"} \
  CONFIGMANAGERPARAMS_CONFIGENABLED=${CONFIGMANAGERPARAMS_CONFIGENABLED:-"true"} \
  CONFIGMANAGERPARAMS_CONFIGDEBOUNCEINTERVALSEC=${CONFIGMANAGERPARAMS_CONFIGDEBOUNCEINTERVALSEC:-"30"} \
  MODELERPARAMS_WIFISCANBUFFERSIZE=${MODELERPARAMS_WIFISCANBUFFERSIZE:-"10"} \
  APISERVERPARAMS_HTTPPORT=${APISERVERPARAMS_HTTPPORT:-"16789"} \
  APISERVERPARAMS_USEBASICAUTH=${APISERVERPARAMS_USEBASICAUTH:-"true"} \
  APISERVERPARAMS_BASICAUTHUSER=${APISERVERPARAMS_BASICAUTHUSER:-"admin"} \
  APISERVERPARAMS_BASICAUTHPASSWORD=${APISERVERPARAMS_BASICAUTHPASSWORD:-"openwifi"} \
  envsubst < /settings.json.tmpl > /openwifi/settings.json
fi

exec "$@"
