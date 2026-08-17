using Avalonia;

namespace LlmPlatform.Desktop;

/// <summary>Avalonia 桌面进程入口，平台探测和字体配置在 UI 线程启动前完成。</summary>
internal static class Program
{
    [STAThread]
    public static void Main(string[] args)
    {
        BuildAvaloniaApp().StartWithClassicDesktopLifetime(args);
    }

    public static AppBuilder BuildAvaloniaApp()
    {
        return AppBuilder.Configure<App>().UsePlatformDetect().WithInterFont().LogToTrace();
    }
}
