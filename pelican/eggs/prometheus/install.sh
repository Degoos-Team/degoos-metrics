#!/bin/bash
# Pelican install script for Prometheus: official release binary into /mnt/server, plus a starter config.
set -euo pipefail

apt-get update -qq
apt-get install -y -qq curl ca-certificates tar >/dev/null

cd /mnt/server
VERSION="${PROMETHEUS_VERSION#v}"
ARCH="$(uname -m | sed -e 's/x86_64/amd64/' -e 's/aarch64/arm64/')"
NAME="prometheus-${VERSION}.linux-${ARCH}"
URL="https://github.com/prometheus/prometheus/releases/download/v${VERSION}/${NAME}.tar.gz"

echo "Downloading ${URL}"
curl -fsSL -o prometheus.tar.gz "${URL}"
curl -fsSL -o sha256sums.txt "https://github.com/prometheus/prometheus/releases/download/v${VERSION}/sha256sums.txt"
EXPECTED="$(grep " ${NAME}.tar.gz\$" sha256sums.txt | cut -d' ' -f1)"
echo "${EXPECTED}  prometheus.tar.gz" | sha256sum -c -
tar -xzf prometheus.tar.gz --strip-components=1 "${NAME}/prometheus" "${NAME}/promtool"
rm -f prometheus.tar.gz sha256sums.txt
chmod +x prometheus promtool
mkdir -p data targets

# The config is created once and never overwritten, so a reinstall (version upgrade) keeps the scrape jobs.
if [ ! -f prometheus.yml ]; then
cat > prometheus.yml <<'EOF'
# Prometheus for the Hytale servers. Reload by restarting the server in Pelican.
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  # Self-scrape: the target port must match this server's own allocation (9090 by default); change it if the allocation differs.
  - job_name: prometheus
    static_configs:
      - targets: ["localhost:9090"]

  # Degoos Metrics on every Hytale server: one file per server in targets/, for example targets/survival.yml:
  #   - targets: ["172.18.0.1:9521"]
  #     labels: { server: survival }
  # The bearer token is the same `token` as in each server's mods/Degoos_Metrics/metrics.json, kept in
  # hytale-token.txt next to this file.
  - job_name: hytale
    metrics_path: /metrics
    authorization:
      type: Bearer
      credentials_file: hytale-token.txt
    file_sd_configs:
      - files: ["targets/*.yml"]
EOF
fi
[ -f hytale-token.txt ] || head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n' > hytale-token.txt

echo "Prometheus ${VERSION} installed"
