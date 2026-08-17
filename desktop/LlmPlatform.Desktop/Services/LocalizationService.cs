using Avalonia;
using Avalonia.Controls;
using Avalonia.Markup.Xaml.Styling;
using Avalonia.Styling;

namespace LlmPlatform.Desktop.Services;

/// <summary>动态替换中英文资源字典并同步主题，确保切换后新界面文本立即使用目标语言。</summary>
public sealed class LocalizationService
{
    private readonly App _app;

    public LocalizationService(App app)
    {
        _app = app;
    }

    /// <summary>语言或主题变化后触发，供托盘菜单等非绑定文案刷新。</summary>
    public event EventHandler? Changed;

    public void Apply(string language, string theme)
    {
        string source = string.Equals(language, "en", StringComparison.OrdinalIgnoreCase)
            ? "avares://LlmPlatform.Desktop/Resources/Strings.en.axaml"
            : "avares://LlmPlatform.Desktop/Resources/Strings.zh.axaml";
        ResourceInclude include = new(new Uri("avares://LlmPlatform.Desktop/"))
        {
            Source = new Uri(source, UriKind.Absolute)
        };
        // 字典只保留一种语言，避免相同资源键的合并顺序导致部分文本仍使用旧语言。
        _app.Resources.MergedDictionaries.Clear();
        _app.Resources.MergedDictionaries.Add(include);
        _app.RequestedThemeVariant = theme switch
        {
            "light" => ThemeVariant.Light,
            "dark" => ThemeVariant.Dark,
            _ => ThemeVariant.Default
        };

        Changed?.Invoke(this, EventArgs.Empty);
    }

    public string Text(string key)
    {
        if (Application.Current is { } app
            && app.TryGetResource(key, app.ActualThemeVariant, out object? value)
            && value is string text)
        {
            return text;
        }

        // 缺失资源时显示键名，比返回空字符串更容易定位中英文资源不同步。
        return key;
    }
}
