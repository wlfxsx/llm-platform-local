using System.Collections.ObjectModel;
using System.Globalization;
using System.Net.Http;
using System.Text.Json;
using Avalonia.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using LlmPlatform.Desktop.Services;

namespace LlmPlatform.Desktop.ViewModels;

/// <summary>
/// 设置窗口的聚合状态。应用级设置与每模型设置分别保存，避免切换模型时覆盖其它模型参数。
/// </summary>
public sealed partial class SettingsViewModel : ViewModelBase
{
    private readonly PlatformClient _client;
    private readonly LocalizationService _localization;
    private readonly FileDialogService _dialogs;
    private bool _loading;
    private bool _loaded;
    private int _autoSaveSuppression;
    private string _currentModelId = string.Empty;
    private string _hardwareDeviceId = string.Empty;
    private string _currentRemoteModelId = string.Empty;
    private string _modelState = string.Empty;
    private string _modelStateKey = "ModelStateStopped";
    private DispatcherTimer? _statusTimer;

    [ObservableProperty]
    private OptionItemViewModel? _selectedLanguage;

    [ObservableProperty]
    private OptionItemViewModel? _selectedTheme;

    [ObservableProperty]
    private OptionItemViewModel? _selectedChatProvider;

    [ObservableProperty]
    private bool _networkEnabled;

    [ObservableProperty]
    private bool _isLocalChat = true;

    [ObservableProperty]
    private bool _isRemoteChat;

    [ObservableProperty]
    private string _remoteNetworkHint = string.Empty;

    [ObservableProperty]
    private bool _remoteLocked;

    [ObservableProperty]
    private RemoteModelItemViewModel? _selectedRemoteModel;

    [ObservableProperty]
    private string _remoteName = string.Empty;

    [ObservableProperty]
    private string _remoteBaseUrl = string.Empty;

    [ObservableProperty]
    private string _remoteModelName = string.Empty;

    [ObservableProperty]
    private string _remoteApiKey = string.Empty;

    [ObservableProperty]
    private string _remoteApiKeyHint = string.Empty;

    [ObservableProperty]
    private bool _remoteEditingNew;

    [ObservableProperty]
    private ModelItemViewModel? _selectedModel;

    [ObservableProperty]
    private HardwareItemViewModel? _selectedHardware;

    [ObservableProperty]
    private bool _hasSelectedModel;

    [ObservableProperty]
    private int _contextSize = 4096;

    [ObservableProperty]
    private int _threads = 4;

    [ObservableProperty]
    private int _gpuLayers = 999;

    [ObservableProperty]
    private bool _gpuLayersAuto = true;

    private int _manualGpuLayers;

    [ObservableProperty]
    private int _batchSize = 512;

    [ObservableProperty]
    private int _ubatchSize = 512;

    [ObservableProperty]
    private bool _flashAttention;

    [ObservableProperty]
    private bool _memoryMap = true;

    [ObservableProperty]
    private bool _memoryLock;

    [ObservableProperty]
    private double _temperature = 0.7;

    [ObservableProperty]
    private double _topP = 0.9;

    [ObservableProperty]
    private int _topK = 40;

    [ObservableProperty]
    private double _minP = 0.05;

    [ObservableProperty]
    private int _maxTokens = 1024;

    [ObservableProperty]
    private double _repeatPenalty = 1.1;

    [ObservableProperty]
    private int _repeatLastN = 64;

    [ObservableProperty]
    private string _seedText = string.Empty;

    [ObservableProperty]
    private double _frequencyPenalty;

    [ObservableProperty]
    private double _presencePenalty;

    [ObservableProperty]
    private string _stopText = string.Empty;

    [ObservableProperty]
    private bool _compressionEnabled = true;

    [ObservableProperty]
    private double _compressionTriggerRatio = 0.75;

    [ObservableProperty]
    private int _keepRecentMessages = 8;

    [ObservableProperty]
    private int _summaryMaxTokens = 256;

    [ObservableProperty]
    private string _advancedJson = "{}";

    [ObservableProperty]
    private string _advancedJsonError = string.Empty;

    [ObservableProperty]
    private bool _advancedJsonValid = true;

    [ObservableProperty]
    private string _restartHint = string.Empty;

    [ObservableProperty]
    private bool _modelRunning;

    [ObservableProperty]
    private bool _modelBusy;

    [ObservableProperty]
    private string _modelRuntimeStatus = string.Empty;

    [ObservableProperty]
    private string _profileName = string.Empty;

    [ObservableProperty]
    private string _profileHint = string.Empty;

    [ObservableProperty]
    private ProfileItemViewModel? _selectedProfile;

    /// <summary>参数属于预设而非模型，因此只在本地进程运行时锁定编辑。</summary>
    public bool CanEditModel => IsLocalChat && !ModelRunning && !ModelBusy;

    public bool CanStartModel =>
        IsLocalChat && HasSelectedModel && SelectedProfile is not null && !ModelRunning && !ModelBusy;

    public bool CanStopModel => IsLocalChat && ModelRunning && !ModelBusy;

    public bool CanSaveModelConfig => CanEditModel && AdvancedJsonValid;

    public bool CanEditGpuLayers => CanEditModel && !GpuLayersAuto;

    public bool CanEditRemote => IsRemoteChat && !RemoteLocked && !ModelBusy;

    /// <summary>只有选了远程来源、且联网总开关关闭时才需要提示先开联网。</summary>
    public bool ShowRemoteNetworkHint => IsRemoteChat && RemoteLocked;

    public bool CanSaveRemote => CanEditRemote && !string.IsNullOrWhiteSpace(RemoteName)
        && !string.IsNullOrWhiteSpace(RemoteBaseUrl)
        && !string.IsNullOrWhiteSpace(RemoteModelName)
        && (RemoteEditingNew || SelectedRemoteModel is not null)
        && (RemoteEditingNew ? !string.IsNullOrWhiteSpace(RemoteApiKey) : true);

    public bool CanTestRemote => CanEditRemote && SelectedRemoteModel is not null && !RemoteEditingNew;

    public bool CanDeleteRemote => CanEditRemote && SelectedRemoteModel is not null && !RemoteEditingNew;

    public SettingsViewModel(PlatformClient client, LocalizationService localization, FileDialogService dialogs)
    {
        _client = client;
        _localization = localization;
        _dialogs = dialogs;
        Hardware = [];
        Models = [];
        RemoteModels = [];
        Capabilities = [];
        Tools = [];
        Documents = [];
        McpServers = [];
        Skills = [];
        Profiles = [];
        Languages =
        [
            new OptionItemViewModel("zh", "LanguageChinese"),
            new OptionItemViewModel("en", "LanguageEnglish")
        ];
        Themes =
        [
            new OptionItemViewModel("system", "ThemeSystem"),
            new OptionItemViewModel("light", "ThemeLight"),
            new OptionItemViewModel("dark", "ThemeDark")
        ];
        ChatProviders =
        [
            new OptionItemViewModel("local", "ChatProviderLocal"),
            new OptionItemViewModel("remote", "ChatProviderRemote")
        ];
        SelectedLanguage = Languages[0];
        SelectedTheme = Themes[0];
        SelectedChatProvider = ChatProviders[0];
        localization.Changed += (_, _) => RefreshTexts();
        RefreshTexts();
    }

    public ObservableCollection<OptionItemViewModel> Languages { get; }

    public ObservableCollection<OptionItemViewModel> Themes { get; }

    public ObservableCollection<OptionItemViewModel> ChatProviders { get; }

    public ObservableCollection<HardwareItemViewModel> Hardware { get; }

    public ObservableCollection<ModelItemViewModel> Models { get; }

    public ObservableCollection<RemoteModelItemViewModel> RemoteModels { get; }

    public ObservableCollection<CapabilityItemViewModel> Capabilities { get; }

    public ObservableCollection<string> Tools { get; }

    public ObservableCollection<DocumentItemViewModel> Documents { get; }

    public ObservableCollection<string> McpServers { get; }

    public ObservableCollection<SkillItemViewModel> Skills { get; }

    public ObservableCollection<ProfileItemViewModel> Profiles { get; }

    /// <summary>先应用语言和主题，再刷新依赖本地化资源的列表展示文本。</summary>
    public async Task LoadAsync(CancellationToken cancellationToken)
    {
        string language;
        string theme;
        using (SuppressAutoSave())
        {
            JsonElement settings = await _client.GetAsync("api/settings", cancellationToken).ConfigureAwait(true);
            language = settings.GetProperty("language").GetString() ?? "zh";
            theme = settings.GetProperty("theme").GetString() ?? "system";
            SelectedLanguage = Find(Languages, language);
            SelectedTheme = Find(Themes, theme);
            NetworkEnabled = settings.GetProperty("networkEnabled").GetBoolean();
            _currentModelId = settings.TryGetProperty("currentModelId", out JsonElement current)
                ? current.GetString() ?? string.Empty
                : string.Empty;
            _hardwareDeviceId = settings.TryGetProperty("hardwareDeviceId", out JsonElement device)
                ? device.GetString() ?? string.Empty
                : string.Empty;
            _currentRemoteModelId = settings.TryGetProperty("currentRemoteModelId", out JsonElement remoteId)
                ? remoteId.GetString() ?? string.Empty
                : string.Empty;
            string chatProvider = settings.TryGetProperty("chatProvider", out JsonElement provider)
                ? provider.GetString() ?? "local"
                : "local";
            SelectedChatProvider = Find(ChatProviders, chatProvider) ?? ChatProviders[0];
            ApplyChatProviderFlags(SelectedChatProvider.Value);
            _localization.Apply(language, theme);

            await ReloadListsAsync(cancellationToken).ConfigureAwait(true);
        }

        // 读到服务端取值后才允许自动保存，否则构造期的默认值会被当成用户改动写回。
        _loaded = true;
        await RefreshModelStatusAsync(cancellationToken).ConfigureAwait(true);
    }

    public void StartStatusPolling()
    {
        _statusTimer ??= new DispatcherTimer { Interval = TimeSpan.FromSeconds(2) };
        _statusTimer.Tick -= OnStatusTick;
        _statusTimer.Tick += OnStatusTick;
        _statusTimer.Start();
    }

    public void StopStatusPolling()
    {
        if (_statusTimer is null)
        {
            return;
        }

        _statusTimer.Tick -= OnStatusTick;
        _statusTimer.Stop();
    }

    private async void OnStatusTick(object? sender, EventArgs e)
    {
        try
        {
            await RefreshModelStatusAsync(CancellationToken.None).ConfigureAwait(true);
        }
        catch (Exception)
        {
            // 轮询失败时保留上一状态，避免把界面锁死在忙碌。
        }
    }

    /// <summary>把常规页的当前取值写回服务端；调用方负责界面刷新与失败回读。</summary>
    private async Task PersistSettingsAsync(CancellationToken cancellationToken)
    {
        string language = SelectedLanguage?.Value ?? "zh";
        string theme = SelectedTheme?.Value ?? "system";
        JsonElement current = await _client.GetAsync("api/settings", cancellationToken).ConfigureAwait(true);
        // 应用设置可能包含当前界面尚未编辑的字段，基于服务端快照合并可避免误删。
        Dictionary<string, object?> body = JsonSerializer.Deserialize<Dictionary<string, object?>>(current.GetRawText())
            ?? [];
        body["language"] = language;
        body["theme"] = theme;
        body["networkEnabled"] = NetworkEnabled;
        body["chatProvider"] = SelectedChatProvider?.Value ?? "local";
        body["currentRemoteModelId"] = _currentRemoteModelId;
        if (SelectedHardware is not null)
        {
            _hardwareDeviceId = SelectedHardware.Id;
            body["hardwareDeviceId"] = _hardwareDeviceId;
        }

        await _client.SendAsync(HttpMethod.Put, "api/settings", body, cancellationToken).ConfigureAwait(true);
        // 联网总开关与能力开关必须同向，否则能力页看起来开着却无法出网。
        try
        {
            await _client.SendAsync(
                    HttpMethod.Put,
                    "api/capabilities/network",
                    new { enabled = NetworkEnabled },
                    cancellationToken)
                .ConfigureAwait(true);
        }
        catch
        {
            // 设置已写入；能力同步失败时由调用方回读列表，避免开关停在错误位置。
        }

        _localization.Apply(language, theme);
    }

    /// <summary>
    /// 常规页取消了保存按钮，任一选项变化后立即写回；写入失败时回读服务端，
    /// 避免界面停在没有生效的取值上。
    /// </summary>
    private async Task AutoSaveAsync(bool reloadLists)
    {
        if (!_loaded || _autoSaveSuppression > 0)
        {
            return;
        }

        try
        {
            await PersistSettingsAsync(CancellationToken.None).ConfigureAwait(true);
            using (SuppressAutoSave())
            {
                if (reloadLists)
                {
                    await ReloadListsAsync(CancellationToken.None).ConfigureAwait(true);
                }
                else
                {
                    await ReloadCapabilitiesAsync(CancellationToken.None).ConfigureAwait(true);
                }
            }
        }
        catch (Exception)
        {
            try
            {
                await LoadAsync(CancellationToken.None).ConfigureAwait(true);
            }
            catch (Exception)
            {
                // 服务端不可用时保留界面现状，下一次操作会重试。
            }
        }
    }

    /// <summary>回读服务端数据期间屏蔽自动保存，防止赋值触发新一轮写入形成回环。</summary>
    private IDisposable SuppressAutoSave()
    {
        _autoSaveSuppression++;
        return new AutoSaveScope(this);
    }

    /// <summary>硬件选择虽然改完即存，但保存失败时不会阻断操作；启动前再兜底写一次，避免按旧设备拉起进程。</summary>
    private async Task PersistHardwareAsync(CancellationToken cancellationToken)
    {
        if (SelectedHardware is null || SelectedHardware.Id == _hardwareDeviceId)
        {
            return;
        }

        JsonElement current = await _client.GetAsync("api/settings", cancellationToken).ConfigureAwait(true);
        Dictionary<string, object?> body = JsonSerializer.Deserialize<Dictionary<string, object?>>(current.GetRawText())
            ?? [];
        _hardwareDeviceId = SelectedHardware.Id;
        body["hardwareDeviceId"] = _hardwareDeviceId;
        await _client.SendAsync(HttpMethod.Put, "api/settings", body, cancellationToken).ConfigureAwait(true);
    }

    [RelayCommand]
    private Task ImportModelAsync(CancellationToken cancellationToken)
    {
        return RunGuardedAsync(() => ImportModelCoreAsync(cancellationToken), "OperationFailed");
    }

    private async Task ImportModelCoreAsync(CancellationToken cancellationToken)
    {
        string? path = await _dialogs.OpenFileAsync(cancellationToken).ConfigureAwait(true);
        if (path is null)
        {
            return;
        }

        JsonElement imported = await _client.SendAsync(HttpMethod.Post, "api/models/import", new { path }, cancellationToken)
            .ConfigureAwait(true);
        _currentModelId = imported.TryGetProperty("id", out JsonElement id)
            ? id.GetString() ?? _currentModelId
            : _currentModelId;
        await ReloadListsAsync(cancellationToken).ConfigureAwait(true);
    }

    [RelayCommand]
    private Task DeleteModelAsync(CancellationToken cancellationToken)
    {
        return RunGuardedAsync(() => DeleteModelCoreAsync(cancellationToken), "OperationFailed");
    }

    private async Task DeleteModelCoreAsync(CancellationToken cancellationToken)
    {
        if (SelectedModel is null || !CanEditModel)
        {
            return;
        }

        string deletedId = SelectedModel.Id;
        await _client.SendAsync(HttpMethod.Delete, $"api/models/{deletedId}", null, cancellationToken)
            .ConfigureAwait(true);
        if (string.Equals(_currentModelId, deletedId, StringComparison.Ordinal))
        {
            _currentModelId = string.Empty;
        }

        await ReloadModelsAsync(cancellationToken).ConfigureAwait(true);
        RestartHint = _localization.Text("ModelDeleted");
    }

    /// <summary>启动前先把所选模型设为当前模型，再按所选预设写入运行参数。</summary>
    [RelayCommand]
    private async Task StartLlamafileAsync(CancellationToken cancellationToken)
    {
        if (SelectedModel is null || SelectedProfile is null)
        {
            return;
        }

        ModelBusy = true;
        NotifyLock();
        try
        {
        if (SelectedHardware is not null)
        {
            await PersistHardwareAsync(cancellationToken).ConfigureAwait(true);
        }

        if (!string.Equals(SelectedModel.Id, _currentModelId, StringComparison.Ordinal))
            {
                await _client.SendAsync(HttpMethod.Post, $"api/models/{SelectedModel.Id}/select", new { }, cancellationToken)
                    .ConfigureAwait(true);
                _currentModelId = SelectedModel.Id;
            }

            await _client.SendAsync(
                    HttpMethod.Post,
                    "api/status/llamafile/start",
                    new { profileId = SelectedProfile.Id },
                    cancellationToken)
                .ConfigureAwait(true);
            await RefreshModelStatusAsync(cancellationToken).ConfigureAwait(true);
        }
        catch (Exception)
        {
            RestartHint = _localization.Text("ChatFailed");
        }
        finally
        {
            ModelBusy = false;
            NotifyLock();
        }
    }

    [RelayCommand]
    private async Task StopLlamafileAsync(CancellationToken cancellationToken)
    {
        ModelBusy = true;
        NotifyLock();
        try
        {
            await RunGuardedAsync(
                    async () =>
                    {
                        await _client.SendAsync(HttpMethod.Post, "api/status/llamafile/stop", new { }, cancellationToken)
                            .ConfigureAwait(true);
                        await RefreshModelStatusAsync(cancellationToken).ConfigureAwait(true);
                    },
                    "OperationFailed")
                .ConfigureAwait(true);
        }
        finally
        {
            ModelBusy = false;
            NotifyLock();
        }
    }

    [RelayCommand]
    private void NewRemoteModel()
    {
        if (!CanEditRemote)
        {
            return;
        }

        RemoteEditingNew = true;
        SelectedRemoteModel = null;
        RemoteName = string.Empty;
        RemoteBaseUrl = "https://api.openai.com";
        RemoteModelName = string.Empty;
        RemoteApiKey = string.Empty;
        RemoteApiKeyHint = _localization.Text("RemoteApiKeyMissing");
        NotifyLock();
    }

    /// <summary>
    /// 接口错误只转成界面提示：命令里未捕获的异常会沿 UI 线程抛出并结束进程。
    /// 服务端统一错误 JSON 已按当前语言给出 message，优先展示它。
    /// </summary>
    private async Task RunGuardedAsync(Func<Task> action, string fallbackKey, Action<string>? report = null)
    {
        try
        {
            await action().ConfigureAwait(true);
        }
        catch (OperationCanceledException)
        {
            // 窗口关闭或取消令牌触发，无需提示。
        }
        catch (Exception ex)
        {
            string text = ServerMessage(ex) ?? _localization.Text(fallbackKey);
            if (report is null)
            {
                RestartHint = text;
            }
            else
            {
                report(text);
            }
        }
    }

    private static string? ServerMessage(Exception ex)
    {
        try
        {
            using JsonDocument document = JsonDocument.Parse(ex.Message);
            return document.RootElement.TryGetProperty("message", out JsonElement message)
                ? message.GetString()
                : null;
        }
        catch (JsonException)
        {
            return null;
        }
    }

    [RelayCommand]
    private Task SaveRemoteModelAsync(CancellationToken cancellationToken)
    {
        return RunGuardedAsync(() => SaveRemoteModelCoreAsync(cancellationToken), "RemoteSaveFailed");
    }

    private async Task SaveRemoteModelCoreAsync(CancellationToken cancellationToken)
    {
        if (!CanSaveRemote)
        {
            return;
        }

        object body = new
        {
            name = RemoteName.Trim(),
            baseUrl = RemoteBaseUrl.Trim(),
            modelName = RemoteModelName.Trim(),
            apiKey = string.IsNullOrWhiteSpace(RemoteApiKey) ? null : RemoteApiKey.Trim()
        };
        JsonElement saved;
        if (RemoteEditingNew || SelectedRemoteModel is null)
        {
            saved = await _client.SendAsync(HttpMethod.Post, "api/remote-models", body, cancellationToken)
                .ConfigureAwait(true);
        }
        else
        {
            saved = await _client.SendAsync(
                    HttpMethod.Put,
                    $"api/remote-models/{SelectedRemoteModel.Id}",
                    body,
                    cancellationToken)
                .ConfigureAwait(true);
        }

        _currentRemoteModelId = saved.GetProperty("id").GetString() ?? string.Empty;
        RemoteApiKey = string.Empty;
        RemoteEditingNew = false;
        await PersistSettingsAsync(cancellationToken).ConfigureAwait(true);
        await ReloadRemoteModelsAsync(cancellationToken).ConfigureAwait(true);
        RestartHint = _localization.Text("RemoteSaved");
        await RefreshModelStatusAsync(cancellationToken).ConfigureAwait(true);
    }

    [RelayCommand]
    private Task DeleteRemoteModelAsync(CancellationToken cancellationToken)
    {
        return RunGuardedAsync(() => DeleteRemoteModelCoreAsync(cancellationToken), "RemoteDeleteFailed");
    }

    private async Task DeleteRemoteModelCoreAsync(CancellationToken cancellationToken)
    {
        if (!CanDeleteRemote || SelectedRemoteModel is null)
        {
            return;
        }

        string id = SelectedRemoteModel.Id;
        await _client.SendAsync(HttpMethod.Delete, $"api/remote-models/{id}", null, cancellationToken)
            .ConfigureAwait(true);
        if (string.Equals(_currentRemoteModelId, id, StringComparison.Ordinal))
        {
            _currentRemoteModelId = string.Empty;
        }

        await ReloadRemoteModelsAsync(cancellationToken).ConfigureAwait(true);
        await PersistSettingsAsync(cancellationToken).ConfigureAwait(true);
        await RefreshModelStatusAsync(cancellationToken).ConfigureAwait(true);
    }

    [RelayCommand]
    private Task TestRemoteModelAsync(CancellationToken cancellationToken)
    {
        return RunGuardedAsync(() => TestRemoteModelCoreAsync(cancellationToken), "RemoteTestFailed");
    }

    private async Task TestRemoteModelCoreAsync(CancellationToken cancellationToken)
    {
        if (!CanTestRemote || SelectedRemoteModel is null)
        {
            return;
        }

        JsonElement result = await _client.SendAsync(
                HttpMethod.Post,
                $"api/remote-models/{SelectedRemoteModel.Id}/test",
                new { },
                cancellationToken)
            .ConfigureAwait(true);
        bool ok = result.TryGetProperty("ok", out JsonElement okNode) && okNode.GetBoolean();
        RestartHint = ok ? _localization.Text("RemoteTestOk") : _localization.Text("RemoteTestFailed");
    }

    /// <summary>参数只能存为预设；同名保存由后端按覆盖处理。</summary>
    [RelayCommand]
    private Task SaveProfileAsync(CancellationToken cancellationToken)
    {
        return RunGuardedAsync(
            () => SaveProfileCoreAsync(cancellationToken),
            "OperationFailed",
            message => ProfileHint = message);
    }

    private async Task SaveProfileCoreAsync(CancellationToken cancellationToken)
    {
        if (!CanSaveModelConfig)
        {
            return;
        }

        string name = string.IsNullOrWhiteSpace(ProfileName)
            ? SelectedProfile?.Name ?? string.Empty
            : ProfileName.Trim();
        if (string.IsNullOrWhiteSpace(name))
        {
            ProfileHint = _localization.Text("ProfileNameRequired");
            return;
        }

        JsonElement saved = await _client.SendAsync(
                HttpMethod.Post,
                "api/model-config-profiles",
                new { name, config = BuildConfigBody() },
                cancellationToken)
            .ConfigureAwait(true);
        string id = saved.GetProperty("id").GetString() ?? string.Empty;
        await ReloadProfilesAsync(cancellationToken).ConfigureAwait(true);
        SelectedProfile = Profiles.FirstOrDefault(item => item.Id == id) ?? Profiles.FirstOrDefault();
        ProfileHint = _localization.Text("ProfileSaved");
    }

    [RelayCommand]
    private Task DeleteProfileAsync(CancellationToken cancellationToken)
    {
        return RunGuardedAsync(
            () => DeleteProfileCoreAsync(cancellationToken),
            "OperationFailed",
            message => ProfileHint = message);
    }

    private async Task DeleteProfileCoreAsync(CancellationToken cancellationToken)
    {
        if (SelectedProfile is null || !CanEditModel)
        {
            return;
        }

        await _client.SendAsync(HttpMethod.Delete, $"api/model-config-profiles/{SelectedProfile.Id}", null, cancellationToken)
            .ConfigureAwait(true);
        await ReloadProfilesAsync(cancellationToken).ConfigureAwait(true);
        ProfileHint = _localization.Text("ProfileDeleted");
    }

    [RelayCommand]
    private Task ImportDocumentAsync(CancellationToken cancellationToken)
    {
        return RunGuardedAsync(() => ImportDocumentCoreAsync(cancellationToken), "OperationFailed");
    }

    private async Task ImportDocumentCoreAsync(CancellationToken cancellationToken)
    {
        string? path = await _dialogs.OpenFileAsync(cancellationToken).ConfigureAwait(true);
        if (path is null)
        {
            return;
        }

        await _client.SendAsync(HttpMethod.Post, "api/rag/documents/import", new { path }, cancellationToken)
            .ConfigureAwait(true);
        await ReloadListsAsync(cancellationToken).ConfigureAwait(true);
    }

    [RelayCommand]
    private Task DeleteDocumentAsync(DocumentItemViewModel item, CancellationToken cancellationToken)
    {
        return RunGuardedAsync(() => DeleteDocumentCoreAsync(item, cancellationToken), "OperationFailed");
    }

    private async Task DeleteDocumentCoreAsync(DocumentItemViewModel item, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(item.Id))
        {
            return;
        }

        await _client.SendAsync(HttpMethod.Delete, $"api/rag/documents/{item.Id}", null, cancellationToken)
            .ConfigureAwait(true);
        await ReloadListsAsync(cancellationToken).ConfigureAwait(true);
    }

    [RelayCommand]
    private Task ImportSkillAsync(CancellationToken cancellationToken)
    {
        return RunGuardedAsync(() => ImportSkillCoreAsync(cancellationToken), "OperationFailed");
    }

    private async Task ImportSkillCoreAsync(CancellationToken cancellationToken)
    {
        string? path = await _dialogs.OpenFolderAsync(cancellationToken).ConfigureAwait(true);
        if (path is null)
        {
            return;
        }

        await _client.SendAsync(HttpMethod.Post, "api/skills/import", new { path }, cancellationToken)
            .ConfigureAwait(true);
        await ReloadListsAsync(cancellationToken).ConfigureAwait(true);
    }

    [RelayCommand]
    private Task ToggleSkillAsync(SkillItemViewModel item, CancellationToken cancellationToken)
    {
        return RunGuardedAsync(() => ToggleSkillCoreAsync(item, cancellationToken), "OperationFailed");
    }

    private async Task ToggleSkillCoreAsync(SkillItemViewModel item, CancellationToken cancellationToken)
    {
        string action = item.Enabled ? "disable" : "enable";
        await _client.SendAsync(HttpMethod.Post, $"api/skills/{item.Id}/{action}", new { }, cancellationToken)
            .ConfigureAwait(true);
        await ReloadListsAsync(cancellationToken).ConfigureAwait(true);
    }

    [RelayCommand]
    private Task DeleteSkillAsync(SkillItemViewModel item, CancellationToken cancellationToken)
    {
        return RunGuardedAsync(() => DeleteSkillCoreAsync(item, cancellationToken), "OperationFailed");
    }

    private async Task DeleteSkillCoreAsync(SkillItemViewModel item, CancellationToken cancellationToken)
    {
        await _client.SendAsync(HttpMethod.Delete, $"api/skills/{item.Id}", null, cancellationToken)
            .ConfigureAwait(true);
        await ReloadListsAsync(cancellationToken).ConfigureAwait(true);
    }

    [RelayCommand]
    private async Task ToggleCapabilityAsync(CapabilityItemViewModel item, CancellationToken cancellationToken)
    {
        try
        {
            await _client.SendAsync(
                    HttpMethod.Put,
                    $"api/capabilities/{item.Id}",
                    new { enabled = item.Enabled },
                    cancellationToken)
                .ConfigureAwait(true);
        }
        catch
        {
            // 基础对话不能关闭、联网总开关未开时能力接口会拒绝；回读服务端状态避免开关停在错误位置。
            await ReloadCapabilitiesAsync(cancellationToken).ConfigureAwait(true);
        }
    }

    partial void OnSelectedModelChanged(ModelItemViewModel? value)
    {
        HasSelectedModel = value is not null;
        NotifyLock();
    }

    partial void OnSelectedLanguageChanged(OptionItemViewModel? value)
    {
        // 语言变化后空列表占位文本也要换语种，因此整体重载。
        _ = AutoSaveAsync(reloadLists: true);
    }

    partial void OnSelectedThemeChanged(OptionItemViewModel? value)
    {
        _ = AutoSaveAsync(reloadLists: false);
    }

    partial void OnNetworkEnabledChanged(bool value)
    {
        SyncCapabilityLocks();
        SyncRemoteLocks();
        _ = AutoSaveAsync(reloadLists: false);
    }

    partial void OnSelectedChatProviderChanged(OptionItemViewModel? value)
    {
        if (value is null || _autoSaveSuppression > 0)
        {
            return;
        }

        ApplyChatProviderFlags(value.Value);
        SyncRemoteLocks();
        NotifyLock();
        _ = AutoSaveAsync(reloadLists: false);
        _ = RefreshModelStatusAsync(CancellationToken.None);
    }

    partial void OnSelectedRemoteModelChanged(RemoteModelItemViewModel? value)
    {
        if (_loading)
        {
            return;
        }

        if (value is null)
        {
            NotifyLock();
            return;
        }

        RemoteEditingNew = false;
        ApplyRemoteForm(value);
        _currentRemoteModelId = value.Id;
        NotifyLock();
        _ = AutoSaveAsync(reloadLists: false);
        _ = RefreshModelStatusAsync(CancellationToken.None);
    }

    partial void OnRemoteNameChanged(string value) => NotifyLock();

    partial void OnRemoteBaseUrlChanged(string value) => NotifyLock();

    partial void OnRemoteModelNameChanged(string value) => NotifyLock();

    partial void OnRemoteApiKeyChanged(string value) => NotifyLock();

    partial void OnRemoteEditingNewChanged(bool value) => NotifyLock();

    partial void OnSelectedHardwareChanged(HardwareItemViewModel? value)
    {
        _ = AutoSaveAsync(reloadLists: false);
    }

    partial void OnSelectedProfileChanged(ProfileItemViewModel? value)
    {
        NotifyLock();
        if (_loading || value is null)
        {
            return;
        }

        ProfileName = value.Name;
        // 选择变化不阻塞 UI，后一次 ApplyConfig 覆盖前一次结果。
        _ = LoadProfileConfigAsync(value.Id, CancellationToken.None);
    }

    partial void OnAdvancedJsonChanged(string value)
    {
        ValidateAdvancedJson();
    }

    partial void OnGpuLayersAutoChanged(bool value)
    {
        if (value)
        {
            if (GpuLayers < 999)
            {
                _manualGpuLayers = GpuLayers;
            }
            GpuLayers = 999;
        }
        else if (GpuLayers >= 999)
        {
            GpuLayers = _manualGpuLayers;
        }

        OnPropertyChanged(nameof(CanEditGpuLayers));
    }

    partial void OnGpuLayersChanged(int value)
    {
        if (!GpuLayersAuto && value < 999)
        {
            _manualGpuLayers = value;
        }
    }

    private async Task LoadProfileConfigAsync(string profileId, CancellationToken cancellationToken)
    {
        JsonElement profile = await _client.GetAsync($"api/model-config-profiles/{profileId}", cancellationToken)
            .ConfigureAwait(true);
        ApplyConfig(profile.GetProperty("config"));
    }

    private void ApplyConfig(JsonElement config)
    {
        // 容忍旧服务缺少新增字段，界面使用与后端默认模板一致的回退值。
        ContextSize = ReadInt(config, "contextSize", 4096);
        Threads = ReadInt(config, "threads", 4);
        int gpuLayers = ReadInt(config, "gpuLayers", 999);
        GpuLayers = gpuLayers;
        GpuLayersAuto = gpuLayers >= 999;
        BatchSize = ReadInt(config, "batchSize", 512);
        UbatchSize = ReadInt(config, "ubatchSize", 512);
        FlashAttention = ReadBool(config, "flashAttention", false);
        MemoryMap = ReadBool(config, "memoryMap", true);
        MemoryLock = ReadBool(config, "memoryLock", false);
        Temperature = ReadDouble(config, "temperature", 0.7);
        TopP = ReadDouble(config, "topP", 0.9);
        TopK = ReadInt(config, "topK", 40);
        MinP = ReadDouble(config, "minP", 0.05);
        MaxTokens = ReadInt(config, "maxTokens", 1024);
        RepeatPenalty = ReadDouble(config, "repeatPenalty", 1.1);
        RepeatLastN = ReadInt(config, "repeatLastN", 64);
        SeedText = config.TryGetProperty("seed", out JsonElement seed) && seed.ValueKind == JsonValueKind.Number
            ? seed.GetInt32().ToString(CultureInfo.InvariantCulture)
            : string.Empty;
        FrequencyPenalty = ReadDouble(config, "frequencyPenalty", 0);
        PresencePenalty = ReadDouble(config, "presencePenalty", 0);
        StopText = config.TryGetProperty("stop", out JsonElement stop) && stop.ValueKind == JsonValueKind.Array
            ? string.Join(", ", stop.EnumerateArray().Select(item => item.GetString()).Where(text => !string.IsNullOrWhiteSpace(text)))
            : string.Empty;
        CompressionEnabled = ReadBool(config, "compressionEnabled", true);
        CompressionTriggerRatio = ReadDouble(config, "compressionTriggerRatio", 0.75);
        KeepRecentMessages = ReadInt(config, "keepRecentMessages", 8);
        SummaryMaxTokens = ReadInt(config, "summaryMaxTokens", 256);
        AdvancedJson = config.TryGetProperty("advancedInferenceParams", out JsonElement advanced)
            ? advanced.GetRawText()
            : "{}";
        ValidateAdvancedJson();
    }

    private void ValidateAdvancedJson()
    {
        try
        {
            using JsonDocument document = JsonDocument.Parse(string.IsNullOrWhiteSpace(AdvancedJson) ? "{}" : AdvancedJson);
            if (document.RootElement.ValueKind != JsonValueKind.Object)
            {
                AdvancedJsonValid = false;
                AdvancedJsonError = _localization.Text("AdvancedJsonInvalid");
                NotifyLock();
                return;
            }

            foreach (JsonProperty property in document.RootElement.EnumerateObject())
            {
                // 与后端保留键保持一致，让用户在提交前就获得明确反馈。
                if (property.Name is "model" or "messages" or "stream" or "prompt" or "input")
                {
                    AdvancedJsonValid = false;
                    AdvancedJsonError = _localization.Text("AdvancedJsonReserved");
                    NotifyLock();
                    return;
                }
            }

            AdvancedJsonValid = true;
            AdvancedJsonError = string.Empty;
            NotifyLock();
        }
        catch (JsonException)
        {
            AdvancedJsonValid = false;
            AdvancedJsonError = _localization.Text("AdvancedJsonInvalid");
            NotifyLock();
        }
    }

    private int? ParseSeed()
    {
        return int.TryParse(SeedText, NumberStyles.Integer, CultureInfo.InvariantCulture, out int seed)
            ? seed
            : null;
    }

    private List<string> ParseStopTokens()
    {
        return StopText
            .Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries)
            .ToList();
    }

    private Dictionary<string, object?> BuildConfigBody()
    {
        using JsonDocument advanced = JsonDocument.Parse(string.IsNullOrWhiteSpace(AdvancedJson) ? "{}" : AdvancedJson);
        return new Dictionary<string, object?>
        {
            ["contextSize"] = ContextSize,
            ["threads"] = Threads,
            ["gpuLayers"] = GpuLayers,
            ["batchSize"] = BatchSize,
            ["ubatchSize"] = UbatchSize,
            ["flashAttention"] = FlashAttention,
            ["memoryMap"] = MemoryMap,
            ["memoryLock"] = MemoryLock,
            ["temperature"] = Temperature,
            ["topP"] = TopP,
            ["topK"] = TopK,
            ["minP"] = MinP,
            ["maxTokens"] = MaxTokens,
            ["repeatPenalty"] = RepeatPenalty,
            ["repeatLastN"] = RepeatLastN,
            ["seed"] = ParseSeed(),
            ["frequencyPenalty"] = FrequencyPenalty,
            ["presencePenalty"] = PresencePenalty,
            ["stop"] = ParseStopTokens(),
            ["compressionEnabled"] = CompressionEnabled,
            ["compressionTriggerRatio"] = CompressionTriggerRatio,
            ["keepRecentMessages"] = KeepRecentMessages,
            ["summaryMaxTokens"] = SummaryMaxTokens,
            ["advancedInferenceParams"] = JsonSerializer.Deserialize<JsonElement>(advanced.RootElement.GetRawText())
        };
    }

    private async Task RefreshModelStatusAsync(CancellationToken cancellationToken)
    {
        JsonElement status = await _client.GetAsync("api/status", cancellationToken).ConfigureAwait(true);
        string chatProvider = status.TryGetProperty("chatProvider", out JsonElement provider)
            ? provider.GetString() ?? "local"
            : "local";
        bool remote = string.Equals(chatProvider, "remote", StringComparison.OrdinalIgnoreCase);
        if (remote)
        {
            JsonElement remoteStatus = status.GetProperty("remote");
            bool ready = remoteStatus.TryGetProperty("ready", out JsonElement readyNode) && readyNode.GetBoolean();
            ModelRunning = false;
            _modelStateKey = ready ? "RemoteReady" : "RemoteNotReady";
            ModelRuntimeStatus = _localization.Text(_modelStateKey);
            string remoteState = ready ? "ready" : "stopped";
            if (!string.Equals(remoteState, _modelState, StringComparison.Ordinal))
            {
                _modelState = remoteState;
                RestartHint = string.Empty;
            }
        }
        else
        {
            JsonElement llamafile = status.GetProperty("llamafile");
            string state = llamafile.GetProperty("state").GetString() ?? "stopped";
            ModelRunning = state is "ready" or "starting";
            _modelStateKey = state switch
            {
                "ready" => "ModelStateReady",
                "starting" => "ModelStateStarting",
                _ => "ModelStateStopped"
            };
            ModelRuntimeStatus = _localization.Text(_modelStateKey);
            if (!string.Equals(state, _modelState, StringComparison.Ordinal))
            {
                // 运行状态已经改变，上一条一次性提示（启动失败、模型已删除等）不能继续留在界面上。
                _modelState = state;
                RestartHint = string.Empty;
            }
            if (llamafile.TryGetProperty("modelId", out JsonElement modelId) && modelId.ValueKind == JsonValueKind.String)
            {
                _currentModelId = modelId.GetString() ?? _currentModelId;
            }
        }

        NotifyLock();
    }

    /// <summary>列表刷新后必须保持有选中项，否则模型无法启动、参数表单也失去归属。</summary>
    private async Task ReloadProfilesAsync(CancellationToken cancellationToken)
    {
        string? selectedId = SelectedProfile?.Id;
        _loading = true;
        try
        {
            Profiles.Clear();
            JsonElement response = await _client.GetAsync("api/model-config-profiles", cancellationToken).ConfigureAwait(true);
            foreach (JsonElement item in response.EnumerateArray())
            {
                Profiles.Add(
                    new ProfileItemViewModel
                    {
                        Id = item.GetProperty("id").GetString() ?? string.Empty,
                        Name = item.GetProperty("name").GetString() ?? string.Empty
                    });
            }

            SelectedProfile = Profiles.FirstOrDefault(item => item.Id == selectedId) ?? Profiles.FirstOrDefault();
        }
        finally
        {
            _loading = false;
        }

        if (SelectedProfile is not null)
        {
            ProfileName = SelectedProfile.Name;
            await LoadProfileConfigAsync(SelectedProfile.Id, cancellationToken).ConfigureAwait(true);
        }

        NotifyLock();
    }

    private void NotifyLock()
    {
        OnPropertyChanged(nameof(CanEditModel));
        OnPropertyChanged(nameof(CanEditGpuLayers));
        OnPropertyChanged(nameof(CanStartModel));
        OnPropertyChanged(nameof(CanStopModel));
        OnPropertyChanged(nameof(CanSaveModelConfig));
        OnPropertyChanged(nameof(CanEditRemote));
        OnPropertyChanged(nameof(CanSaveRemote));
        OnPropertyChanged(nameof(CanTestRemote));
        OnPropertyChanged(nameof(CanDeleteRemote));
        OnPropertyChanged(nameof(ShowRemoteNetworkHint));
    }

    partial void OnModelRunningChanged(bool value) => NotifyLock();

    partial void OnModelBusyChanged(bool value) => NotifyLock();

    private static OptionItemViewModel? Find(ObservableCollection<OptionItemViewModel> options, string value)
    {
        return options.FirstOrDefault(option => option.Value == value) ?? options.FirstOrDefault();
    }

    private static string CapabilityKey(string id)
    {
        return id switch
        {
            "ai.chat" => "CapChat",
            "conversation.context" => "CapContext",
            "rag" => "CapRag",
            "tools" => "CapTools",
            "mcp" => "CapMcp",
            "skills" => "CapSkills",
            "network" => "CapNetwork",
            "embedding" => "CapEmbedding",
            _ => id
        };
    }

    /// <summary>集中维护协议能力 ID 到图标的映射；未知扩展能力回退为设置图标，避免出现空白项。</summary>
    private static string CapabilityIcon(string id)
    {
        return id switch
        {
            "ai.chat" => "Chat",
            "conversation.context" => "Chat",
            "rag" => "Document",
            "tools" => "Tools",
            "mcp" => "Plug",
            "skills" => "Lightbulb",
            "network" => "Network",
            "embedding" => "Grid",
            _ => "Settings"
        };
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

    /// <summary>下拉里同时给出设备名和类型，便于区分同厂商的独显与核显。</summary>
    private static string HardwareDisplay(HardwareItemViewModel device)
    {
        return string.IsNullOrEmpty(device.TypeLabel)
            ? device.Name
            : $"{device.Name} · {device.TypeLabel}";
    }

    private static string HardwareReasonKey(string reason)
    {
        return reason switch
        {
            "noVulkanRuntime" => "HwNoVulkan",
            _ => "HwUnavailable"
        };
    }

    private static int ReadInt(JsonElement element, string name, int fallback)
    {
        return element.TryGetProperty(name, out JsonElement value) && value.ValueKind == JsonValueKind.Number
            ? value.GetInt32()
            : fallback;
    }

    private static double ReadDouble(JsonElement element, string name, double fallback)
    {
        return element.TryGetProperty(name, out JsonElement value) && value.ValueKind == JsonValueKind.Number
            ? value.GetDouble()
            : fallback;
    }

    private static bool ReadBool(JsonElement element, string name, bool fallback)
    {
        return element.TryGetProperty(name, out JsonElement value) && value.ValueKind is JsonValueKind.True or JsonValueKind.False
            ? value.GetBoolean()
            : fallback;
    }

    private void RefreshTexts()
    {
        foreach (OptionItemViewModel option in Languages.Concat(Themes).Concat(ChatProviders))
        {
            option.Display = _localization.Text(option.ResourceKey);
        }

        foreach (CapabilityItemViewModel capability in Capabilities)
        {
            capability.Display = _localization.Text(CapabilityKey(capability.Id));
            capability.Description = _localization.Text(CapabilityKey(capability.Id) + "Desc");
        }

        SyncCapabilityLocks();
        SyncRemoteLocks();
        if (SelectedRemoteModel is not null)
        {
            RemoteApiKeyHint = SelectedRemoteModel.HasApiKey
                ? _localization.Text("RemoteApiKeyConfigured")
                : _localization.Text("RemoteApiKeyMissing");
        }
        else if (RemoteEditingNew)
        {
            RemoteApiKeyHint = _localization.Text("RemoteApiKeyMissing");
        }

        foreach (HardwareItemViewModel device in Hardware)
        {
            device.TypeLabel = _localization.Text(HardwareLabelKey(device.Type));
            device.RecommendedLabel = _localization.Text("Recommended");
            device.Display = HardwareDisplay(device);
            if (device.Unavailable)
            {
                device.UnavailableLabel = _localization.Text(HardwareReasonKey(device.Reason));
            }
        }

        // 尚未产生操作结果时展示静态说明，避免提示行留空。
        if (string.IsNullOrEmpty(ProfileHint))
        {
            ProfileHint = _localization.Text("ProfileHint");
        }

        ModelRuntimeStatus = _localization.Text(_modelStateKey);

        ValidateAdvancedJson();
    }

    private async Task ReloadListsAsync(CancellationToken cancellationToken)
    {
        Hardware.Clear();
        HardwareItemViewModel? fallback = null;
        foreach (JsonElement item in (await _client.GetAsync("api/hardware", cancellationToken).ConfigureAwait(true)).EnumerateArray())
        {
            string type = item.GetProperty("type").GetString() ?? string.Empty;
            bool available = !item.TryGetProperty("available", out JsonElement ava) || ava.GetBoolean();
            string reason = item.TryGetProperty("unavailableReason", out JsonElement why)
                ? why.GetString() ?? string.Empty
                : string.Empty;
            HardwareItemViewModel device = new()
            {
                Id = item.GetProperty("id").GetString() ?? string.Empty,
                Name = item.GetProperty("name").GetString() ?? string.Empty,
                Type = type,
                TypeLabel = _localization.Text(HardwareLabelKey(type)),
                Recommended = item.TryGetProperty("recommended", out JsonElement rec) && rec.GetBoolean(),
                RecommendedLabel = _localization.Text("Recommended"),
                Unavailable = !available,
                Reason = reason,
                UnavailableLabel = available ? string.Empty : _localization.Text(HardwareReasonKey(reason))
            };
            device.Display = HardwareDisplay(device);
            // 不可用设备无法运行推理，只在监控页展示原因，不进入可选列表。
            if (available)
            {
                Hardware.Add(device);
                if (device.Recommended)
                {
                    fallback = device;
                }
            }
        }

        // 未选择或所选设备已消失时退回后端给出的默认设备：独显、核显、CPU。
        SelectedHardware = Hardware.FirstOrDefault(item => item.Id == _hardwareDeviceId)
            ?? fallback
            ?? Hardware.FirstOrDefault();

        await ReloadCapabilitiesAsync(cancellationToken).ConfigureAwait(true);
        await ReloadModelsAsync(cancellationToken).ConfigureAwait(true);
        await FillAsync(Tools, "api/tools", "name", cancellationToken).ConfigureAwait(true);
        await ReloadDocumentsAsync(cancellationToken).ConfigureAwait(true);
        await FillAsync(McpServers, "api/mcp", "name", cancellationToken).ConfigureAwait(true);
        await ReloadSkillsAsync(cancellationToken).ConfigureAwait(true);
        await ReloadProfilesAsync(cancellationToken).ConfigureAwait(true);
        await ReloadRemoteModelsAsync(cancellationToken).ConfigureAwait(true);
        SyncRemoteLocks();
    }

    private async Task ReloadCapabilitiesAsync(CancellationToken cancellationToken)
    {
        Capabilities.Clear();
        foreach (JsonElement item in (await _client.GetAsync("api/capabilities", cancellationToken).ConfigureAwait(true)).EnumerateArray())
        {
            string id = item.GetProperty("id").GetString() ?? string.Empty;
            Capabilities.Add(
                new CapabilityItemViewModel
                {
                    Id = id,
                    Icon = CapabilityIcon(id),
                    Display = _localization.Text(CapabilityKey(id)),
                    Description = _localization.Text(CapabilityKey(id) + "Desc"),
                    Enabled = item.GetProperty("enabled").GetBoolean()
                });
        }

        SyncCapabilityLocks();
    }

    /// <summary>
    /// 联网能力受常规页的联网总开关约束，总开关关闭时后端会拒绝启用。
    /// 这里直接锁住开关并说明前置条件，避免用户反复拨动却没有任何反馈。
    /// </summary>
    private void SyncCapabilityLocks()
    {
        foreach (CapabilityItemViewModel capability in Capabilities)
        {
            if (!string.Equals(capability.Id, "network", StringComparison.Ordinal))
            {
                continue;
            }

            capability.Locked = !NetworkEnabled;
            capability.Hint = NetworkEnabled ? string.Empty : _localization.Text("CapNetworkBlocked");
        }
    }

    private void SyncRemoteLocks()
    {
        RemoteLocked = !NetworkEnabled;
        RemoteNetworkHint = NetworkEnabled ? string.Empty : _localization.Text("RemoteNetworkBlocked");
        NotifyLock();
    }

    private void ApplyChatProviderFlags(string provider)
    {
        IsRemoteChat = string.Equals(provider, "remote", StringComparison.OrdinalIgnoreCase);
        IsLocalChat = !IsRemoteChat;
    }

    private void ApplyRemoteForm(RemoteModelItemViewModel item)
    {
        RemoteName = item.Name;
        RemoteBaseUrl = item.BaseUrl;
        RemoteModelName = item.ModelName;
        RemoteApiKey = string.Empty;
        RemoteApiKeyHint = item.HasApiKey
            ? _localization.Text("RemoteApiKeyConfigured")
            : _localization.Text("RemoteApiKeyMissing");
    }

    private async Task ReloadRemoteModelsAsync(CancellationToken cancellationToken)
    {
        string? selectedId = SelectedRemoteModel?.Id ?? _currentRemoteModelId;
        _loading = true;
        try
        {
            RemoteModels.Clear();
            JsonElement response = await _client.GetAsync("api/remote-models", cancellationToken).ConfigureAwait(true);
            foreach (JsonElement item in response.EnumerateArray())
            {
                RemoteModels.Add(
                    new RemoteModelItemViewModel
                    {
                        Id = item.GetProperty("id").GetString() ?? string.Empty,
                        Name = item.GetProperty("name").GetString() ?? string.Empty,
                        BaseUrl = item.GetProperty("baseUrl").GetString() ?? string.Empty,
                        ModelName = item.GetProperty("modelName").GetString() ?? string.Empty,
                        HasApiKey = item.TryGetProperty("hasApiKey", out JsonElement hasKey) && hasKey.GetBoolean()
                    });
            }

            SelectedRemoteModel = RemoteModels.FirstOrDefault(item => item.Id == selectedId)
                ?? RemoteModels.FirstOrDefault();
            if (SelectedRemoteModel is not null)
            {
                _currentRemoteModelId = SelectedRemoteModel.Id;
                ApplyRemoteForm(SelectedRemoteModel);
                RemoteEditingNew = false;
            }
            else
            {
                _currentRemoteModelId = string.Empty;
                RemoteEditingNew = true;
                RemoteName = string.Empty;
                RemoteBaseUrl = "https://api.openai.com";
                RemoteModelName = string.Empty;
                RemoteApiKey = string.Empty;
                RemoteApiKeyHint = _localization.Text("RemoteApiKeyMissing");
            }
        }
        finally
        {
            _loading = false;
            NotifyLock();
        }
    }

    private async Task ReloadModelsAsync(CancellationToken cancellationToken)
    {
        // _loading 抑制 SelectedModel 的生成回调，防止集合重建期间发出过期配置请求。
        _loading = true;
        try
        {
            Models.Clear();
            JsonElement response = await _client.GetAsync("api/models", cancellationToken).ConfigureAwait(true);
            foreach (JsonElement item in response.EnumerateArray())
            {
                Models.Add(
                    new ModelItemViewModel
                    {
                        Id = item.GetProperty("id").GetString() ?? string.Empty,
                        Name = item.GetProperty("name").GetString() ?? string.Empty
                    });
            }

            SelectedModel = Models.FirstOrDefault(model => model.Id == _currentModelId) ?? Models.FirstOrDefault();
            HasSelectedModel = SelectedModel is not null;
            NotifyLock();
        }
        finally
        {
            _loading = false;
        }
    }

    /// <summary>拉取列表并写入集合；空列表填充占位文本，避免界面出现空白卡片。</summary>
    private async Task FillAsync(
        ObservableCollection<string> target,
        string path,
        string property,
        CancellationToken cancellationToken)
    {
        target.Clear();
        JsonElement response = await _client.GetAsync(path, cancellationToken).ConfigureAwait(true);
        foreach (JsonElement item in response.EnumerateArray())
        {
            target.Add(item.GetProperty(property).GetString() ?? string.Empty);
        }

        if (target.Count == 0)
        {
            target.Add(_localization.Text("EmptyList"));
        }
    }

    private async Task ReloadDocumentsAsync(CancellationToken cancellationToken)
    {
        Documents.Clear();
        JsonElement response = await _client.GetAsync("api/rag/documents", cancellationToken).ConfigureAwait(true);
        foreach (JsonElement item in response.EnumerateArray())
        {
            Documents.Add(
                new DocumentItemViewModel
                {
                    Id = item.GetProperty("id").GetString() ?? string.Empty,
                    Title = item.GetProperty("title").GetString() ?? string.Empty
                });
        }
    }

    private async Task ReloadSkillsAsync(CancellationToken cancellationToken)
    {
        Skills.Clear();
        JsonElement response = await _client.GetAsync("api/skills", cancellationToken).ConfigureAwait(true);
        foreach (JsonElement item in response.EnumerateArray())
        {
            Skills.Add(
                new SkillItemViewModel
                {
                    Id = item.GetProperty("id").GetString() ?? string.Empty,
                    Name = item.GetProperty("name").GetString() ?? string.Empty,
                    Enabled = item.TryGetProperty("enabled", out JsonElement enabled) && enabled.GetBoolean()
                });
        }
    }

    private sealed class AutoSaveScope : IDisposable
    {
        private readonly SettingsViewModel _owner;
        private bool _released;

        public AutoSaveScope(SettingsViewModel owner)
        {
            _owner = owner;
        }

        public void Dispose()
        {
            if (_released)
            {
                return;
            }

            _released = true;
            _owner._autoSaveSuppression--;
        }
    }
}

/// <summary>模型下拉项同时保留稳定 ID 和用户可见文件名。</summary>
public sealed partial class ModelItemViewModel : ViewModelBase
{
    [ObservableProperty]
    private string _id = string.Empty;

    [ObservableProperty]
    private string _name = string.Empty;
}

/// <summary>能力开关行，展示文本会在语言切换后重新解析。</summary>
public sealed partial class CapabilityItemViewModel : ViewModelBase
{
    [ObservableProperty]
    private string _id = string.Empty;

    [ObservableProperty]
    private string _icon = string.Empty;

    [ObservableProperty]
    private string _display = string.Empty;

    [ObservableProperty]
    private string _description = string.Empty;

    [ObservableProperty]
    private bool _enabled;

    /// <summary>前置条件未满足时由外部锁定，例如联网总开关关闭时的联网能力。</summary>
    [ObservableProperty]
    private bool _locked;

    /// <summary>锁定原因，直接显示在开关所在行，告诉用户需要先做什么。</summary>
    [ObservableProperty]
    private string _hint = string.Empty;

    /// <summary>基础对话由后端强制开启，界面不允许拨动以免保存失败后开关抖动。</summary>
    public bool CanToggle => !string.Equals(Id, "ai.chat", StringComparison.Ordinal) && !Locked;

    public bool HasHint => !string.IsNullOrEmpty(Hint);

    partial void OnIdChanged(string value)
    {
        OnPropertyChanged(nameof(CanToggle));
    }

    partial void OnLockedChanged(bool value)
    {
        OnPropertyChanged(nameof(CanToggle));
    }

    partial void OnHintChanged(string value)
    {
        OnPropertyChanged(nameof(HasHint));
    }
}

/// <summary>硬件探测结果行，区分可用性、设备类型与推荐状态。</summary>
public sealed partial class HardwareItemViewModel : ViewModelBase
{
    [ObservableProperty]
    private string _id = string.Empty;

    [ObservableProperty]
    private string _display = string.Empty;

    [ObservableProperty]
    private string _name = string.Empty;

    [ObservableProperty]
    private string _type = string.Empty;

    [ObservableProperty]
    private string _typeLabel = string.Empty;

    [ObservableProperty]
    private bool _recommended;

    [ObservableProperty]
    private string _recommendedLabel = string.Empty;

    [ObservableProperty]
    private bool _unavailable;

    [ObservableProperty]
    private string _reason = string.Empty;

    [ObservableProperty]
    private string _unavailableLabel = string.Empty;
}

/// <summary>知识库文档行，删除时使用稳定 ID。</summary>
public sealed partial class DocumentItemViewModel : ViewModelBase
{
    [ObservableProperty]
    private string _id = string.Empty;

    [ObservableProperty]
    private string _title = string.Empty;
}

/// <summary>技能包行，启停使用与插件相同的反向动作。</summary>
public sealed partial class SkillItemViewModel : ViewModelBase
{
    [ObservableProperty]
    private string _id = string.Empty;

    [ObservableProperty]
    private string _name = string.Empty;

    [ObservableProperty]
    private bool _enabled;
}

/// <summary>可命名参数策略下拉项。</summary>
public sealed partial class ProfileItemViewModel : ViewModelBase
{
    [ObservableProperty]
    private string _id = string.Empty;

    [ObservableProperty]
    private string _name = string.Empty;
}
