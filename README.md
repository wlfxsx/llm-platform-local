# 本地 AI 平台

一个面向 Windows、macOS 和 Linux 的本地优先大模型桌面平台。项目使用 Java 21、Spring Boot、Avalonia、llamafile 和 AgentScope Java 构建。

英文文档：[readme/README-EN.md](readme/README-EN.md)

## 功能概览

- 本地模型：导入 GGUF 模型，通过 llamafile 显式启动和停止
- 参数预设：保存多套命名参数，并在启动模型时选择预设
- 远程模型：管理多套 OpenAI 兼容服务配置，支持在本地与远程模型之间切换
- 对话管理：会话列表、流式输出、Markdown、消息修改和撤回
- 资源监控：展示系统、Java 控制面、llamafile、GPU 和推理吞吐指标
- 能力扩展：Tool、MCP、Skill、RAG、Memory 和插件管理
- 本地化界面：中文和英文资源、系统主题与系统强调色

## 架构

```
Avalonia 桌面应用
  └─ HTTP / SSE / WebSocket
      └─ Spring Boot 本机控制面（127.0.0.1:17890）
          ├─ llamafile 本地推理（默认 127.0.0.1:17891）
          ├─ OpenAI 兼容远程模型
          ├─ SQLite 本地数据
          └─ Tool / MCP / Skill / RAG / Memory
```

桌面应用负责界面、托盘和进程生命周期；Java 控制面负责模型编排、会话、能力扩展、监控与持久化。

## 隐私与安全

- 控制面和本地推理服务仅监听回环地址，不开放外部入站端口。
- 远程 API Key 保存到操作系统凭据库；SQLite 只保存凭据引用。
- 使用本地模型时可关闭联网总开关，使推理和检索保持在本机。
- 退出应用时会停止由应用启动的 Java 控制面和 llamafile 进程。
- 运行数据默认保存在各平台用户数据目录（当前默认值为 `~/.llm-platform`），不写入仓库。

远程模型、联网 Tool 或联网 Provider 会把请求发送给对应服务；是否启用由用户配置决定。

## 从源码运行

前置条件：

- JDK 21
- Maven 3.9+
- .NET 10 SDK
- 本地推理需要与操作系统和架构匹配的 llamafile

先构建 Java 控制面：

```shell
cd platform-server
mvn package
```

再启动桌面应用：

```shell
dotnet run --project desktop/LlmPlatform.Desktop/LlmPlatform.Desktop.csproj
```

构建产物写入 `.me/build/`（仅开发机，不入库）。自动化测试默认也不入库，仍可在本机 `platform-server/src/test/` 运行。

## 模型使用

- 本地模式：在设置中导入 GGUF 文件、选择运行硬件和参数预设，然后显式启动模型。
- 远程模式：添加 OpenAI 兼容 API 地址、模型名和 API Key，并开启联网总开关。
- 应用启动不会自动加载本地模型。

## 打包

组装与安装程序脚本见 [packaging/README.md](packaging/README.md)。

## 分支

- `dev`：默认开发分支
- `main`：阶段性稳定代码

## 许可证

本项目以 [Apache License 2.0](LICENSE) 开源发布。版权声明见 [NOTICE](NOTICE)。

第三方依赖的许可证摘要见 [packaging/THIRD-PARTY-NOTICES.md](packaging/THIRD-PARTY-NOTICES.md)。
