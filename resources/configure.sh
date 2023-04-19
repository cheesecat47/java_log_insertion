#!/bin/bash

AGENT_DIR="./src/main/java/${1//.//}"
mkdir -p "$AGENT_DIR"
sed "1s/PACKAGENAME/${1}/" ./resources/MyAgent.java > "$AGENT_DIR/MyAgent.java"