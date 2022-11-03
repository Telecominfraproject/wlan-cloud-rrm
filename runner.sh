#!/usr/bin/env sh

# script to help run the binary with specific jvm and jvm options
# Defaults to openj9. Switch by setting JVM_IMPL environment variable

JAVA_BIN=$1
JAR=$2
shift 2

COMMON_PARAMETERS=" \
-XX:+CompactStrings \
"

JVM_IMPL="${JVM_IMPL:-openj9}"
EXTRA_JVM_FLAGS="${EXTRA_JVM_FLAGS:-}"

if [ "$JVM_IMPL" = "hotspot" ]; then
# for hotspot
PARAMETERS="\
$COMMON_PARAMETERS \
-XX:+UseG1GC \
-XX:+UseStringDeduplication \
$EXTRA_JVM_FLAGS \
"
elif [ "$JVM_IMPL" = "openj9" ]; then
# for openj9
PARAMETERS=" \
$COMMON_PARAMETERS \
-XX:+IdleTuningGcOnIdle \
-Xtune:virtualized
$EXTRA_JVM_FLAGS \
"
else
echo "Invalid JVM_IMPL option"
exit 1
fi

"$JAVA_BIN" \
	$PARAMETERS \
	-jar "$JAR" \
	$@
