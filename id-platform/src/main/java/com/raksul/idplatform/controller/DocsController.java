package com.raksul.idplatform.controller;

import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class DocsController {

    private final ResourceLoader resourceLoader;

    public DocsController(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    private static final Map<String, String> DOC_TITLES = new LinkedHashMap<>();

    static {
        DOC_TITLES.put("assignment-response", "Assignment Response");
        DOC_TITLES.put("readme", "Project README");
        DOC_TITLES.put("requirements", "Requirements Gathering");
        DOC_TITLES.put("design-decisions", "Design Decisions");
        DOC_TITLES.put("test-plan", "Test Plan");
        DOC_TITLES.put("scaling-strategy", "Scaling Strategy");
        DOC_TITLES.put("security-review", "Security Review");
        DOC_TITLES.put("api-contract", "API Contract Specification");
        DOC_TITLES.put("operational-runbook", "Operational Runbook");
    }

    @GetMapping(value = "/docs/{name}", produces = MediaType.TEXT_HTML_VALUE)
    public String getDoc(@PathVariable String name) throws IOException {
        if (!DOC_TITLES.containsKey(name)) {
            return errorPage("Document not found: " + name);
        }
        String md;
        try {
            var resource = resourceLoader.getResource("classpath:static/docs/" + name + ".md");
            md = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return errorPage("Could not load document: " + name);
        }
        return wrapHtml(DOC_TITLES.get(name), markdownToHtml(md), name);
    }

    private String wrapHtml(String title, String bodyHtml, String currentDoc) {
        StringBuilder nav = new StringBuilder();
        for (var entry : DOC_TITLES.entrySet()) {
            String active = entry.getKey().equals(currentDoc) ? "font-weight:700;color:#38bdf8;" : "";
            nav.append("<a href=\"/docs/").append(entry.getKey()).append("\" style=\"display:block;padding:8px 16px;color:#94a3b8;text-decoration:none;font-size:13px;border-left:3px solid ").append(entry.getKey().equals(currentDoc) ? "#38bdf8" : "transparent").append(";").append(active).append("\">").append(entry.getValue()).append("</a>");
        }

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>%s — Raksul ID Platform Docs</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#0f172a;color:#e2e8f0;display:flex;min-height:100vh}
.nav{width:240px;background:#1e293b;border-right:1px solid #334155;padding:20px 0;flex-shrink:0;position:fixed;height:100vh;overflow-y:auto}
.nav h2{padding:0 16px 12px;font-size:14px;color:#f8fafc;border-bottom:1px solid #334155;margin-bottom:8px}
.nav a:hover{background:#334155;color:#e2e8f0}
.content{margin-left:240px;flex:1;padding:32px 48px;max-width:900px;line-height:1.7}
h1{font-size:28px;color:#f8fafc;margin-bottom:16px;padding-bottom:12px;border-bottom:2px solid #334155}
h2{font-size:20px;color:#38bdf8;margin:28px 0 12px;padding-bottom:8px;border-bottom:1px solid #334155}
h3{font-size:16px;color:#a78bfa;margin:20px 0 8px}
h4{font-size:14px;color:#f59e0b;margin:16px 0 6px}
p{margin:8px 0;color:#cbd5e1;font-size:14px}
table{width:100%%;border-collapse:collapse;margin:12px 0}
th,td{padding:10px 14px;text-align:left;border-bottom:1px solid #334155;font-size:13px}
th{color:#64748b;font-weight:600;background:#1e293b}
td{color:#cbd5e1}
code{background:#1e293b;padding:2px 6px;border-radius:4px;font-size:12px;color:#a5f3fc;font-family:'Fira Code',monospace}
pre{background:#1e293b;border:1px solid #334155;border-radius:8px;padding:16px;margin:12px 0;overflow-x:auto}
pre code{background:none;padding:0;color:#a5f3fc}
ul,ol{margin:8px 0 8px 24px;color:#cbd5e1;font-size:14px}
li{margin:4px 0}
strong{color:#f8fafc}
hr{border:none;border-top:1px solid #334155;margin:24px 0}
.back{display:inline-block;margin-bottom:16px;color:#38bdf8;text-decoration:none;font-size:13px}
.back:hover{text-decoration:underline}
</style>
</head>
<body>
<div class="nav">
<h2>Documentation</h2>
%s
<div style="padding:16px;margin-top:16px;border-top:1px solid #334155">
<a href="/" style="display:block;padding:8px 16px;color:#38bdf8;text-decoration:none;font-size:13px">← Back to Dashboard</a>
</div>
</div>
<div class="content">
<a href="/" class="back">← Back to Dashboard</a>
%s
</div>
</body>
</html>""".formatted(title, nav, bodyHtml);
    }

    private String errorPage(String message) {
        return """
<!DOCTYPE html>
<html><head><meta charset="UTF-8"><title>Error</title>
<style>body{font-family:sans-serif;background:#0f172a;color:#e2e8f0;display:flex;justify-content:center;align-items:center;min-height:100vh}
.box{background:#1e293b;border:1px solid #334155;border-radius:12px;padding:40px;text-align:center}
h1{color:#f87171;margin-bottom:12px}p{color:#94a3b8}a{color:#38bdf8}</style></head>
<body><div class="box"><h1>Error</h1><p>%s</p><p style="margin-top:16px"><a href="/docs/requirements">View Documentation</a></p></div></body></html>""".formatted(message);
    }

    private String markdownToHtml(String md) {
        StringBuilder html = new StringBuilder();
        String[] lines = md.split("\n");
        boolean inCodeBlock = false;
        boolean inTable = false;
        boolean inList = false;
        boolean inOrderedList = false;

        for (String line : lines) {
            String trimmed = line.strip();

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    html.append("</code></pre>");
                    inCodeBlock = false;
                } else {
                    if (inTable) { html.append("</table>"); inTable = false; }
                    if (inList) { html.append("</ul>"); inList = false; }
                    if (inOrderedList) { html.append("</ol>"); inOrderedList = false; }
                    html.append("<pre><code>");
                    inCodeBlock = true;
                }
                continue;
            }

            if (inCodeBlock) {
                html.append(escapeHtml(line)).append("\n");
                continue;
            }

            if (trimmed.isEmpty()) {
                if (inTable) { html.append("</table>"); inTable = false; }
                if (inList) { html.append("</ul>"); inList = false; }
                if (inOrderedList) { html.append("</ol>"); inOrderedList = false; }
                continue;
            }

            if (trimmed.startsWith("|")) {
                if (!inTable) {
                    if (inList) { html.append("</ul>"); inList = false; }
                    if (inOrderedList) { html.append("</ol>"); inOrderedList = false; }
                    html.append("<table>");
                    inTable = true;
                }
                String[] cells = trimmed.split("\\|");
                if (cells.length > 1 && cells[1].strip().matches("-+")) continue;
                html.append("<tr>");
                for (int i = 1; i < cells.length - 1; i++) {
                    html.append("<td>").append(formatInline(cells[i].strip())).append("</td>");
                }
                html.append("</tr>");
                continue;
            } else if (inTable) {
                html.append("</table>");
                inTable = false;
            }

            if (trimmed.startsWith("### ")) {
                if (inList) { html.append("</ul>"); inList = false; }
                if (inOrderedList) { html.append("</ol>"); inOrderedList = false; }
                html.append("<h3>").append(formatInline(trimmed.substring(4))).append("</h3>");
            } else if (trimmed.startsWith("## ")) {
                if (inList) { html.append("</ul>"); inList = false; }
                if (inOrderedList) { html.append("</ol>"); inOrderedList = false; }
                html.append("<h2>").append(formatInline(trimmed.substring(3))).append("</h2>");
            } else if (trimmed.startsWith("# ")) {
                if (inList) { html.append("</ul>"); inList = false; }
                if (inOrderedList) { html.append("</ol>"); inOrderedList = false; }
                html.append("<h1>").append(formatInline(trimmed.substring(2))).append("</h1>");
            } else if (trimmed.startsWith("---")) {
                html.append("<hr>");
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                if (!inList) {
                    if (inOrderedList) { html.append("</ol>"); inOrderedList = false; }
                    html.append("<ul>");
                    inList = true;
                }
                html.append("<li>").append(formatInline(trimmed.substring(2))).append("</li>");
            } else if (trimmed.matches("\\d+\\..*")) {
                if (!inOrderedList) {
                    if (inList) { html.append("</ul>"); inList = false; }
                    html.append("<ol>");
                    inOrderedList = true;
                }
                String content = trimmed.replaceFirst("\\d+\\.\\s*", "");
                html.append("<li>").append(formatInline(content)).append("</li>");
            } else {
                if (inList) { html.append("</ul>"); inList = false; }
                if (inOrderedList) { html.append("</ol>"); inOrderedList = false; }
                html.append("<p>").append(formatInline(trimmed)).append("</p>");
            }
        }

        if (inTable) html.append("</table>");
        if (inList) html.append("</ul>");
        if (inOrderedList) html.append("</ol>");
        if (inCodeBlock) html.append("</code></pre>");

        return html.toString();
    }

    private String formatInline(String text) {
        text = text.replaceAll("!\\[([^]]*)\\]\\(([^)]+)\\)", "<img src=\"$2\" alt=\"$1\" style=\"max-width:100%;border-radius:8px;border:1px solid #334155;margin:12px 0;display:block\" />");
        text = text.replaceAll("`([^`]+)`", "<code>$1</code>");
        text = text.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
        text = text.replaceAll("\\*([^*]+)\\*", "<em>$1</em>");
        text = text.replaceAll("\\[([^]]+)]\\(([^)]+)\\)", "<a href=\"$2\" style=\"color:#38bdf8\">$1</a>");
        return text;
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
