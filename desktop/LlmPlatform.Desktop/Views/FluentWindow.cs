using Avalonia;
using Avalonia.Animation;
using Avalonia.Animation.Easings;
using Avalonia.Controls;
using Avalonia.Media;
using Avalonia.Styling;
using System.Runtime.InteropServices;

namespace LlmPlatform.Desktop.Views;

/// <summary>不使用云母透底，避免壁纸把窗口染成蓝色；背景与标题栏都固定为 WinUI 页面色。</summary>
public class FluentWindow : Window
{
    private const int DwmwaCaptionColor = 35;
    private const int DwmwaTextColor = 36;

    public FluentWindow()
    {
        TransparencyLevelHint = [WindowTransparencyLevel.None];
        SystemDecorations = SystemDecorations.Full;
    }

    protected override void OnOpened(EventArgs e)
    {
        base.OnOpened(e);
        ApplyChrome();
        PlayEnterAnimation();
    }

    /// <summary>窗口出现时内容淡入并轻微上浮，避免从托盘唤出时画面硬切。</summary>
    private void PlayEnterAnimation()
    {
        if (Content is not Control content)
        {
            return;
        }

        Animation animation = new()
        {
            Duration = TimeSpan.FromMilliseconds(220),
            Easing = new CubicEaseOut(),
            FillMode = FillMode.Forward,
            Children =
            {
                new KeyFrame
                {
                    Cue = new Cue(0d),
                    Setters =
                    {
                        new Avalonia.Styling.Setter(OpacityProperty, 0d),
                        new Avalonia.Styling.Setter(TranslateTransform.YProperty, 8d)
                    }
                },
                new KeyFrame
                {
                    Cue = new Cue(1d),
                    Setters =
                    {
                        new Avalonia.Styling.Setter(OpacityProperty, 1d),
                        new Avalonia.Styling.Setter(TranslateTransform.YProperty, 0d)
                    }
                }
            }
        };

        _ = animation.RunAsync(content);
    }

    protected override void OnPropertyChanged(AvaloniaPropertyChangedEventArgs change)
    {
        base.OnPropertyChanged(change);
        if (change.Property == ActualThemeVariantProperty)
        {
            ApplyChrome();
        }
    }

    private void ApplyChrome()
    {
        if (this.TryFindResource("WindowBackgroundBrush", ActualThemeVariant, out object? value)
            && value is IBrush brush)
        {
            Background = brush;
        }

        ApplyCaptionColors();
    }

    private void ApplyCaptionColors()
    {
        if (!OperatingSystem.IsWindows())
        {
            return;
        }

        nint hwnd = TryGetPlatformHandle()?.Handle ?? 0;
        if (hwnd == 0)
        {
            return;
        }

        // DWM 标题栏色是 COLORREF（0x00BBGGRR），跟随当前实际主题，避免系统强调色把标题栏染成铜色。
        bool light = ActualThemeVariant == ThemeVariant.Light;
        uint background = light ? 0x00F3F3F3u : 0x00202020u;
        uint foreground = light ? 0x00000000u : 0x00FFFFFFu;
        _ = DwmSetWindowAttribute(hwnd, DwmwaCaptionColor, ref background, sizeof(uint));
        _ = DwmSetWindowAttribute(hwnd, DwmwaTextColor, ref foreground, sizeof(uint));
    }

    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(nint hwnd, int attribute, ref uint value, int size);
}
