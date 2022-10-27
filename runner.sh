#!/usr/bin/env bash

# script to help run the binary with specific jvm and jvm options
# Defaults to openj9. Turn on hotspot by setting env JVM_HOTSPOT

JVM=$1
BIN=$2
shift 2

COMMON_PARAMETERS=(
-XX:+CompactStrings
)

if [ -n "$JVM_HOTSPOT" ]; then
# for hotspot
PARAMETERS=(
"${COMMON_PARAMETERS[@]}"
-XX:+UseG1GC
-XX:+UseStringDeduplication
)
else
# for openj9
PARAMETERS=(
"${COMMON_PARAMETERS[@]}"
-XX:+IdleTuningGcOnIdle
-Xtune:virtualized
)
fi

"$JVM" \
	"${PARAMETERS[@]}" \
	-jar "$BIN" \
	"$@"
