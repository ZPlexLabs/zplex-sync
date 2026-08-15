# zplex-sync

Standalone **indexer / ingest** service for ZPlex Labs. It is a one-shot batch job
(run on a schedule) that walks the Google Drive media libraries, enriches each title
with metadata from **TMDB** and **OMDB**, writes the catalog into **PostgreSQL**, and
refreshes the filter caches in **Redis/Valkey (RedisJSON)**. The API and stream services
read what this job produces — this is the write side of the CQRS split.

```
Google Drive ──▶ zplex-sync ──▶ PostgreSQL (catalog)
   TMDB/OMDB ──▶ (enrich)   ──▶ Redis/Valkey (filter caches, RedisJSON)
```

## How it runs

```bash
# Build the fat jar
./gradlew build            # produces build/libs/zplex-sync.jar

# Run (prod profile = application.properties only)
java -jar build/libs/zplex-sync.jar

# Run with the dev overrides (application.dev.properties)
java -jar build/libs/zplex-sync.jar --dev
```

Entry point: `zechs.zplex.sync.MainKt` → `IndexingService()` runs genres, movies, shows,
then filter caches, and exits.

The schema in `src/main/resources/setup-db.sql` also defines the read-side query functions
`search_media` and `count_media`. Both accept trailing per-user access params
(`p_allowed_ratings` — NULL means no rating ceiling, `p_allow_unrated`, `p_unrated_ratings`,
`p_blacklist_ids`) so `zplex-api` can filter catalog listings by rating rank and hide
blacklisted titles.

## Configuration

Config is layered: `application.properties` (base) is overridden by
`application.<profile>.properties` when `--dev` is passed. Values support
`${ENV_VAR:default}` placeholders resolved from environment variables first, then JVM
properties, then other properties.

| Key | Purpose |
|-----|---------|
| `tmdb.api.key` / `omdb.api.key` | Metadata provider keys |
| `movies.folder` / `shows.folder` | Google Drive library folder IDs |
| `google.drive.client.email` / `client.id` / `private.key` / `private.key.id` | Drive service-account credentials |
| `zplex.db.host` / `port` / `username` / `password` | PostgreSQL catalog store |
| `zplex.cache.host` / `port` / `username` / `password` | Redis/Valkey (TLS) filter cache |

## Tech stack

| Concern | Library | Version |
|---------|---------|---------|
| Language | Kotlin (JVM 21) | 2.3.21 |
| Build | Gradle | 9.7.0 |
| Fat jar | GradleUp Shadow | 9.6.1 |
| Codegen | KSP | 2.3.11 |
| Redis/Valkey | Jedis | 8.0.0 |
| HTTP | OkHttp | 5.4.0 |
| REST | Retrofit | 3.0.0 |
| JSON | Moshi | 1.15.2 |
| DB driver | PostgreSQL | 42.7.13 |
| Coroutines | kotlinx-coroutines | 1.11.0 |
| Google Drive | google-api-services-drive | v3-rev20260720-2.0.0 |

## Dependency notes

- **Kotlin is pinned to 2.3.21** (latest with a matching stable KSP `2.3.11`); Kotlin
  2.4.x has no released KSP yet.
- **Shadow moved to the GradleUp coordinates** (`com.gradleup.shadow`); the task class
  namespace stayed `com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar`.
- **Jedis 8 removed `JedisPooled`.** The client is now built via the recommended
  `RedisClient.builder()` family (see `RedisConfig`), and TLS is configured through
  `SslOptions` instead of the deprecated `.ssl(true)` setter. TLS trust uses the JVM
  default truststore — add your provider CA if it is not publicly trusted.

## No tests

This service ships without a test suite by project policy; verify changes with
`./gradlew build` and a manual/dry run.
