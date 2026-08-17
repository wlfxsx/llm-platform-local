using System.Collections.ObjectModel;
using System.Net.Http;
using System.Text.Json;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using LlmPlatform.Desktop.Services;

namespace LlmPlatform.Desktop.ViewModels;

/// <summary>插件管理页状态，所有变更后重新读取服务端列表以服务端状态为准。</summary>
public sealed partial class PluginManagerViewModel : ViewModelBase
{
    private readonly PlatformClient _client;
    private readonly FileDialogService _dialogs;
    private readonly LocalizationService _localization;

    [ObservableProperty]
    private bool _hasPlugins;

    public PluginManagerViewModel(
        PlatformClient client,
        FileDialogService dialogs,
        LocalizationService localization)
    {
        _client = client;
        _dialogs = dialogs;
        _localization = localization;
        Plugins = [];
        localization.Changed += (_, _) => RefreshTexts();
    }

    public ObservableCollection<PluginItemViewModel> Plugins { get; }

    public async Task LoadAsync(CancellationToken cancellationToken)
    {
        Plugins.Clear();
        JsonElement list = await _client.GetAsync("api/plugins", cancellationToken).ConfigureAwait(true);
        foreach (JsonElement item in list.EnumerateArray())
        {
            Plugins.Add(
                new PluginItemViewModel
                {
                    Id = item.GetProperty("id").GetString() ?? string.Empty,
                    Name = item.GetProperty("name").GetString() ?? string.Empty,
                    Version = item.GetProperty("version").GetString() ?? string.Empty,
                    StatusKey = item.GetProperty("status").GetString() ?? string.Empty,
                    Enabled = item.GetProperty("enabled").GetBoolean()
                });
        }
        HasPlugins = Plugins.Count > 0;
        RefreshTexts();
    }

    [RelayCommand]
    private Task RefreshAsync(CancellationToken cancellationToken)
    {
        return LoadAsync(cancellationToken);
    }

    [RelayCommand]
    private async Task ImportAsync(CancellationToken cancellationToken)
    {
        string? path = await _dialogs.OpenFileAsync(cancellationToken).ConfigureAwait(true);
        if (path is null)
        {
            return;
        }

        await _client.SendAsync(HttpMethod.Post, "api/plugins/import", new { path }, cancellationToken)
            .ConfigureAwait(true);
        await LoadAsync(cancellationToken).ConfigureAwait(true);
    }

    [RelayCommand]
    private async Task EnableAsync(PluginItemViewModel item, CancellationToken cancellationToken)
    {
        // Enabled 是服务端返回的当前态，命令执行其反向动作而不是重复设置当前值。
        string action = item.Enabled ? "disable" : "enable";
        await _client.SendAsync(HttpMethod.Post, $"api/plugins/{item.Id}/{action}", new { }, cancellationToken)
            .ConfigureAwait(true);
        await LoadAsync(cancellationToken).ConfigureAwait(true);
    }

    [RelayCommand]
    private async Task DeleteAsync(PluginItemViewModel item, CancellationToken cancellationToken)
    {
        await _client.SendAsync(HttpMethod.Delete, $"api/plugins/{item.Id}", null, cancellationToken)
            .ConfigureAwait(true);
        await LoadAsync(cancellationToken).ConfigureAwait(true);
    }

    private void RefreshTexts()
    {
        foreach (PluginItemViewModel plugin in Plugins)
        {
            plugin.Status = _localization.Text(
                plugin.StatusKey switch
                {
                    "ready" => "PluginReady",
                    "stopped" => "PluginStopped",
                    "waiting-dependency" => "PluginWaitingDependency",
                    _ => plugin.StatusKey
                });
        }
    }
}

/// <summary>插件列表行，包含启停操作所需的稳定 ID 与当前状态。</summary>
public sealed partial class PluginItemViewModel : ViewModelBase
{
    [ObservableProperty]
    private string _id = string.Empty;

    [ObservableProperty]
    private string _name = string.Empty;

    [ObservableProperty]
    private string _version = string.Empty;

    [ObservableProperty]
    private string _status = string.Empty;

    [ObservableProperty]
    private string _statusKey = string.Empty;

    [ObservableProperty]
    private bool _enabled;
}
