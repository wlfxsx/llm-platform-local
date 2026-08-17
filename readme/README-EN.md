# Local AI Platform

A local-first desktop platform for large language models on Windows, macOS, and Linux. It is built with Java 21, Spring Boot, Avalonia, llamafile, and AgentScope Java.

Chinese documentation: [README.md](../README.md)

## Features

- Local models: import GGUF models and explicitly start or stop them through llamafile
- Parameter profiles: save multiple named profiles and select one when starting a model
- Remote models: manage multiple OpenAI-compatible endpoints and switch between local and remote models
- Chat management: session history, streaming output, Markdown, message editing, and retraction
- Local knowledge base: import text-bearing documents, structure-aware chunking, hybrid vector + FTS retrieval, and local reranking
- Remote retrieval enhancements: GraphRAG and HyDE are optional and require remote chat mode
- Resource monitoring: system, Java control plane, llamafile, GPU, and inference-throughput metrics
- Capability layer: Tool, MCP, Skill, RAG, Memory, and plugin management
- Localized UI: Chinese and English resources, system theme, and system accent color

## Architecture

```
Avalonia desktop application
  └─ HTTP / SSE / WebSocket
      └─ Spring Boot local control plane (127.0.0.1:17890)
          ├─ llamafile chat inference (127.0.0.1:17891 by default)
          ├─ llamafile embeddings (127.0.0.1:17892 by default)
          ├─ llamafile reranking (127.0.0.1:17893 by default)
          ├─ OpenAI-compatible remote models
          ├─ local SQLite data and knowledge-base indexes
          └─ Tool / MCP / Skill / RAG / Memory
```

The desktop application owns the UI, system tray, and process lifecycle. The Java control plane handles model orchestration, sessions, capabilities, monitoring, and persistence.

## Privacy and Security

- The control plane and local inference service listen only on loopback addresses.
- Remote API keys are stored in the operating system credential store; SQLite stores only credential references.
- The global network switch can keep inference and local knowledge-base retrieval on-device.
- GraphRAG and HyDE depend on a remote chat model and send related text to the configured remote service when enabled.
- Exiting the application stops the Java control plane and llamafile processes started by the application.
- Runtime data is stored in the platform user-data directory (`~/.llm-platform` by default), outside the repository.

Remote models, network-enabled tools, and remote providers send requests to their configured services only when enabled by the user.

## Run from Source

Requirements:

- JDK 21
- Maven 3.9+
- .NET 10 SDK
- A llamafile binary matching the operating system and architecture for local inference (optional for remote-only use)

### Where to put llamafile

The repository does not ship the llamafile binary. Download it from [Mozilla llamafile Releases](https://github.com/Mozilla-Ocho/llamafile/releases), then place it using one of these options (`llamafile.exe` on Windows, `llamafile` elsewhere):

1. **Local development (not committed):** put it at `.me/files/llamafile.exe` or `.me/files/llamafile` under the repository root
2. **Assembled / installed layout:** put it at `runtime/llamafile.exe` or `runtime/llamafile` next to `platform-server.jar`
3. **Explicit path:** set `llm.llamafile.binary-path` in `platform-server/src/main/resources/application.yml` to an absolute path

The control plane resolves the binary from settings, config, the working directory, `runtime/`, paths next to the JAR, and `.me/files/` when walking up from the working directory (development machines only).

### Where to put knowledge-base models

Local RAG defaults to BGE-M3 embeddings and bge-reranker-v2-m3 reranking (Q4_K_M GGUF, roughly 438MB each). Weights are not committed; when found they are copied into the user-data directory:

| Role | File name | User-data directory | Assembled layout | Local development fallback (not committed) |
|------|-----------|---------------------|------------------|--------------------------------------------|
| Embedding | `bge-m3-q4_k_m.gguf` | `~/.llm-platform/models/embedding/` | `runtime/models/embedding/` | `.me/model/` |
| Rerank | `bge-reranker-v2-m3-q4_k_m.gguf` | `~/.llm-platform/models/rerank/` | `runtime/models/rerank/` | `.me/model/` |

Document import requires the embedding model. Without the reranker, hybrid vector + FTS retrieval still works. Supported extracts include text-bearing TXT/Markdown/HTML/PDF/Office files; scanned-image OCR is not supported.

Build the Java control plane:

```shell
cd platform-server
mvn package
```

Then run the desktop application:

```shell
dotnet run --project desktop/LlmPlatform.Desktop/LlmPlatform.Desktop.csproj
```

Build outputs are written to `.me/build/` (development machine only; not committed). Automated tests are also omitted from the repository by default; they can still be run locally from `platform-server/src/test/`.

## Model Setup

- Local mode: make sure llamafile is placed as above, import a GGUF file, select the execution device and a parameter profile, then explicitly start the model.
- Remote mode: add an OpenAI-compatible API URL, model name, and API key, then enable the global network switch.
- Knowledge base: import documents in Settings. Local retrieval works by default. GraphRAG / HyDE require remote chat mode and their capability switches.
- The application does not load a local chat model automatically at startup; embedding and rerank processes start on demand for import or retrieval.

## Packaging

Assembly and installer scripts are documented in [packaging/README.md](../packaging/README.md).

## Branches

- `dev`: default development branch
- `main`: milestone-ready code

## License

This project is released under the [Apache License 2.0](../LICENSE). See [NOTICE](../NOTICE) for copyright attribution.

Third-party dependency notices are summarized in [packaging/THIRD-PARTY-NOTICES.md](../packaging/THIRD-PARTY-NOTICES.md).
