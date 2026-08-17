# Local AI Platform

A local-first desktop platform for large language models on Windows, macOS, and Linux. It is built with Java 21, Spring Boot, Avalonia, llamafile, and AgentScope Java.

> This project is under active development and does not have a stable release yet. Configuration formats, the database schema, and the user interface may continue to change.

Chinese documentation: [README.md](../README.md)

## Features

- Local models: import GGUF models and explicitly start or stop them through llamafile
- Parameter profiles: save multiple named profiles and select one when starting a model
- Remote models: manage multiple OpenAI-compatible endpoints and switch between local and remote models
- Chat management: session history, streaming output, Markdown, message editing, and retraction
- Resource monitoring: system, Java control plane, llamafile, GPU, and inference-throughput metrics
- Capability layer: Tool, MCP, Skill, RAG, Memory, and plugin management
- Localized UI: Chinese and English resources, system theme, and system accent color

## Architecture

```
Avalonia desktop application
  └─ HTTP / SSE / WebSocket
      └─ Spring Boot local control plane (127.0.0.1:17890)
          ├─ llamafile local inference (127.0.0.1:17891 by default)
          ├─ OpenAI-compatible remote models
          ├─ local SQLite data
          └─ Tool / MCP / Skill / RAG / Memory
```

The desktop application owns the UI, system tray, and process lifecycle. The Java control plane handles model orchestration, sessions, capabilities, monitoring, and persistence.

## Privacy and Security

- The control plane and local inference service listen only on loopback addresses.
- Remote API keys are stored in the operating system credential store; SQLite stores only credential references.
- The global network switch can keep inference and retrieval local when local models are used.
- Exiting the application stops the Java control plane and llamafile processes started by the application.
- Runtime data is stored in the platform user-data directory (`~/.llm-platform` by default), outside the repository.

Remote models, network-enabled tools, and remote providers send requests to their configured services only when enabled by the user.

## Run from Source

Requirements:

- JDK 21
- Maven 3.9+
- .NET 10 SDK
- A llamafile binary matching the operating system and architecture for local inference

Build the Java control plane:

```shell
cd platform-server
mvn package
```

Then run the desktop application:

```shell
dotnet run --project desktop/LlmPlatform.Desktop/LlmPlatform.Desktop.csproj
```

Build outputs are written to `.me/build/`. This directory is local to the development machine and is not committed. Automated tests are also omitted from the repository by default; they can still be run locally from `platform-server/src/test/`.

## Model Setup

- Local mode: import a GGUF file, select the execution device and a parameter profile, then explicitly start the model.
- Remote mode: add an OpenAI-compatible API URL, model name, and API key, then enable the global network switch.
- The application does not load a local model automatically at startup.

## Packaging

The repository currently provides development-stage assembly scripts, not prebuilt installers or GitHub Releases. See [packaging/README.md](../packaging/README.md).

## Branches

- `dev`: default development branch
- `main`: milestone-ready code

## License

This project is released under the [Apache License 2.0](../LICENSE). See [NOTICE](../NOTICE) for copyright attribution.

Third-party dependency notices are summarized in [packaging/THIRD-PARTY-NOTICES.md](../packaging/THIRD-PARTY-NOTICES.md).
