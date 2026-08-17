using CommunityToolkit.Mvvm.ComponentModel;

namespace LlmPlatform.Desktop.ViewModels;

/// <summary>会话侧栏中的一条记录。</summary>
public sealed partial class SessionItemViewModel : ViewModelBase
{
    [ObservableProperty]
    private string _id = string.Empty;

    [ObservableProperty]
    private string _title = string.Empty;

    [ObservableProperty]
    private bool _isSelected;
}
