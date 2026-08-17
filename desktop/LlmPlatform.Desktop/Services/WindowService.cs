using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.ApplicationLifetimes;
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
        window.Activate();
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
