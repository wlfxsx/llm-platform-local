using Avalonia;
using Avalonia.Controls.Primitives;
using Avalonia.Media;

namespace LlmPlatform.Desktop.Controls;

/// <summary>用矢量绘制 Fluent 线型图标，不依赖本机是否安装 Segoe 字体。</summary>
public sealed class FluentIcon : TemplatedControl
{
    public static readonly StyledProperty<string> KindProperty = AvaloniaProperty.Register<
        FluentIcon,
        string>(nameof(Kind), "Settings");

    static FluentIcon()
    {
        AffectsRender<FluentIcon>(KindProperty, ForegroundProperty, BoundsProperty);
        WidthProperty.OverrideDefaultValue<FluentIcon>(16d);
        HeightProperty.OverrideDefaultValue<FluentIcon>(16d);
    }

    public string Kind
    {
        get => GetValue(KindProperty);
        set => SetValue(KindProperty, value);
    }

    public override void Render(DrawingContext context)
    {
        if (Bounds.Width <= 0 || Bounds.Height <= 0)
        {
            return;
        }

        IBrush brush = Foreground ?? Brushes.White;
        Pen pen = new(brush, 1.7)
        {
            LineCap = PenLineCap.Round,
            LineJoin = PenLineJoin.Round
        };

        // 所有图形都在统一的 24×24 坐标系定义，渲染时按控件实际尺寸等比缩放。
        using (context.PushTransform(Matrix.CreateScale(Bounds.Width / 24d, Bounds.Height / 24d)))
        {
            Draw(Kind, context, brush, pen);
        }
    }

    private static void Draw(string kind, DrawingContext context, IBrush brush, Pen pen)
    {
        switch (kind)
        {
            case "Globe":
                context.DrawEllipse(null, pen, new Point(12, 12), 8, 8);
                context.DrawEllipse(null, pen, new Point(12, 12), 3.2, 8);
                context.DrawLine(pen, new Point(4.2, 12), new Point(19.8, 12));
                break;
            case "Theme":
                context.DrawEllipse(null, pen, new Point(12, 12), 8, 8);
                context.DrawGeometry(brush, null, StreamGeometry.Parse("M12,4 A8,8 0 0 0 12,20 A8,8 0 0 0 12,4 Z"));
                break;
            case "Network":
                context.DrawEllipse(brush, null, new Point(12, 18.5), 1.1, 1.1);
                context.DrawGeometry(
                    null,
                    pen,
                    StreamGeometry.Parse("M7.2,14.8 A6.2,6.2 0 0 1 16.8,14.8 M4.6,11.2 A9.6,9.6 0 0 1 19.4,11.2 M3,8 A12,12 0 0 1 21,8"));
                break;
            case "Models":
                context.DrawRectangle(null, pen, new Rect(4.5, 5.5, 15, 13), 2, 2);
                context.DrawLine(pen, new Point(4.5, 9.5), new Point(19.5, 9.5));
                context.DrawLine(pen, new Point(8.5, 13), new Point(15.5, 13));
                break;
            case "Hardware":
                context.DrawRectangle(null, pen, new Rect(7, 7, 10, 10), 1.5, 1.5);
                context.DrawLine(pen, new Point(12, 4), new Point(12, 7));
                context.DrawLine(pen, new Point(12, 17), new Point(12, 20));
                context.DrawLine(pen, new Point(4, 12), new Point(7, 12));
                context.DrawLine(pen, new Point(17, 12), new Point(20, 12));
                break;
            case "Grid":
                context.DrawRectangle(null, pen, new Rect(4.5, 4.5, 6.2, 6.2), 1.2, 1.2);
                context.DrawRectangle(null, pen, new Rect(13.3, 4.5, 6.2, 6.2), 1.2, 1.2);
                context.DrawRectangle(null, pen, new Rect(4.5, 13.3, 6.2, 6.2), 1.2, 1.2);
                context.DrawRectangle(null, pen, new Rect(13.3, 13.3, 6.2, 6.2), 1.2, 1.2);
                break;
            case "Tools":
                context.DrawGeometry(
                    null,
                    pen,
                    StreamGeometry.Parse(
                        "M15.5,8.2 L19.2,4.5 M14.2,4.8 A4.2,4.2 0 0 1 19.2,9.8 L10.6,18.4 A2.1,2.1 0 0 1 7.6,18.4 L5.6,16.4 A2.1,2.1 0 0 1 5.6,13.4 Z"));
                break;
            case "Plug":
                context.DrawLine(pen, new Point(9, 4), new Point(9, 8));
                context.DrawLine(pen, new Point(15, 4), new Point(15, 8));
                context.DrawRectangle(null, pen, new Rect(7, 8, 10, 8), 2, 2);
                context.DrawLine(pen, new Point(12, 16), new Point(12, 20.5));
                break;
            case "Lightbulb":
                context.DrawEllipse(null, pen, new Point(12, 10), 5.2, 5.4);
                context.DrawLine(pen, new Point(10, 16.8), new Point(14, 16.8));
                context.DrawLine(pen, new Point(10.4, 19.2), new Point(13.6, 19.2));
                break;
            case "Folder":
                context.DrawGeometry(
                    null,
                    pen,
                    StreamGeometry.Parse("M3.8,7.2 L3.8,18.2 A1.6,1.6 0 0 0 5.4,19.8 L18.6,19.8 A1.6,1.6 0 0 0 20.2,18.2 L20.2,9.4 A1.6,1.6 0 0 0 18.6,7.8 L11.4,7.8 L9.6,5.6 L5.4,5.6 A1.6,1.6 0 0 0 3.8,7.2 Z"));
                break;
            case "Document":
                context.DrawGeometry(
                    null,
                    pen,
                    StreamGeometry.Parse("M7,3.8 L14.2,3.8 L19.2,8.8 L19.2,20.2 A1.4,1.4 0 0 1 17.8,21.6 L7,21.6 A1.4,1.4 0 0 1 5.6,20.2 L5.6,5.2 A1.4,1.4 0 0 1 7,3.8 Z"));
                context.DrawLine(pen, new Point(14.2, 3.8), new Point(14.2, 8.8));
                context.DrawLine(pen, new Point(14.2, 8.8), new Point(19.2, 8.8));
                break;
            case "Memory":
                context.DrawRectangle(null, pen, new Rect(4, 7.5, 16, 9), 1.6, 1.6);
                context.DrawLine(pen, new Point(7, 7.5), new Point(7, 5.4));
                context.DrawLine(pen, new Point(12, 7.5), new Point(12, 5.4));
                context.DrawLine(pen, new Point(17, 7.5), new Point(17, 5.4));
                context.DrawLine(pen, new Point(7, 16.5), new Point(7, 18.6));
                context.DrawLine(pen, new Point(12, 16.5), new Point(12, 18.6));
                context.DrawLine(pen, new Point(17, 16.5), new Point(17, 18.6));
                break;
            case "Monitor":
                context.DrawRectangle(null, pen, new Rect(4, 5.5, 16, 11), 1.6, 1.6);
                context.DrawLine(pen, new Point(8, 19), new Point(16, 19));
                context.DrawLine(pen, new Point(12, 16.5), new Point(12, 19));
                context.DrawLine(pen, new Point(7, 14), new Point(10, 11));
                context.DrawLine(pen, new Point(10, 11), new Point(13, 13));
                context.DrawLine(pen, new Point(13, 13), new Point(17, 8.5));
                break;
            case "Puzzle":
                context.DrawGeometry(
                    null,
                    pen,
                    StreamGeometry.Parse(
                        "M8.2,4.5 L11,4.5 A1.8,1.8 0 1 1 11,8.2 L15.8,8.2 L15.8,11 A1.8,1.8 0 1 1 15.8,14.6 L15.8,19.5 L8.4,19.5 A1.6,1.6 0 0 1 6.8,17.9 L6.8,14.6 A1.8,1.8 0 1 0 6.8,11 L6.8,6.1 A1.6,1.6 0 0 1 8.2,4.5 Z"));
                break;
            case "Chat":
                context.DrawGeometry(
                    null,
                    pen,
                    StreamGeometry.Parse(
                        "M5.2,5.2 L18.8,5.2 A2.2,2.2 0 0 1 21,7.4 L21,14.6 A2.2,2.2 0 0 1 18.8,16.8 L10.4,16.8 L6.2,20.2 L6.2,16.8 L5.2,16.8 A2.2,2.2 0 0 1 3,14.6 L3,7.4 A2.2,2.2 0 0 1 5.2,5.2 Z"));
                break;
            case "Delete":
                context.DrawLine(pen, new Point(5, 7), new Point(19, 7));
                context.DrawLine(pen, new Point(9.2, 7), new Point(9.8, 4.6));
                context.DrawLine(pen, new Point(14.2, 4.6), new Point(14.8, 7));
                context.DrawGeometry(
                    null,
                    pen,
                    StreamGeometry.Parse("M7.2,7 L8,19.2 A1.4,1.4 0 0 0 9.4,20.5 L14.6,20.5 A1.4,1.4 0 0 0 16,19.2 L16.8,7"));
                break;
            case "Add":
                context.DrawLine(pen, new Point(12, 5), new Point(12, 19));
                context.DrawLine(pen, new Point(5, 12), new Point(19, 12));
                break;
            case "Send":
                context.DrawGeometry(null, pen, StreamGeometry.Parse("M4.2,12 L20,5.2 L14.2,12 L20,18.8 Z"));
                break;
            case "Menu":
                context.DrawLine(pen, new Point(4.5, 7), new Point(19.5, 7));
                context.DrawLine(pen, new Point(4.5, 12), new Point(19.5, 12));
                context.DrawLine(pen, new Point(4.5, 17), new Point(19.5, 17));
                break;
            case "Warning":
                context.DrawGeometry(
                    null,
                    pen,
                    StreamGeometry.Parse("M12,4.2 L21,19.6 L3,19.6 Z"));
                context.DrawLine(pen, new Point(12, 10), new Point(12, 14.6));
                context.DrawEllipse(brush, null, new Point(12, 17.2), 1, 1);
                break;
            default:
                // 未识别图标退回通用设置齿轮，避免导航项因扩展类型而出现空白。
                context.DrawEllipse(null, pen, new Point(12, 12), 3.1, 3.1);
                for (int i = 0; i < 8; i++)
                {
                    double angle = i * Math.PI / 4d;
                    context.DrawLine(
                        pen,
                        new Point(12 + (Math.Cos(angle) * 5.3), 12 + (Math.Sin(angle) * 5.3)),
                        new Point(12 + (Math.Cos(angle) * 7.6), 12 + (Math.Sin(angle) * 7.6)));
                }

                break;
        }
    }
}
