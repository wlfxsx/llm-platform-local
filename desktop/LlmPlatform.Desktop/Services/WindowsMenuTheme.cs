using System.Runtime.InteropServices;

namespace LlmPlatform.Desktop.Services;

/// <summary>
/// 让原生 Win32 菜单跟随应用深浅色。uxtheme.dll 只按序号导出这两个入口
/// （135 = SetPreferredAppMode，136 = FlushMenuThemes），这是让系统菜单变深色的唯一途径；
/// 入口不存在（旧版 Windows）时保持系统默认浅色，不影响菜单功能。
/// </summary>
internal static class WindowsMenuTheme
{
    private const int ModeForceDark = 2;
    private const int ModeForceLight = 3;

    /// <summary>SetPreferredAppMode 自 Windows 10 1903（build 18362）起才存在。</summary>
    private const int MinimumBuild = 18362;

    private static SetPreferredAppModeDelegate? _setMode;
    private static FlushMenuThemesDelegate? _flushThemes;
    private static bool _probed;
    private static int _appliedMode = -1;

    private delegate int SetPreferredAppModeDelegate(int mode);

    private delegate void FlushMenuThemesDelegate();

    public static void Apply(bool dark)
    {
        if (!TryResolve())
        {
            return;
        }

        int mode = dark ? ModeForceDark : ModeForceLight;
        if (_appliedMode == mode)
        {
            return;
        }

        _ = _setMode!(mode);
        _flushThemes!();
        _appliedMode = mode;
    }

    private static bool TryResolve()
    {
        if (_probed)
        {
            return _setMode is not null && _flushThemes is not null;
        }

        _probed = true;
        if (!OperatingSystem.IsWindows() || Environment.OSVersion.Version.Build < MinimumBuild)
        {
            return false;
        }

        IntPtr module = GetModuleHandleW("uxtheme.dll");
        if (module == IntPtr.Zero)
        {
            module = LoadLibraryW("uxtheme.dll");
        }

        if (module == IntPtr.Zero)
        {
            return false;
        }

        IntPtr setMode = GetProcAddress(module, 135);
        IntPtr flushThemes = GetProcAddress(module, 136);
        if (setMode == IntPtr.Zero || flushThemes == IntPtr.Zero)
        {
            return false;
        }

        _setMode = Marshal.GetDelegateForFunctionPointer<SetPreferredAppModeDelegate>(setMode);
        _flushThemes = Marshal.GetDelegateForFunctionPointer<FlushMenuThemesDelegate>(flushThemes);
        return true;
    }

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr GetModuleHandleW(string moduleName);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr LoadLibraryW(string fileName);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern IntPtr GetProcAddress(IntPtr module, nint ordinal);
}
