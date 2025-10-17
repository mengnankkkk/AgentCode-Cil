#!/bin/bash
# HarmonySafeAgent Unix/Linux/macOS Launcher
# Usage: agent-safe [options]

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="$SCRIPT_DIR/../target/harmony-agent.jar"

# Check if JAR exists
if [ ! -f "$JAR_PATH" ]; then
    echo "Error: harmony-agent.jar not found at $JAR_PATH"
    echo "Please run: mvn clean package"
    exit 1
fi

# Launch the application
java -jar "$JAR_PATH" "$@"
