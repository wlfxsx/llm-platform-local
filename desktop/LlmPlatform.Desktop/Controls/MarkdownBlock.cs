using System.Text;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Documents;
using Avalonia.Media;

namespace LlmPlatform.Desktop.Controls;

/// <summary>
/// 轻量 Markdown 渲染：覆盖标题、列表、粗斜体、行内代码与围栏代码块。
/// 不引入第三方包，避免桌面壳依赖膨胀；复杂表格/链接仍按纯文本降级显示。
/// </summary>
public sealed class MarkdownBlock : ContentControl
{
    public static readonly StyledProperty<string?> MarkdownProperty = AvaloniaProperty.Register<
        MarkdownBlock,
        string?>(nameof(Markdown));

    static MarkdownBlock()
    {
        MarkdownProperty.Changed.AddClassHandler<MarkdownBlock>((control, _) => control.Rebuild());
    }

    public string? Markdown
    {
        get => GetValue(MarkdownProperty);
        set => SetValue(MarkdownProperty, value);
    }

    private void Rebuild()
    {
        StackPanel root = new() { Spacing = 8 };
        string source = Markdown ?? string.Empty;
        if (string.IsNullOrEmpty(source))
        {
            Content = root;
            return;
        }

        List<string> lines = [.. source.Replace("\r\n", "\n").Split('\n')];
        int index = 0;
        while (index < lines.Count)
        {
            string line = lines[index];
            if (line.StartsWith("```", StringComparison.Ordinal))
            {
                string language = line[3..].Trim();
                index++;
                StringBuilder code = new();
                while (index < lines.Count && !lines[index].StartsWith("```", StringComparison.Ordinal))
                {
                    if (code.Length > 0)
                    {
                        code.Append('\n');
                    }

                    code.Append(lines[index]);
                    index++;
                }

                if (index < lines.Count)
                {
                    index++;
                }

                root.Children.Add(CreateCodeBlock(code.ToString(), language));
                continue;
            }

            if (IsThematicBreak(line))
            {
                root.Children.Add(
                    new Border
                    {
                        Classes = { "divider" },
                        Margin = new Thickness(0, 4, 0, 4)
                    });
                index++;
                continue;
            }

            if (TryHeading(line, out int level, out string heading))
            {
                root.Children.Add(CreateHeading(heading, level));
                index++;
                continue;
            }

            if (TryListItem(line, out string bullet))
            {
                StringBuilder list = new();
                while (index < lines.Count && TryListItem(lines[index], out string item))
                {
                    if (list.Length > 0)
                    {
                        list.Append('\n');
                    }

                    list.Append("• ").Append(item);
                    index++;
                }

                root.Children.Add(CreateParagraph(list.ToString()));
                continue;
            }

            if (string.IsNullOrWhiteSpace(line))
            {
                index++;
                continue;
            }

            StringBuilder paragraph = new();
            while (index < lines.Count
                && !string.IsNullOrWhiteSpace(lines[index])
                && !lines[index].StartsWith("```", StringComparison.Ordinal)
                && !IsThematicBreak(lines[index])
                && !TryHeading(lines[index], out _, out _)
                && !TryListItem(lines[index], out _))
            {
                if (paragraph.Length > 0)
                {
                    paragraph.Append(' ');
                }

                paragraph.Append(lines[index].Trim());
                index++;
            }

            root.Children.Add(CreateParagraph(paragraph.ToString()));
        }

        Content = root;
    }

    private static Control CreateCodeBlock(string code, string language)
    {
        StackPanel panel = new() { Spacing = 4 };
        if (!string.IsNullOrWhiteSpace(language))
        {
            panel.Children.Add(
                new TextBlock
                {
                    Text = language,
                    Classes = { "caption" },
                    Opacity = 0.72
                });
        }

        panel.Children.Add(
            new SelectableTextBlock
            {
                Text = code,
                TextWrapping = TextWrapping.Wrap,
                FontFamily = new FontFamily("Cascadia Code, Consolas, Courier New, monospace"),
                FontSize = 12.5
            });

        return new Border
        {
            Classes = { "code-block" },
            Child = panel
        };
    }

    private static Control CreateHeading(string text, int level)
    {
        double size = level switch
        {
            1 => 22,
            2 => 18,
            _ => 15
        };
        SelectableTextBlock block = new()
        {
            TextWrapping = TextWrapping.Wrap,
            FontSize = size,
            FontWeight = FontWeight.SemiBold,
            Margin = new Thickness(0, level == 1 ? 4 : 2, 0, 0)
        };
        ApplyInlines(block.Inlines, text);
        return block;
    }

    private static Control CreateParagraph(string text)
    {
        // 不固定 LineHeight：中文与 emoji 的行盒比 22 高，写死会把字的下沿裁掉。
        SelectableTextBlock block = new()
        {
            TextWrapping = TextWrapping.Wrap,
            Opacity = 0.94,
            LineSpacing = 4
        };
        ApplyInlines(block.Inlines, text);
        return block;
    }

    private static void ApplyInlines(InlineCollection? inlines, string text)
    {
        if (inlines is null)
        {
            return;
        }

        inlines.Clear();
        int index = 0;
        while (index < text.Length)
        {
            if (TryInline(text, index, '`', out string code, out int next))
            {
                inlines.Add(
                    new Run(code)
                    {
                        FontFamily = new FontFamily("Cascadia Code, Consolas, Courier New, monospace"),
                        FontSize = 12.5
                    });
                index = next;
                continue;
            }

            if (TryInline(text, index, "**", out string bold, out next)
                || TryInline(text, index, "__", out bold, out next))
            {
                inlines.Add(new Run(bold) { FontWeight = FontWeight.SemiBold });
                index = next;
                continue;
            }

            if (TryInline(text, index, "*", out string italic, out next)
                || TryInline(text, index, "_", out italic, out next))
            {
                inlines.Add(new Run(italic) { FontStyle = FontStyle.Italic });
                index = next;
                continue;
            }

            int plainEnd = NextMarkup(text, index);
            inlines.Add(new Run(text[index..plainEnd]));
            index = plainEnd;
        }
    }

    private static bool TryInline(string text, int index, char marker, out string value, out int next)
    {
        value = string.Empty;
        next = index;
        if (index >= text.Length || text[index] != marker)
        {
            return false;
        }

        int close = text.IndexOf(marker, index + 1);
        if (close <= index + 1)
        {
            return false;
        }

        value = text[(index + 1)..close];
        next = close + 1;
        return true;
    }

    private static bool TryInline(string text, int index, string marker, out string value, out int next)
    {
        value = string.Empty;
        next = index;
        if (!text.AsSpan(index).StartsWith(marker, StringComparison.Ordinal))
        {
            return false;
        }

        int close = text.IndexOf(marker, index + marker.Length, StringComparison.Ordinal);
        if (close <= index + marker.Length)
        {
            return false;
        }

        value = text[(index + marker.Length)..close];
        next = close + marker.Length;
        return true;
    }

    private static int NextMarkup(string text, int index)
    {
        for (int i = index + 1; i < text.Length; i++)
        {
            char current = text[i];
            if (current is '`' or '*' or '_')
            {
                return i;
            }
        }

        return text.Length;
    }

    /// <summary>三个及以上的 -、* 或 _ 独占一行时是分割线，不能按正文或列表输出。</summary>
    private static bool IsThematicBreak(string line)
    {
        string trimmed = line.Trim();
        if (trimmed.Length < 3)
        {
            return false;
        }

        char marker = trimmed[0];
        return marker is '-' or '*' or '_' && trimmed.All(current => current == marker);
    }

    private static bool TryHeading(string line, out int level, out string text)
    {
        level = 0;
        text = string.Empty;
        if (!line.StartsWith('#') || line.Length < 2)
        {
            return false;
        }

        while (level < line.Length && line[level] == '#' && level < 3)
        {
            level++;
        }

        if (level == 0 || level >= line.Length || line[level] != ' ')
        {
            return false;
        }

        text = line[(level + 1)..].Trim();
        return text.Length > 0;
    }

    private static bool TryListItem(string line, out string text)
    {
        text = string.Empty;
        string trimmed = line.TrimStart();
        if (trimmed.StartsWith("- ", StringComparison.Ordinal)
            || trimmed.StartsWith("* ", StringComparison.Ordinal)
            || trimmed.StartsWith("+ ", StringComparison.Ordinal))
        {
            text = trimmed[2..].Trim();
            return true;
        }

        int dot = trimmed.IndexOf(". ", StringComparison.Ordinal);
        if (dot > 0 && int.TryParse(trimmed[..dot], out _))
        {
            text = trimmed[(dot + 2)..].Trim();
            return true;
        }

        return false;
    }
}
