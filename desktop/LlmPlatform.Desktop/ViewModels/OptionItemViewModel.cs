using CommunityToolkit.Mvvm.ComponentModel;

namespace LlmPlatform.Desktop.ViewModels;

/// <summary>下拉选项：协议值固定，展示文本随应用语言刷新。</summary>
public sealed partial class OptionItemViewModel : ViewModelBase
{
    [ObservableProperty]
    private string _display = string.Empty;

    public OptionItemViewModel(string value, string resourceKey)
    {
        Value = value;
        ResourceKey = resourceKey;
    }

    public string Value { get; }

    public string ResourceKey { get; }
}
