using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Markup.Xaml;
using Avalonia.Threading;
using LlmPlatform.Desktop.Services;
using LlmPlatform.Desktop.ViewModels;

namespace LlmPlatform.Desktop;

/// <summary>桌面应用组合根，装配本机服务、窗口服务与托盘 ViewModel，并统一释放进程资源。</summary>
public partial class App : Application
{
    public override void Initialize()
    {
        AvaloniaXamlLoader.Load(this);
    }

    public override void OnFrameworkInitializationCompleted()
    {
        if (ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
        {
            // 托盘常驻：关闭窗口不退出，只有托盘菜单的退出才结束进程。
            desktop.ShutdownMode = ShutdownMode.OnExplicitShutdown;

            // 强调色跟随系统个性化设置，必须在窗口创建前生效，避免首帧使用内置蓝色。
            new SystemAccentService(this).Start();

            LocalizationService localization = new(this);
            PlatformClient client = new();
            ServerProcessService server = new();
            FileDialogService dialogs = new(localization);
            WindowService windows = new(dialogs);
            TrayViewModel viewModel = new(client, server, localization, dialogs, windows);
            windows.Bind(viewModel);
            DataContext = viewModel;

            TrayIconService trayIcon = new(this, viewModel);
            trayIcon.Start();

            desktop.Exit += (_, _) =>
            {
                trayIcon.Dispose();
                // 退出时先尽量走优雅关机，再释放 HTTP 客户端。
                try
                {
                    viewModel.ShutdownBackgroundAsync().GetAwaiter().GetResult();
                }
                catch (Exception)
                {
                    server.Dispose();
                }

                client.Dispose();
            };

            // 不阻塞 Avalonia 消息循环；服务启动结果会异步更新托盘状态和页面数据。
            // --settings / --chat 必须等控制面就绪后再开窗口，否则会话列表会在连接失败时空着不刷新。
            string[] args = desktop.Args ?? [];
            _ = Dispatcher.UIThread.InvokeAsync(async () =>
            {
                // 冷启动时本地服务还在拉起，初始化失败要重试；
                // 即使全部失败也必须把窗口显示出来，窗口内部会继续重试并在状态栏提示原因。
                for (int attempt = 0; attempt < 6; attempt++)
                {
                    try
                    {
                        await viewModel.InitializeAsync().ConfigureAwait(true);
                        break;
                    }
                    catch (Exception)
                    {
                        await Task.Delay(800).ConfigureAwait(true);
                    }
                }

                if (args.Contains("--settings"))
                {
                    windows.ShowSettings();
                }
                else if (args.Contains("--chat"))
                {
                    windows.ShowChat();
                }
            });
        }

        base.OnFrameworkInitializationCompleted();
    }
}
