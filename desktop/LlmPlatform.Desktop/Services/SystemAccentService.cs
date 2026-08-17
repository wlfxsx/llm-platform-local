using Avalonia;
using Avalonia.Controls;
using Avalonia.Media;
using Avalonia.Media.Immutable;
using Avalonia.Platform;
using Avalonia.Themes.Fluent;
using Avalonia.Styling;

namespace LlmPlatform.Desktop.Services;

/// <summary>
/// 把系统个性化强调色同步到界面：Fluent 控件（开关、复选框、输入焦点）走主题调色板，
/// 自绘部分（主按钮、导航指示条、监控折线）走应用级资源。系统改色或主题切换后即时重算。
/// </summary>
public sealed class SystemAccentService
{
    /// <summary>取不到系统颜色时退回 Windows 默认蓝，避免界面出现无强调色的灰白状态。</summary>
    private static readonly Color FallbackAccent = Color.FromRgb(0x00, 0x78, 0xD4);

    private readonly Application _app;

    public SystemAccentService(Application app)
    {
        _app = app;
    }

    public void Start()
    {
        if (_app.PlatformSettings is { } platform)
        {
            platform.ColorValuesChanged += (_, _) => Apply();
        }

        _app.ActualThemeVariantChanged += (_, _) => Apply();
        Apply();
    }

    public void Apply()
    {
        Color accent = _app.PlatformSettings?.GetColorValues().AccentColor1 ?? FallbackAccent;
        // 浅色背景上用加深的色阶，深色背景上用提亮的色阶，保证文字与填充的对比度。
        Color onLight = Shade(accent, 0.85);
        Color onDark = Shade(accent, 1.45);

        foreach (FluentTheme theme in _app.Styles.OfType<FluentTheme>())
        {
            if (theme.Palettes.TryGetValue(ThemeVariant.Light, out ColorPaletteResources? light))
            {
                light.Accent = onLight;
            }

            if (theme.Palettes.TryGetValue(ThemeVariant.Dark, out ColorPaletteResources? dark))
            {
                dark.Accent = onDark;
            }
        }

        // 启动早期 ActualThemeVariant 可能仍是 Default，此时要按系统主题判断，
        // 否则深色界面会拿到为浅色算的深色阶，强调色看起来发灰。
        bool isDark = Equals(_app.ActualThemeVariant, ThemeVariant.Dark)
            || (!Equals(_app.ActualThemeVariant, ThemeVariant.Light)
                && _app.PlatformSettings?.GetColorValues().ThemeVariant == PlatformThemeVariant.Dark);
        Color fill = isDark ? onDark : onLight;
        Color text = Luminance(fill) > 0.55 ? Colors.Black : Colors.White;
        IResourceDictionary resources = _app.Resources;
        resources["AccentFillColor"] = fill;
        resources["OnAccentFillColor"] = text;
        resources["AccentBrush"] = Brush(fill);
        resources["AccentTextBrush"] = Brush(text);
        resources["AccentButtonBackground"] = Brush(fill);
        resources["AccentButtonBackgroundPointerOver"] = Brush(WithAlpha(fill, 0xE6));
        resources["AccentButtonBackgroundPressed"] = Brush(WithAlpha(fill, 0xCC));
        resources["AccentButtonForeground"] = Brush(text);
        resources["AccentButtonForegroundPressed"] = Brush(WithAlpha(text, 0xB3));
    }

    private static ImmutableSolidColorBrush Brush(Color color)
    {
        return new ImmutableSolidColorBrush(color);
    }

    private static Color WithAlpha(Color color, byte alpha)
    {
        return Color.FromArgb(alpha, color.R, color.G, color.B);
    }

    /// <summary>
    /// 按系统生成强调色色阶的方式取色：保持色相与饱和度、只缩放明度，
    /// 明度触顶后再等比降饱和度，避免直接和黑白混色导致颜色发灰。
    /// </summary>
    private static Color Shade(Color color, double valueScale)
    {
        HsvColor hsv = color.ToHsv();
        double value = hsv.V * valueScale;
        double saturation = value > 1 ? hsv.S / value : hsv.S;
        return new HsvColor(1, hsv.H, Math.Clamp(saturation, 0, 1), Math.Clamp(value, 0.08, 1)).ToRgb();
    }

    /// <summary>按人眼感知加权估算亮度，用于决定强调色上的文字用黑还是白。</summary>
    private static double Luminance(Color color)
    {
        return ((0.2126 * color.R) + (0.7152 * color.G) + (0.0722 * color.B)) / 255.0;
    }
}
