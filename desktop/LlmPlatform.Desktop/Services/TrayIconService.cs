using Avalonia;
using Avalonia.Controls;
using Avalonia.Platform;
using LlmPlatform.Desktop.ViewModels;

namespace LlmPlatform.Desktop.Services;

/// <summary>
/// 托盘图标入口：Windows 优先用原生实现，菜单由系统按工作区定位，不会遮住任务栏；
/// 其他平台以及原生调用失败时退回 Avalonia 内置托盘。
/// </summary>
public sealed class TrayIconService : IDisposable
{
    private static readonly Uri IconUri = new("avares://LlmPlatform.Desktop/Assets/tray.ico");

    private readonly Application _app;
    private readonly TrayViewModel _tray;

    private WindowsTrayIcon? _native;
    private TrayIcon? _managed;
    private TrayIcons? _icons;
    private NativeMenuItem? _chatItem;
    private NativeMenuItem? _settingsItem;
    private NativeMenuItem? _exitItem;

    public TrayIconService(Application app, TrayViewModel tray)
    {
        _app = app;
        _tray = tray;
    }

    public void Start()
    {
        if (OperatingSystem.IsWindows())
        {
            WindowsTrayIcon native = new(_app, _tray);
            if (native.TryStart())
            {
                _native = native;
                return;
            }

            native.Dispose();
        }

        StartManaged();
    }

    public void Dispose()
    {
        _native?.Dispose();
        _native = null;

        if (_managed is not null)
        {
            _tray.PropertyChanged -= OnTrayPropertyChanged;
            TrayIcon.SetIcons(_app, new TrayIcons());
            _managed.Dispose();
            _managed = null;
            _icons = null;
        }
    }

    private void StartManaged()
    {
        _chatItem = new NativeMenuItem { Header = _tray.ChatMenuHeader, Command = _tray.ShowChatCommand };
        _settingsItem = new NativeMenuItem { Header = _tray.SettingsMenuHeader, Command = _tray.ShowSettingsCommand };
        _exitItem = new NativeMenuItem { Header = _tray.ExitMenuHeader, Command = _tray.ExitCommand };

        NativeMenu menu = new();
        menu.Items.Add(_chatItem);
        menu.Items.Add(_settingsItem);
        menu.Items.Add(new NativeMenuItemSeparator());
        menu.Items.Add(_exitItem);

        _managed = new TrayIcon
        {
            Icon = new WindowIcon(AssetLoader.Open(IconUri)),
            ToolTipText = _tray.TrayTooltip,
            Command = _tray.ShowChatCommand,
            Menu = menu
        };

        _icons = new TrayIcons { _managed };
        TrayIcon.SetIcons(_app, _icons);
        _tray.PropertyChanged += OnTrayPropertyChanged;
    }

    private void OnTrayPropertyChanged(object? sender, System.ComponentModel.PropertyChangedEventArgs e)
    {
        // 语言切换后重新取文案，托盘菜单不参与数据绑定。
        switch (e.PropertyName)
        {
            case nameof(TrayViewModel.TrayTooltip) when _managed is not null:
                _managed.ToolTipText = _tray.TrayTooltip;
                break;
            case nameof(TrayViewModel.ChatMenuHeader) when _chatItem is not null:
                _chatItem.Header = _tray.ChatMenuHeader;
                break;
            case nameof(TrayViewModel.SettingsMenuHeader) when _settingsItem is not null:
                _settingsItem.Header = _tray.SettingsMenuHeader;
                break;
            case nameof(TrayViewModel.ExitMenuHeader) when _exitItem is not null:
                _exitItem.Header = _tray.ExitMenuHeader;
                break;
            default:
                break;
        }
    }
}
