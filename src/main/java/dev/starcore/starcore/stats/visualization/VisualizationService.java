package dev.starcore.starcore.stats.visualization;

import java.util.*;

/**
 * 数据可视化服务
 *
 * 提供ASCII艺术图表、进度条、统计图形等可视化输出
 */
public class VisualizationService {

    // ASCII 字符
    private static final char BLOCK = '█';
    private static final char HALF_BLOCK = '▄';
    private static final char QUARTER_BLOCK = '▂';
    private static final char[] GRAPH_CHARS = {'█', '▄', '▂', '▀'};

    // 颜色代码 (控制台ANSI)
    private static final String RESET = "\033[0m";
    private static final String RED = "\033[31m";
    private static final String GREEN = "\033[32m";
    private static final String YELLOW = "\033[33m";
    private static final String BLUE = "\033[34m";
    private static final String MAGENTA = "\033[35m";
    private static final String CYAN = "\033[36m";

    /**
     * 生成水平条形图
     */
    public String generateHorizontalBar(long current, long max, int width) {
        return generateHorizontalBar(current, max, width, BAR_STYLE.SOLID);
    }

    /**
     * 生成水平条形图
     */
    public String generateHorizontalBar(long current, long max, int width, BAR_STYLE style) {
        if (max <= 0) max = 1;
        double ratio = Math.min(1.0, (double) current / max);
        int filled = (int) (ratio * width);

        StringBuilder sb = new StringBuilder();
        String fillChar = switch (style) {
            case SOLID -> "█";
            case DASHED -> "─";
            case DOTTED -> "·";
            case STAR -> "★";
        };

        sb.append("[");
        for (int i = 0; i < width; i++) {
            if (i < filled) {
                sb.append(fillChar);
            } else if (i == filled && ratio > 0 && ratio < 1) {
                sb.append("▐"); // 右半格
            } else {
                sb.append(" ");
            }
        }
        sb.append("] ");
        sb.append(String.format("%.1f%%", ratio * 100));

        return sb.toString();
    }

    /**
     * 生成垂直条形图 (ASCII art)
     */
    public String generateVerticalBarChart(Map<String, Long> data, int maxHeight, int maxWidth) {
        if (data == null || data.isEmpty()) {
            return "No data available";
        }

        // 找出最大值用于归一化
        long maxValue = data.values().stream().max(Long::compare).orElse(1L);

        // 排序数据
        List<Map.Entry<String, Long>> sorted = data.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(maxWidth)
            .toList();

        StringBuilder sb = new StringBuilder();
        String[] yLabels = {"100%", " 75%", " 50%", " 25%", "  0%"};

        // Y轴标签和条形
        for (int h = maxHeight; h >= 0; h--) {
            String yLabel = h < yLabels.length ? yLabels[h] : "";
            sb.append(yLabel).append(" |");

            for (Map.Entry<String, Long> entry : sorted) {
                long barHeight = (entry.getValue() * maxHeight) / maxValue;
                if (barHeight > h) {
                    sb.append("  ").append(BLOCK).append(BLOCK);
                } else {
                    sb.append("     ");
                }
            }
            sb.append("\n");
        }

        // X轴
        sb.append("     +");
        for (int i = 0; i < sorted.size(); i++) {
            sb.append("-----");
        }
        sb.append("\n");

        // X轴标签 (截断过长名称)
        sb.append("      ");
        for (Map.Entry<String, Long> entry : sorted) {
            String label = truncate(entry.getKey(), 4);
            sb.append(" ").append(label).append(" ");
        }

        return sb.toString();
    }

    /**
     * 生成饼图 (ASCII art)
     */
    public String generatePieChart(Map<String, Long> data, int radius) {
        if (data == null || data.isEmpty()) {
            return "No data available";
        }

        long total = data.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) {
            return "Total is zero";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(" 饼图 (半径=").append(radius).append(")\n");

        // 计算每个类别的大小
        Map<String, Double> percentages = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : data.entrySet()) {
            percentages.put(entry.getKey(), (double) entry.getValue() / total * 100);
        }

        // 简化饼图 - 用字符表示各部分
        int width = radius * 2 + 2;
        int height = radius * 2 + 2;
        char[][] canvas = new char[height][width];

        // 填充空白
        for (char[] row : canvas) {
            Arrays.fill(row, ' ');
        }

        // 绘制圆周
        String[] segments = {"●", "○", "■", "□", "★", "☆", "◆", "◇"};
        int segmentIndex = 0;

        sb.append("\n");
        for (Map.Entry<String, Double> entry : percentages.entrySet()) {
            sb.append(segments[segmentIndex % segments.length])
              .append(" ")
              .append(entry.getKey())
              .append(": ")
              .append(String.format("%.1f%%", entry.getValue()))
              .append("\n");
            segmentIndex++;
        }

        return sb.toString();
    }

    /**
     * 生成Sparkline (迷你趋势线)
     */
    public String generateSparkline(List<Long> values, int width) {
        if (values == null || values.isEmpty()) {
            return "No data";
        }

        if (values.size() == 1) {
            return "▄".repeat(width);
        }

        long min = values.stream().min(Long::compare).orElse(0L);
        long max = values.stream().max(Long::compare).orElse(1L);
        long range = max - min;
        if (range == 0) range = 1;

        StringBuilder sb = new StringBuilder();
        int step = Math.max(1, values.size() / width);

        for (int i = 0; i < width && i * step < values.size(); i++) {
            long value = values.get(i * step);
            int level = (int) ((value - min) * 4 / range);
            level = Math.max(0, Math.min(3, level));

            sb.append(GRAPH_CHARS[3 - level]);
        }

        return sb.toString();
    }

    /**
     * 生成排名表
     */
    public String generateRankingTable(List<RankingEntry> entries, int rankColumnWidth, int valueColumnWidth) {
        if (entries == null || entries.isEmpty()) {
            return "No data available";
        }

        StringBuilder sb = new StringBuilder();
        String line = "=".repeat(rankColumnWidth + valueColumnWidth + 3);

        sb.append(line).append("\n");
        sb.append("排名").append(" ".repeat(rankColumnWidth - 2))
          .append("| ")
          .append("数值").append(" ".repeat(valueColumnWidth - 2))
          .append("\n");
        sb.append(line).append("\n");

        for (RankingEntry entry : entries) {
            String rankStr = "#" + entry.rank();
            String valueStr = formatValue(entry.value());

            sb.append(rankStr)
              .append(" ".repeat(Math.max(1, rankColumnWidth - rankStr.length())))
              .append("| ")
              .append(valueStr)
              .append(" ".repeat(Math.max(1, valueColumnWidth - valueStr.length())))
              .append("\n");
        }

        sb.append(line);
        return sb.toString();
    }

    /**
     * 生成对比条形图
     */
    public String generateComparisonBar(String label, long value1, long value2, int width, String label1, String label2) {
        long max = Math.max(value1, value2);
        if (max == 0) max = 1;

        StringBuilder sb = new StringBuilder();
        sb.append(label).append("\n");

        // 值1
        int len1 = (int) ((double) value1 / max * width);
        sb.append("  ").append(label1).append(": ");
        sb.append(GREEN);
        sb.append(String.valueOf(BLOCK).repeat(len1));
        sb.append(RESET);
        sb.append(" ").append(value1).append("\n");

        // 值2
        int len2 = (int) ((double) value2 / max * width);
        sb.append("  ").append(label2).append(": ");
        sb.append(BLUE);
        sb.append(String.valueOf(BLOCK).repeat(len2));
        sb.append(RESET);
        sb.append(" ").append(value2).append("\n");

        return sb.toString();
    }

    /**
     * 生成统计卡片
     */
    public String generateStatCard(String title, Map<String, Object> stats) {
        StringBuilder sb = new StringBuilder();
        int width = 40;

        String border = "+" + "-".repeat(width - 2) + "+";

        sb.append(border).append("\n");
        sb.append("|").append(center(title, width - 2)).append("|\n");
        sb.append(border).append("\n");

        for (Map.Entry<String, Object> entry : stats.entrySet()) {
            String line = "  " + entry.getKey() + ": " + entry.getValue();
            sb.append("|").append(truncate(line, width - 4).concat(" ".repeat(Math.max(0, width - 4 - line.length())))).append("|\n");
        }

        sb.append(border);
        return sb.toString();
    }

    /**
     * 生成彩色进度指示器
     */
    public String generateProgressIndicator(long current, long target) {
        if (target <= 0) target = 1;
        double percent = (double) current / target * 100;

        String color;
        if (percent >= 100) {
            color = GREEN;
        } else if (percent >= 75) {
            color = CYAN;
        } else if (percent >= 50) {
            color = YELLOW;
        } else if (percent >= 25) {
            color = MAGENTA;
        } else {
            color = RED;
        }

        return color + String.format("[%-10s] %.1f%%", String.valueOf(BLOCK).repeat((int)(percent / 10)), percent) + RESET;
    }

    /**
     * 生成趋势箭头
     */
    public String generateTrendArrow(double changePercent) {
        if (changePercent > 5) {
            return GREEN + "↑" + RESET + " +" + String.format("%.1f%%", changePercent);
        } else if (changePercent < -5) {
            return RED + "↓" + RESET + " " + String.format("%.1f%%", changePercent);
        } else {
            return YELLOW + "↔" + RESET + " " + String.format("%.1f%%", changePercent);
        }
    }

    /**
     * 生成迷你仪表盘
     */
    public String generateMiniDashboard(Map<String, Long> metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== 实时数据仪表盘 ===\n\n");

        int maxLabelLen = metrics.keySet().stream()
            .mapToInt(String::length)
            .max()
            .orElse(10);

        int barWidth = 20;

        for (Map.Entry<String, Long> entry : metrics.entrySet()) {
            String label = entry.getKey();
            Long value = entry.getValue();

            sb.append(label)
              .append(":")
              .append(" ".repeat(Math.max(1, maxLabelLen - label.length() + 2)));

            // 简化条形图
            long maxVal = metrics.values().stream().max(Long::compare).orElse(1L);
            int barLen = (int) ((double) value / maxVal * barWidth);
            sb.append(BLUE).append(String.valueOf(BLOCK).repeat(barLen)).append(RESET);
            sb.append(" ").append(formatValue(value)).append("\n");
        }

        return sb.toString();
    }

    // ==================== 辅助方法 ====================

    private String center(String text, int width) {
        int padding = Math.max(0, (width - text.length()) / 2);
        return " ".repeat(padding) + text + " ".repeat(Math.max(0, width - padding - text.length()));
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 1) + "…";
    }

    private String formatValue(long value) {
        if (value >= 1_000_000_000) {
            return String.format("%.1fB", value / 1_000_000_000.0);
        } else if (value >= 1_000_000) {
            return String.format("%.1fM", value / 1_000_000.0);
        } else if (value >= 1_000) {
            return String.format("%.1fK", value / 1_000.0);
        }
        return String.valueOf(value);
    }

    // ==================== 内部类 ====================

    /**
     * 条形图样式
     */
    public enum BAR_STYLE {
        SOLID,    // 实心方块
        DASHED,   // 虚线
        DOTTED,   // 点线
        STAR      // 星号
    }

    /**
     * 排名条目
     */
    public record RankingEntry(int rank, String label, long value) {}

    /**
     * 图表数据点
     */
    public record ChartDataPoint(String label, long value, long timestamp) {}
}
