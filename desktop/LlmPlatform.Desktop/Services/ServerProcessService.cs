using System.Diagnostics;
using System.Net.Http;
using System.Text.RegularExpressions;

namespace LlmPlatform.Desktop.Services;

/// <summary>
/// 桌面壳负责拉起本机 Java 服务，安装包可将 JRE 与 jar 放在 runtime 目录。
/// </summary>
public sealed class ServerProcessService : IDisposable
{
    private Process? _process;
    private bool _startedByShell;

    public async Task<bool> EnsureRunningAsync(CancellationToken cancellationToken)
    {
        // 复用用户已启动的兼容服务，避免桌面壳重复占用固定回环端口。
        if (await PingAsync(cancellationToken).ConfigureAwait(false))
        {
            return true;
        }

        string? java = FindJava();
        string? jar = FindJar();
        if (java is null || jar is null)
        {
            return false;
        }

        ProcessStartInfo info = new()
        {
            FileName = java,
            Arguments = $"-jar \"{jar}\"",
            UseShellExecute = false,
            CreateNoWindow = true
        };
        _process = Process.Start(info);
        _startedByShell = _process is not null;
        // 最长等待 20 秒，每 500 毫秒探测一次，让服务有时间完成数据库迁移和 Spring 启动。
        for (int i = 0; i < 40; i++)
        {
            cancellationToken.ThrowIfCancellationRequested();
            if (await PingAsync(cancellationToken).ConfigureAwait(false))
            {
                return true;
            }

            await Task.Delay(500, cancellationToken).ConfigureAwait(false);
        }

        return false;
    }

    /// <summary>先请求后端停模型和退出控制面，超时后再按端口与进程名强清残留。</summary>
    public async Task ShutdownAllAsync(PlatformClient client, CancellationToken cancellationToken)
    {
        try
        {
            using CancellationTokenSource timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            timeout.CancelAfter(TimeSpan.FromSeconds(4));
            await client.SendAsync(HttpMethod.Post, "api/shutdown", new { }, timeout.Token).ConfigureAwait(false);
        }
        catch (Exception)
        {
            // 服务可能已经退出或尚未监听，继续走进程清扫。
        }

        if (_startedByShell && _process is { HasExited: false })
        {
            if (!_process.WaitForExit(8000))
            {
                _process.Kill(entireProcessTree: true);
            }
        }

        SweepOrphans();
    }

    public void Dispose()
    {
        try
        {
            SweepOrphans();
        }
        finally
        {
            if (_startedByShell && _process is { HasExited: false })
            {
                _process.Kill(entireProcessTree: true);
            }

            _process?.Dispose();
            _process = null;
        }
    }

    private void SweepOrphans()
    {
        KillByName("llamafile");
        KillPlatformServerJava();
        KillListeners(17890);
        if (_startedByShell && _process is { HasExited: false })
        {
            try
            {
                _process.Kill(entireProcessTree: true);
            }
            catch (Exception)
            {
                // 进程可能在关机接口之后已经退出。
            }
        }
    }

    /// <summary>按命令行识别本平台 jar，覆盖“服务不是由当前壳进程拉起”的情况。</summary>
    private static void KillPlatformServerJava()
    {
        if (!OperatingSystem.IsWindows())
        {
            return;
        }

        try
        {
            ProcessStartInfo info = new()
            {
                FileName = "powershell",
                Arguments =
                    "-NoProfile -Command \"Get-CimInstance Win32_Process -Filter \\\"Name='java.exe'\\\" | Where-Object { $_.CommandLine -match 'platform-server.*\\.jar' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }\"",
                RedirectStandardOutput = true,
                UseShellExecute = false,
                CreateNoWindow = true
            };
            using Process killer = Process.Start(info)!;
            killer.WaitForExit(5000);
        }
        catch (Exception)
        {
            // 清扫失败不能阻止桌面退出。
        }
    }

    private static void KillByName(string name)
    {
        foreach (Process process in Process.GetProcessesByName(name))
        {
            try
            {
                process.Kill(entireProcessTree: true);
            }
            catch (Exception)
            {
                // 拒绝访问或已经退出都不阻断其余清扫。
            }
            finally
            {
                process.Dispose();
            }
        }
    }

    private static void KillListeners(int port)
    {
        if (!OperatingSystem.IsWindows())
        {
            return;
        }

        try
        {
            ProcessStartInfo info = new()
            {
                FileName = "netstat",
                Arguments = "-ano -p tcp",
                RedirectStandardOutput = true,
                UseShellExecute = false,
                CreateNoWindow = true
            };
            using Process netstat = Process.Start(info)!;
            string output = netstat.StandardOutput.ReadToEnd();
            netstat.WaitForExit(2000);
            HashSet<int> pids = [];
            foreach (string line in output.Split(['\r', '\n'], StringSplitOptions.RemoveEmptyEntries))
            {
                if (!line.Contains($":{port}", StringComparison.Ordinal) || !line.Contains("LISTENING", StringComparison.OrdinalIgnoreCase))
                {
                    continue;
                }

                Match match = Regex.Match(line.Trim(), @"\s(\d+)$");
                if (match.Success && int.TryParse(match.Groups[1].Value, out int pid) && pid > 0)
                {
                    pids.Add(pid);
                }
            }

            foreach (int pid in pids)
            {
                try
                {
                    using Process process = Process.GetProcessById(pid);
                    process.Kill(entireProcessTree: true);
                }
                catch (Exception)
                {
                    // 监听进程可能已经随关机接口退出。
                }
            }
        }
        catch (Exception)
        {
            // 清扫失败不能阻止桌面退出。
        }
    }

    private static async Task<bool> PingAsync(CancellationToken cancellationToken)
    {
        try
        {
            using HttpClient client = new() { Timeout = TimeSpan.FromSeconds(2) };
            HttpResponseMessage response = await client.GetAsync("http://127.0.0.1:17890/api/status", cancellationToken)
                .ConfigureAwait(false);
            return response.IsSuccessStatusCode;
        }
        catch (Exception)
        {
            return false;
        }
    }

    private static string? FindJava()
    {
        string bundled = Path.Combine(AppContext.BaseDirectory, "runtime", "jre", "bin", OperatingSystem.IsWindows() ? "java.exe" : "java");
        if (File.Exists(bundled))
        {
            return bundled;
        }

        string? home = Environment.GetEnvironmentVariable("JAVA_HOME");
        if (!string.IsNullOrWhiteSpace(home))
        {
            string candidate = Path.Combine(home, "bin", OperatingSystem.IsWindows() ? "java.exe" : "java");
            if (File.Exists(candidate))
            {
                return candidate;
            }
        }

        return "java";
    }

    private static string? FindJar()
    {
        string? env = Environment.GetEnvironmentVariable("LLM_PLATFORM_SERVER_JAR");
        if (!string.IsNullOrWhiteSpace(env) && File.Exists(env))
        {
            return env;
        }

        string[] nearby =
        [
            Path.Combine(AppContext.BaseDirectory, "platform-server.jar"),
            Path.Combine(AppContext.BaseDirectory, "runtime", "platform-server.jar")
        ];
        foreach (string path in nearby)
        {
            if (File.Exists(path))
            {
                return path;
            }
        }

        DirectoryInfo? dir = new(AppContext.BaseDirectory);
        for (int i = 0; i < 8 && dir is not null; i++)
        {
            string target = Path.Combine(dir.FullName, "platform-server", "target", "platform-server-0.1.0-SNAPSHOT.jar");
            string meTarget = Path.Combine(dir.FullName, ".me", "build", "platform-server", "platform-server-0.1.0-SNAPSHOT.jar");
            if (File.Exists(target))
            {
                return target;
            }

            if (File.Exists(meTarget))
            {
                return meTarget;
            }

            dir = dir.Parent;
        }

        return null;
    }
}
