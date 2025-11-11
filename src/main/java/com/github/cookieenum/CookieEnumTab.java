package com.github.cookieenum;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import com.github.cookieenum.async.CookieAutoProcessor;
import com.github.cookieenum.models.AnalysisResultRow;
import com.github.cookieenum.service.CookieInfoService;
import com.github.cookieenum.ui.CookieDatabaseViewerPanel;
import com.github.cookieenum.ui.CookieInfoSettingsPanel;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CookieEnumTab {
    private final MontoyaApi api;
    private final JPanel mainPanel;
    private final JTabbedPane tabbedPane;
    private DefaultTableModel tableModel;
    private JTable resultsTable;
    private JTextArea statusArea;
    private IntelligentCookieAnalyzer intelligentAnalyzer;
    private HttpRequestEditor requestEditor;
    private HttpResponseEditor responseEditor;
    private Map<String, AnalysisResultRow> resultRows;
    private JFrame statusLogWindow;

    public CookieEnumTab(MontoyaApi api, CookieInfoService cookieService, CookieAutoProcessor autoProcessor) {
        this.api = api;
        this.resultRows = new HashMap<>();

        // Create main panel with tabbed pane
        mainPanel = new JPanel(new BorderLayout());
        tabbedPane = new JTabbedPane();

        // Create Analysis tab
        JPanel analysisPanel = createAnalysisPanel();
        tabbedPane.addTab("Cookie Enumeration", analysisPanel);

        // Create Database Viewer tab
        CookieDatabaseViewerPanel dbViewerPanel = new CookieDatabaseViewerPanel(api, cookieService);
        tabbedPane.addTab("Database Viewer", dbViewerPanel);

        // Create AI Settings tab
        CookieInfoSettingsPanel settingsPanel = new CookieInfoSettingsPanel(api, cookieService, autoProcessor);
        tabbedPane.addTab("AI Settings", settingsPanel.uiComponent());

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Apply theme
        api.userInterface().applyThemeToComponent(mainPanel);

        // Load saved analysis results from project file
        loadSavedResults();
    }

    private JPanel createAnalysisPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create title label
        JLabel titleLabel = new JLabel("Cookie Requirement Analyzer");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        panel.add(titleLabel, BorderLayout.NORTH);

        // Create main vertical split: top = table, bottom = request/response viewer
        JSplitPane mainVerticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mainVerticalSplit.setResizeWeight(0.5);

        // Create results table
        String[] columnNames = {"Cookie Name", "Status", "Required", "Response Code", "Details"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        resultsTable = new JTable(tableModel);
        resultsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // Enable table sorting - click column headers to sort
        resultsTable.setAutoCreateRowSorter(true);

        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        resultsTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        resultsTable.getColumnModel().getColumn(4).setPreferredWidth(300);

        // Add Ctrl+C keyboard shortcut for copying cookie names
        setupCopyAction();

        // Add selection listener for request/response viewing
        resultsTable.getSelectionModel().addListSelectionListener(this::onTableRowSelected);

        JScrollPane tableScrollPane = new JScrollPane(resultsTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("Analysis Results (click row to view request/response)"));

        // Create status area (hidden, shown in separate window)
        statusArea = new JTextArea(20, 80);
        statusArea.setEditable(false);
        statusArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        // Bottom section: request/response viewer (side-by-side)
        JPanel bottomPanel = createRequestResponseViewer();

        mainVerticalSplit.setTopComponent(tableScrollPane);
        mainVerticalSplit.setBottomComponent(bottomPanel);

        panel.add(mainVerticalSplit, BorderLayout.CENTER);

        // Create button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton clearButton = new JButton("Clear Results");
        clearButton.addActionListener(e -> clearResults());
        buttonPanel.add(clearButton);

        JButton statusLogButton = new JButton("View Status Log");
        statusLogButton.addActionListener(e -> showStatusLogWindow());
        buttonPanel.add(statusLogButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        // Initialize intelligent analyzer with callback
        intelligentAnalyzer = new IntelligentCookieAnalyzer(api, this::logStatus);

        // Log initial status
        logStatus("Ready. Right-click on a request and select 'Analyze Required Cookies' to begin.");
        logStatus("The analyzer will intelligently determine required cookies without exhaustive testing.");

        return panel;
    }

    /**
     * Create request/response viewer panel with side-by-side layout
     */
    private JPanel createRequestResponseViewer() {
        JPanel panel = new JPanel(new BorderLayout());

        // Create request and response editors
        requestEditor = api.userInterface().createHttpRequestEditor();
        responseEditor = api.userInterface().createHttpResponseEditor();

        // Create horizontal split pane for side-by-side viewing
        JSplitPane viewerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        viewerSplit.setResizeWeight(0.5);

        // Create panels with titles for request and response
        JPanel requestPanel = new JPanel(new BorderLayout());
        requestPanel.setBorder(BorderFactory.createTitledBorder("Request"));
        requestPanel.add(requestEditor.uiComponent(), BorderLayout.CENTER);

        JPanel responsePanel = new JPanel(new BorderLayout());
        responsePanel.setBorder(BorderFactory.createTitledBorder("Response"));
        responsePanel.add(responseEditor.uiComponent(), BorderLayout.CENTER);

        viewerSplit.setLeftComponent(requestPanel);
        viewerSplit.setRightComponent(responsePanel);

        panel.add(viewerSplit, BorderLayout.CENTER);

        // Add instruction label
        JLabel instructionLabel = new JLabel("Select a row in the table above to view its request and response");
        instructionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        instructionLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.add(instructionLabel, BorderLayout.NORTH);

        return panel;
    }

    /**
     * Handle table row selection to show request/response
     */
    private void onTableRowSelected(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
            return;
        }

        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow >= 0) {
            // Convert view row index to model row index (important for sorting)
            int modelRow = resultsTable.convertRowIndexToModel(selectedRow);
            String cookieName = (String) tableModel.getValueAt(modelRow, 0);
            AnalysisResultRow resultRow = resultRows.get(cookieName);

            if (resultRow != null && resultRow.getRequestResponse() != null) {
                HttpRequestResponse requestResponse = resultRow.getRequestResponse();

                // Set the request and response in the editors
                requestEditor.setRequest(requestResponse.request());
                if (requestResponse.response() != null) {
                    responseEditor.setResponse(requestResponse.response());
                } else {
                    responseEditor.setResponse(null);
                }
            } else {
                // Clear editors if no request/response available
                requestEditor.setRequest(null);
                responseEditor.setResponse(null);
            }
        }
    }

    /**
     * Set up Ctrl+C action to copy selected cookie names to clipboard
     */
    private void setupCopyAction() {
        // Get the table's input map and action map
        InputMap inputMap = resultsTable.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = resultsTable.getActionMap();

        // Map Ctrl+C (or Cmd+C on Mac) to our copy action
        KeyStroke copyKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_C,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());

        inputMap.put(copyKeyStroke, "copyCookieNames");
        actionMap.put("copyCookieNames", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copyCookieNamesToClipboard();
            }
        });
    }

    /**
     * Copy selected cookie names to the system clipboard
     */
    private void copyCookieNamesToClipboard() {
        int[] selectedRows = resultsTable.getSelectedRows();

        if (selectedRows.length == 0) {
            logStatus("No cookies selected to copy.");
            return;
        }

        List<String> cookieNames = new ArrayList<>();

        // Extract cookie names from selected rows
        for (int row : selectedRows) {
            // Convert view row index to model row index (important for sorting)
            int modelRow = resultsTable.convertRowIndexToModel(row);
            String cookieName = (String) tableModel.getValueAt(modelRow, 0);
            // Skip the BASELINE and MINIMAL SET rows
            if (!"BASELINE".equals(cookieName) && !"MINIMAL SET".equals(cookieName)) {
                cookieNames.add(cookieName);
            }
        }

        if (cookieNames.isEmpty()) {
            logStatus("No cookie names to copy (BASELINE and MINIMAL SET rows cannot be copied).");
            return;
        }

        // Join cookie names with spaces
        String cookieText = String.join(" ", cookieNames);

        // Copy to clipboard
        try {
            StringSelection stringSelection = new StringSelection(cookieText);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);

            logStatus("Copied " + cookieNames.size() + " cookie name(s) to clipboard: " +
                    String.join(", ", cookieNames));
        } catch (Exception e) {
            logStatus("Error copying to clipboard: " + e.getMessage());
            api.logging().logToError("Clipboard copy error: " + e.getMessage());
        }
    }

    public Component getComponent() {
        return mainPanel;
    }

    public void analyzeRequest(HttpRequestResponse requestResponse) {
        // Clear previous results
        clearResults();

        // Get cookies from the request
        List<ParsedHttpParameter> cookies = requestResponse.request().parameters(HttpParameterType.COOKIE);

        if (cookies.isEmpty()) {
            logStatus("No cookies found in the selected request.");
            JOptionPane.showMessageDialog(mainPanel,
                    "The selected request does not contain any cookies.",
                    "No Cookies Found",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        logStatus("Starting intelligent analysis for request: " + requestResponse.request().url());
        logStatus("Found " + cookies.size() + " cookie(s) to analyze.");
        logStatus("");

        // Run analysis in background thread
        new Thread(() -> {
            try {
                performIntelligentAnalysis(requestResponse, cookies);
            } catch (Exception e) {
                logStatus("Error during analysis: " + e.getMessage());
                api.logging().logToError("Cookie analysis error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void performIntelligentAnalysis(HttpRequestResponse requestResponse,
                                          List<ParsedHttpParameter> cookies) {
        api.logging().logToOutput("=== STARTING INTELLIGENT COOKIE ANALYSIS ===");
        api.logging().logToOutput("Cookies to analyze: " + cookies.size());

        // Run the intelligent analyzer
        IntelligentCookieAnalyzer.AnalysisResult result =
            intelligentAnalyzer.analyze(requestResponse.request(), cookies);

        api.logging().logToOutput("Analysis completed. Result: " +
            result.getRequiredCookies().size() + " required, " +
            result.getOptionalCookies().size() + " optional, " +
            result.getTotalRequests() + " requests sent");

        // Display results in table
        // Get baseline request/response
        HttpRequestResponse baselineRR = result.getRequestResponse("Baseline (all cookies)");
        addResult("BASELINE", "Tested", "N/A",
            result.getBaseline() != null ? String.valueOf(result.getBaseline().getStatusCode()) : "N/A",
            "Reference request with ALL cookies included", baselineRR);

        // Add results for each cookie
        for (ParsedHttpParameter cookie : cookies) {
            boolean isRequired = result.isRequired(cookie);
            String requiredStr = isRequired ? "YES" : "NO";
            String details = result.getDetails().get(cookie);

            // Get the request/response for this cookie test
            HttpRequestResponse cookieRR = result.getRequestResponse("Without: " + cookie.name());

            addResult(cookie.name(), "Tested", requiredStr, "-", details, cookieRR);
        }

        // Add MINIMAL SET row showing final verification
        HttpRequestResponse minimalSetRR = result.getRequestResponse("MINIMAL SET (final verification)");
        if (minimalSetRR != null) {
            String minimalCookies = result.getRequiredCookies().stream()
                .map(ParsedHttpParameter::name)
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
            int minimalStatus = minimalSetRR.response() != null ?
                minimalSetRR.response().statusCode() : 0;
            addResult("MINIMAL SET", "Verified", "N/A",
                String.valueOf(minimalStatus),
                "Final proof: Success with ONLY required cookies (" + minimalCookies + ")",
                minimalSetRR);
        }

        // Log summary
        logStatus("\n╔════════════════════════════════════════════════════════════╗");
        logStatus("║                  FINAL RESULTS                             ║");
        logStatus("╚════════════════════════════════════════════════════════════╝");

        logStatus("\n✓ Required Cookies (" + result.getRequiredCookies().size() + "):");
        if (result.getRequiredCookies().isEmpty()) {
            logStatus("  → No cookies required! All cookies are optional.");
        } else {
            for (ParsedHttpParameter cookie : result.getRequiredCookies()) {
                logStatus("  → " + cookie.name());
            }
        }

        if (!result.getAlternatives().isEmpty()) {
            logStatus("\n✓ Alternative Relationships (OR logic):");
            for (var entry : result.getAlternatives().entrySet()) {
                String main = entry.getKey().name();
                String alts = entry.getValue().stream()
                    .map(ParsedHttpParameter::name)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
                logStatus("  → " + main + " can be replaced by: " + alts);
            }
        }

        logStatus("\n✓ Total requests sent: " + result.getTotalRequests());
        logStatus("\n=== Analysis complete! ===");

        // Save results to Burp project file
        saveResults();

        // Show summary dialog
        SwingUtilities.invokeLater(() -> {
            showIntelligentSummary(result, cookies);
        });
    }

    private void showIntelligentSummary(IntelligentCookieAnalyzer.AnalysisResult result,
                                       List<ParsedHttpParameter> cookies) {
        StringBuilder summary = new StringBuilder();
        summary.append("Intelligent Analysis Complete!\n\n");
        summary.append("Total cookies analyzed: ").append(cookies.size()).append("\n");
        summary.append("Total requests sent: ").append(result.getTotalRequests()).append("\n\n");

        summary.append("Required Cookies (").append(result.getRequiredCookies().size()).append("):\n");
        if (result.getRequiredCookies().isEmpty()) {
            summary.append("  ✓ No cookies required!\n");
        } else {
            for (ParsedHttpParameter cookie : result.getRequiredCookies()) {
                summary.append("  ✓ ").append(cookie.name());
                if (result.hasAlternatives(cookie)) {
                    summary.append(" (has alternatives)");
                }
                summary.append("\n");
            }
        }

        if (!result.getOptionalCookies().isEmpty()) {
            summary.append("\nOptional Cookies (").append(result.getOptionalCookies().size()).append("):\n");
            for (ParsedHttpParameter cookie : result.getOptionalCookies()) {
                summary.append("  - ").append(cookie.name()).append("\n");
            }
        }

        if (!result.getAlternatives().isEmpty()) {
            summary.append("\nAlternative Relationships (OR logic):\n");
            for (var entry : result.getAlternatives().entrySet()) {
                String main = entry.getKey().name();
                String alts = entry.getValue().stream()
                    .map(ParsedHttpParameter::name)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
                summary.append("  ").append(main).append(" OR ").append(alts).append("\n");
            }
        }

        summary.append("\n💡 The analyzer used intelligent deduction to avoid ");
        summary.append("exhaustive testing while ensuring accurate results.");

        JOptionPane.showMessageDialog(mainPanel, summary.toString(),
                "Analysis Complete", JOptionPane.INFORMATION_MESSAGE);
    }

    private void addResult(String cookieName, String status, String required,
                           String responseCode, String details) {
        addResult(cookieName, status, required, responseCode, details, null);
    }

    private void addResult(String cookieName, String status, String required,
                           String responseCode, String details, HttpRequestResponse requestResponse) {
        SwingUtilities.invokeLater(() -> {
            // Store the result with request/response
            AnalysisResultRow resultRow = new AnalysisResultRow(
                cookieName, status, required, responseCode, details, requestResponse);
            resultRows.put(cookieName, resultRow);

            // Update or add to table
            boolean found = false;
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                if (tableModel.getValueAt(i, 0).equals(cookieName)) {
                    // Update existing row
                    tableModel.setValueAt(status, i, 1);
                    tableModel.setValueAt(required, i, 2);
                    tableModel.setValueAt(responseCode, i, 3);
                    tableModel.setValueAt(details, i, 4);
                    found = true;
                    break;
                }
            }

            if (!found) {
                // Add new row
                tableModel.addRow(resultRow.toTableRow());
            }
        });
    }

    private void logStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            statusArea.append(message + "\n");
            statusArea.setCaretPosition(statusArea.getDocument().getLength());
            // Force immediate UI update
            statusArea.repaint();
        });

        // Also log to Burp's output for debugging
        api.logging().logToOutput(message);
    }

    /**
     * Show status log in a separate window
     */
    private void showStatusLogWindow() {
        // If window already exists and is visible, bring it to front
        if (statusLogWindow != null && statusLogWindow.isVisible()) {
            statusLogWindow.toFront();
            statusLogWindow.requestFocus();
            return;
        }

        // Create new window
        statusLogWindow = new JFrame("Cookie Analyzer - Status Log");
        statusLogWindow.setSize(900, 600);
        statusLogWindow.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Create panel for the window
        JPanel windowPanel = new JPanel(new BorderLayout());
        windowPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Add status area to scroll pane
        JScrollPane scrollPane = new JScrollPane(statusArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Analysis Progress Log"));
        windowPanel.add(scrollPane, BorderLayout.CENTER);

        // Add button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton clearLogButton = new JButton("Clear Log");
        clearLogButton.addActionListener(e -> statusArea.setText(""));
        buttonPanel.add(clearLogButton);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> statusLogWindow.dispose());
        buttonPanel.add(closeButton);

        windowPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Add info label
        JLabel infoLabel = new JLabel("This log shows the progress of cookie analysis in real-time");
        infoLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        windowPanel.add(infoLabel, BorderLayout.NORTH);

        statusLogWindow.add(windowPanel);
        statusLogWindow.setLocationRelativeTo(mainPanel);

        // Apply Burp theme to the window
        api.userInterface().applyThemeToComponent(statusLogWindow);

        statusLogWindow.setVisible(true);
    }

    private void clearResults() {
        tableModel.setRowCount(0);
        statusArea.setText("");
        resultRows.clear();
        requestEditor.setRequest(null);
        responseEditor.setResponse(null);
    }

    public Map<String, AnalysisResultRow> getResultRows() {
        return resultRows;
    }

    /**
     * Save analysis results to Burp project file
     */
    private void saveResults() {
        try {
            // Save the number of results
            api.persistence().extensionData().setInteger("cookieAnalysis.resultCount", resultRows.size());

            // Save each result
            int index = 0;
            for (Map.Entry<String, AnalysisResultRow> entry : resultRows.entrySet()) {
                String prefix = "cookieAnalysis.result." + index + ".";
                AnalysisResultRow row = entry.getValue();

                api.persistence().extensionData().setString(prefix + "cookieName", row.getCookieName());
                api.persistence().extensionData().setString(prefix + "status", row.getStatus());
                api.persistence().extensionData().setString(prefix + "required", row.getRequired());
                api.persistence().extensionData().setString(prefix + "responseCode", row.getResponseCode());
                api.persistence().extensionData().setString(prefix + "details", row.getDetails());

                index++;
            }

            api.logging().logToOutput("Saved " + resultRows.size() + " analysis results to project file");
        } catch (Exception e) {
            api.logging().logToError("Error saving analysis results: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load analysis results from Burp project file
     */
    private void loadSavedResults() {
        try {
            Integer count = api.persistence().extensionData().getInteger("cookieAnalysis.resultCount");
            if (count == null || count == 0) {
                return; // No saved results
            }

            api.logging().logToOutput("Loading " + count + " saved analysis results...");

            for (int i = 0; i < count; i++) {
                String prefix = "cookieAnalysis.result." + i + ".";

                String cookieName = api.persistence().extensionData().getString(prefix + "cookieName");
                String status = api.persistence().extensionData().getString(prefix + "status");
                String required = api.persistence().extensionData().getString(prefix + "required");
                String responseCode = api.persistence().extensionData().getString(prefix + "responseCode");
                String details = api.persistence().extensionData().getString(prefix + "details");

                if (cookieName != null) {
                    // Note: We don't have the request/response data, so pass null
                    addResult(cookieName, status, required, responseCode, details, null);
                }
            }

            if (count > 0) {
                logStatus("Loaded " + count + " previous analysis results from project file.");
                logStatus("Note: Request/response data is not persisted. Re-run analysis to view HTTP traffic.");
            }

            api.logging().logToOutput("Successfully loaded " + count + " analysis results");
        } catch (Exception e) {
            api.logging().logToError("Error loading analysis results: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

