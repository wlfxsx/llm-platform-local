using System.Collections.ObjectModel;
using System.Globalization;
using System.Text.Json;
using Avalonia.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using LlmPlatform.Desktop.Services;

namespace LlmPlatform.Desktop.ViewModels;

/// <summary>监控页：仅在可见时每秒拉取快照，隐藏后停止采样。</summary>
public sealed partial class MonitorViewModel : ViewModelBase
{
    private const int WindowSize = 60;
    private readonly PlatformClient _client;
    private readonly LocalizationService _localization;
    private readonly DispatcherTimer _timer;
    private bool _active;
    private bool _loading;

    [ObservableProperty]
    private string _modelState = string.Empty;

    [ObservableProperty]
    private string _modelName = string.Empty;

    [ObservableProperty]
    private string _modelPidText = "PID —";

    [ObservableProperty]
    private string _uptimeText = string.Empty;

    [ObservableProperty]
    private bool _modelRunning;

    [ObservableProperty]
    private bool _gpuAvailable;

    [ObservableProperty]
    private string _systemCpuText = "0%";

    [ObservableProperty]
    private string _systemMemoryText = string.Empty;

    [ObservableProperty]
    private string _platformCpuText = "0%";

    [ObservableProperty]
    private string _platformMemoryText = string.Empty;

    [ObservableProperty]
    private string _modelCpuText = "0%";

    [ObservableProperty]
    private string _modelMemoryText = string.Empty;

    [ObservableProperty]
    private string _gpuName = string.Empty;

    [ObservableProperty]
    private string _gpuText = string.Empty;

    [ObservableProperty]
    private string _inferenceText = string.Empty;

    [ObservableProperty]
    private int _chartRevision;

    public MonitorViewModel(PlatformClient client, LocalizationService localization)
    {
        _client = client;
        _localization = localization;
        Hardware = [];
        SystemCpu = [];
        SystemMemory = [];
        PlatformCpu = [];
        PlatformMemory = [];
        ModelCpu = [];
        ModelMemory = [];
        GpuUtil = [];
        TokenRate = [];
        _timer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
        _timer.Tick += async (_, _) => await TickAsync().ConfigureAwait(true);
        localization.Changed += (_, _) => RefreshTexts();
        RefreshTexts();
    }

    public ObservableCollection<HardwareItemViewModel> Hardware { get; }

    public ObservableCollection<double> SystemCpu { get; }

    public ObservableCollection<double> SystemMemory { get; }

    public ObservableCollection<double> PlatformCpu { get; }

    public ObservableCollection<double> PlatformMemory { get; }

    public ObservableCollection<double> ModelCpu { get; }

    public ObservableCollection<double> ModelMemory { get; }

    public ObservableCollection<double> GpuUtil { get; }

    public ObservableCollection<double> TokenRate { get; }

    public async Task LoadHardwareAsync(CancellationToken cancellationToken)
    {
        Hardware.Clear();
        JsonElement response = await _client.GetAsync("api/hardware", cancellationToken).ConfigureAwait(true);
        foreach (JsonElement item in response.EnumerateArray())
        {
            string type = item.GetProperty("type").GetString() ?? string.Empty;
            bool available = !item.TryGetProperty("available", out JsonElement ava) || ava.GetBoolean();
            string reason = item.TryGetProperty("unavailableReason", out JsonElement why)
                ? why.GetString() ?? string.Empty
                : string.Empty;
            Hardware.Add(
                new HardwareItemViewModel
                {
                    Name = item.GetProperty("name").GetString() ?? string.Empty,
                    Type = type,
                    TypeLabel = _localization.Text(HardwareLabelKey(type)),
                    Recommended = item.TryGetProperty("recommended", out JsonElement rec) && rec.GetBoolean(),
                    RecommendedLabel = _localization.Text("Recommended"),
                    Unavailable = !available,
                    Reason = reason,
                    UnavailableLabel = available ? string.Empty : _localization.Text(HardwareReasonKey(reason))
                });
        }
    }

    public void SetActive(bool active)
    {
        _active = active;
        if (active)
        {
            if (!_timer.IsEnabled)
            {
                _timer.Start();
            }

            _ = TickAsync();
        }
        else
        {
            _timer.Stop();
        }
    }

    private async Task TickAsync()
    {
        if (!_active || _loading)
        {
            return;
        }

        _loading = true;
        try
        {
            JsonElement snapshot = await _client.GetAsync("api/monitor/snapshot", CancellationToken.None).ConfigureAwait(true);
            ApplySnapshot(snapshot);
        }
        catch (Exception)
        {
            ModelState = _localization.Text("MonitorUnavailable");
        }
        finally
        {
            _loading = false;
        }
    }

    private void ApplySnapshot(JsonElement snapshot)
    {
        JsonElement llamafile = snapshot.GetProperty("llamafile");
        string state = llamafile.GetProperty("state").GetString() ?? "stopped";
        ModelRunning = state is "ready" or "starting";
        ModelState = _localization.Text(state switch
        {
            "ready" => "ModelStateReady",
            "starting" => "ModelStateStarting",
            _ => "ModelStateStopped"
        });
        ModelName = llamafile.TryGetProperty("modelName", out JsonElement name) && name.ValueKind == JsonValueKind.String
            ? name.GetString() ?? _localization.Text("MonitorNoModel")
            : _localization.Text("MonitorNoModel");
        ModelPidText = llamafile.TryGetProperty("pid", out JsonElement pid) && pid.ValueKind == JsonValueKind.Number
            ? $"PID {pid.GetInt64().ToString(CultureInfo.InvariantCulture)}"
            : "PID —";
        UptimeText = FormatUptime(llamafile);

        Push(SystemCpu, ReadDouble(snapshot.GetProperty("system"), "cpuPercent"));
        double memUsed = ReadDouble(snapshot.GetProperty("system"), "memoryUsedBytes");
        double memTotal = ReadDouble(snapshot.GetProperty("system"), "memoryTotalBytes");
        Push(SystemMemory, memTotal <= 0 ? 0 : memUsed * 100d / memTotal);
        SystemCpuText = $"{SystemCpu.LastOrDefault():0.0}%";
        SystemMemoryText = $"{FormatBytes(memUsed)} / {FormatBytes(memTotal)}";

        JsonElement platform = snapshot.GetProperty("platformProcess");
        Push(PlatformCpu, ReadDouble(platform, "cpuPercent"));
        Push(PlatformMemory, ReadDouble(platform, "rssBytes") / (1024d * 1024d));
        PlatformCpuText = $"{PlatformCpu.LastOrDefault():0.0}%";
        PlatformMemoryText = FormatBytes(ReadDouble(platform, "rssBytes"));

        JsonElement model = snapshot.GetProperty("modelProcess");
        bool modelAvailable = model.TryGetProperty("available", out JsonElement av) && av.GetBoolean();
        Push(ModelCpu, modelAvailable ? ReadDouble(model, "cpuPercent") : 0);
        Push(ModelMemory, modelAvailable ? ReadDouble(model, "rssBytes") / (1024d * 1024d) : 0);
        ModelCpuText = modelAvailable ? $"{ModelCpu.LastOrDefault():0.0}%" : _localization.Text("MonitorModelIdle");
        // 线程数并入内存读数，避免这张卡片比同排其它卡片多出一行导致图表错位。
        ModelMemoryText = modelAvailable
            ? $"{FormatBytes(ReadDouble(model, "rssBytes"))} · {ReadDouble(model, "threadCount"):0} {_localization.Text("MonitorThreads")}"
            : "—";

        JsonElement gpu = snapshot.GetProperty("gpu");
        GpuAvailable = gpu.TryGetProperty("available", out JsonElement gpuOn) && gpuOn.GetBoolean();
        Push(GpuUtil, GpuAvailable ? ReadDouble(gpu, "utilizationPercent") : 0);
        GpuName = GpuAvailable
            ? gpu.GetProperty("name").GetString() ?? _localization.Text("MonitorGpu")
            : _localization.Text("MonitorGpu");
        GpuText = GpuAvailable
            ? $"{GpuUtil.LastOrDefault():0.0}% · {FormatBytes(ReadDouble(gpu, "memoryUsedBytes"))} / {FormatBytes(ReadDouble(gpu, "memoryTotalBytes"))}"
            : _localization.Text("MonitorGpuUnavailable");

        JsonElement inference = snapshot.GetProperty("inference");
        double tps = ReadNullableDouble(inference, "tokensPerSecond") ?? 0;
        Push(TokenRate, tps);
        int? active = ReadNullableInt(inference, "activeRequests");
        InferenceText = active is null && ReadNullableDouble(inference, "tokensPerSecond") is null
            ? _localization.Text("MonitorInferenceUnavailable")
            : $"{tps:0.0} tok/s · {active ?? 0}";

        ChartRevision++;
    }

    private void RefreshTexts()
    {
        foreach (HardwareItemViewModel device in Hardware)
        {
            device.TypeLabel = _localization.Text(HardwareLabelKey(device.Type));
            device.RecommendedLabel = _localization.Text("Recommended");
            if (device.Unavailable)
            {
                device.UnavailableLabel = _localization.Text(HardwareReasonKey(device.Reason));
            }
        }

        // 首次采样前也要让每张卡片有占位读数，否则标题下会出现空行。
        if (!_active)
        {
            ModelState = _localization.Text("ModelStateStopped");
            ModelName = _localization.Text("MonitorNoModel");
            UptimeText = "—";
            SystemMemoryText = "—";
            PlatformMemoryText = "—";
            ModelCpuText = _localization.Text("MonitorModelIdle");
            ModelMemoryText = "—";
            GpuText = _localization.Text("MonitorGpuUnavailable");
            InferenceText = _localization.Text("MonitorInferenceUnavailable");
        }

        GpuName = string.IsNullOrWhiteSpace(GpuName) ? _localization.Text("MonitorGpu") : GpuName;
    }

    private static void Push(ObservableCollection<double> series, double value)
    {
        series.Add(value);
        while (series.Count > WindowSize)
        {
            series.RemoveAt(0);
        }
    }

    private static double ReadDouble(JsonElement element, string name)
    {
        return element.TryGetProperty(name, out JsonElement value) && value.ValueKind == JsonValueKind.Number
            ? value.GetDouble()
            : 0;
    }

    private static double? ReadNullableDouble(JsonElement element, string name)
    {
        return element.TryGetProperty(name, out JsonElement value) && value.ValueKind == JsonValueKind.Number
            ? value.GetDouble()
            : null;
    }

    private static int? ReadNullableInt(JsonElement element, string name)
    {
        return element.TryGetProperty(name, out JsonElement value) && value.ValueKind == JsonValueKind.Number
            ? value.GetInt32()
            : null;
    }

    private string FormatUptime(JsonElement llamafile)
    {
        if (!llamafile.TryGetProperty("startedAt", out JsonElement started) || started.ValueKind != JsonValueKind.Number)
        {
            return "—";
        }

        TimeSpan span = DateTimeOffset.UtcNow - DateTimeOffset.FromUnixTimeMilliseconds(started.GetInt64());
        if (span.TotalHours >= 1)
        {
            return $"{(int)span.TotalHours}h {span.Minutes}m";
        }

        return span.TotalMinutes >= 1 ? $"{span.Minutes}m {span.Seconds}s" : $"{Math.Max(0, span.Seconds)}s";
    }

    private static string FormatBytes(double bytes)
    {
        if (bytes <= 0)
        {
            return "0 B";
        }

        string[] units = ["B", "KB", "MB", "GB", "TB"];
        int unit = 0;
        while (bytes >= 1024 && unit < units.Length - 1)
        {
            bytes /= 1024;
            unit++;
        }

        return $"{bytes:0.0} {units[unit]}";
    }

    private static string HardwareLabelKey(string type)
    {
        return type switch
        {
            "cpu" => "HwCpu",
            "igpu" => "HwIgpu",
            "gpu" => "HwGpu",
            _ => "HwOther"
        };
    }

    private static string HardwareReasonKey(string reason)
    {
        return reason switch
        {
            "noVulkanRuntime" => "HwNoVulkan",
            _ => "HwUnavailable"
        };
    }
}
