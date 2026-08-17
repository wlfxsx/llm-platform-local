using System.Runtime.InteropServices;
using Avalonia;
using Avalonia.Platform;
using Avalonia.Styling;
using Avalonia.Threading;
using LlmPlatform.Desktop.ViewModels;

namespace LlmPlatform.Desktop.Services;

/// <summary>
/// Windows 原生托盘图标：菜单走 TrackPopupMenuEx，弹出位置按显示器工作区计算，
/// 不会像自绘弹窗那样压住任务栏；菜单文案每次弹出时重建，语言切换后立即生效。
/// </summary>
internal sealed class WindowsTrayIcon : IDisposable
{
    private const string ClassName = "LlmPlatformTrayHost";
    private const uint TrayCallbackMessage = 0x8000 + 1;
    private const uint WmNull = 0x0000;
    private const uint WmDestroy = 0x0002;
    private const uint WmLeftButtonUp = 0x0202;
    private const uint WmRightButtonUp = 0x0205;
    private const uint WmContextMenu = 0x007B;

    private const uint NimAdd = 0x00000000;
    private const uint NimModify = 0x00000001;
    private const uint NimDelete = 0x00000002;
    private const uint NifMessage = 0x00000001;
    private const uint NifIcon = 0x00000002;
    private const uint NifTip = 0x00000004;

    private const uint ImageIcon = 1;
    private const uint LrLoadFromFile = 0x00000010;
    private const int SmCxSmIcon = 49;
    private const int SmCySmIcon = 50;

    private const uint MfString = 0x00000000;
    private const uint MfSeparator = 0x00000800;
    private const uint TpmLeftAlign = 0x0000;
    private const uint TpmRightAlign = 0x0008;
    private const uint TpmTopAlign = 0x0000;
    private const uint TpmBottomAlign = 0x0020;
    private const uint TpmRightButton = 0x0002;
    private const uint TpmReturnCmd = 0x0100;
    private const uint TpmNoNotify = 0x0080;

    private const uint MonitorDefaultToNearest = 0x00000002;

    private const nuint IdChat = 1;
    private const nuint IdSettings = 2;
    private const nuint IdExit = 3;

    private static ushort _registeredClass;

    private readonly Application _app;
    private readonly TrayViewModel _tray;
    private readonly WndProcDelegate _wndProc;
    private IntPtr _hwnd;
    private IntPtr _icon;
    private bool _added;

    public WindowsTrayIcon(Application app, TrayViewModel tray)
    {
        _app = app;
        _tray = tray;
        _wndProc = OnMessage;
    }

    private delegate IntPtr WndProcDelegate(IntPtr hwnd, uint message, IntPtr wParam, IntPtr lParam);

    /// <summary>创建消息窗口并注册托盘图标；本机调用失败时返回 false，由调用方退回 Avalonia 托盘。</summary>
    public bool TryStart()
    {
        IntPtr instance = GetModuleHandleW(null);
        if (_registeredClass == 0)
        {
            WNDCLASSEXW cls = new()
            {
                cbSize = (uint)Marshal.SizeOf<WNDCLASSEXW>(),
                lpfnWndProc = Marshal.GetFunctionPointerForDelegate(_wndProc),
                hInstance = instance,
                lpszClassName = ClassName
            };
            _registeredClass = RegisterClassExW(ref cls);
            if (_registeredClass == 0)
            {
                return false;
            }
        }

        // 只收消息不显示：HWND_MESSAGE 窗口不会出现在任务栏和 Alt+Tab 里。
        _hwnd = CreateWindowExW(0, ClassName, string.Empty, 0, 0, 0, 0, 0, -3, IntPtr.Zero, instance, IntPtr.Zero);
        if (_hwnd == IntPtr.Zero)
        {
            return false;
        }

        _icon = LoadIcon();
        NOTIFYICONDATAW data = CreateData();
        data.uFlags = NifMessage | NifTip | (_icon == IntPtr.Zero ? 0 : NifIcon);
        _added = Shell_NotifyIconW(NimAdd, ref data);
        if (!_added)
        {
            return false;
        }

        _tray.PropertyChanged += OnTrayPropertyChanged;
        return true;
    }

    public void Dispose()
    {
        _tray.PropertyChanged -= OnTrayPropertyChanged;
        if (_added)
        {
            NOTIFYICONDATAW data = CreateData();
            _ = Shell_NotifyIconW(NimDelete, ref data);
            _added = false;
        }

        if (_icon != IntPtr.Zero)
        {
            _ = DestroyIcon(_icon);
            _icon = IntPtr.Zero;
        }

        if (_hwnd != IntPtr.Zero)
        {
            _ = DestroyWindow(_hwnd);
            _hwnd = IntPtr.Zero;
        }
    }

    private void OnTrayPropertyChanged(object? sender, System.ComponentModel.PropertyChangedEventArgs e)
    {
        if (e.PropertyName != nameof(TrayViewModel.TrayTooltip) || !_added)
        {
            return;
        }

        NOTIFYICONDATAW data = CreateData();
        data.uFlags = NifTip;
        _ = Shell_NotifyIconW(NimModify, ref data);
    }

    private NOTIFYICONDATAW CreateData()
    {
        return new NOTIFYICONDATAW
        {
            cbSize = (uint)Marshal.SizeOf<NOTIFYICONDATAW>(),
            hWnd = _hwnd,
            uID = 1,
            uCallbackMessage = TrayCallbackMessage,
            hIcon = _icon,
            // szTip 上限 128 个字符，超长会被 Shell 直接拒绝。
            szTip = Truncate(_tray.TrayTooltip, 127)
        };
    }

    private static string Truncate(string value, int max)
    {
        return value.Length <= max ? value : value[..max];
    }

    /// <summary>托盘图标来自应用资源，Shell 只接受文件或资源句柄，因此先落到临时文件再加载。</summary>
    private static IntPtr LoadIcon()
    {
        try
        {
            string path = Path.Combine(Path.GetTempPath(), "llm-platform-tray.ico");
            using (Stream source = AssetLoader.Open(new Uri("avares://LlmPlatform.Desktop/Assets/tray.ico")))
            using (FileStream target = File.Create(path))
            {
                source.CopyTo(target);
            }

            return LoadImageW(
                IntPtr.Zero,
                path,
                ImageIcon,
                GetSystemMetrics(SmCxSmIcon),
                GetSystemMetrics(SmCySmIcon),
                LrLoadFromFile);
        }
        catch (IOException)
        {
            // 临时目录不可写时退化为无图标托盘，功能仍可用。
            return IntPtr.Zero;
        }
    }

    private IntPtr OnMessage(IntPtr hwnd, uint message, IntPtr wParam, IntPtr lParam)
    {
        if (message == TrayCallbackMessage)
        {
            uint mouse = (uint)(lParam.ToInt64() & 0xFFFF);
            if (mouse == WmLeftButtonUp)
            {
                Post(() => _tray.ShowChatCommand.Execute(null));
                return IntPtr.Zero;
            }

            if (mouse is WmRightButtonUp or WmContextMenu)
            {
                ShowMenu();
                return IntPtr.Zero;
            }
        }
        else if (message == WmDestroy)
        {
            return IntPtr.Zero;
        }

        return DefWindowProcW(hwnd, message, wParam, lParam);
    }

    private void ShowMenu()
    {
        if (!GetCursorPos(out POINT cursor))
        {
            return;
        }

        // 菜单每次弹出前同步深浅色：应用内可以切换主题，不能只在启动时设置一次。
        WindowsMenuTheme.Apply(Equals(_app.ActualThemeVariant, ThemeVariant.Dark));

        IntPtr menu = CreatePopupMenu();
        if (menu == IntPtr.Zero)
        {
            return;
        }

        try
        {
            _ = AppendMenuW(menu, MfString, IdChat, _tray.ChatMenuHeader);
            _ = AppendMenuW(menu, MfString, IdSettings, _tray.SettingsMenuHeader);
            _ = AppendMenuW(menu, MfSeparator, 0, null);
            _ = AppendMenuW(menu, MfString, IdExit, _tray.ExitMenuHeader);

            // 不置前的话菜单会在指针移出后残留，是 Shell 托盘菜单的固定用法。
            _ = SetForegroundWindow(_hwnd);
            (int x, int y, uint align) = Anchor(cursor);
            uint command = TrackPopupMenuEx(menu, align | TpmRightButton | TpmReturnCmd | TpmNoNotify, x, y, _hwnd, IntPtr.Zero);
            _ = PostMessageW(_hwnd, WmNull, IntPtr.Zero, IntPtr.Zero);
            Dispatch(command);
        }
        finally
        {
            _ = DestroyMenu(menu);
        }
    }

    /// <summary>把菜单贴到显示器工作区边缘：工作区已排除任务栏，菜单因此不会压住任务栏。</summary>
    private static (int X, int Y, uint Align) Anchor(POINT cursor)
    {
        IntPtr monitor = MonitorFromPoint(cursor, MonitorDefaultToNearest);
        MONITORINFO info = new() { cbSize = (uint)Marshal.SizeOf<MONITORINFO>() };
        if (monitor == IntPtr.Zero || !GetMonitorInfoW(monitor, ref info))
        {
            return (cursor.X, cursor.Y, TpmLeftAlign | TpmBottomAlign);
        }

        RECT work = info.rcWork;
        bool nearBottom = cursor.Y > (work.Top + work.Bottom) / 2;
        bool nearRight = cursor.X > (work.Left + work.Right) / 2;
        int x = Math.Clamp(cursor.X, work.Left, work.Right);
        int y = nearBottom ? work.Bottom : work.Top;
        uint align = (nearRight ? TpmRightAlign : TpmLeftAlign) | (nearBottom ? TpmBottomAlign : TpmTopAlign);
        return (x, y, align);
    }

    private void Dispatch(uint command)
    {
        switch (command)
        {
            case (uint)IdChat:
                Post(() => _tray.ShowChatCommand.Execute(null));
                break;
            case (uint)IdSettings:
                Post(() => _tray.ShowSettingsCommand.Execute(null));
                break;
            case (uint)IdExit:
                Post(() => _tray.ExitCommand.Execute(null));
                break;
            default:
                break;
        }
    }

    /// <summary>菜单是在原生模态循环里返回的，命令必须回到 Avalonia 调度器再执行。</summary>
    private static void Post(Action action)
    {
        Dispatcher.UIThread.Post(action, DispatcherPriority.Normal);
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct POINT
    {
        public int X;
        public int Y;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct RECT
    {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MONITORINFO
    {
        public uint cbSize;
        public RECT rcMonitor;
        public RECT rcWork;
        public uint dwFlags;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct WNDCLASSEXW
    {
        public uint cbSize;
        public uint style;
        public IntPtr lpfnWndProc;
        public int cbClsExtra;
        public int cbWndExtra;
        public IntPtr hInstance;
        public IntPtr hIcon;
        public IntPtr hCursor;
        public IntPtr hbrBackground;
        [MarshalAs(UnmanagedType.LPWStr)]
        public string? lpszMenuName;
        [MarshalAs(UnmanagedType.LPWStr)]
        public string lpszClassName;
        public IntPtr hIconSm;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct NOTIFYICONDATAW
    {
        public uint cbSize;
        public IntPtr hWnd;
        public uint uID;
        public uint uFlags;
        public uint uCallbackMessage;
        public IntPtr hIcon;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)]
        public string szTip;
        public uint dwState;
        public uint dwStateMask;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)]
        public string szInfo;
        public uint uTimeoutOrVersion;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 64)]
        public string szInfoTitle;
        public uint dwInfoFlags;
        public Guid guidItem;
        public IntPtr hBalloonIcon;
    }

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr GetModuleHandleW(string? moduleName);

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern ushort RegisterClassExW(ref WNDCLASSEXW windowClass);

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr CreateWindowExW(
        uint exStyle,
        string className,
        string windowName,
        uint style,
        int x,
        int y,
        int width,
        int height,
        nint parent,
        IntPtr menu,
        IntPtr instance,
        IntPtr param);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr DefWindowProcW(IntPtr hwnd, uint message, IntPtr wParam, IntPtr lParam);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool DestroyWindow(IntPtr hwnd);

    [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
    private static extern bool Shell_NotifyIconW(uint message, ref NOTIFYICONDATAW data);

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr LoadImageW(IntPtr instance, string name, uint type, int cx, int cy, uint load);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool DestroyIcon(IntPtr icon);

    [DllImport("user32.dll")]
    private static extern int GetSystemMetrics(int index);

    [DllImport("user32.dll")]
    private static extern IntPtr CreatePopupMenu();

    [DllImport("user32.dll")]
    private static extern bool DestroyMenu(IntPtr menu);

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool AppendMenuW(IntPtr menu, uint flags, nuint item, string? content);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hwnd);

    [DllImport("user32.dll")]
    private static extern uint TrackPopupMenuEx(IntPtr menu, uint flags, int x, int y, IntPtr hwnd, IntPtr parameters);

    [DllImport("user32.dll")]
    private static extern bool GetCursorPos(out POINT point);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern bool PostMessageW(IntPtr hwnd, uint message, IntPtr wParam, IntPtr lParam);

    [DllImport("user32.dll")]
    private static extern IntPtr MonitorFromPoint(POINT point, uint flags);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern bool GetMonitorInfoW(IntPtr monitor, ref MONITORINFO info);
}
