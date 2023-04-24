#!/bin/bash

ISSLAB_DIR="${2}/src/main/java/kr/ac/knu/isslab"
AGENT_DIR="${2}/src/main/java/${1//.//}"
RESOURCE_DIR="${2}/src/main/resources"

mkdir -p "${AGENT_DIR}"

sed "1s/PACKAGENAME/${1}/" "${2}/resources/MyAgent.java" >"${AGENT_DIR}/MyAgent.java"

sed "s/PACKAGENAME/${1}/g" "${ISSLAB_DIR}/InvokeStaticInstrumenter.java" >"${ISSLAB_DIR}/InvokeStaticInstrumenter.java.tmp"
mv "${ISSLAB_DIR}/InvokeStaticInstrumenter.java.tmp" "${ISSLAB_DIR}/InvokeStaticInstrumenter.java"

sed "s/PACKAGENAME/${1}/g" "${ISSLAB_DIR}/WjtpTransformer.java" >"${ISSLAB_DIR}/WjtpTransformer.java.tmp"
mv "${ISSLAB_DIR}/WjtpTransformer.java.tmp" "${ISSLAB_DIR}/WjtpTransformer.java"

sed "s/PACKAGENAME/${1}/g" "${RESOURCE_DIR}/logback-spring.xml" >"${RESOURCE_DIR}/logback-spring.xml.tmp"
mv "${RESOURCE_DIR}/logback-spring.xml.tmp" "${RESOURCE_DIR}/logback-spring.xml"
