using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Platform;
using LlmPlatform.Desktop.ViewModels;
using LlmPlatform.Desktop.Views;

namespace LlmPlatform.Desktop.Services;

/// <summary>
/// 托盘常驻应用：窗口只在需要时创建，关闭按钮收回托盘而不结束进程。
/// </summary>
public sealed class WindowService : IWindowService
{
    private readonly FileDialogService _dialogs;
    private TrayViewModel? _tray;
    private ChatWindow? _chat;
    private SettingsWindow? _settings;

    public WindowService(FileDialogService dialogs)
    {
        _dialogs = dialogs;
    }

    public void Bind(TrayViewModel tray)
    {
        _tray = tray;
    }

    public void ShowChat()
    {
        if (_tray is null)
        {
            return;
        }

        if (_chat is null)
        {
            _chat = new ChatWindow { DataContext = _tray.Chat };
            HideOnClose(_chat);
        }

        Present(_chat);
    }

    public void ShowSettings()
    {
        if (_tray is null)
        {
            return;
        }

        if (_settings is null)
        {
            _settings = new SettingsWindow { DataContext = _tray };
            HideOnClose(_settings);
        }

        Present(_settings);
    }

    public void Shutdown()
    {
        if (Application.Current?.ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
        {
            desktop.Shutdown();
        }
    }

    private void Present(Window window)
    {
        // 文件选择器必须绑定当前可见窗口，否则跨平台存储提供器可能没有有效父窗口。
        _dialogs.Host = window;
        window.Show();
        RestoreDesignedFrame(window);
        window.Activate();
    }

    /// <summary>
    /// XAML 的 900×640 只在首次构造生效。关闭进托盘后同一实例会记住被系统贴边或拖拽后的客户区，
    /// 再打开就会变成比设置页更窄的竖条。
    /// </summary>
    private void RestoreDesignedFrame(Window window)
    {
        if (window.WindowState == WindowState.Maximized)
        {
            return;
        }

        const double designedWidth = 900;
        const double designedHeight = 640;
        if (window.Width >= window.Height && window.Width + 0.5 >= designedWidth)
        {
            return;
        }

        window.WindowState = WindowState.Normal;
        window.Width = designedWidth;
        window.Height = designedHeight;
        if (window is ChatWindow)
        {
            _tray!.Chat.SidebarCollapsed = false;
        }
        else if (window is SettingsWindow)
        {
            _tray!.NavCollapsed = false;
        }

        Screen? screen = window.Screens.ScreenFromWindow(window) ?? window.Screens.Primary;
        if (screen is null)
        {
            return;
        }

        PixelRect area = screen.WorkingArea;
        double scale = window.RenderScaling <= 0 ? 1 : window.RenderScaling;
        int pixelWidth = (int)Math.Round(window.Width * scale);
        int pixelHeight = (int)Math.Round(window.Height * scale);
        int x = area.X + Math.Max(0, (area.Width - pixelWidth) / 2);
        int y = area.Y + Math.Max(0, (area.Height - pixelHeight) / 2);
        window.Position = new PixelPoint(x, y);
    }

    private void HideOnClose(Window window)
    {
        window.Closing += (sender, args) =>
        {
            // 托盘应用复用窗口实例和 ViewModel，普通关闭只隐藏，显式退出由 Shutdown 负责。
            args.Cancel = true;
            ((Window)sender!).Hide();
            if (sender is SettingsWindow)
            {
                _tray?.Monitor.SetActive(false);
            }
        };
    }
}
