# CurseForge page

What we paste into the Degoos Metrics project on CurseForge. Upload the JAR from the GitHub release, not a local
build. Logo: `docs/curseforge/logo.png`.

- Name: Degoos Metrics
- Summary: Prometheus metrics for Hytale servers. Players, tick times, memory, and room for your own plugin's numbers.
- Category: Server utility
- License: MIT
- Source: https://github.com/Degoos-Team/degoos-metrics
- Issues: https://github.com/Degoos-Team/degoos-metrics/issues

## Description

We run a handful of Hytale servers and got tired of finding out about lag from players. So we wrote this: a small
plugin that exposes a `/metrics` page Prometheus can scrape, and then we graph everything in Grafana.

Out of the box you get players (per server and per world), tick times per world, JVM memory and GC, and container
memory if you're on Linux/Docker. It doesn't need any other plugin.

It won't open anything until you tell it to. After the first start, edit `mods/Degoos_Metrics/metrics.json`, pick a
port and a token (32 characters or more) and restart:

```json
{ "bind": "0.0.0.0", "port": 9520, "token": "put-a-long-random-string-here" }
```

Then add the server to Prometheus with that token as a bearer token. Please don't leave the port open to the
internet. We bind it to a private Docker network, but a firewall rule does the job too.

If you make plugins: you can add your own numbers to the same page (gauges, counters, summaries). There's a small
bridge class that only uses Java types, so you can treat Degoos Metrics as optional and not compile against it at all.
The README has the details and a copy-paste example.

The GitHub repo also has the Pelican eggs we use for Prometheus and Grafana, and the Grafana dashboard for the server
stats.

Made by Gael Rial and Antonio Terrero (Degoos Team). Questions or bugs: GitHub issues.
