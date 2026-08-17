using CommunityToolkit.Mvvm.ComponentModel;

namespace LlmPlatform.Desktop.ViewModels;

/// <summary>单条对话消息；Content 可在流式响应期间持续更新。</summary>
public sealed partial class ChatLineViewModel : ViewModelBase
{
    [ObservableProperty]
    private string _content = string.Empty;

    [ObservableProperty]
    private bool _isUser;

    /// <summary>会话内序号；只有已持久化的消息才有值，撤回与修改按它截断。</summary>
    [ObservableProperty]
    private int _sequenceNo;

    /// <summary>尚未落库的消息（正在生成、发送失败）没有截断点，不提供撤回。</summary>
    [ObservableProperty]
    private bool _canRetract;

    /// <summary>只有用户自己的消息可以改写后重发。</summary>
    [ObservableProperty]
    private bool _canModify;
}
