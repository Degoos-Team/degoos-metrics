#!/bin/bash
# Pelican install script for cloudflared: the official release binary into /mnt/server, checked against the SHA-256
# digest GitHub publishes for the release asset.
set -euo pipefail

apt-get update -qq
apt-get install -y -qq curl ca-certificates jq >/dev/null

cd /mnt/server
VERSION="${CLOUDFLARED_VERSION}"
ARCH="$(uname -m | sed -e 's/x86_64/amd64/' -e 's/aarch64/arm64/')"
ASSET="cloudflared-linux-${ARCH}"
RELEASE="https://api.github.com/repos/cloudflare/cloudflared/releases/tags/${VERSION}"

EXPECTED="$(curl -fsSL "${RELEASE}" | jq -r --arg a "${ASSET}" '.assets[] | select(.name == $a) | .digest' | sed 's/^sha256://')"
if [ -z "${EXPECTED}" ] || [ "${EXPECTED}" = "null" ]; then
    echo "No SHA-256 digest published for ${ASSET} ${VERSION}" >&2
    exit 1
fi

URL="https://github.com/cloudflare/cloudflared/releases/download/${VERSION}/${ASSET}"
echo "Downloading ${URL}"
curl -fsSL -o cloudflared.new "${URL}"
echo "${EXPECTED}  cloudflared.new" | sha256sum -c -
mv cloudflared.new cloudflared
chmod +x cloudflared

echo "cloudflared ${VERSION} installed"
