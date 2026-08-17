namespace LlmPlatform.Desktop.Services;

/// <summary>
/// ViewModel 通过该接口请求打开窗口或退出，不直接引用具体窗口类型。
/// </summary>
public interface IWindowService
{
    void ShowChat();

    void ShowSettings();

    void Shutdown();
}
