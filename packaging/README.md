# 安装包骨架

本目录描述如何把本地 AI 框架平台打成可安装应用。构建产物写入 `.me/build/`，不进入 Git。

## 捆绑内容

- 桌面壳：Avalonia 原生发布包
- 后端：`platform-server` 可执行 jar（含锁定的 AgentScope Java 2.0.1）
- JRE：Java 21 运行时
- llamafile：对应操作系统/架构的二进制
- SQLite 向量扩展：Vec1 或 sqlite-vec 动态库，放入 `native/`
- 第三方许可证：`THIRD-PARTY-NOTICES.md`

安装包不内置 GGUF 模型。首次对话前需在设置中导入本地模型文件。

## 布局

```
llm-platform/
  LlmPlatform.Desktop
  runtime/
    jre/
    platform-server.jar
    llamafile 或 llamafile.exe
  native/
    vec0.dll / vec0.so / vec0.dylib
  THIRD-PARTY-NOTICES.md
```

## 构建

在仓库根目录执行：

- Windows：`powershell -File packaging/assemble.ps1`
- macOS / Linux：`sh packaging/assemble.sh`

脚本会：

1. 编译后端 jar 到 `.me/build/platform-server`
2. 发布桌面应用到 `.me/build/desktop`
3. 组装 `.me/build/dist/llm-platform`

可选环境变量：

- `LLM_BUNDLE_JRE=1`：从 `JAVA_HOME` 复制 JRE
- `LLM_LLAMAFILE`：llamafile 二进制路径；缺省时尝试 `.me/files/` 下已有文件

Windows 可用 `packaging/windows/llm-platform.iss` 把组装目录打成安装程序。
