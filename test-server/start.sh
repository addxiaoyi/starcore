#!/bin/bash
# StarCore Plugin Test Server Launcher
# Paper 1.21.11 Server for testing StarCore plugin

SERVER_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_FILE="$SERVER_DIR/paper-1.21.11.jar"

echo "=========================================="
echo "  StarCore Test Server Launcher"
echo "=========================================="

# Check if Paper JAR exists
if [ ! -f "$JAR_FILE" ]; then
    echo "ERROR: paper-1.21.11.jar not found!"
    echo ""
    echo "Please download Paper 1.21.11:"
    echo "  1. Visit: https://papermc.io/downloads/paper"
    echo "  2. Select version: 1.21.11"
    echo "  3. Download the JAR file"
    echo "  4. Save it as: paper-1.21.11.jar"
    echo "  5. Place it in this directory: $SERVER_DIR"
    echo ""
    echo "Or run this command:"
    echo "  curl -L -o paper-1.21.11.jar 'https://api.papermc.io/v2/projects/paper/versions/1.21.11/builds/128/downloads/paper-1.21.11-128.jar'"
    exit 1
fi

# Copy StarCore plugin to plugins folder
PLUGIN_JAR="$SERVER_DIR/../target/starcore-0.1.0-SNAPSHOT.jar"
PLUGINS_DIR="$SERVER_DIR/plugins"

mkdir -p "$PLUGINS_DIR"

if [ -f "$PLUGIN_JAR" ]; then
    cp "$PLUGIN_JAR" "$PLUGINS_DIR/"
    echo "[OK] StarCore plugin copied to plugins/"
else
    echo "[WARN] StarCore JAR not found at: $PLUGIN_JAR"
    echo "       Build with: cd .. && mvn clean package"
fi

# Create eula.txt if not exists
if [ ! -f "$SERVER_DIR/eula.txt" ]; then
    echo "eula=true" > "$SERVER_DIR/eula.txt"
    echo "[OK] EULA accepted (eula.txt created)"
fi

# Create server.properties if not exists
if [ ! -f "$SERVER_DIR/server.properties" ]; then
    cat > "$SERVER_DIR/server.properties" << 'EOF'
server-port=25565
enable-query=true
query.port=25565
level-name=world
enable-rcon=false
level-seed=
server-ip=
allow-flight=false
level-type=FLAT
view-distance=10
spawn-protection=0
online-mode=false
max-players=10
allow-nether=true
motd=StarCore Test Server
EOF
    echo "[OK] Server properties created"
fi

# Start the server
echo ""
echo "Starting Paper server..."
echo "=========================================="
java -Xmx2G -Xms1G -jar "$JAR_FILE" --nogui
