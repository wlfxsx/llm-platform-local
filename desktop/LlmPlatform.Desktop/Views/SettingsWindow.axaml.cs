using System;
using System.Linq;
using Avalonia;
using Avalonia.Animation;
using Avalonia.Animation.Easings;
using Avalonia.Controls;
using Avalonia.Controls.Presenters;
using Avalonia.Media;
using Avalonia.Styling;
using Avalonia.VisualTree;
using LlmPlatform.Desktop.ViewModels;

namespace LlmPlatform.Desktop.Views;

/// <summary>设置窗口代码后置；监控页仅在选中且窗口可见时采样。</summary>
public partial class SettingsWindow : FluentWindow
{
    public SettingsWindow()
    {
        InitializeComponent();
        PropertyChanged += OnWindowPropertyChanged;
    }

    private void OnSettingsTabChanged(object? sender, SelectionChangedEventArgs e)
    {
        SyncMonitorPolling();
        PlayPageTransition();
    }

    protected override void OnOpened(EventArgs e)
    {
        base.OnOpened(e);
        SyncMonitorPolling();
    }

    private void OnWindowPropertyChanged(object? sender, AvaloniaPropertyChangedEventArgs e)
    {
        if (e.Property == IsVisibleProperty)
        {
            SyncMonitorPolling();
        }
    }

    /// <summary>切页时让内容区淡入并轻微上浮，避免页面瞬间硬切。</summary>
    private void PlayPageTransition()
    {
        // XAML 初始化阶段就会触发一次选中变更，此时可视树还没建立，遍历会失败。
        if (!IsLoaded)
        {
            return;
        }

        ContentPresenter? host = SettingsTabs.GetVisualDescendants()
            .OfType<ContentPresenter>()
            .FirstOrDefault(presenter => presenter.Name == "PART_SelectedContentHost");
        if (host is null)
        {
            return;
        }

        Animation animation = new()
        {
            Duration = TimeSpan.FromMilliseconds(200),
            Easing = new CubicEaseOut(),
            FillMode = FillMode.Forward,
            Children =
            {
                new KeyFrame
                {
                    Cue = new Cue(0d),
                    Setters =
                    {
                        new Setter(OpacityProperty, 0d),
                        new Setter(TranslateTransform.YProperty, 10d)
                    }
                },
                new KeyFrame
                {
                    Cue = new Cue(1d),
                    Setters =
                    {
                        new Setter(OpacityProperty, 1d),
                        new Setter(TranslateTransform.YProperty, 0d)
                    }
                }
            }
        };

        // 动画失败不应影响切页，忽略返回的任务即可。
        _ = animation.RunAsync(host);
    }

    private void SyncMonitorPolling()
    {
        if (DataContext is not TrayViewModel tray)
        {
            return;
        }

        bool monitorVisible = IsVisible && SettingsTabs.SelectedIndex == 2;
        tray.Monitor.SetActive(monitorVisible);
    }
}
