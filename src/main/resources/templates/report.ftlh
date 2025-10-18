<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Security Analysis Report - ${scanId}</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
            line-height: 1.6;
            color: #333;
            background: #f5f7fa;
        }

        .container {
            max-width: 1400px;
            margin: 0 auto;
            padding: 20px;
        }

        /* Header */
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 40px 20px;
            border-radius: 12px;
            margin-bottom: 30px;
            box-shadow: 0 10px 30px rgba(102, 126, 234, 0.3);
        }

        .header h1 {
            font-size: 2.5em;
            margin-bottom: 10px;
            font-weight: 700;
        }

        .header .subtitle {
            font-size: 1.1em;
            opacity: 0.9;
        }

        .header .meta {
            margin-top: 20px;
            display: flex;
            gap: 30px;
            flex-wrap: wrap;
            font-size: 0.95em;
        }

        .header .meta-item {
            display: flex;
            align-items: center;
            gap: 8px;
        }

        /* Stats Cards */
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }

        .stat-card {
            background: white;
            padding: 25px;
            border-radius: 12px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.08);
            border-left: 4px solid #667eea;
            transition: transform 0.2s, box-shadow 0.2s;
        }

        .stat-card:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 20px rgba(0,0,0,0.12);
        }

        .stat-card.critical {
            border-left-color: #ef4444;
        }

        .stat-card.high {
            border-left-color: #f97316;
        }

        .stat-card.medium {
            border-left-color: #f59e0b;
        }

        .stat-card.low {
            border-left-color: #10b981;
        }

        .stat-card .label {
            font-size: 0.9em;
            color: #6b7280;
            margin-bottom: 8px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .stat-card .value {
            font-size: 2.5em;
            font-weight: 700;
            color: #1f2937;
        }

        .stat-card .icon {
            font-size: 1.5em;
            margin-right: 8px;
        }

        /* Severity Badge */
        .severity-badge {
            display: inline-block;
            padding: 6px 12px;
            border-radius: 20px;
            font-size: 0.85em;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .severity-critical {
            background: #fee2e2;
            color: #991b1b;
        }

        .severity-high {
            background: #ffedd5;
            color: #9a3412;
        }

        .severity-medium {
            background: #fef3c7;
            color: #92400e;
        }

        .severity-low {
            background: #d1fae5;
            color: #065f46;
        }

        .severity-info {
            background: #dbeafe;
            color: #1e40af;
        }

        /* Section */
        .section {
            background: white;
            padding: 30px;
            border-radius: 12px;
            margin-bottom: 30px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.08);
        }

        .section-title {
            font-size: 1.8em;
            font-weight: 700;
            margin-bottom: 20px;
            color: #1f2937;
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .section-title .icon {
            font-size: 1.2em;
        }

        /* Issue Card */
        .issue-card {
            background: #f9fafb;
            border-left: 4px solid #d1d5db;
            padding: 20px;
            margin-bottom: 20px;
            border-radius: 8px;
            transition: all 0.2s;
        }

        .issue-card:hover {
            background: #f3f4f6;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }

        .issue-card.critical {
            border-left-color: #ef4444;
            background: #fef2f2;
        }

        .issue-card.high {
            border-left-color: #f97316;
            background: #fff7ed;
        }

        .issue-card.medium {
            border-left-color: #f59e0b;
            background: #fffbeb;
        }

        .issue-card.low {
            border-left-color: #10b981;
            background: #f0fdf4;
        }

        .issue-header {
            display: flex;
            justify-content: space-between;
            align-items: start;
            margin-bottom: 12px;
            gap: 20px;
        }

        .issue-title {
            font-size: 1.2em;
            font-weight: 600;
            color: #1f2937;
            flex: 1;
        }

        .issue-meta {
            display: flex;
            flex-wrap: wrap;
            gap: 15px;
            margin-bottom: 12px;
            font-size: 0.9em;
            color: #6b7280;
        }

        .issue-meta-item {
            display: flex;
            align-items: center;
            gap: 5px;
        }

        .issue-description {
            color: #374151;
            margin-bottom: 15px;
            line-height: 1.7;
        }

        /* AI Enhancement Box */
        .ai-box {
            background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
            border: 2px solid #0ea5e9;
            border-radius: 8px;
            padding: 15px;
            margin-top: 15px;
        }

        .ai-box-title {
            font-weight: 600;
            color: #0c4a6e;
            margin-bottom: 8px;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .ai-box-content {
            color: #0c4a6e;
            line-height: 1.7;
        }

        /* Confidence Badge */
        .confidence-badge {
            display: inline-block;
            padding: 4px 10px;
            border-radius: 12px;
            font-size: 0.85em;
            font-weight: 600;
        }

        .confidence-high {
            background: #d1fae5;
            color: #065f46;
        }

        .confidence-medium {
            background: #fef3c7;
            color: #92400e;
        }

        .confidence-low {
            background: #fee2e2;
            color: #991b1b;
        }

        /* Code Block */
        .code-block {
            background: #1f2937;
            color: #e5e7eb;
            padding: 15px;
            border-radius: 8px;
            overflow-x: auto;
            margin-top: 15px;
            font-family: 'Courier New', Courier, monospace;
            font-size: 0.9em;
            line-height: 1.5;
        }

        .code-block code {
            white-space: pre;
        }

        /* Progress Bar */
        .progress-bar {
            height: 30px;
            background: #e5e7eb;
            border-radius: 15px;
            overflow: hidden;
            margin: 15px 0;
        }

        .progress-fill {
            height: 100%;
            background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
            display: flex;
            align-items: center;
            justify-content: center;
            color: white;
            font-weight: 600;
            font-size: 0.9em;
            transition: width 0.5s ease;
        }

        /* Footer */
        .footer {
            text-align: center;
            padding: 30px;
            color: #6b7280;
            font-size: 0.9em;
        }

        .footer strong {
            color: #667eea;
        }

        /* Responsive */
        @media (max-width: 768px) {
            .header h1 {
                font-size: 2em;
            }

            .stats-grid {
                grid-template-columns: 1fr;
            }

            .issue-header {
                flex-direction: column;
            }

            .issue-meta {
                flex-direction: column;
                gap: 8px;
            }
        }

        /* No Issues Message */
        .no-issues {
            text-align: center;
            padding: 60px 20px;
            color: #10b981;
        }

        .no-issues .icon {
            font-size: 4em;
            margin-bottom: 20px;
        }

        .no-issues h2 {
            font-size: 2em;
            margin-bottom: 10px;
        }
    </style>
</head>
<body>
    <div class="container">
        <!-- Header -->
        <div class="header">
            <h1>🛡️ Security Analysis Report</h1>
            <div class="subtitle">HarmonySafe Agent - AI-Powered Code Security Analysis</div>
            <div class="meta">
                <div class="meta-item">
                    <span>📁</span>
                    <span><strong>Source:</strong> ${sourcePath}</span>
                </div>
                <div class="meta-item">
                    <span>🔍</span>
                    <span><strong>Scan ID:</strong> ${scanId?substring(0, 8)}</span>
                </div>
                <div class="meta-item">
                    <span>⏱️</span>
                    <span><strong>Duration:</strong> ${duration.toSeconds()}s</span>
                </div>
                <div class="meta-item">
                    <span>📅</span>
                    <span><strong>Completed:</strong> ${endTime?string["yyyy-MM-dd HH:mm:ss"]}</span>
                </div>
            </div>
        </div>

        <!-- Stats Overview -->
        <div class="stats-grid">
            <div class="stat-card">
                <div class="label">Total Issues</div>
                <div class="value">${totalIssues}</div>
            </div>

            <div class="stat-card critical">
                <div class="label">Critical</div>
                <div class="value">${(severityCounts['CRITICAL']!0)?c}</div>
            </div>

            <div class="stat-card high">
                <div class="label">High</div>
                <div class="value">${(severityCounts['HIGH']!0)?c}</div>
            </div>

            <div class="stat-card medium">
                <div class="label">Medium</div>
                <div class="value">${(severityCounts['MEDIUM']!0)?c}</div>
            </div>

            <div class="stat-card low">
                <div class="label">Low</div>
                <div class="value">${(severityCounts['LOW']!0)?c}</div>
            </div>

            <div class="stat-card">
                <div class="label">🤖 AI Validated</div>
                <div class="value">${aiValidatedCount?c}</div>
            </div>
        </div>

        <!-- AI Enhancement Stats -->
        <#if (aiValidatedCount > 0)>
        <div class="section">
            <div class="section-title">
                <span class="icon">🤖</span>
                AI Enhancement Summary
            </div>
            <p style="margin-bottom: 15px; color: #6b7280;">
                AI analysis validated <strong>${aiValidatedCount}</strong> issues and filtered out
                <strong>${aiFilteredCount}</strong> false positives.
            </p>
            <div class="progress-bar">
                <div class="progress-fill" style="width: ${(aiValidatedCount * 100.0 / (totalIssues + aiFilteredCount))?string['0.0']}%">
                    ${(aiValidatedCount * 100.0 / (totalIssues + aiFilteredCount))?string['0.0']}% Validated
                </div>
            </div>
        </div>
        </#if>

        <!-- Issues List -->
        <div class="section">
            <div class="section-title">
                <span class="icon">⚠️</span>
                Detailed Issues (${totalIssues} found)
            </div>

            <#if (totalIssues == 0)>
                <div class="no-issues">
                    <div class="icon">✅</div>
                    <h2>No Security Issues Found!</h2>
                    <p>Your code passed all security checks.</p>
                </div>
            <#else>
                <#list issues as issue>
                    <div class="issue-card ${issue.severity?lower_case}">
                        <!-- Issue Header -->
                        <div class="issue-header">
                            <div class="issue-title">${issue.title}</div>
                            <span class="severity-badge severity-${issue.severity?lower_case}">
                                ${issue.severity}
                            </span>
                        </div>

                        <!-- Issue Metadata -->
                        <div class="issue-meta">
                            <div class="issue-meta-item">
                                <span>📁</span>
                                <span>${issue.location.filePath}:${issue.location.lineNumber}</span>
                            </div>
                            <div class="issue-meta-item">
                                <span>🏷️</span>
                                <span>${issue.category.displayName}</span>
                            </div>
                            <div class="issue-meta-item">
                                <span>🔧</span>
                                <span>${issue.analyzer}</span>
                            </div>

                            <!-- Confidence Badge -->
                            <#if issue.metadata['ai_confidence']??>
                                <#assign confidence = issue.metadata['ai_confidence']>
                                <div class="issue-meta-item">
                                    <span>🎯</span>
                                    <span class="confidence-badge <#if (confidence >= 0.9)>confidence-high<#elseif (confidence >= 0.7)>confidence-medium<#else>confidence-low</#if>">
                                        ${(confidence * 100)?string['0']}% Confidence
                                    </span>
                                </div>
                            </#if>
                        </div>

                        <!-- Issue Description -->
                        <div class="issue-description">
                            ${issue.description}
                        </div>

                        <!-- AI Explanation (if available) -->
                        <#if issue.metadata['ai_explanation']??>
                            <div class="ai-box">
                                <div class="ai-box-title">
                                    <span>🤖</span>
                                    <span>AI Expert Analysis</span>
                                </div>
                                <div class="ai-box-content">
                                    ${issue.metadata['ai_explanation']?replace('\n', '<br>')}
                                </div>
                            </div>
                        </#if>

                        <!-- Code Context (if available) -->
                        <#if issue.metadata['code_context']??>
                            <div class="code-block">
                                <code>${issue.metadata['code_context']?html}</code>
                            </div>
                        </#if>
                    </div>
                </#list>
            </#if>
        </div>

        <!-- Footer -->
        <div class="footer">
            Generated by <strong>HarmonySafe Agent</strong> with AI-powered analysis<br>
            Scan ID: ${scanId} | Generated: ${endTime?string["yyyy-MM-dd HH:mm:ss"]}
        </div>
    </div>
</body>
</html>
