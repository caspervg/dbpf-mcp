# dbpf-mcp

`dbpf-mcp` is a Kotlin/JVM Model Context Protocol server for reading and writing SimCity 4 DBPF packages. It exposes tools for listing package entries, indexing Plugins folders, decoding common SC4 resource types, exporting decoded resources to text or image files, and creating/patching new DBPF packages (exemplars, cohorts, LTEXT, FSH textures, Network INI resources, and raw entries).

The server currently uses the `backend-scdbpf` adapter and runs over MCP stdio.

## Features

### Read

- List and summarize DBPF package entries with stable TGI metadata. `list_entries` reports both the package's total entry count and how many entries matched the filters, and flags when a page was truncated.
- Inspect one package for notable entries, SC4 object hints, and recommended next tools.
- Build a persistent metadata index for a Plugins folder, then search it without rescanning.
- Decode exemplars and cohorts as semantic JSON with property names, type hints, decoded values, resource keys, and optional parent cohort resolution.
- Render exemplars and cohorts as canonical SC4 text-exemplar syntax, either in-memory or exported to disk.
- Decode SC4PATHS entries as JSON or canonical path text, either in-memory or exported to disk.
- Decode LTEXT, S3D metadata, FSH metadata, image entries, and raw entry previews.
- Export selected FSH bitmap images as PNG files.
- Decode individual exemplar property values for quick property interpretation.
- Read a Network INI resource (`read_ini`) from a DBPF package by TGI, including QFS-compressed entries such as `00000000-8A5971C5-8A5993B9`.

### Write

- `write_exemplars`: create a DBPF package with new exemplar/cohort entries and caller-specified properties (Uint8/16/32, Sint32/64, Float32, Bool, String, and Tgi resource-key triplets). Property type can be inferred from the bundled SC4 property registry or declared explicitly (an explicit type always overrides the registry, so custom/modded properties are supported). Optional non-fatal `warnings` surface inferred/mismatched types.
- `write_ltext`: create a DBPF package with new LTEXT (localizable text) entries.
- `write_fsh`: create a DBPF package with new FSH texture entries encoded from PNG images. Supports Dxt1, Dxt3, A8R8G8B8, A0R8G8B8, A1R5G5B5, A0R5G6B5, A4R4G4B4, multiple elements per entry, and caller-supplied mip chains. Dxt5 encoding is not supported by the bundled scdbpf version (decoding Dxt5 via `read_fsh`/`export_fsh_png` is unaffected).
- `write_raw_entries`: write arbitrary bytes to any TGI with no format decoding, for entry kinds without a dedicated encoder (KEYCFG, TAB, RUL, EFFDIR, PNG, etc.).
- `write_ini`: install exact Network INI text at a caller-specified TGI in a new or existing DBPF package. `merge: true` preserves unrelated package entries and replaces the matching TGI.

All write tools accept `outputPath`, `overwrite` (replace an existing file entirely), and `merge` (keep existing entries not addressed by the request and replace/append by TGI). DBPF write tools also accept `compressed` (QFS-compress new entries, default true) and reject duplicate TGIs within one request.

Experimental tools:

- `read_keycfg`: heuristic decoder for KEYCFG/TAB-like text resources. It recovers text fragments and may not reconstruct complete shortcut records.
- `read_tab_binary`: structural binary probe for compiled TAB resources. It returns little-endian words and chunks, not a semantic TAB model.

Both operate on the decoded entry payload. Earlier versions passed them the stored bytes without
decompressing first, which is why their output used to contain QFS artefacts.

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
- The `vendor/sc4-properties` git submodule (see Build & test below).
- A local MCP client that can launch stdio servers.

## Build & test

The SC4 property registry is a git submodule, and without it the server fails at runtime on the
first property lookup. Fetch it before the first build:

```sh
git submodule update --init --recursive
```

(If you have not cloned yet, `git clone --recurse-submodules` does the same thing.)

Then, from the repository root:

```sh
./gradlew build
./gradlew test
./gradlew ktlintCheck
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
7. Use `read_ini` / `write_ini` for Network INI text stored directly in DBPF entries.

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

`index_plugins` reuses an index that is still current for every package in the folder; pass
`forceRefresh: true` to rebuild regardless. The cache is versioned and written atomically, so an
index left behind by an interrupted run, or by an older release, is reported as needing a rebuild
rather than failing every later call.

## Environment variables

- `DBPF_MCP_INDEX_DIR`: overrides the directory used for persistent `index_plugins` JSONL cache files. By default, dbpf-mcp uses Java's `user.home` and appends `.cache/dbpf-mcp/indexes`. On Windows this is typically `C:\Users\<you>\.cache\dbpf-mcp\indexes`; on macOS/Linux this is typically `~/.cache/dbpf-mcp/indexes`.
- `JAVA_HOME`: selects the JDK used by Gradle and the installed server launcher. Use a JDK 21-compatible installation.
- `JAVA_OPTS`: optional JVM options used by the installed server launcher.
- `GRADLE_OPTS`: optional JVM options used when launching through Gradle.

## Tool schemas and output shape

Every tool publishes both an `inputSchema` and an `outputSchema`, generated from the same Kotlin
types used to decode arguments and encode results, so the published constraints cannot drift from
the enforced ones. Argument bounds, enumerations, and hexadecimal formats are part of the schema
rather than prose.

Absent optional fields are omitted from responses rather than sent as explicit `null`s. TGI
components and property IDs are hexadecimal strings (`"6534284A"`), not numbers.

On failure a tool returns `isError: true` with an `error` message and no `structuredContent`,
since an error payload would not satisfy the declared output schema.

## Limitations

- This is still in development. Tool output schemas may still evolve.
- Folder-wide scanning happens only through `index_plugins`; other tools expect one DBPF package path.
- Cross-package parent cohort resolution requires a current Plugins index and is limited to entries present in that index.
- S3D support reports model metadata, mesh group summaries, materials, and animation metadata; it does not export full geometry, and there is no `write_s3d`.
- `read_keycfg` and `read_tab_binary` are not finished.
- `write_fsh` cannot encode Dxt5 (the bundled scdbpf version only supports Dxt5 decode) and does not generate mip levels automatically; callers must supply each mip image pre-downscaled.
- `write_ltext` always encodes UTF-16 LTEXT; other LTEXT formats are not selectable in the bundled scdbpf version.
- `write_ini` stores the supplied text exactly; it does not parse, normalize, reorder, or deduplicate Network INI rules.

## Common commands

```sh
git submodule update --init --recursive
./gradlew build
./gradlew test
./gradlew ktlintCheck
./gradlew ktlintFormat
./gradlew :sc4-semantics:test
./gradlew :backend-scdbpf:test
./gradlew :integration-tests:test
./gradlew :mcp-server:test
./gradlew :mcp-server:run
./gradlew :mcp-server:installDist
```
