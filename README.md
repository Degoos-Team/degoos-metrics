# Degoos Metrics

A small Hytale server plugin for every server of a network. It serves Prometheus metrics over HTTP (text format
0.0.4), scraped by a private Prometheus over the Docker bridge, and lets game plugins register
their own metrics into the same page.

- JDK `HttpServer` only: no runtime dependencies besides Kayle.
- `GET /metrics` with `Authorization: Bearer <token>`; everything else is refused (401 without the token, then 404 for
  other paths and 405 for other methods).
- Collectors read thread-safe snapshots at scrape time and never hop onto a world thread. A collector that fails is
  logged once and skipped; the rest of the page is still served.
- Labels are bounded: values match `[A-Za-z0-9_.:-]{1,64}` and a metric keeps at most 500 series.

## Metrics

Generic, on every server:

| Metric | Type | Labels |
|---|---|---|
| `hytale_players` | gauge | |
| `hytale_world_players` | gauge | `world` |
| `hytale_world_count` | gauge | `world` |
| `hytale_world_tps_target` | gauge | `world` |
| `hytale_world_tick_seconds` | gauge | `world`, `stat` = avg, max (last 10 s) |
| `hytale_jvm_memory_bytes` | gauge | `area` = heap_used, heap_committed, heap_max, non_heap_used |
| `hytale_jvm_buffer_pool_bytes` | gauge | `pool` |
| `hytale_jvm_threads` | gauge | |
| `hytale_jvm_gc_collections_total` | counter | `gc` |
| `hytale_jvm_gc_seconds_total` | counter | `gc` |
| `hytale_process_resident_bytes` | gauge | |
| `hytale_container_memory_bytes` | gauge | |
| `hytale_container_memory_limit_bytes` | gauge | |
| `hytale_uptime_seconds` | gauge | |

The process and container figures come from `/proc/self/status` and cgroup v2, so they exist on Linux only; a value
that cannot be read is not emitted. The container limit is absent when the container has none. World names are
sanitized into label values (other characters become `_`, truncated to 64).

### World groups

A plugin that creates a world per instance (for example `dungeon-<id>`) would add a series per world to the three
`hytale_world_*` metrics. Such plugins register a world grouper (a function from world name to group label, or null to
keep the world's own label; see `MetricsBridge.groupWorlds` below). The label of a world is the first non-null group
from the groupers (in registration order), sanitized like any label value, otherwise the world's own sanitized name. A
grouper that throws counts as null and is logged once.

Worlds that share a label are merged:

| Metric | Merged value |
|---|---|
| `hytale_world_players` | sum |
| `hytale_world_tps_target` | maximum |
| `hytale_world_tick_seconds` `stat="avg"` | maximum of the averages (the busiest world) |
| `hytale_world_tick_seconds` `stat="max"` | maximum |
| `hytale_world_count` | number of loaded worlds with that label |

## Configuration

`mods/Degoos_Metrics/metrics.json` is written with these defaults when missing:

```json
{ "bind": "0.0.0.0", "port": 0, "token": "" }
```

Nothing is served until `port` is 1 to 65535 and `token` has at least 32 characters. The plugin then logs one line
saying why, stays loaded, and the registry keeps working for other plugins. The token is never logged. Inside the
container, `bind` stays `0.0.0.0`: exposure is decided by the allocation's IP (see below).

The JDK HTTP server's timeouts are bounded at start: `sun.net.httpserver.maxReqTime` = 10 s and `maxRspTime` = 30 s.
They are JVM-global system properties, so they are only set when no one set them before (and affect any other JDK
HttpServer in the same JVM).

## Network setup

1. In Pelican, give each server an extra allocation for metrics on the Docker bridge IP `172.18.0.1`. The allocation
   must stay on that IP, never the node's public one: the plugin binds `0.0.0.0` inside the container and the
   allocation's IP alone keeps the port off the internet (the token is a second guard, not the only one):

   | Server | Port |
   |---|---|
   | lobby | 9520 |
   | survival | 9521 |
   | minigames | 9522 |

   (Example names and ports: pick one free port per server.)

2. In that server's `metrics.json`, set `port` to its allocation and `token` to the content of the Prometheus
   server's `hytale-token.txt` (the same token on every server). Restart the server (stop, upload the JAR to `mods/`,
   start; never swap the JAR while it runs).
3. In the Prometheus server, add `targets/<server>.yml`, for example `targets/minigames.yml`:

   ```yaml
   - targets: ["172.18.0.1:9522"]
     labels: { server: minigames }
   ```

4. Restart the Prometheus server. The `hytale` job in its `prometheus.yml` already reads `targets/*.yml` and sends the
   token from `hytale-token.txt`.

See [pelican/README.md](pelican/README.md) for the Prometheus and Grafana eggs.

## Registering metrics from a game plugin

Declare an optional dependency in the game plugin's manifest:

```json
"OptionalDependencies": { "Degoos:Metrics": "*" }
```

At start, when the plugin is loaded, register with an owner string (the plugin name) and the metric names carrying the
plugin's prefix:

```kotlin
val registry = DegoosMetrics.registry   // always available once the class is loaded
val owner = "MyGame"

// Gauge: the callback runs at scrape time on the HTTP thread. Keep it quick, read snapshots, never wait for a world.
registry.gauge("mygame_parties", "Parties online.", listOf("size"), owner) {
    listOf(Sample(2.0, "2"), Sample(1.0, "3"))
}

// Counter and summary handles are backed by atomic adders and safe from any thread.
val started = registry.counter("mygame_matches_started_total", "Matches started.", listOf("mode"), owner)
started.inc("solo")

val build = registry.summary("mygame_arena_build_seconds", "Arena build time.", listOf("map"), owner)
build.observe(1.8, "temple")
```

`observedCounter(...)` is a callback-read counter for totals kept elsewhere. At shutdown call
`registry.unregister(owner)` to remove everything the plugin registered.

Rules:

- Names match `[a-zA-Z_:][a-zA-Z0-9_:]*`; a duplicate name throws `IllegalArgumentException`.
- Label values match `[A-Za-z0-9_.:-]{1,64}`: no player names, UUIDs or run ids. A sample with an invalid value is
  dropped and logged once per metric; a wrong number of label values throws.
- At most 500 series per metric; extra series are dropped and logged once per metric. The cap never frees slots until
  the metric is unregistered.
- Gauge samples with a repeated label set are dropped (the first is kept) and logged once per metric.
- A name may not collide with a summary's `_count` or `_sum`, and a summary may not use a name whose `_count` or `_sum`
  is taken.
- A collector that fails with an exception or a linkage error (for example a missing class) is logged once and skipped.
- Counters only grow (`add` refuses negative or non-finite amounts); summaries expose `_count` and `_sum`.

The registry is created with the class and never replaced, so handles stay valid when Degoos Metrics restarts; its
shutdown removes only its own `hytale_` collectors. Dependents register at their start and unregister at their shutdown
(`unregister` is always safe). When Metrics is an optional dependency, wrap the first access in try/catch so the game
plugin works without it:

```kotlin
val metrics = try {
    DegoosMetrics.registry
} catch (e: NoClassDefFoundError) {
    null   // Degoos Metrics is not installed: skip every registration
}
metrics?.let { registerMyGameMetrics(it) }
```

Keep all references to `DegoosMetrics`, `MetricsRegistry` and their handles inside classes that are only loaded when
`metrics` is not null.

## Registering without a compile dependency: `MetricsBridge`

A plugin that should not compile against Degoos Metrics uses `com.degoos.metrics.MetricsBridge`, whose public static
methods take and return JDK types only. Look the class up by name and call it through `MethodHandles`; if the lookup
fails, Metrics is not installed and every registration is a no-op. Declare the optional dependency in the manifest so
Metrics loads first:

```json
"OptionalDependencies": { "Degoos:Metrics": "*" }
```

| Method | Meaning |
|---|---|
| `VERSION` (int, currently 1) | Bumped on an incompatible change to these signatures. |
| `gauge(owner, name, help, labelNames: List<String>, read: Supplier<Map<List<String>, Double>>)` | Gauge read at scrape time: label values to value. |
| `counter(owner, name, help, labelNames): ObjDoubleConsumer<List<String>>` | `accept(labelValues, amount)`. |
| `summary(owner, name, help, labelNames): ObjDoubleConsumer<List<String>>` | `accept(labelValues, observedValue)`. |
| `groupWorlds(owner, grouper: Function<String, String?>)` | World name to group label, or null to keep the world's own label. |
| `unregister(owner)` | Removes the owner's metrics and world groupers; always safe. |

Registration errors (invalid name, label or owner, a name collision) throw `IllegalArgumentException` at registration.
The counter and summary consumers never throw: a wrong number of label values, an invalid label value or a negative or
non-finite amount is dropped and logged once per metric.

```kotlin
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.function.Function
import java.util.function.ObjDoubleConsumer

/** Handles into Degoos Metrics; build it with [connect], which returns null when the plugin is not installed. */
class MetricsLink(val matchesStarted: ObjDoubleConsumer<List<String>>, val close: () -> Unit) {
    companion object {
        fun connect(owner: String): MetricsLink? = try {
            val bridge = Class.forName("com.degoos.metrics.MetricsBridge")
            val lookup = MethodHandles.publicLookup()
            val str = String::class.java
            val counter = lookup.findStatic(
                bridge, "counter",
                MethodType.methodType(ObjDoubleConsumer::class.java, str, str, str, List::class.java),
            )
            val group = lookup.findStatic(bridge, "groupWorlds", MethodType.methodType(Void.TYPE, str, Function::class.java))
            val unregister = lookup.findStatic(bridge, "unregister", MethodType.methodType(Void.TYPE, str))

            @Suppress("UNCHECKED_CAST")
            val matches = counter.invoke(owner, "mygame_matches_started_total", "Matches started.", listOf("mode")) as ObjDoubleConsumer<List<String>>
            group.invoke(owner, Function<String, String?> { if (it.startsWith("dungeon-")) "dungeons" else null })
            MetricsLink(matches) { unregister.invoke(owner) }
        } catch (e: ReflectiveOperationException) {
            null   // class or method missing
        } catch (e: LinkageError) {
            null
        }
    }
}
```

The lookup is the point: `Class.forName`, then `findStatic` with the exact JDK signature, then `invoke`. Call the
consumer from anywhere, for example `link?.matchesStarted?.accept(listOf("solo"), 1.0)`, and call `close()` at shutdown.

## Build

```
./gradlew test shadowJar
```

The JAR is `build/libs/DegoosMetrics-0.2.0.jar`. It needs `hytale.path` in `~/.gradle/gradle.properties` and Kayle from
`https://dev.degoos.xyz/maven/repo`. Tests run on Java 25 and need no Hytale runtime.
