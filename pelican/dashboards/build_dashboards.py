"""Builds the Grafana dashboards (JSON) for the Hytale servers from the Degoos Metrics series.

Run: python -I pelican/dashboards/build_dashboards.py  ->  pelican/dashboards/Hytale/*.json
Copy the folder into the Grafana server's dashboards/ directory; Grafana picks the files up within seconds.
"""
import json
import pathlib

HERE = pathlib.Path(__file__).resolve().parent
DS = {"type": "prometheus", "uid": "prometheus"}
SEL = 'job="hytale", server=~"$server"'


def target(expr, legend, ref="A", instant=False):
    return {"datasource": DS, "expr": expr, "legendFormat": legend, "refId": ref, "instant": instant, "range": not instant}


def grid(x, y, w, h):
    return {"x": x, "y": y, "w": w, "h": h}


def row(title, y, panel_id):
    return {"type": "row", "title": title, "id": panel_id, "collapsed": False, "gridPos": grid(0, y, 24, 1), "panels": []}


def stat(title, pos, targets, unit="none", mappings=None, thresholds=None, description="", text_mode="auto"):
    return {
        "type": "stat", "title": title, "description": description, "datasource": DS, "gridPos": pos, "targets": targets,
        "fieldConfig": {"defaults": {
            "unit": unit,
            "mappings": mappings or [],
            "thresholds": thresholds or {"mode": "absolute", "steps": [{"color": "green", "value": None}]},
        }, "overrides": []},
        "options": {"reduceOptions": {"calcs": ["lastNotNull"], "fields": "", "values": False},
                    "colorMode": "background" if mappings else "value", "graphMode": "none",
                    "textMode": text_mode, "justifyMode": "auto", "orientation": "auto"},
    }


def timeseries(title, pos, targets, unit="none", description="", stack=False, min_zero=True, thresholds=None,
               overrides=None):
    custom = {"lineWidth": 1, "fillOpacity": 10, "showPoints": "never", "spanNulls": False,
              "stacking": {"mode": "normal" if stack else "none", "group": "A"}}
    if thresholds:
        custom["thresholdsStyle"] = {"mode": "dashed"}
    defaults = {"unit": unit, "custom": custom}
    if min_zero:
        defaults["min"] = 0
    if thresholds:
        defaults["thresholds"] = thresholds
    return {
        "type": "timeseries", "title": title, "description": description, "datasource": DS, "gridPos": pos,
        "targets": targets, "fieldConfig": {"defaults": defaults, "overrides": overrides or []},
        "options": {"legend": {"displayMode": "list", "placement": "bottom", "showLegend": True},
                    "tooltip": {"mode": "multi", "sort": "desc"}},
    }


def network():
    up_mappings = [{"type": "value", "options": {
        "0": {"text": "Down", "color": "red", "index": 0},
        "1": {"text": "Up", "color": "green", "index": 1}}}]
    budget = {"mode": "absolute", "steps": [{"color": "green", "value": None}, {"color": "orange", "value": 80},
                                             {"color": "red", "value": 100}]}
    panels = [
        row("Now", 0, 1),
        stat("Server status", grid(0, 1, 12, 4), [target(f'up{{{SEL}}}', "{{server}}", instant=True)],
             mappings=up_mappings, description="Whether Prometheus could scrape each server's Degoos Metrics endpoint."),
        stat("Players online", grid(12, 1, 4, 4), [target(f'sum(hytale_players{{{SEL}}})', "Players", instant=True)],
             description="All players on the selected servers."),
        stat("Players per server", grid(16, 1, 8, 4),
             [target(f'sum by (server) (hytale_players{{{SEL}}})', "{{server}}", instant=True)]),
        row("Players", 5, 2),
        timeseries("Players per server", grid(0, 6, 12, 8),
                   [target(f'sum by (server) (hytale_players{{{SEL}}})', "{{server}}")], stack=True),
        timeseries("Players per world", grid(12, 6, 12, 8),
                   [target(f'sum by (server, world) (hytale_world_players{{{SEL}}}) > 0', "{{server}} / {{world}}")],
                   description="Worlds with at least one player."),
        row("Performance", 14, 3),
        timeseries("Tick budget used (busiest world)", grid(0, 15, 12, 8),
                   [target(f'max by (server) (hytale_world_tick_seconds{{{SEL}, stat="avg"}} '
                           f'* on (server, world) group_left hytale_world_tps_target{{{SEL}}}) * 100', "{{server}}")],
                   unit="percent", thresholds=budget,
                   description="Average tick length over the last 10 s as a share of the tick budget (1 / target TPS) "
                               "in each server's busiest world. Above 100% the world falls behind its target TPS."),
        timeseries("Longest tick (busiest world)", grid(12, 15, 12, 8),
                   [target(f'max by (server) (hytale_world_tick_seconds{{{SEL}, stat="max"}})', "{{server}}")],
                   unit="s", description="Longest single tick in the last 10 s. Spikes show stutters."),
        row("Memory", 23, 4),
        timeseries("Container memory", grid(0, 24, 12, 8),
                   [target(f'hytale_container_memory_bytes{{{SEL}}}', "{{server}}"),
                    target(f'hytale_container_memory_limit_bytes{{{SEL}}}', "{{server}} limit", ref="B")],
                   unit="bytes", description="Memory used by each server's container against its limit (dashed).",
                   overrides=[{"matcher": {"id": "byRegexp", "options": ".* limit"},
                               "properties": [{"id": "custom.lineStyle", "value": {"fill": "dash", "dash": [10, 10]}},
                                              {"id": "custom.fillOpacity", "value": 0}]}]),
        timeseries("JVM heap used", grid(12, 24, 12, 8),
                   [target(f'hytale_jvm_memory_bytes{{{SEL}, area="heap_used"}}', "{{server}}")], unit="bytes"),
        timeseries("Time spent in GC", grid(0, 32, 12, 8),
                   [target(f'sum by (server) (rate(hytale_jvm_gc_seconds_total{{{SEL}}}[$__rate_interval]))',
                           "{{server}}")],
                   unit="percentunit", description="Share of wall time the JVM spent collecting garbage."),
        timeseries("Direct buffers", grid(12, 32, 12, 8),
                   [target(f'hytale_jvm_buffer_pool_bytes{{{SEL}, pool="direct"}}', "{{server}}")], unit="bytes",
                   description="Off-heap direct buffers (networking, chunk data)."),
        row("Process", 40, 5),
        stat("Uptime", grid(0, 41, 12, 4), [target(f'hytale_uptime_seconds{{{SEL}}}', "{{server}}", instant=True)],
             unit="s"),
        timeseries("JVM threads", grid(12, 41, 12, 8), [target(f'hytale_jvm_threads{{{SEL}}}', "{{server}}")]),
    ]
    for i, panel in enumerate(panels, start=10):
        panel.setdefault("id", i)
    return {
        "uid": "hytale-network",
        "title": "Hytale network",
        "description": "Every Hytale server through Degoos Metrics: status, players, tick budget and memory.",
        "tags": ["hytale", "degoos-metrics"],
        "timezone": "browser",
        "editable": True,
        "schemaVersion": 39,
        "refresh": "30s",
        "time": {"from": "now-6h", "to": "now"},
        "templating": {"list": [{
            "name": "server", "label": "Server", "type": "query", "datasource": DS,
            "query": {"query": 'label_values(up{job="hytale"}, server)', "refId": "server"},
            "definition": 'label_values(up{job="hytale"}, server)',
            "includeAll": True, "multi": True, "allValue": ".*",
            "current": {"selected": True, "text": ["All"], "value": ["$__all"]},
            "refresh": 2, "sort": 1,
        }]},
        "panels": panels,
    }


def bargauge(title, pos, targets, unit="none", description=""):
    return {
        "type": "bargauge", "title": title, "description": description, "datasource": DS, "gridPos": pos,
        "targets": targets,
        "fieldConfig": {"defaults": {"unit": unit, "min": 0,
                                     "thresholds": {"mode": "absolute", "steps": [{"color": "blue", "value": None}]}},
                        "overrides": []},
        "options": {"orientation": "horizontal", "displayMode": "gradient", "showUnfilled": True,
                    "reduceOptions": {"calcs": ["lastNotNull"], "fields": "", "values": False}},
    }


def game_dashboard(uid, title, description, tags, panels):
    for i, panel in enumerate(panels, start=10):
        panel.setdefault("id", i)
    return {
        "uid": uid, "title": title, "description": description, "tags": ["hytale", "degoos-metrics", *tags],
        "timezone": "browser", "editable": True, "schemaVersion": 39, "refresh": "30s",
        "time": {"from": "now-7d", "to": "now"}, "templating": {"list": []}, "panels": panels,
    }



EX = 'job="hytale", server="minigames"'


def example_game():
    """An example game dashboard for a plugin that registers `mygame_*` series (see the main README)."""
    r = "[$__range]"
    panels = [
        row("Live", 0, 1),
        stat("Online", grid(0, 1, 8, 4), [target(f'sum(hytale_players{{{EX}}})', "Online", instant=True)]),
        stat("Parties", grid(8, 1, 16, 4),
             [target(f'sum by (size) (mygame_parties{{{EX}}})', "{{size}} players", instant=True)]),
        timeseries("Matches started per hour", grid(0, 5, 12, 8),
                   [target(f'sum by (mode) (increase(mygame_matches_started_total{{{EX}}}[1h]))', "{{mode}}")]),
        bargauge("Average arena build time", grid(12, 5, 12, 8),
                 [target(f'sum by (map) (increase(mygame_arena_build_seconds_sum{{{EX}}}{r})) / '
                         f'sum by (map) (increase(mygame_arena_build_seconds_count{{{EX}}}{r}))', "{{map}}",
                         instant=True)], unit="s"),
    ]
    return game_dashboard("example-game", "Example game", "An example of a game plugin's own dashboard.",
                          ["example"], panels)


def write(dashboards, out_dir):
    """Writes each dashboard (relative path to JSON data) under out_dir."""
    for path, data in dashboards.items():
        out = pathlib.Path(out_dir) / path
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
        print(f"wrote {out}")


if __name__ == "__main__":
    write({"Hytale/hytale-network.json": network(), "Examples/example-game.json": example_game()}, HERE)
