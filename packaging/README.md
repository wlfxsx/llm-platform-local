# 打包说明

本目录包含应用组装脚本和 Windows 安装程序定义。构建与组装产物写入 `.me/build/`（仅开发机，不入库）。

## 捆绑内容

- 桌面壳：Avalonia 原生发布包
- 后端：`platform-server` 可执行 JAR
- 可选 Java 运行时：通过 `JAVA_HOME` 提供
- 可选 llamafile：对应操作系统和架构的二进制
- 可选 SQLite 向量扩展：按目标平台放入输出目录的 `native/`
- 许可证：根目录 `LICENSE`、`NOTICE`，以及 `THIRD-PARTY-NOTICES.md`

组装目录不包含 GGUF 模型。用户可在应用中导入本地模型，或配置远程 OpenAI 兼容模型。

## 前置条件

- JDK 21
- Maven 3.9+
- .NET 10 SDK
- 目标平台需要的可选运行时文件（JRE、llamafile、SQLite 向量扩展）

## 布局

```
llm-platform/
  desktop/
    LlmPlatform.Desktop.exe  （或对应平台可执行文件）
  runtime/
    jre/                     （可选，需 LLM_BUNDLE_JRE=1）
    platform-server.jar
    llamafile 或 llamafile.exe （可选）
  native/
    README.md
    vec0.dll / vec0.so / vec0.dylib  （可选，需手动放入）
  LICENSE
  NOTICE
  THIRD-PARTY-NOTICES.md
```

## 构建

在仓库根目录执行：

- Windows：`powershell -ExecutionPolicy Bypass -File packaging/assemble.ps1`
- macOS / Linux：`sh packaging/assemble.sh`

单次执行脚本会按顺序完成：

1. 编译后端 JAR 到 `.me/build/platform-server`（仅开发机）
2. 把桌面应用发布到 `.me/build/dist/llm-platform/desktop`
3. 把 JAR、许可证和可选运行时复制进同一组装目录 `.me/build/dist/llm-platform`

可选环境变量：

- `LLM_BUNDLE_JRE=1`：把 `JAVA_HOME` 指向的运行时复制到组装目录
- `LLM_LLAMAFILE`：指定 llamafile 二进制；未指定时脚本尝试使用 `.me/files/`（仅开发机）中的本机文件

脚本不会自动下载 SQLite 向量扩展，只会把 `packaging/native/README.md` 复制到组装目录的 `native/`。需要时将对应平台的动态库放入 `.me/build/dist/llm-platform/native/`。

## Windows 安装程序

安装 Inno Setup 后，可使用 `packaging/windows/llm-platform.iss` 把组装目录生成安装程序。安装程序输出位于 `.me/build/dist/installer/`（仅开发机）。
