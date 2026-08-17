# 将当前仓库编译结果组装为本地安装目录。
# 输出位于仓库 .me/build/dist/llm-platform，不会提交到 Git。

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $root ".me\build\dist\llm-platform"

New-Item -ItemType Directory -Force -Path $dist | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $dist "runtime") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $dist "native") | Out-Null

Push-Location (Join-Path $root "platform-server")
try {
    mvn -q -DskipTests package
} finally {
    Pop-Location
}

Push-Location (Join-Path $root "desktop")
try {
    dotnet publish "LlmPlatform.Desktop\LlmPlatform.Desktop.csproj" -c Release -o (Join-Path $dist "desktop")
} finally {
    Pop-Location
}

$jar = Join-Path $root ".me\build\platform-server\platform-server-0.1.0-SNAPSHOT.jar"
if (Test-Path $jar) {
    Copy-Item $jar (Join-Path $dist "runtime\platform-server.jar") -Force
}

Copy-Item (Join-Path $root "LICENSE") $dist -Force
Copy-Item (Join-Path $root "NOTICE") $dist -Force
Copy-Item (Join-Path $root "packaging\THIRD-PARTY-NOTICES.md") $dist -Force
Copy-Item (Join-Path $root "packaging\native\README.md") (Join-Path $dist "native") -Force

if ($env:LLM_BUNDLE_JRE -eq "1" -and $env:JAVA_HOME) {
    Copy-Item -Recurse -Force $env:JAVA_HOME (Join-Path $dist "runtime\jre")
}

$llama = $env:LLM_LLAMAFILE
if (-not $llama) {
    $candidate = Get-ChildItem (Join-Path $root ".me\files") -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidate) {
        $llama = $candidate.FullName
    }
}
if ($llama -and (Test-Path $llama)) {
    $name = if ([System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([System.Runtime.InteropServices.OSPlatform]::Windows)) { "llamafile.exe" } else { "llamafile" }
    Copy-Item $llama (Join-Path $dist "runtime\$name") -Force
}

Write-Host "组装完成：$dist"
