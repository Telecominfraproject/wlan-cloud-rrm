#!/usr/bin/env sh

BIN=$1
shift 1

echo $@

java \
	-XX:+IdleTuningGcOnIdle -Xtune:virtualized \
	-jar "$BIN" \
	"$@"
