package com.reflectiontest.springReflectionTest.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Formatter for exporting test reports in different formats
 */
public class TestReportFormatter {
    private static final Logger logger = LoggerFactory.getLogger(TestReportFormatter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Formats a test report as JSON
     */
    public static String formatAsJson(TestReport report) {
        try {
            return objectMapper.writeValueAsString(report.toMap());
        } catch (Exception e) {
            logger.error("Error formatting report as JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Formats a test report as XML
     */
    public static String formatAsXml(TestReport report) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<testReport id=\"").append(report.getId()).append("\">\n");

        // Add times
        xml.append("  <startTime>").append(report.getStartTime()).append("</startTime>\n");
        xml.append("  <endTime>").append(report.getEndTime()).append("</endTime>\n");

        // Add metrics
        xml.append("  <metrics>\n");
        for (Map.Entry<String, Object> entry : report.getMetrics().entrySet()) {
            xml.append("    <metric name=\"").append(entry.getKey()).append("\">")
                    .append(entry.getValue()).append("</metric>\n");
        }
        xml.append("  </metrics>\n");

        // Add warnings
        if (!report.getWarnings().isEmpty()) {
            xml.append("  <warnings>\n");
            for (String warning : report.getWarnings()) {
                xml.append("    <warning>").append(escapeXml(warning)).append("</warning>\n");
            }
            xml.append("  </warnings>\n");
        }

        // Add results
        xml.append("  <results>\n");
        for (TestResult result : report.getResults()) {
            xml.append("    <result id=\"").append(result.getId()).append("\">\n");
            xml.append("      <className>").append(escapeXml(result.getClassName())).append("</className>\n");
            xml.append("      <methodName>").append(escapeXml(result.getMethodName())).append("</methodName>\n");
            xml.append("      <input>").append(escapeXml(result.getInput())).append("</input>\n");

            if (result.getExpected() != null) {
                xml.append("      <expected>").append(escapeXml(result.getExpected())).append("</expected>\n");
            }

            if (result.getActual() != null) {
                xml.append("      <actual>").append(escapeXml(result.getActual())).append("</actual>\n");
            }

            xml.append("      <status>").append(result.getStatus()).append("</status>\n");
            xml.append("      <executionTimeMs>").append(result.getExecutionTimeMs()).append("</executionTimeMs>\n");

            if (result.getErrorMessage() != null) {
                xml.append("      <errorMessage>").append(escapeXml(result.getErrorMessage())).append("</errorMessage>\n");
            }

            xml.append("      <timestamp>").append(result.getTimestamp()).append("</timestamp>\n");
            xml.append("    </result>\n");
        }
        xml.append("  </results>\n");

        xml.append("</testReport>");
        return xml.toString();
    }

    /**
     * Formats a test report as HTML
     */
    public static String formatAsHtml(TestReport report) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>Test Report - ").append(report.getId()).append("</title>\n");
        html.append("  <style>\n");
        html.append("    body { font-family: Arial, sans-serif; margin: 0; padding: 20px; }\n");
        html.append("    .header { background-color: #f0f0f0; padding: 10px; border-radius: 5px; margin-bottom: 20px; }\n");
        html.append("    .summary { display: flex; flex-wrap: wrap; margin-bottom: 20px; }\n");
        html.append("    .summary-item { margin-right: 20px; margin-bottom: 10px; }\n");
        html.append("    .summary-label { font-weight: bold; }\n");
        html.append("    .success { color: green; }\n");
        html.append("    .failure { color: red; }\n");
        html.append("    .error { color: orangered; }\n");
        html.append("    .skipped { color: gray; }\n");
        html.append("    table { border-collapse: collapse; width: 100%; }\n");
        html.append("    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
        html.append("    th { background-color: #f2f2f2; }\n");
        html.append("    tr:nth-child(even) { background-color: #f9f9f9; }\n");
        html.append("    tr:hover { background-color: #f5f5f5; }\n");
        html.append("    .details { margin-top: 5px; font-size: 0.9em; }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");

        // Header
        html.append("  <div class=\"header\">\n");
        html.append("    <h1>Test Report</h1>\n");
        html.append("    <p>ID: ").append(report.getId()).append("</p>\n");
        html.append("    <p>Started: ").append(formatDateTime(report.getStartTime())).append("</p>\n");
        html.append("    <p>Ended: ").append(formatDateTime(report.getEndTime())).append("</p>\n");
        html.append("    <p>Duration: ").append(formatDuration(report.getStartTime(), report.getEndTime())).append("</p>\n");
        html.append("  </div>\n");

        // Summary
        html.append("  <div class=\"summary\">\n");
        Map<String, Object> metrics = report.getMetrics();

        int total = report.getResults().size();
        int passed = report.getResultsByStatus(TestStatus.SUCCESS).size();
        int failed = report.getResultsByStatus(TestStatus.FAILURE).size();
        int errors = report.getResultsByStatus(TestStatus.ERROR).size();
        int skipped = report.getResultsByStatus(TestStatus.SKIPPED).size();

        html.append("    <div class=\"summary-item\">\n");
        html.append("      <div class=\"summary-label\">Total Tests:</div>\n");
        html.append("      <div>").append(total).append("</div>\n");
        html.append("    </div>\n");

        html.append("    <div class=\"summary-item\">\n");
        html.append("      <div class=\"summary-label\">Passed:</div>\n");
        html.append("      <div class=\"success\">").append(passed);
        html.append(" (").append(String.format("%.1f%%", total > 0 ? (double) passed / total * 100 : 0)).append(")");
        html.append("</div>\n");
        html.append("    </div>\n");

        html.append("    <div class=\"summary-item\">\n");
        html.append("      <div class=\"summary-label\">Failed:</div>\n");
        html.append("      <div class=\"failure\">").append(failed).append("</div>\n");
        html.append("    </div>\n");

        html.append("    <div class=\"summary-item\">\n");
        html.append("      <div class=\"summary-label\">Errors:</div>\n");
        html.append("      <div class=\"error\">").append(errors).append("</div>\n");
        html.append("    </div>\n");

        html.append("    <div class=\"summary-item\">\n");
        html.append("      <div class=\"summary-label\">Skipped:</div>\n");
        html.append("      <div class=\"skipped\">").append(skipped).append("</div>\n");
        html.append("    </div>\n");

        if (metrics.containsKey("averageExecutionTimeMs")) {
            html.append("    <div class=\"summary-item\">\n");
            html.append("      <div class=\"summary-label\">Average Test Time:</div>\n");
            html.append("      <div>").append(String.format("%.2f ms", metrics.get("averageExecutionTimeMs"))).append("</div>\n");
            html.append("    </div>\n");
        }

        html.append("  </div>\n");

        // Warnings
        if (!report.getWarnings().isEmpty()) {
            html.append("  <div class=\"warnings\">\n");
            html.append("    <h2>Warnings</h2>\n");
            html.append("    <ul>\n");
            for (String warning : report.getWarnings()) {
                html.append("      <li>").append(escapeHtml(warning)).append("</li>\n");
            }
            html.append("    </ul>\n");
            html.append("  </div>\n");
        }

        // Results
        html.append("  <div class=\"results\">\n");
        html.append("    <h2>Test Results</h2>\n");
        html.append("    <table>\n");
        html.append("      <tr>\n");
        html.append("        <th>Class</th>\n");
        html.append("        <th>Method</th>\n");
        html.append("        <th>Input</th>\n");
        html.append("        <th>Status</th>\n");
        html.append("        <th>Time (ms)</th>\n");
        html.append("        <th>Details</th>\n");
        html.append("      </tr>\n");

        for (TestResult result : report.getResults()) {
            String statusClass = "";
            switch (result.getStatus()) {
                case SUCCESS: statusClass = "success"; break;
                case FAILURE: statusClass = "failure"; break;
                case ERROR: statusClass = "error"; break;
                case SKIPPED: statusClass = "skipped"; break;
            }

            html.append("      <tr>\n");
            html.append("        <td>").append(escapeHtml(result.getClassName())).append("</td>\n");
            html.append("        <td>").append(escapeHtml(result.getMethodName())).append("</td>\n");
            html.append("        <td>").append(escapeHtml(truncate(result.getInput(), 50))).append("</td>\n");
            html.append("        <td class=\"").append(statusClass).append("\">").append(result.getStatus()).append("</td>\n");
            html.append("        <td>").append(result.getExecutionTimeMs()).append("</td>\n");
            html.append("        <td>\n");

            if (result.getStatus() == TestStatus.FAILURE) {
                html.append("          <div class=\"details\">\n");
                html.append("            <div><strong>Expected:</strong> ").append(escapeHtml(truncate(result.getExpected(), 100))).append("</div>\n");
                html.append("            <div><strong>Actual:</strong> ").append(escapeHtml(truncate(result.getActual(), 100))).append("</div>\n");
                html.append("          </div>\n");
            } else if (result.getStatus() == TestStatus.ERROR && result.getErrorMessage() != null) {
                html.append("          <div class=\"details\">\n");
                html.append("            <div><strong>Error:</strong> ").append(escapeHtml(result.getErrorMessage())).append("</div>\n");
                html.append("          </div>\n");
            }

            html.append("        </td>\n");
            html.append("      </tr>\n");
        }

        html.append("    </table>\n");
        html.append("  </div>\n");

        html.append("</body>\n");
        html.append("</html>");

        return html.toString();
    }

    /**
     * Formats a test report as Markdown
     */
    public static String formatAsMarkdown(TestReport report) {
        StringBuilder md = new StringBuilder();
        md.append("# Test Report\n\n");

        // Header
        md.append("## Summary\n\n");
        md.append("- **ID:** ").append(report.getId()).append("\n");
        md.append("- **Started:** ").append(formatDateTime(report.getStartTime())).append("\n");
        md.append("- **Ended:** ").append(formatDateTime(report.getEndTime())).append("\n");
        md.append("- **Duration:** ").append(formatDuration(report.getStartTime(), report.getEndTime())).append("\n\n");

        // Metrics
        Map<String, Object> metrics = report.getMetrics();

        int total = report.getResults().size();
        int passed = report.getResultsByStatus(TestStatus.SUCCESS).size();
        int failed = report.getResultsByStatus(TestStatus.FAILURE).size();
        int errors = report.getResultsByStatus(TestStatus.ERROR).size();
        int skipped = report.getResultsByStatus(TestStatus.SKIPPED).size();

        md.append("## Results\n\n");
        md.append("- **Total Tests:** ").append(total).append("\n");
        md.append("- **Passed:** ").append(passed);
        md.append(" (").append(String.format("%.1f%%", total > 0 ? (double) passed / total * 100 : 0)).append(")");
        md.append("\n");
        md.append("- **Failed:** ").append(failed).append("\n");
        md.append("- **Errors:** ").append(errors).append("\n");
        md.append("- **Skipped:** ").append(skipped).append("\n");

        if (metrics.containsKey("averageExecutionTimeMs")) {
            md.append("- **Average Test Time:** ")
                    .append(String.format("%.2f ms", metrics.get("averageExecutionTimeMs")))
                    .append("\n");
        }

        md.append("\n");

        // Warnings
        if (!report.getWarnings().isEmpty()) {
            md.append("## Warnings\n\n");
            for (String warning : report.getWarnings()) {
                md.append("- ").append(warning).append("\n");
            }
            md.append("\n");
        }

        // Failed Tests
        if (failed > 0) {
            md.append("## Failed Tests\n\n");
            for (TestResult result : report.getResultsByStatus(TestStatus.FAILURE)) {
                md.append("### ").append(result.getClassName()).append(".").append(result.getMethodName()).append("\n\n");
                md.append("- **Input:** `").append(result.getInput()).append("`\n");
                md.append("- **Expected:** `").append(result.getExpected()).append("`\n");
                md.append("- **Actual:** `").append(result.getActual()).append("`\n");
                md.append("- **Time:** ").append(result.getExecutionTimeMs()).append(" ms\n\n");
            }
        }

        // Errors
        if (errors > 0) {
            md.append("## Errors\n\n");
            for (TestResult result : report.getResultsByStatus(TestStatus.ERROR)) {
                md.append("### ").append(result.getClassName()).append(".").append(result.getMethodName()).append("\n\n");
                md.append("- **Input:** `").append(result.getInput()).append("`\n");
                if (result.getErrorMessage() != null) {
                    md.append("- **Error:** ").append(result.getErrorMessage()).append("\n");
                }
                md.append("- **Time:** ").append(result.getExecutionTimeMs()).append(" ms\n\n");
            }
        }

        // All Tests
        md.append("## All Tests\n\n");
        md.append("| Class | Method | Input | Status | Time (ms) |\n");
        md.append("|-------|--------|-------|--------|----------|\n");

        for (TestResult result : report.getResults()) {
            String status = result.getStatus().toString();
            String truncatedInput = truncate(result.getInput(), 50);

            md.append("| ")
                    .append(result.getClassName()).append(" | ")
                    .append(result.getMethodName()).append(" | ")
                    .append("`").append(truncatedInput).append("` | ")
                    .append(status).append(" | ")
                    .append(result.getExecutionTimeMs()).append(" |\n");
        }

        return md.toString();
    }

    /**
     * Formats a date time for display
     */
    private static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "N/A";
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Formats the duration between two date times
     */
    private static String formatDuration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return "N/A";

        long seconds = java.time.Duration.between(start, end).getSeconds();
        return String.format("%d.%03d seconds", seconds, (java.time.Duration.between(start, end).toMillis() % 1000));
    }

    /**
     * Escapes a string for XML
     */
    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Escapes a string for HTML
     */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Truncates a string to the specified length
     */
    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}