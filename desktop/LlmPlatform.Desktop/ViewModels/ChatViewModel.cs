using System.Collections.ObjectModel;
using System.Text.Json;
using Avalonia.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using LlmPlatform.Desktop.Services;

namespace LlmPlatform.Desktop.ViewModels;

/// <summary>
/// 管理会话侧栏、当前会话消息与流式生成；切换会话时从后端回放历史。
/// 新会话在首次发送时由后端隐式创建，避免侧栏堆积空会话。
/// </summary>
public sealed partial class ChatViewModel : ViewModelBase
{
    private readonly PlatformClient _client;
    private readonly LocalizationService _localization;

    [ObservableProperty]
    private string _input = string.Empty;

    [ObservableProperty]
    private string _statusText = string.Empty;

    [ObservableProperty]
    private string? _sessionId;

    [ObservableProperty]
    private bool _busy;

    [ObservableProperty]
    private SessionItemViewModel? _selectedSession;

    [ObservableProperty]
    private bool _sidebarCollapsed;

    /// <summary>侧栏宽度由折叠状态决定；收起后只保留图标列。</summary>
    public double SidebarWidth => SidebarCollapsed ? 48 : 240;

    public ChatViewModel(PlatformClient client, LocalizationService localization)
    {
        _client = client;
        _localization = localization;
        Messages = [];
        Sessions = [];
    }

    public ObservableCollection<ChatLineViewModel> Messages { get; }

    public ObservableCollection<SessionItemViewModel> Sessions { get; }

    /// <summary>流式增量追加文本时触发，供窗口在不新增集合项时继续滚到底部。</summary>
    public event EventHandler? ContentUpdated;

    /// <summary>请求把焦点交回输入框，用于修改消息后立即继续编辑。</summary>
    public event EventHandler? InputFocusRequested;

    public async Task InitializeAsync(CancellationToken cancellationToken)
    {
        await ReloadSessionsAsync(cancellationToken).ConfigureAwait(true);
        if (SelectedSession is not null)
        {
            await LoadMessagesAsync(SelectedSession.Id, cancellationToken).ConfigureAwait(true);
        }
    }

    [RelayCommand]
    private async Task SendAsync(CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(Input) || Busy)
        {
            return;
        }

        string text = Input.Trim();
        Input = string.Empty;
        Messages.Add(new ChatLineViewModel { IsUser = true, Content = text });
        // 先插入空助手行，后续增量直接更新同一对象，避免每个 token 创建一个 UI 节点。
        ChatLineViewModel assistant = new() { IsUser = false, Content = string.Empty };
        Messages.Add(assistant);
        RaiseContentUpdated();
        Busy = true;
        StatusText = _localization.Text("StatusGenerating");
        try
        {
            SessionId = await _client.StreamChatAsync(
                    SessionId,
                    text,
                    delta => ApplyDelta(assistant, delta),
                    cancellationToken)
                .ConfigureAwait(true);

            // 会话标题与消息序号都由服务端生成，回读一次才能支持后续撤回与修改。
            await ReloadSessionsAsync(cancellationToken).ConfigureAwait(true);
            if (SessionId is not null)
            {
                await LoadMessagesAsync(SessionId, cancellationToken).ConfigureAwait(true);
            }
        }
        catch (Exception ex)
        {
            // 展示服务端具体原因（远程 Key、联网、本地模型未启动等），笼统文案只作兜底。
            assistant.Content = ResolveChatError(ex);
            RaiseContentUpdated();
        }
        finally
        {
            Busy = false;
            StatusText = string.Empty;
        }
    }

    [RelayCommand]
    private void ToggleSidebar()
    {
        SidebarCollapsed = !SidebarCollapsed;
    }

    partial void OnSidebarCollapsedChanged(bool value)
    {
        OnPropertyChanged(nameof(SidebarWidth));
    }

    /// <summary>新会话只清空当前视图，真正的会话行等首条消息发送后由服务端创建。</summary>
    [RelayCommand]
    private void NewSession()
    {
        if (Busy)
        {
            return;
        }

        Messages.Clear();
        SessionId = null;
        SelectedSession = null;
        SyncSelection();
        InputFocusRequested?.Invoke(this, EventArgs.Empty);
    }

    [RelayCommand]
    private async Task SelectSessionAsync(SessionItemViewModel? session, CancellationToken cancellationToken)
    {
        if (session is null || Busy)
        {
            return;
        }

        SelectedSession = session;
        SyncSelection();
        SessionId = session.Id;
        await LoadMessagesAsync(session.Id, cancellationToken).ConfigureAwait(true);
    }

    [RelayCommand]
    private async Task DeleteSessionAsync(SessionItemViewModel? session, CancellationToken cancellationToken)
    {
        if (session is null || Busy)
        {
            return;
        }

        await _client.SendAsync(HttpMethod.Delete, $"api/sessions/{session.Id}", null, cancellationToken)
            .ConfigureAwait(true);
        bool wasSelected = SelectedSession?.Id == session.Id;
        Sessions.Remove(session);
        if (!wasSelected)
        {
            return;
        }

        Messages.Clear();
        SessionId = null;
        SelectedSession = null;
        SyncSelection();
    }

    /// <summary>撤回该条及其之后的消息，服务端历史与界面同时回退。</summary>
    [RelayCommand]
    private async Task RetractAsync(ChatLineViewModel? line, CancellationToken cancellationToken)
    {
        await TruncateAsync(line, false, cancellationToken).ConfigureAwait(true);
    }

    /// <summary>修改等于撤回后把原文放回输入框，改完重新发送。</summary>
    [RelayCommand]
    private async Task EditAsync(ChatLineViewModel? line, CancellationToken cancellationToken)
    {
        await TruncateAsync(line, true, cancellationToken).ConfigureAwait(true);
    }

    private async Task TruncateAsync(
        ChatLineViewModel? line,
        bool restoreInput,
        CancellationToken cancellationToken)
    {
        if (line is null || Busy || SessionId is null || line.SequenceNo <= 0)
        {
            return;
        }

        string original = line.Content;
        await _client.SendAsync(
                HttpMethod.Post,
                $"api/sessions/{SessionId}/messages/truncate",
                new { sequenceNo = line.SequenceNo },
                cancellationToken)
            .ConfigureAwait(true);
        await LoadMessagesAsync(SessionId, cancellationToken).ConfigureAwait(true);
        // 撤回可能把会话清空，服务端不再返回它；侧栏必须同步，否则留下点不开内容的空会话。
        if (Messages.Count == 0)
        {
            SessionId = null;
            SelectedSession = null;
        }

        await ReloadSessionsAsync(cancellationToken).ConfigureAwait(true);
        if (restoreInput)
        {
            Input = original;
            InputFocusRequested?.Invoke(this, EventArgs.Empty);
        }
    }

    private async Task ReloadSessionsAsync(CancellationToken cancellationToken)
    {
        string? selectedId = SelectedSession?.Id ?? SessionId;
        Sessions.Clear();
        JsonElement response = await _client.GetAsync("api/sessions", cancellationToken).ConfigureAwait(true);
        foreach (JsonElement item in response.EnumerateArray())
        {
            string title = item.GetProperty("title").GetString() ?? string.Empty;
            Sessions.Add(
                new SessionItemViewModel
                {
                    Id = item.GetProperty("id").GetString() ?? string.Empty,
                    Title = string.IsNullOrWhiteSpace(title) || title == "session"
                        ? _localization.Text("UntitledSession")
                        : title
                });
        }

        SelectedSession = Sessions.FirstOrDefault(item => item.Id == selectedId);
        SyncSelection();
    }

    private async Task LoadMessagesAsync(string sessionId, CancellationToken cancellationToken)
    {
        Messages.Clear();
        JsonElement response = await _client.GetAsync($"api/sessions/{sessionId}/messages", cancellationToken)
            .ConfigureAwait(true);
        foreach (JsonElement item in response.EnumerateArray())
        {
            string role = item.GetProperty("role").GetString() ?? "assistant";
            if (role is "system")
            {
                continue;
            }

            bool isUser = role == "user";
            int sequenceNo = item.GetProperty("sequenceNo").GetInt32();
            Messages.Add(
                new ChatLineViewModel
                {
                    IsUser = isUser,
                    Content = item.GetProperty("content").GetString() ?? string.Empty,
                    SequenceNo = sequenceNo,
                    CanRetract = sequenceNo > 0,
                    CanModify = isUser && sequenceNo > 0
                });
        }

        RaiseContentUpdated();
    }

    /// <summary>
    /// SSE 读取在线程池上跑；绑定属性和滚动必须回到 UI 线程，否则 Avalonia 会抛 Call from invalid thread。
    /// </summary>
    private void ApplyDelta(ChatLineViewModel assistant, string delta)
    {
        void apply()
        {
            assistant.Content += delta;
            RaiseContentUpdated();
        }

        if (Dispatcher.UIThread.CheckAccess())
        {
            apply();
            return;
        }

        Dispatcher.UIThread.Post(apply, DispatcherPriority.Background);
    }

    private void SyncSelection()
    {
        foreach (SessionItemViewModel item in Sessions)
        {
            item.IsSelected = SelectedSession is not null
                && string.Equals(item.Id, SelectedSession.Id, StringComparison.Ordinal);
        }
    }

    private string ResolveChatError(Exception ex)
    {
        string detail = ex.Message?.Trim() ?? string.Empty;
        if (string.IsNullOrWhiteSpace(detail))
        {
            return _localization.Text("ChatFailed");
        }

        // HTTP 非流式失败时异常正文可能是统一错误 JSON。
        try
        {
            using JsonDocument document = JsonDocument.Parse(detail);
            if (document.RootElement.TryGetProperty("message", out JsonElement message)
                && message.ValueKind == JsonValueKind.String
                && !string.IsNullOrWhiteSpace(message.GetString()))
            {
                return message.GetString()!;
            }

            if (document.RootElement.TryGetProperty("messageKey", out JsonElement key)
                && key.ValueKind == JsonValueKind.String)
            {
                detail = key.GetString() ?? detail;
            }
        }
        catch (JsonException)
        {
            // 非 JSON 时按普通文案或 messageKey 处理。
        }

        return detail switch
        {
            "error.modelNotReady" => _localization.Text("ChatFailedLocal"),
            "error.remoteApiKeyRequired" => _localization.Text("ChatFailedRemoteKey"),
            "error.remoteNotSelected" => _localization.Text("ChatFailedRemote"),
            "error.remoteUnauthorized" => _localization.Text("ChatFailedRemoteKey"),
            "error.remoteUnreachable" => _localization.Text("ChatFailedRemote"),
            "error.networkDisabled" => _localization.Text("ChatFailedNetwork"),
            "error.internal" => _localization.Text("ChatFailed"),
            _ when detail.StartsWith("error.", StringComparison.Ordinal) => _localization.Text("ChatFailed"),
            _ => detail
        };
    }

    private void RaiseContentUpdated()
    {
        ContentUpdated?.Invoke(this, EventArgs.Empty);
    }
}
