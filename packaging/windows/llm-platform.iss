; 使用 Inno Setup 把 .me/build/dist/llm-platform 打成 Windows 安装包。
; 不把 GGUF 模型打进安装程序。

#define MyAppName "本地 AI 框架平台"
#define MyAppVersion "0.1.0"
#define MyAppPublisher "llm-platform-local"
#define DistDir "..\..\..\.me\build\dist\llm-platform"

[Setup]
AppId={{8F3C2A11-6B47-4E2D-9C1A-0B7E4D2A91F0}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\LlmPlatform
DefaultGroupName={#MyAppName}
OutputDir=..\..\..\.me\build\dist\installer
OutputBaseFilename=llm-platform-setup
Compression=lzma
SolidCompression=yes
PrivilegesRequired=lowest
ArchitecturesInstallIn64BitMode=x64compatible
DisableProgramGroupPage=yes

[Files]
Source: "{#DistDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\desktop\LlmPlatform.Desktop.exe"

[Run]
Filename: "{app}\desktop\LlmPlatform.Desktop.exe"; Description: "启动应用"; Flags: nowait postinstall skipifsilent
