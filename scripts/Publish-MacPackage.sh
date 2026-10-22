#!/bin/bash
# Builds web/downloads/MobiArmy2-Mac.tar.gz from the current client build; tar keeps the launcher executable.
set -e
root="$(cd "$(dirname "$0")/.." && pwd)"
pkg="$root/runtime/pkg/MobiArmy2-Mac"
rm -rf "$pkg" && mkdir -p "$pkg"
cp -r "$root/desktop-build/build/install/army2-lan-desktop/lib" "$pkg/lib"
cp "$root/scripts/client-package-mac/MobiArmy2.command" "$root/scripts/client-package-mac/HUONG-DAN.txt" "$pkg/"
cp "$root/runtime/pkg/MobiArmy2-PC/version.txt" "$pkg/version.txt"
tar -C "$root/runtime/pkg" --owner=0 --group=0 --mode='u+rwX,go+rX' -czf "$root/web/downloads/MobiArmy2-Mac.tar.gz.tmp" \
    --mode=755 MobiArmy2-Mac/MobiArmy2.command --mode='u+rw,go+r' --exclude=MobiArmy2-Mac/MobiArmy2.command MobiArmy2-Mac
mv -f "$root/web/downloads/MobiArmy2-Mac.tar.gz.tmp" "$root/web/downloads/MobiArmy2-Mac.tar.gz"
echo "Mac package: web/downloads/MobiArmy2-Mac.tar.gz"
