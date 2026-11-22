#!/bin/bash
# Pelican install script for Grafana OSS: official release into /mnt/server/grafana, a locked-down grafana.ini,
# a generated admin password and the Prometheus data source.
set -euo pipefail

apt-get update -qq
apt-get install -y -qq curl ca-certificates tar >/dev/null

cd /mnt/server
VERSION="${GRAFANA_VERSION#v}"
ARCH="$(uname -m | sed -e 's/x86_64/amd64/' -e 's/aarch64/arm64/')"
URL="https://dl.grafana.com/oss/release/grafana-${VERSION}.linux-${ARCH}.tar.gz"

echo "Downloading ${URL}"
curl -fsSL -o grafana.tar.gz "${URL}"
curl -fsSL -o grafana.tar.gz.sha256 "${URL}.sha256"
echo "$(cut -d' ' -f1 grafana.tar.gz.sha256)  grafana.tar.gz" | sha256sum -c -
rm -rf grafana
mkdir grafana
tar -xzf grafana.tar.gz --strip-components=1 -C grafana
rm -f grafana.tar.gz grafana.tar.gz.sha256
mkdir -p data logs plugins provisioning/datasources provisioning/dashboards provisioning/alerting dashboards

# Signs sessions and encrypts stored secrets; generated once so it is never Grafana's public default.
[ -f secret-key.txt ] || head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n' > secret-key.txt
chmod 600 secret-key.txt
# The admin password only matters on the first start, when Grafana creates the admin user, so it is generated with
# the first grafana.ini only. Read it in the Pelican file manager, change it in Grafana, then delete the file and the
# admin_password line; a reinstall never brings it back.
if [ ! -f grafana.ini ]; then
    head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n' > admin-password.txt
    chmod 600 admin-password.txt
fi

# Created once and never overwritten, so a reinstall (version upgrade) keeps local changes.
if [ ! -f grafana.ini ]; then
cat > grafana.ini <<'EOF'
[paths]
data = /home/container/data
logs = /home/container/logs
plugins = /home/container/plugins
provisioning = /home/container/provisioning

[server]
http_addr = 0.0.0.0
http_port = ${SERVER_PORT}
root_url = ${GRAFANA_ROOT_URL}

[security]
admin_user = admin
admin_password = $__file{/home/container/admin-password.txt}
secret_key = $__file{/home/container/secret-key.txt}
disable_gravatar = true
cookie_samesite = strict

[users]
allow_sign_up = false
allow_org_create = false

[auth.anonymous]
enabled = false

[snapshots]
external_enabled = false

[analytics]
reporting_enabled = false
check_for_updates = false
check_for_plugin_updates = false

[news]
news_feed_enabled = false

[plugins]
preinstall_disabled = true

# Single sign-on through Cloudflare Access (off unless CF_ACCESS_ENABLED=true): Grafana trusts the signed token
# Access adds to every request, checked against the team's keys and this application's audience tag.
[auth.jwt]
enabled = ${CF_ACCESS_ENABLED}
header_name = Cf-Access-Jwt-Assertion
email_claim = email
username_claim = email
jwk_set_url = https://${CF_ACCESS_TEAM}.cloudflareaccess.com/cdn-cgi/access/certs
cache_ttl = 60m
expect_claims = {"aud": "${CF_ACCESS_AUD}", "iss": "https://${CF_ACCESS_TEAM}.cloudflareaccess.com"}
auto_sign_up = true
allow_assign_grafana_admin = true
role_attribute_path = email == '${GRAFANA_ADMIN_EMAIL}' && 'GrafanaAdmin' || 'Viewer'

[log]
mode = console
EOF
fi

if [ ! -f provisioning/datasources/prometheus.yml ]; then
cat > provisioning/datasources/prometheus.yml <<'EOF'
apiVersion: 1
datasources:
  - name: Prometheus
    uid: prometheus
    type: prometheus
    access: proxy
    url: ${PROMETHEUS_URL}
    isDefault: true
    editable: false
EOF
fi

if [ ! -f provisioning/dashboards/files.yml ]; then
cat > provisioning/dashboards/files.yml <<'EOF'
apiVersion: 1
providers:
  - name: files
    type: file
    allowUiUpdates: true
    options:
      path: /home/container/dashboards
      foldersFromFilesStructure: true
EOF
fi

echo "Grafana ${VERSION} installed"
