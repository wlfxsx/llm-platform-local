using Avalonia;
using Avalonia.Controls;
using Avalonia.Media;
using Avalonia.Media.Immutable;

namespace LlmPlatform.Desktop.Controls;

/// <summary>轻量实时折线，避免引入额外图表依赖。</summary>
public sealed class RealtimeChart : Control
{
    public static readonly StyledProperty<IList<double>?> ValuesProperty =
        AvaloniaProperty.Register<RealtimeChart, IList<double>?>(nameof(Values));

    public static readonly StyledProperty<int> RevisionProperty =
        AvaloniaProperty.Register<RealtimeChart, int>(nameof(Revision));

    public static readonly StyledProperty<string> UnitProperty =
        AvaloniaProperty.Register<RealtimeChart, string>(nameof(Unit), string.Empty);

    public static readonly StyledProperty<string> CaptionProperty =
        AvaloniaProperty.Register<RealtimeChart, string>(nameof(Caption), string.Empty);

    public static readonly StyledProperty<string> EmptyTextProperty =
        AvaloniaProperty.Register<RealtimeChart, string>(nameof(EmptyText), string.Empty);

    static RealtimeChart()
    {
        AffectsRender<RealtimeChart>(ValuesProperty, RevisionProperty, UnitProperty, CaptionProperty, EmptyTextProperty, BoundsProperty);
        MinHeightProperty.OverrideDefaultValue<RealtimeChart>(88d);
    }

    public IList<double>? Values
    {
        get => GetValue(ValuesProperty);
        set => SetValue(ValuesProperty, value);
    }

    public int Revision
    {
        get => GetValue(RevisionProperty);
        set => SetValue(RevisionProperty, value);
    }

    public string Unit
    {
        get => GetValue(UnitProperty);
        set => SetValue(UnitProperty, value);
    }

    public string Caption
    {
        get => GetValue(CaptionProperty);
        set => SetValue(CaptionProperty, value);
    }

    public string EmptyText
    {
        get => GetValue(EmptyTextProperty);
        set => SetValue(EmptyTextProperty, value);
    }

    public override void Render(DrawingContext context)
    {
        Rect bounds = new(0, 0, Bounds.Width, Bounds.Height);
        if (bounds.Width <= 1 || bounds.Height <= 1)
        {
            return;
        }

        IBrush stroke = new ImmutableSolidColorBrush(Color.FromRgb(96, 160, 255));
        if (this.TryFindResource("AccentBrush", out object? accent) && accent is IBrush accentBrush)
        {
            stroke = accentBrush;
        }

        IBrush textBrush = Brushes.Gray;
        if (this.TryFindResource("TextSecondaryBrush", out object? text) && text is IBrush resolvedText)
        {
            textBrush = resolvedText;
        }

        IList<double>? values = Values;
        if (values is null || values.Count == 0)
        {
            DrawLabel(context, EmptyText, bounds, textBrush);
            return;
        }

        double max = Math.Max(1, values.Max());
        double min = Math.Min(0, values.Min());
        double range = Math.Max(0.001, max - min);
        double left = 8;
        double right = bounds.Width - 8;
        double top = 22;
        double bottom = bounds.Height - 8;
        StreamGeometry geometry = new();
        using (StreamGeometryContext geo = geometry.Open())
        {
            for (int i = 0; i < values.Count; i++)
            {
                double x = values.Count == 1 ? left : left + ((right - left) * i / (values.Count - 1));
                double y = bottom - ((values[i] - min) / range * (bottom - top));
                Point point = new(x, y);
                if (i == 0)
                {
                    geo.BeginFigure(point, false);
                }
                else
                {
                    geo.LineTo(point);
                }
            }
        }

        context.DrawGeometry(null, new Pen(stroke, 1.8), geometry);
        double current = values[^1];
        string caption = string.IsNullOrWhiteSpace(Caption) ? current.ToString("0.##") : Caption;
        if (!string.IsNullOrWhiteSpace(Unit))
        {
            caption += " " + Unit;
        }

        DrawLabel(context, caption, new Rect(8, 4, bounds.Width - 16, 18), textBrush);
    }

    private static void DrawLabel(DrawingContext context, string text, Rect area, IBrush brush)
    {
        if (string.IsNullOrWhiteSpace(text))
        {
            return;
        }

        FormattedText formatted = new(
            text,
            System.Globalization.CultureInfo.CurrentCulture,
            FlowDirection.LeftToRight,
            new Typeface("Segoe UI"),
            12,
            brush);
        context.DrawText(formatted, new Point(area.X, area.Y));
    }
}
