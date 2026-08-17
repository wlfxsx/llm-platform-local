using Avalonia.Input;
using Avalonia.Interactivity;
using Avalonia.Threading;
using LlmPlatform.Desktop.ViewModels;

namespace LlmPlatform.Desktop.Views;

/// <summary>聊天窗口代码后置：滚动贴底、回车发送与输入焦点。</summary>
public partial class ChatWindow : FluentWindow
{
    private ChatViewModel? _chat;
    private bool _stickToBottom = true;
    private bool _userScrolling;

    public ChatWindow()
    {
        InitializeComponent();
        DataContextChanged += OnDataContextChanged;
        // TextBox 自己会在冒泡阶段吞掉回车并插入换行，必须在隧道阶段先接管按键。
        InputBox.AddHandler(InputElement.KeyDownEvent, OnInputKeyDown, RoutingStrategies.Tunnel);
        // 流式输出时消息高度不断变化，只在内容更新那一刻滚动会停在上一帧的高度上，
        // 因此每次布局完成都重新贴底，直到用户主动向上翻阅。
        MessageScroll.LayoutUpdated += (_, _) =>
        {
            if (_stickToBottom)
            {
                MessageScroll.ScrollToEnd();
            }
        };
        // 只有用户自己滚动才允许脱离贴底：切换会话时内容分批填充，
        // 若按每次滚动事件判断，中途的 remaining 很大会误判成“用户已上翻”。
        MessageScroll.AddHandler(
            InputElement.PointerWheelChangedEvent,
            OnUserScrollIntent,
            RoutingStrategies.Tunnel);
        MessageScroll.AddHandler(
            InputElement.PointerPressedEvent,
            OnUserScrollIntent,
            RoutingStrategies.Tunnel);
        MessageScroll.AddHandler(InputElement.KeyDownEvent, OnUserScrollIntent, RoutingStrategies.Tunnel);
        MessageScroll.ScrollChanged += (_, _) =>
        {
            if (!_userScrolling)
            {
                return;
            }

            _userScrolling = false;
            double remaining = MessageScroll.Extent.Height
                - MessageScroll.Offset.Y
                - MessageScroll.Viewport.Height;
            _stickToBottom = remaining <= 24;
        };
        Opened += async (_, _) =>
        {
            InputBox.Focus();
            if (DataContext is ChatViewModel chat)
            {
                // 托盘菜单打开时服务通常已就绪；仍做短重试，避免偶发连接抖动留下空侧栏。
                for (int attempt = 0; attempt < 5; attempt++)
                {
                    try
                    {
                        await chat.InitializeAsync(CancellationToken.None).ConfigureAwait(true);
                        break;
                    }
                    catch (Exception)
                    {
                        await Task.Delay(500).ConfigureAwait(true);
                    }
                }
            }
        };
    }

    private void OnDataContextChanged(object? sender, EventArgs e)
    {
        if (_chat is not null)
        {
            _chat.Messages.CollectionChanged -= OnMessagesChanged;
            _chat.ContentUpdated -= OnContentUpdated;
            _chat.InputFocusRequested -= OnInputFocusRequested;
        }

        _chat = DataContext as ChatViewModel;
        if (_chat is not null)
        {
            _chat.Messages.CollectionChanged += OnMessagesChanged;
            _chat.ContentUpdated += OnContentUpdated;
            _chat.InputFocusRequested += OnInputFocusRequested;
        }
    }

    /// <summary>回车发送，Ctrl+回车换行；Shift+回车同样换行，避免误发。</summary>
    private void OnInputKeyDown(object? sender, KeyEventArgs e)
    {
        if (e.Key is not (Key.Enter or Key.Return))
        {
            return;
        }

        e.Handled = true;
        if (e.KeyModifiers.HasFlag(KeyModifiers.Control) || e.KeyModifiers.HasFlag(KeyModifiers.Shift))
        {
            string text = InputBox.Text ?? string.Empty;
            int caret = Math.Clamp(InputBox.CaretIndex, 0, text.Length);
            InputBox.Text = text[..caret] + "\n" + text[caret..];
            InputBox.CaretIndex = caret + 1;
            return;
        }

        _chat?.SendCommand.Execute(null);
    }

    private void OnUserScrollIntent(object? sender, RoutedEventArgs e)
    {
        _userScrolling = true;
    }

    private void OnInputFocusRequested(object? sender, EventArgs e)
    {
        Dispatcher.UIThread.Post(
            () =>
            {
                InputBox.Focus();
                InputBox.CaretIndex = InputBox.Text?.Length ?? 0;
            },
            DispatcherPriority.Background);
    }

    private void OnMessagesChanged(object? sender, System.Collections.Specialized.NotifyCollectionChangedEventArgs e)
    {
        _stickToBottom = true;
        Dispatcher.UIThread.Post(() => MessageScroll.ScrollToEnd(), DispatcherPriority.Background);
    }

    private void OnContentUpdated(object? sender, EventArgs e)
    {
        if (!_stickToBottom)
        {
            return;
        }

        Dispatcher.UIThread.Post(() => MessageScroll.ScrollToEnd(), DispatcherPriority.Background);
    }
}
