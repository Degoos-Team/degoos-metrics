# Pelican eggs

Two small eggs host the monitoring stack as ordinary Pelican servers, so they are started, stopped, backed up and
resource-limited like every other server. Both download the official release binary at install, verify its checksum,
and run on `ghcr.io/pelican-eggs/yolks:debian`. The versions are Pelican variables.

| Egg | File | Server name (example) |
|---|---|---|
| Prometheus | `eggs/egg-prometheus.json` | `prometheus` |
| Grafana | `eggs/egg-grafana.json` | `grafana` |
| Cloudflare Tunnel | `eggs/egg-cloudflared.json` | `cloudflared` |

## Building the eggs

The egg JSON files are generated from the install scripts in `eggs/prometheus/` and `eggs/grafana/`:

```
python -I pelican/eggs/build_eggs.py
```

Import the resulting `egg-prometheus.json` and `egg-grafana.json` in the Pelican panel. Their ids are fixed, so
re-importing updates the same egg.

## Allocations

Give both servers (and each Hytale server's metrics port) allocations on the Docker bridge IP `172.18.0.1` only, never
on the node's public IP. Docker then publishes them on the bridge: other containers on the node can reach them, the
internet cannot.

- Prometheus: `172.18.0.1:9090`
- Grafana: `172.18.0.1:3000`
- Hytale metrics: one port per server, for example `172.18.0.1:9520` and up (see the [main README](../README.md))

## Prometheus

The install creates, once and never overwritten, `prometheus.yml` with a `hytale` job that reads `targets/*.yml` and
sends the bearer token from `hytale-token.txt` (generated at install; use its content as `token` in every server's
`metrics.json`). Add one file per server in `targets/` and restart the server in Pelican to apply it.

## Grafana

Grafana keeps its own login, with anonymous access and sign-up off. The admin password is generated at install into
`admin-password.txt`; read it in the Pelican file manager (log in as `admin`), change the password in Grafana, then delete the file and the `admin_password` line of `grafana.ini` (a reinstall does not recreate them). Its `secret_key` (sessions and stored
secrets) is generated the same way into `secret-key.txt`; keep that file, since changing it makes stored data-source
secrets unreadable. Grafana has no public port of its own. Reach it either through the Cloudflare Tunnel below or
through an SSH tunnel to the node:

```
ssh -L 3000:172.18.0.1:3000 <node>
```

then browse to http://localhost:3000.

## Dashboards

The dashboards live in `dashboards/` as generated JSON (`python -I pelican/dashboards/build_dashboards.py`). Copy
the `Hytale` folder into the Grafana server's `dashboards/` directory: Grafana loads every file there into a folder of
the same name within seconds, with no restart. Edit the generator, not the JSON, and copy again.

- **Hytale network** (`hytale-network`): server status, players per server and world, tick budget used and longest
  tick in each server's busiest world, container and JVM memory, GC time, direct buffers, uptime and threads, with a
  server filter.
- **Example game** (`example-game`, in `Examples/`): how a game plugin's own dashboard looks, built from the `mygame_*`
  series of the main README's examples. A network keeps its real game dashboards in its own (private) repository and
  imports the helpers of `build_dashboards.py` (`target`, `stat`, `timeseries`, `bargauge`, `game_dashboard`, `write`).

## Cloudflare Tunnel

The Cloudflare Tunnel server runs a remotely managed Cloudflare Tunnel: it only connects out to Cloudflare, so nothing listens
publicly on the node. Its allocation (on the bridge IP) only serves cloudflared's own metrics.

1. In Cloudflare Zero Trust, Networks, Tunnels, create a tunnel of type cloudflared and copy its token into the
   server's `TUNNEL_TOKEN` variable (hidden from the startup command; cloudflared reads it from the environment).
2. Add a public hostname to the tunnel, for example `grafana.<domain>` to `http://172.18.0.1:3000`.
3. Before sharing the hostname, protect it with a Cloudflare Access application whose policy allows only the staff
   emails.
4. Optionally let Access sign people in to Grafana too: on the Grafana server set `Root URL` to the public address,
   `Cloudflare Access team`, `Cloudflare Access audience` (the application's AUD tag) and `Admin email`, then
   `Cloudflare Access login` to `true`. Grafana then checks the token Access adds to every request and creates the
   user on first visit: the admin email becomes Grafana server admin, everyone else a Viewer. The local `admin` keeps
   working through the SSH tunnel. Servers installed before this option need the `[auth.jwt]` section of
   `eggs/grafana/install.sh` added to their `grafana.ini` by hand, since the install never overwrites that file.
