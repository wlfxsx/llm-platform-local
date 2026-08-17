using System.Net.Http;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;

namespace LlmPlatform.Desktop.Services;

/// <summary>
/// 只访问回环地址的平台 API 客户端，统一 JSON 命名、超时、错误边界和 SSE 解析。
/// </summary>
public sealed class PlatformClient : IDisposable
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        PropertyNameCaseInsensitive = true
    };

    private readonly HttpClient _http = new()
    {
        BaseAddress = new Uri("http://127.0.0.1:17890/"),
        Timeout = TimeSpan.FromMinutes(10)
    };

    public Task<JsonElement> GetAsync(string path, CancellationToken cancellationToken)
    {
        return SendAsync(HttpMethod.Get, path, null, cancellationToken);
    }

    public Task<JsonElement> SendAsync(HttpMethod method, string path, object? body, CancellationToken cancellationToken)
    {
        return SendRawAsync(method, path, body, cancellationToken);
    }

    public async Task<string> StreamChatAsync(
        string? sessionId,
        string content,
        Action<string> onDelta,
        CancellationToken cancellationToken)
    {
        using HttpRequestMessage request = new(HttpMethod.Post, "api/ai/chat/stream")
        {
            Content = JsonContent.Create(
                new
                {
                    sessionId,
                    messages = new[] { new { role = "user", content } }
                },
                options: JsonOptions)
        };
        using HttpResponseMessage response = await _http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, cancellationToken)
            .ConfigureAwait(false);
        response.EnsureSuccessStatusCode();
        await using Stream stream = await response.Content.ReadAsStreamAsync(cancellationToken).ConfigureAwait(false);
        using StreamReader reader = new(stream, Encoding.UTF8);
        // 新会话 ID 只会在 done 事件中返回；读取完成后交给 ViewModel 持久到下一轮请求。
        string resolvedSessionId = sessionId ?? string.Empty;
        while (true)
        {
            cancellationToken.ThrowIfCancellationRequested();
            string? line = await reader.ReadLineAsync(cancellationToken).ConfigureAwait(false);
            if (line is null)
            {
                break;
            }
            if (string.IsNullOrWhiteSpace(line) || !line.StartsWith("data:", StringComparison.Ordinal))
            {
                // 忽略 SSE 空行、心跳和未来可能增加的非数据字段。
                continue;
            }

            string json = line["data:".Length..].Trim();
            using JsonDocument document = JsonDocument.Parse(json);
            if (!document.RootElement.TryGetProperty("type", out JsonElement type)
                || !document.RootElement.TryGetProperty("payload", out JsonElement payload))
            {
                continue;
            }

            if (type.GetString() == "delta" && payload.TryGetProperty("text", out JsonElement text))
            {
                onDelta(text.GetString() ?? string.Empty);
            }
            else if (type.GetString() == "done"
                && payload.TryGetProperty("sessionId", out JsonElement returnedSessionId))
            {
                resolvedSessionId = returnedSessionId.GetString() ?? resolvedSessionId;
            }
            else if (type.GetString() == "error")
            {
                // 优先使用服务端已本地化的 message；否则退回 messageKey 供桌面资源映射。
                string detail = payload.TryGetProperty("message", out JsonElement message)
                    ? message.GetString() ?? string.Empty
                    : string.Empty;
                if (string.IsNullOrWhiteSpace(detail))
                {
                    detail = payload.TryGetProperty("messageKey", out JsonElement key)
                        ? key.GetString() ?? "error.internal"
                        : "error.internal";
                }

                throw new InvalidOperationException(detail);
            }
        }

        return resolvedSessionId;
    }

    public void Dispose()
    {
        _http.Dispose();
    }

    private async Task<JsonElement> SendRawAsync(
        HttpMethod method,
        string path,
        object? body,
        CancellationToken cancellationToken)
    {
        using HttpRequestMessage request = new(method, path);
        if (body is not null)
        {
            request.Content = JsonContent.Create(body, options: JsonOptions);
        }

        using HttpResponseMessage response = await _http.SendAsync(request, cancellationToken).ConfigureAwait(false);
        string json = await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false);
        if (!response.IsSuccessStatusCode)
        {
            // 保留服务端统一错误 JSON，调用方可以显示本地化消息或记录稳定错误码。
            throw new InvalidOperationException(json);
        }

        if (string.IsNullOrWhiteSpace(json))
        {
            // DELETE、启停等成功响应没有正文；默认 JsonElement 表示“无返回值”，不伪造空对象。
            return default;
        }

        return JsonSerializer.Deserialize<JsonElement>(json, JsonOptions);
    }
}
