using Avalonia.Controls;
using Avalonia.Platform.Storage;

namespace LlmPlatform.Desktop.Services;

/// <summary>通过当前宿主窗口访问 Avalonia 存储提供器，并只返回可供后端读取的本机路径。</summary>
public sealed class FileDialogService
{
    private readonly LocalizationService _localization;

    public FileDialogService(LocalizationService localization)
    {
        _localization = localization;
    }

    /// <summary>窗口服务在显示页面时设置宿主，避免服务层长期持有已关闭窗口。</summary>
    public Window? Host { get; set; }

    public async Task<string?> OpenFileAsync(CancellationToken cancellationToken)
    {
        if (Host is null)
        {
            return null;
        }

        // 后端导入接口接收本机路径，非文件系统 URI 无法安全传递，因此使用 TryGetLocalPath。
        IReadOnlyList<IStorageFile> files = await Host.StorageProvider.OpenFilePickerAsync(
            new FilePickerOpenOptions
            {
                AllowMultiple = false,
                Title = _localization.Text("SelectFile")
            });
        cancellationToken.ThrowIfCancellationRequested();
        return files.Count > 0 ? files[0].TryGetLocalPath() : null;
    }

    public async Task<string?> OpenFolderAsync(CancellationToken cancellationToken)
    {
        if (Host is null)
        {
            return null;
        }

        IReadOnlyList<IStorageFolder> folders = await Host.StorageProvider.OpenFolderPickerAsync(
            new FolderPickerOpenOptions
            {
                AllowMultiple = false,
                Title = _localization.Text("SelectFolder")
            });
        cancellationToken.ThrowIfCancellationRequested();
        return folders.Count > 0 ? folders[0].TryGetLocalPath() : null;
    }
}
