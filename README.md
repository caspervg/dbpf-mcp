# dbpf-mcp

`dbpf-mcp` is a Kotlin/JVM Model Context Protocol server for reading and writing SimCity 4 DBPF packages. It exposes tools for listing package entries, indexing Plugins folders, decoding common SC4 resource types, exporting decoded resources to text or image files, and creating/patching new DBPF packages (exemplars, cohorts, LTEXT, FSH textures, and raw entries), plus reading/writing plain INI configuration files.

The server currently uses the `backend-scdbpf` adapter and runs over MCP stdio.

## Features

### Read

- List and summarize DBPF package entries with stable TGI metadata.
- Inspect one package for notable entries, SC4 object hints, and recommended next tools.
- Build a persistent metadata index for a Plugins folder, then search it without rescanning.
- Decode exemplars and cohorts as semantic JSON with property names, type hints, decoded values, resource keys, and optional parent cohort resolution.
- Render exemplars and cohorts as canonical SC4 text-exemplar syntax, either in-memory or exported to disk.
- Decode SC4PATHS entries as JSON or canonical path text, either in-memory or exported to disk.
- Decode LTEXT, S3D metadata, FSH metadata, image entries, and raw entry previews.
- Export selected FSH bitmap images as PNG files.
- Decode individual exemplar property values for quick property interpretation.
- Read a plain filesystem INI file (`read_ini`) into structured sections/keys plus raw text. Not a DBPF format; useful for mod configuration files.

### Write

- `write_exemplars`: create a DBPF package with new exemplar/cohort entries and caller-specified properties (Uint8/16/32, Sint32/64, Float32, Bool, String, and Tgi resource-key triplets). Property type can be inferred from the bundled SC4 property registry or declared explicitly (an explicit type always overrides the registry, so custom/modded properties are supported). Optional non-fatal `warnings` surface inferred/mismatched types.
- `write_ltext`: create a DBPF package with new LTEXT (localizable text) entries.
- `write_fsh`: create a DBPF package with new FSH texture entries encoded from PNG images. Supports Dxt1, Dxt3, A8R8G8B8, A0R8G8B8, A1R5G5B5, A0R5G6B5, A4R4G4B4, multiple elements per entry, and caller-supplied mip chains. Dxt5 encoding is not supported by the bundled scdbpf version (decoding Dxt5 via `read_fsh`/`export_fsh_png` is unaffected).
- `write_raw_entries`: write arbitrary bytes to any TGI with no format decoding, for entry kinds without a dedicated encoder (KEYCFG, TAB, RUL, EFFDIR, PNG, etc.).
- `write_ini`: write or patch a plain filesystem INI file. `merge: true` updates matching `[section]` keys in place and appends new sections/keys while preserving everything else (comments, formatting, unrelated keys) byte-for-byte.

All write tools accept `outputPath`, `overwrite` (replace an existing file entirely), and `merge` (patch: keep existing entries/lines not addressed by the request, replace/append the rest by TGI or key). DBPF write tools also accept `compressed` (QFS-compress new entries, default true) and reject duplicate TGIs within one request.

Experimental tools:

- `read_keycfg`: heuristic decoder for KEYCFG/TAB-like text resources. It may return noisy fragments and may not reconstruct shortcut records.
- `read_tab_binary`: structural binary probe for compiled TAB resources. It returns little-endian words and chunks, not a semantic TAB model.

## Project structure

- `core-api`: backend-agnostic models and service contracts.
- `sc4-semantics`: TGI helpers, property registry loading, and SC4 semantic helpers.
- `backend-scdbpf`: active scdbpf-backed implementation.
- `mcp-server`: stdio MCP server entrypoint.
- `integration-tests`: end-to-end and snapshot-style tests.
- `vendor/sc4-properties`: Git submodule containing SC4 property registry source data.

## Requirements

- JDK 21 or a compatible Java toolchain.
- The Gradle wrapper from this repository.
- A local MCP client that can launch stdio servers.

## Build & test

From the repository root:

```sh
./gradlew build
./gradlew test
```

Run only the MCP server module:

```sh
./gradlew :mcp-server:run
```

Build an installable local distribution:

```sh
./gradlew :mcp-server:installDist
```

The generated launcher is:

```text
mcp-server/build/install/mcp-server/bin/mcp-server
```

## Client setup

For a stdio MCP client, configure the command to point at the installed launcher:

```json
{
  "mcpServers": {
    "dbpf": {
      "command": "/absolute/path/to/dbpf-mcp/mcp-server/build/install/mcp-server/bin/mcp-server"
    }
  }
}
```

For quick local development, you can also launch through Gradle:

```json
{
  "mcpServers": {
    "dbpf": {
      "command": "/absolute/path/to/dbpf-mcp/gradlew",
      "args": ["-p", "/absolute/path/to/dbpf-mcp", ":mcp-server:run"]
    }
  }
}
```

The installed launcher is preferred for day-to-day use because it avoids Gradle startup overhead.

## Basic use

Most tools operate on one DBPF package file, not a Plugins folder. Use `index_plugins` only when you want folder-wide search or cross-package parent cohort lookup.

Typical workflow:

1. Use `index_plugins` with a Plugins folder path, for example `~/Documents/SimCity 4/Plugins`.
2. Use `search_index` to find candidate entries by TGI, resource kind, exemplar name, object class, or property ID.
3. Use `inspect_package`, `summarize_package`, or `list_entries` on a specific `.dat`, `.SC4Lot`, `.SC4Model`, or `.SC4Desc` file.
4. Use focused readers such as `read_exemplar`, `read_cohort`, `read_sc4paths`, `read_fsh`, `read_s3d`, or `read_ltext`.
5. Use export tools such as `export_exemplar_text`, `export_cohort_text`, `export_sc4paths_text`, `export_sc4paths_json`, or `export_fsh_png` when you want files written to disk.
6. Use write tools such as `write_exemplars`, `write_ltext`, `write_fsh`, or `write_raw_entries` to create a new `.dat`, or pass `merge: true` to patch entries into an existing one.
7. Use `read_ini` / `write_ini` for plain filesystem INI configuration files (not DBPF packages).

TGI arguments can be supplied either as one string:

```text
6534284A-A8434037-0C006800
```

or as separate `type`, `group`, and `instance` hex values.

## Index cache

`index_plugins` writes persistent JSONL metadata under:

```text
~/.cache/dbpf-mcp/indexes
```

`search_index` never recursively scans the folder. If `index_status` reports stale or missing files, run `index_plugins` again.

## Environment variables

- `DBPF_MCP_INDEX_DIR`: overrides the directory used for persistent `index_plugins` JSONL cache files. By default, dbpf-mcp uses Java's `user.home` and appends `.cache/dbpf-mcp/indexes`. On Windows this is typically `C:\Users\<you>\.cache\dbpf-mcp\indexes`; on macOS/Linux this is typically `~/.cache/dbpf-mcp/indexes`.
- `JAVA_HOME`: selects the JDK used by Gradle and the installed server launcher. Use a JDK 21-compatible installation.
- `JAVA_OPTS`: optional JVM options used by the installed server launcher.
- `GRADLE_OPTS`: optional JVM options used when launching through Gradle.

## Limitations

- This is still in development. Tool output schemas may still evolve.
- Folder-wide scanning happens only through `index_plugins`; other tools expect one DBPF package path.
- Cross-package parent cohort resolution requires a current Plugins index and is limited to entries present in that index.
- S3D support reports model metadata, mesh group summaries, materials, and animation metadata; it does not export full geometry, and there is no `write_s3d`.
- `read_keycfg` and `read_tab_binary` are not finished.
- `write_fsh` cannot encode Dxt5 (the bundled scdbpf version only supports Dxt5 decode) and does not generate mip levels automatically; callers must supply each mip image pre-downscaled.
- `write_ltext` always encodes UTF-16 LTEXT; other LTEXT formats are not selectable in the bundled scdbpf version.
- `write_ini` merge matches keys by exact, case-sensitive text; it does not normalize or dedupe keys within a section.

## Common commands

```sh
./gradlew build
./gradlew test
./gradlew :sc4-semantics:test
./gradlew :integration-tests:test
./gradlew :mcp-server:run
./gradlew :mcp-server:installDist
```
