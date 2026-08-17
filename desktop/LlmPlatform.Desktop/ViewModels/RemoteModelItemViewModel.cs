using CommunityToolkit.Mvvm.ComponentModel;

namespace LlmPlatform.Desktop.ViewModels;

/// <summary>远程 OpenAI 兼容配置列表项。</summary>
public sealed partial class RemoteModelItemViewModel : ViewModelBase
{
    [ObservableProperty]
    private string _id = string.Empty;

    [ObservableProperty]
    private string _name = string.Empty;

    [ObservableProperty]
    private string _baseUrl = string.Empty;

    [ObservableProperty]
    private string _modelName = string.Empty;

    [ObservableProperty]
    private bool _hasApiKey;
}
