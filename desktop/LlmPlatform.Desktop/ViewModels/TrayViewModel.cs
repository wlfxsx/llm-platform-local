using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using LlmPlatform.Desktop.Services;

namespace LlmPlatform.Desktop.ViewModels;

/// <summary>
/// 托盘常驻状态：负责拉起本机服务，并提供托盘菜单命令。
/// </summary>
public sealed partial class TrayViewModel : ViewModelBase
{
    private readonly ServerProcessService _server;
    private readonly LocalizationService _localization;
    private readonly IWindowService _windows;
    private readonly PlatformClient _client;

    [ObservableProperty]
    private string _trayTooltip = string.Empty;

    [ObservableProperty]
    private string _chatMenuHeader = string.Empty;

    [ObservableProperty]
    private string _settingsMenuHeader = string.Empty;

    [ObservableProperty]
    private string _exitMenuHeader = string.Empty;

    [ObservableProperty]
    private string _serverStatus = string.Empty;

    [ObservableProperty]
    private bool _serverReady;

    [ObservableProperty]
    private bool _navCollapsed;

    private string _serverStatusKey = "ServerStarting";

    public TrayViewModel(
        PlatformClient client,
        ServerProcessService server,
        LocalizationService localization,
        FileDialogService dialogs,
        IWindowService windows)
    {
        _client = client;
        _server = server;
        _localization = localization;
        _windows = windows;
        Chat = new ChatViewModel(client, localization);
        Settings = new SettingsViewModel(client, localization, dialogs);
        Plugins = new PluginManagerViewModel(client, dialogs, localization);
        Monitor = new MonitorViewModel(client, localization);
        localization.Changed += (_, _) => RefreshTexts();
        RefreshTexts();
    }

    public ChatViewModel Chat { get; }

    public SettingsViewModel Settings { get; }

    public PluginManagerViewModel Plugins { get; }

    public MonitorViewModel Monitor { get; }

    public async Task InitializeAsync()
    {
        // 初始化设置 60 秒总边界，避免本机 Java 启动异常时托盘永久停留在“启动中”。
        using CancellationTokenSource cts = new(TimeSpan.FromSeconds(60));
        SetStatus("ServerStarting");
        bool ready = await _server.EnsureRunningAsync(cts.Token).ConfigureAwait(true);
        ServerReady = ready;
        SetStatus(ready ? "ServerReady" : "ServerFailed");
        if (!ready)
        {
            return;
        }

        // 只有控制面就绪后才加载页面数据，避免启动竞态产生一批误导性的连接错误。
        await Settings.LoadAsync(CancellationToken.None).ConfigureAwait(true);
        await Plugins.LoadAsync(CancellationToken.None).ConfigureAwait(true);
        await Monitor.LoadHardwareAsync(CancellationToken.None).ConfigureAwait(true);
        Settings.StartStatusPolling();
    }

    public async Task ShutdownBackgroundAsync()
    {
        Settings.StopStatusPolling();
        Monitor.SetActive(false);
        using CancellationTokenSource cts = new(TimeSpan.FromSeconds(15));
        await _server.ShutdownAllAsync(_client, cts.Token).ConfigureAwait(false);
    }

    [RelayCommand]
    private void ShowChat()
    {
        _windows.ShowChat();
    }

    [RelayCommand]
    private void ShowSettings()
    {
        _windows.ShowSettings();
    }

    [RelayCommand]
    private void Exit()
    {
        _windows.Shutdown();
    }

    [RelayCommand]
    private void ToggleNav()
    {
        NavCollapsed = !NavCollapsed;
    }

    private void SetStatus(string key)
    {
        _serverStatusKey = key;
        ServerStatus = _localization.Text(key);
        TrayTooltip = $"{_localization.Text("AppTitle")} · {ServerStatus}";
    }

    private void RefreshTexts()
    {
        ChatMenuHeader = _localization.Text("TrayChat");
        SettingsMenuHeader = _localization.Text("TraySettings");
        ExitMenuHeader = _localization.Text("TrayExit");
        ServerStatus = _localization.Text(_serverStatusKey);
        TrayTooltip = $"{_localization.Text("AppTitle")} · {ServerStatus}";
    }
}
