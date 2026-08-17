#!/bin/sh
# 将当前仓库编译结果组装为本地安装目录。
# 输出位于仓库 .me/build/dist/llm-platform，不会提交到 Git。
set -eu
root="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
dist="$root/.me/build/dist/llm-platform"
mkdir -p "$dist/runtime" "$dist/native" "$dist/desktop"

(cd "$root/platform-server" && mvn -q -DskipTests package)
(cd "$root/desktop" && dotnet publish LlmPlatform.Desktop/LlmPlatform.Desktop.csproj -c Release -o "$dist/desktop")

jar="$root/.me/build/platform-server/platform-server-0.1.0-SNAPSHOT.jar"
if [ -f "$jar" ]; then
  cp "$jar" "$dist/runtime/platform-server.jar"
fi

cp "$root/packaging/THIRD-PARTY-NOTICES.md" "$dist/"
cp "$root/packaging/native/README.md" "$dist/native/"

if [ "${LLM_BUNDLE_JRE:-}" = "1" ] && [ -n "${JAVA_HOME:-}" ]; then
  cp -R "$JAVA_HOME" "$dist/runtime/jre"
fi

llama="${LLM_LLAMAFILE:-}"
if [ -z "$llama" ] && [ -d "$root/.me/files" ]; then
  llama="$(find "$root/.me/files" -type f | head -n 1 || true)"
fi
if [ -n "$llama" ] && [ -f "$llama" ]; then
  cp "$llama" "$dist/runtime/llamafile"
fi

echo "组装完成：$dist"
