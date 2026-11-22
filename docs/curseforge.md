# CurseForge page

Text and settings for the Degoos Metrics project on CurseForge. Upload the JAR of a GitHub release, never a local
build.

- **Name:** Degoos Metrics
- **Summary (short description):** Prometheus metrics for your Hytale server: players, tick times and memory, plus an
  API for other plugins to add their own.
- **Category:** Server utility (admin tools)
- **License:** MIT
- **Source:** https://github.com/Degoos-Team/degoos-metrics
- **Issues:** https://github.com/Degoos-Team/degoos-metrics/issues
- **Dependencies:** none
- **Game version:** the Hytale server versions the release was tested on

## Description

Degoos Metrics serves your Hytale server's numbers in the Prometheus format, so you can graph them in Grafana and get
alerts when something goes wrong.

**What you get**

- Players online, per server and per world
- Tick target and tick times per world (average and longest of the last 10 seconds), to spot lag
- JVM heap, buffer pools, threads and garbage collection time
- Process and container memory (Linux) and uptime

**Safe by default**

- Nothing is served until you set a port and a token of at least 32 characters
- Every request needs the token; everything else is refused
- Collectors read snapshots and never touch a world thread, and a failing collector is skipped instead of breaking the
  page

**For plugin authors**

Register your own gauges, counters and summaries in the same page, with bounded labels so a bug can't flood your
Prometheus. The `MetricsBridge` class takes JDK types only, so your plugin can use Degoos Metrics as an optional
dependency without compiling against it. Per-instance worlds (one per match or dungeon) can share one `world` label.

**Setup**

1. Put the JAR in `mods/` and start the server once.
2. In `mods/Degoos_Metrics/metrics.json`, set `port` and `token`, then restart.
3. Point Prometheus at `http://<server>:<port>/metrics` with the token as a bearer token. Keep the port off the public
   internet.

The repository also has Pelican eggs for Prometheus, Grafana and a Cloudflare Tunnel, and a Grafana dashboard
generator with a ready network dashboard.
