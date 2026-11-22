"""Builds the Pelican egg JSON files (PLCN_v3) from the install scripts next to this file.

Run: python -I pelican/eggs/build_eggs.py  ->  pelican/eggs/egg-prometheus.json, pelican/eggs/egg-grafana.json
"""
import json
import pathlib
import uuid

HERE = pathlib.Path(__file__).resolve().parent
IMAGE = "ghcr.io/pelican-eggs/yolks:debian"
RAW = "https://raw.githubusercontent.com/Degoos-Team/degoos-metrics/main/pelican/eggs"
INSTALLER = "ghcr.io/pelican-eggs/installers:debian"
# Fixed so a re-import updates the same egg instead of creating a new one.
NAMESPACE = uuid.UUID("6b0d5f4e-6a37-4c5e-9d1e-3f0c1b6a2d10")


def variable(name, env, default, rules, description, sort, editable=True):
    return {
        "name": name,
        "description": description,
        "env_variable": env,
        "default_value": default,
        "user_viewable": True,
        "user_editable": editable,
        "rules": rules,
        "sort": sort,
    }


def egg(key, name, description, startup, done, variables, tags):
    return {
        "_comment": "Built by degoos-metrics/pelican/eggs/build_eggs.py; edit the sources there.",
        "meta": {"version": "PLCN_v3", "update_url": f"{RAW}/egg-{key}.json"},
        "exported_at": "2026-11-21T00:00:00+00:00",
        "name": name,
        "author": "noreply@degoos.xyz",
        "uuid": str(uuid.uuid5(NAMESPACE, key)),
        "description": description,
        "image": None,
        "tags": tags,
        "features": [],
        "docker_images": {"Debian": IMAGE},
        "file_denylist": [],
        "startup_commands": {"Default": startup},
        "config": {
            "files": "{}",
            "startup": json.dumps({"done": done}),
            "logs": "{}",
            "stop": "^C",
        },
        "scripts": {
            "installation": {
                "script": (HERE / key / "install.sh").read_text(encoding="utf-8"),
                "container": INSTALLER,
                "entrypoint": "bash",
            }
        },
        "variables": variables,
    }


EGGS = {
    "prometheus": egg(
        "prometheus",
        "Prometheus",
        "Prometheus time-series database (official release binary). Scrapes the Degoos Metrics endpoints of the "
        "Hytale servers over the Docker bridge. Give it an allocation on the bridge IP, never a public one.",
        "./prometheus --config.file=prometheus.yml --storage.tsdb.path=data "
        "--storage.tsdb.retention.time={{RETENTION}} --web.listen-address=0.0.0.0:{{SERVER_PORT}}",
        "Server is ready to receive web requests.",
        [
            variable("Prometheus version", "PROMETHEUS_VERSION", "3.16.0", ["required", "string", "regex:/^v?\\d+\\.\\d+\\.\\d+$/"],
                     "Release to install; reinstall the server to change it (config and data are kept).", 1),
            variable("Retention", "RETENTION", "30d", ["required", "string", "regex:/^\\d+[dwy]$/"],
                     "How long samples are kept, e.g. 30d.", 2),
        ],
        ["monitoring", "prometheus"],
    ),
    "grafana": egg(
        "grafana",
        "Grafana",
        "Grafana OSS (official release). Login only, no anonymous access or sign-up; the admin password is "
        "generated at install in admin-password.txt. Give it an allocation on the bridge IP; reach it by SSH tunnel or "
        "through a Cloudflare Tunnel with Cloudflare Access, which can also sign people in.",
        "./grafana/bin/grafana server --homepath grafana --config grafana.ini",
        "HTTP Server Listen",
        [
            variable("Grafana version", "GRAFANA_VERSION", "13.3.1", ["required", "string", "regex:/^v?\\d+\\.\\d+\\.\\d+$/"],
                     "Release to install; reinstall the server to change it (config and data are kept).", 1),
            variable("Prometheus URL", "PROMETHEUS_URL", "http://172.18.0.1:9090", ["required", "url"],
                     "Address of the Prometheus server, as Grafana reaches it over the Docker bridge.", 2),
            variable("Root URL", "GRAFANA_ROOT_URL", "http://localhost:3000/", ["required", "url"],
                     "Address people open Grafana at, e.g. https://grafana.example.com/ behind Cloudflare Access.", 3),
            variable("Cloudflare Access login", "CF_ACCESS_ENABLED", "false", ["required", "string", "in:true,false"],
                     "true signs people in with the Cloudflare Access token instead of a second Grafana login.", 4),
            variable("Cloudflare Access team", "CF_ACCESS_TEAM", "", ["nullable", "string", "regex:/^[a-z0-9-]*$/"],
                     "Team name, the <team> in <team>.cloudflareaccess.com.", 5),
            variable("Cloudflare Access audience", "CF_ACCESS_AUD", "", ["nullable", "string", "regex:/^[0-9a-f]*$/"],
                     "Application Audience (AUD) tag of the Access application in front of Grafana.", 6),
            variable("Admin email", "GRAFANA_ADMIN_EMAIL", "", ["nullable", "email"],
                     "Access user who becomes Grafana server admin; every other Access user is a Viewer.", 7),
        ],
        ["monitoring", "grafana"],
    ),
    "cloudflared": egg(
        "cloudflared",
        "Cloudflare Tunnel",
        "cloudflared (official release) running a remotely managed Cloudflare Tunnel. It only connects out to "
        "Cloudflare, so it publishes private services such as Grafana behind Cloudflare Access without opening a "
        "port. The allocation only serves cloudflared's own metrics; give it one on the bridge IP.",
        "./cloudflared tunnel --no-autoupdate --metrics 0.0.0.0:{{SERVER_PORT}} run",
        "Registered tunnel connection",
        [
            variable("cloudflared version", "CLOUDFLARED_VERSION", "2026.11.1", ["required", "string", "regex:/^\\d{4}\\.\\d+\\.\\d+$/"],
                     "Release to install; reinstall the server to change it.", 1),
            # cloudflared reads TUNNEL_TOKEN from the environment, so the token never appears in the startup command.
            {**variable("Tunnel token", "TUNNEL_TOKEN", "", ["required", "string", "min:32"],
                        "Token of the remotely managed tunnel (Cloudflare Zero Trust, Networks, Tunnels). Secret.", 2),
             "user_viewable": False},
        ],
        ["networking", "cloudflare"],
    ),
}

for key, data in EGGS.items():
    out = HERE / f"egg-{key}.json"
    out.write_text(json.dumps(data, indent=4, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {out}")
