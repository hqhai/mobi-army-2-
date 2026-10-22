#!/bin/bash
# MobiArmy 2 for macOS: updates the game from the server, then starts it. Needs Java 21 (adoptium.net).
cd "$(dirname "$0")"
SERVER=10.10.0.172
BASE="http://$SERVER:8123/downloads/update"

remote=$(curl -fsS --max-time 5 "$BASE/version.txt" 2>/dev/null | tr -d '\r\n ')
local_ver=$(cat version.txt 2>/dev/null | tr -d '\r\n ')
if [ -n "$remote" ] && [ "$remote" != "$local_ver" ]; then
    echo "Dang cap nhat game len ban $remote ..."
    tmp=$(mktemp -d)
    if curl -fsS --max-time 300 -o "$tmp/lib.zip" "$BASE/lib.zip" && unzip -q "$tmp/lib.zip" -d "$tmp/lib" \
        && ls "$tmp/lib"/*.jar >/dev/null 2>&1; then
        rm -rf lib && mv "$tmp/lib" lib && echo "$remote" > version.txt && echo "Cap nhat xong."
    else
        echo "Khong cap nhat duoc. Choi bang ban hien tai."
    fi
    rm -rf "$tmp"
fi

JAVA=""
if [ -x jre/bin/java ]; then JAVA=jre/bin/java
elif /usr/libexec/java_home -v 21+ >/dev/null 2>&1; then JAVA="$(/usr/libexec/java_home -v 21+)/bin/java"
elif command -v java >/dev/null 2>&1; then JAVA=java
fi
if [ -z "$JAVA" ]; then
    echo "Chua co Java 21. Tai va cai Temurin 21 tai https://adoptium.net roi mo lai file nay."
    open "https://adoptium.net/temurin/releases/?os=mac&version=21&package=jre" 2>/dev/null
    read -p "Bam Enter de dong..."
    exit 1
fi
mkdir -p data
# GLFW on macOS must run on the first thread.
"$JAVA" -XstartOnFirstThread -Darmy.host=$SERVER -Darmy.port=8122 -Duser.home="$PWD/data" -cp "lib/*" com.mygdx.game.DesktopLauncher
