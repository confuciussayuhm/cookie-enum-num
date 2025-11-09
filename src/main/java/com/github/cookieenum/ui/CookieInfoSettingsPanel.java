package com.github.cookieenum.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.settings.SettingsPanel;
import com.github.cookieenum.async.CookieAutoProcessor;
import com.github.cookieenum.models.QueueStats;
import com.github.cookieenum.service.CookieInfoService;
import com.github.cookieenum.util.DomainFilter;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.Set;

/**
 * Settings panel for cookie info extension
 */
public class CookieInfoSettingsPanel implements SettingsPanel {
    private final MontoyaApi api;
    private final CookieInfoService cookieService;
    private final CookieAutoProcessor autoProcessor;

    private JPanel mainPanel;
    private JTextField databasePathField;
    private JCheckBox autoProcessEnabled;
    private JSpinner workerThreadsSpinner;
    private JSpinner rateLimitSpinner;
    private JComboBox<String> domainFilterModeCombo;
    private JTextField domainListField;
    private JLabel domainListLabel;
    private JComboBox<String> providerCombo;
    private JTextField apiEndpointField;
    private JTextField apiKeyField;
    private JComboBox<String> modelCombo;
    private JButton refreshModelsButton;
    private JTextArea statsArea;
    private JButton testConnectionButton;

    public CookieInfoSettingsPanel(MontoyaApi api,
                                  CookieInfoService service,
                                  CookieAutoProcessor processor) {
        this.api = api;
        this.cookieService = service;
        this.autoProcessor = processor;

        createUI();
        loadSettings();
    }

    private void createUI() {
        mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // ===== Database Configuration =====
        addSectionHeader(mainPanel, gbc, row++, "Database Configuration");

        // Database path
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        mainPanel.add(new JLabel("Database Location:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        databasePathField = new JTextField(40);
        mainPanel.add(databasePathField, gbc);

        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseDatabasePath());
        mainPanel.add(browseButton, gbc);

        row++;

        // ===== Auto-Processing =====
        addSectionHeader(mainPanel, gbc, row++, "Auto-Processing");

        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 4;
        autoProcessEnabled = new JCheckBox("Automatically analyze cookies from all requests");
        autoProcessEnabled.setSelected(false); // Default to OFF until user configures AI
        autoProcessEnabled.setToolTipText("Enable automatic cookie discovery from all HTTP traffic. Requires AI provider to be configured.");
        mainPanel.add(autoProcessEnabled, gbc);

        // Worker threads
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        mainPanel.add(new JLabel("Worker Threads:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 1;
        workerThreadsSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 10, 1));
        mainPanel.add(workerThreadsSpinner, gbc);

        gbc.gridx = 2;
        gbc.gridwidth = 2;
        mainPanel.add(new JLabel("(1-10, requires restart)"), gbc);

        row++;

        // Rate limit
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        mainPanel.add(new JLabel("AI Queries per Minute:"), gbc);

        gbc.gridx = 1;
        rateLimitSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 60, 1));
        mainPanel.add(rateLimitSpinner, gbc);

        gbc.gridx = 2;
        gbc.gridwidth = 2;
        mainPanel.add(new JLabel("(1-60, prevents API throttling)"), gbc);

        row++;

        // ===== Domain Filtering =====
        addSectionHeader(mainPanel, gbc, row++, "Domain Filtering");

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        mainPanel.add(new JLabel("Filter Mode:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 3;
        domainFilterModeCombo = new JComboBox<>(new String[]{"All Domains", "In-Scope Only", "Custom List"});
        domainFilterModeCombo.setToolTipText("Select which domains to analyze cookies for");
        domainFilterModeCombo.addActionListener(e -> updateDomainFilterUI());
        mainPanel.add(domainFilterModeCombo, gbc);

        row++;

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        domainListLabel = new JLabel("Domains:");
        mainPanel.add(domainListLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 3;
        domainListField = new JTextField(40);
        domainListField.setToolTipText("Comma-separated list of domains (e.g., example.com, test.com)");
        mainPanel.add(domainListField, gbc);

        row++;

        // Process existing history button
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 4;
        JButton processHistoryButton = new JButton("Process Existing HTTP History");
        processHistoryButton.setToolTipText("Scan all existing Burp HTTP history and discover cookies from past requests/responses");
        processHistoryButton.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(
                    mainPanel,
                    "This will scan all HTTP history entries and queue cookies for AI analysis.\n\n" +
                    "This may take some time if you have a large history.\n\n" +
                    "Continue?",
                    "Process Existing History",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (result == JOptionPane.YES_OPTION) {
                processHistoryButton.setEnabled(false);
                processHistoryButton.setText("Processing...");

                new Thread(() -> {
                    autoProcessor.processExistingHistory();

                    SwingUtilities.invokeLater(() -> {
                        processHistoryButton.setText("Process Existing HTTP History");
                        processHistoryButton.setEnabled(true);
                    });
                }).start();
            }
        });
        mainPanel.add(processHistoryButton, gbc);

        row++;

        // Force re-analysis button
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 4;
        JButton forceReanalysisButton = new JButton("Force Re-analyze All Cookies in History");
        forceReanalysisButton.setToolTipText("Re-analyze ALL cookies from HTTP history, even if already in database (bypasses cache)");
        forceReanalysisButton.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(
                    mainPanel,
                    "WARNING: This will re-analyze ALL cookies from HTTP history,\n" +
                    "even those already in the database.\n\n" +
                    "This will consume AI API credits and may take significant time.\n\n" +
                    "Are you sure you want to continue?",
                    "Force Re-analyze All Cookies",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (result == JOptionPane.YES_OPTION) {
                forceReanalysisButton.setEnabled(false);
                forceReanalysisButton.setText("Re-analyzing...");

                new Thread(() -> {
                    autoProcessor.processExistingHistory(true);

                    SwingUtilities.invokeLater(() -> {
                        forceReanalysisButton.setText("Force Re-analyze All Cookies in History");
                        forceReanalysisButton.setEnabled(true);
                    });
                }).start();
            }
        });
        mainPanel.add(forceReanalysisButton, gbc);

        row++;

        // ===== AI Provider =====
        addSectionHeader(mainPanel, gbc, row++, "AI Provider");

        // Provider selection
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        mainPanel.add(new JLabel("Provider:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        providerCombo = new JComboBox<>(new String[]{
                "OpenAI",
                "Anthropic (Claude)",
                "Azure OpenAI",
                "Mistral AI",
                "Together AI",
                "Groq",
                "LM Studio (Local)",
                "Ollama (Local)",
                "Custom OpenAI-Compatible"
        });
        providerCombo.addActionListener(e -> updateProviderFields());
        mainPanel.add(providerCombo, gbc);

        row++;

        // API Endpoint (for LM Studio and Custom)
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        mainPanel.add(new JLabel("API Endpoint:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        apiEndpointField = new JTextField(40);
        apiEndpointField.setToolTipText("e.g., http://127.0.0.1:1234/v1 for LM Studio (use 127.0.0.1 instead of localhost)");
        mainPanel.add(apiEndpointField, gbc);

        row++;

        // API Key
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        mainPanel.add(new JLabel("API Key:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        apiKeyField = new JPasswordField(40);
        apiKeyField.setToolTipText("Not required for LM Studio local models");
        mainPanel.add(apiKeyField, gbc);

        gbc.gridx = 3;
        gbc.gridwidth = 1;
        testConnectionButton = new JButton("Test");
        testConnectionButton.addActionListener(e -> testAIConnection());
        mainPanel.add(testConnectionButton, gbc);

        row++;

        // Model selection
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        mainPanel.add(new JLabel("Model:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        modelCombo = new JComboBox<>(new String[]{""});
        modelCombo.setEditable(true);
        modelCombo.setToolTipText("Enter API key and click Refresh to load models (or type model name manually)");
        mainPanel.add(modelCombo, gbc);

        gbc.gridx = 3;
        gbc.gridwidth = 1;
        refreshModelsButton = new JButton("Refresh");
        refreshModelsButton.setToolTipText("Query LM Studio for available models");
        refreshModelsButton.addActionListener(e -> refreshModelsFromEndpoint());
        refreshModelsButton.setEnabled(false); // Initially disabled
        mainPanel.add(refreshModelsButton, gbc);

        row++;

        // ===== Statistics =====
        addSectionHeader(mainPanel, gbc, row++, "Statistics");

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        statsArea = new JTextArea(6, 40);
        statsArea.setEditable(false);
        statsArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        statsArea.setText("Loading...");
        JScrollPane statsScroll = new JScrollPane(statsArea);
        mainPanel.add(statsScroll, gbc);

        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.NORTH;
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> updateStats());
        mainPanel.add(refreshButton, gbc);

        gbc.anchor = GridBagConstraints.WEST;
        row++;

        // Save button
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 4;
        gbc.anchor = GridBagConstraints.CENTER;
        JButton saveButton = new JButton("Save Settings");
        saveButton.addActionListener(e -> save());
        mainPanel.add(saveButton, gbc);

        row++;

        // Spacer at bottom
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 4;
        gbc.weighty = 1.0;
        mainPanel.add(Box.createVerticalGlue(), gbc);

        // Initial stats update
        updateStats();

        // Apply theme
        api.userInterface().applyThemeToComponent(mainPanel);
    }

    private void addSectionHeader(JPanel panel, GridBagConstraints gbc, int row, String title) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 4;
        gbc.insets = new Insets(15, 5, 5, 5);

        JLabel header = new JLabel(title);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(header, gbc);

        gbc.insets = new Insets(5, 5, 5, 5);
    }

    private void browseDatabasePath() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new FileNameExtensionFilter("SQLite Database (*.db)", "db"));

        String currentPath = databasePathField.getText();
        if (!currentPath.isEmpty()) {
            chooser.setSelectedFile(new File(currentPath));
        }

        if (chooser.showSaveDialog(mainPanel) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            String path = selected.getAbsolutePath();

            // Add .db extension if missing
            if (!path.toLowerCase().endsWith(".db")) {
                path += ".db";
            }

            databasePathField.setText(path);
        }
    }

    private void testAIConnection() {
        testConnectionButton.setEnabled(false);
        testConnectionButton.setText("Testing...");

        // Test in background
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                return cookieService.testAIConnection();
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        JOptionPane.showMessageDialog(mainPanel,
                                "Connection successful!",
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(mainPanel,
                                "Connection failed. Check API key and try again.",
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(mainPanel,
                            "Error: " + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    testConnectionButton.setText("Test");
                    testConnectionButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void updateStats() {
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                QueueStats queueStats = autoProcessor.getStats();
                java.util.Map<String, Object> dbStats = cookieService.getDatabaseStats();

                return String.format(
                        "Database: %s\n" +
                        "Total Cookies: %s\n" +
                        "Queue Size: %d\n" +
                        "Processed: %d\n" +
                        "Cache Hits: %d (%.1f%%)\n" +
                        "AI Queries: %d",
                        cookieService.getDatabasePath(),
                        dbStats.getOrDefault("totalCookies", 0),
                        queueStats.getQueueSize(),
                        queueStats.getProcessedCount(),
                        queueStats.getCacheHits(),
                        queueStats.getCacheHitRate() * 100,
                        queueStats.getAiQueries()
                );
            }

            @Override
            protected void done() {
                try {
                    statsArea.setText(get());
                } catch (Exception e) {
                    statsArea.setText("Error loading stats");
                }
            }
        };
        worker.execute();
    }

    private void updateProviderFields() {
        String provider = (String) providerCombo.getSelectedItem();
        if (provider == null) return;

        switch (provider) {
            case "OpenAI":
                apiEndpointField.setText("https://api.openai.com/v1");
                apiEndpointField.setEnabled(false);
                apiKeyField.setToolTipText("Enter your OpenAI API key from https://platform.openai.com/api-keys");
                modelCombo.setModel(new DefaultComboBoxModel<>(new String[]{""}));
                modelCombo.setEditable(true);
                refreshModelsButton.setEnabled(true);
                break;

            case "Anthropic (Claude)":
                apiEndpointField.setText("https://api.anthropic.com/v1");
                apiEndpointField.setEnabled(false);
                apiKeyField.setToolTipText("Enter your Anthropic API key from https://console.anthropic.com/");
                modelCombo.setModel(new DefaultComboBoxModel<>(new String[]{""}));
                modelCombo.setEditable(true);
                refreshModelsButton.setEnabled(true);
                break;

            case "Azure OpenAI":
                apiEndpointField.setText("https://YOUR-RESOURCE.openai.azure.com/");
                apiEndpointField.setEnabled(true);
                apiKeyField.setToolTipText("Enter your Azure OpenAI API key");
                // Azure uses deployment names, user must enter manually
                modelCombo.setModel(new DefaultComboBoxModel<>(new String[]{""}));
                modelCombo.setEditable(true);
                refreshModelsButton.setEnabled(false);
                break;

            case "Mistral AI":
                apiEndpointField.setText("https://api.mistral.ai/v1");
                apiEndpointField.setEnabled(false);
                apiKeyField.setToolTipText("Enter your Mistral AI API key from https://console.mistral.ai/");
                modelCombo.setModel(new DefaultComboBoxModel<>(new String[]{""}));
                modelCombo.setEditable(true);
                refreshModelsButton.setEnabled(true);
                break;

            case "Together AI":
                apiEndpointField.setText("https://api.together.xyz/v1");
                apiEndpointField.setEnabled(false);
                apiKeyField.setToolTipText("Enter your Together AI API key from https://api.together.xyz/settings/api-keys");
                modelCombo.setModel(new DefaultComboBoxModel<>(new String[]{""}));
                modelCombo.setEditable(true);
                refreshModelsButton.setEnabled(true);
                break;

            case "Groq":
                apiEndpointField.setText("https://api.groq.com/openai/v1");
                apiEndpointField.setEnabled(false);
                apiKeyField.setToolTipText("Enter your Groq API key from https://console.groq.com/keys");
                modelCombo.setModel(new DefaultComboBoxModel<>(new String[]{""}));
                modelCombo.setEditable(true);
                refreshModelsButton.setEnabled(true);
                break;

            case "LM Studio (Local)":
                apiEndpointField.setText("http://127.0.0.1:1234/v1");
                apiEndpointField.setEnabled(true);
                apiKeyField.setText(""); // Clear API key for local models
                apiKeyField.setToolTipText("Not required for local models (leave empty)");
                modelCombo.setModel(new DefaultComboBoxModel<>(new String[]{""}));
                modelCombo.setEditable(true);
                refreshModelsButton.setEnabled(true);
                break;

            case "Ollama (Local)":
                apiEndpointField.setText("http://127.0.0.1:11434/v1");
                apiEndpointField.setEnabled(true);
                apiKeyField.setText(""); // Clear API key for local models
                apiKeyField.setToolTipText("Not required for Ollama (leave empty)");
                modelCombo.setModel(new DefaultComboBoxModel<>(new String[]{""}));
                modelCombo.setEditable(true);
                refreshModelsButton.setEnabled(true);
                break;

            default: // Custom OpenAI-Compatible
                apiEndpointField.setText("");
                apiEndpointField.setEnabled(true);
                apiKeyField.setToolTipText("API key if required by your endpoint");
                modelCombo.setModel(new DefaultComboBoxModel<>(new String[]{""}));
                modelCombo.setEditable(true);
                refreshModelsButton.setEnabled(true);
                break;
        }
        modelCombo.setEditable(true);
    }

    private void refreshModelsFromEndpoint() {
        String endpoint = apiEndpointField.getText();
        String provider = (String) providerCombo.getSelectedItem();

        if (endpoint == null || endpoint.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Please enter an API endpoint first",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        refreshModelsButton.setEnabled(false);
        refreshModelsButton.setText("Loading...");

        SwingWorker<java.util.List<String>, Void> worker = new SwingWorker<>() {
            @Override
            protected java.util.List<String> doInBackground() {
                return queryAvailableModels(endpoint);
            }

            @Override
            protected void done() {
                try {
                    java.util.List<String> models = get();

                    if (models != null && !models.isEmpty()) {
                        api.logging().logToOutput("Found " + models.size() + " model(s) for " + provider);

                        String currentSelection = (String) modelCombo.getSelectedItem();
                        modelCombo.setModel(new DefaultComboBoxModel<>(
                                models.toArray(new String[0])));

                        if (currentSelection != null && models.contains(currentSelection)) {
                            modelCombo.setSelectedItem(currentSelection);
                        } else if (!models.isEmpty()) {
                            modelCombo.setSelectedIndex(0);
                        }

                        JOptionPane.showMessageDialog(mainPanel,
                                String.format("Found %d model(s):\n%s",
                                    models.size(),
                                    String.join("\n", models.subList(0, Math.min(5, models.size()))) +
                                    (models.size() > 5 ? "\n..." : "")),
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        api.logging().logToError("No models found. Check the error messages above.");

                        JOptionPane.showMessageDialog(mainPanel,
                                "No models found. Check Burp extension output for details.\n\n" +
                                "Make sure the server is running with models loaded.",
                                "No Models",
                                JOptionPane.WARNING_MESSAGE);
                        modelCombo.setModel(new DefaultComboBoxModel<>(new String[]{
                                "No models found - check Burp output tab"
                        }));
                    }
                } catch (Exception e) {
                    api.logging().logToError("Failed to query models: " + e.getMessage());
                    e.printStackTrace();

                    JOptionPane.showMessageDialog(mainPanel,
                            "Failed to query models: " + e.getMessage() +
                            "\n\nCheck Burp extension output for details.",
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                    modelCombo.setModel(new DefaultComboBoxModel<>(new String[]{
                            "Error - check Burp output tab"
                    }));
                } finally {
                    refreshModelsButton.setText("Refresh");
                    refreshModelsButton.setEnabled(true);
                }
            }
        };

        worker.execute();
    }

    private java.util.List<String> queryAvailableModels(String endpoint) {
        try {
            // Construct models endpoint
            String modelsEndpoint = endpoint;
            if (modelsEndpoint.endsWith("/chat/completions")) {
                modelsEndpoint = modelsEndpoint.replace("/chat/completions", "/models");
            } else if (modelsEndpoint.endsWith("/v1")) {
                modelsEndpoint += "/models";
            } else if (!modelsEndpoint.endsWith("/models")) {
                modelsEndpoint += "/models";
            }

            // Create HttpClient - NO PROXY to avoid routing through Burp's proxy
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .version(java.net.http.HttpClient.Version.HTTP_1_1)
                    .proxy(java.net.ProxySelector.of(null))
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .build();

            java.net.http.HttpRequest.Builder requestBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(modelsEndpoint))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .GET();

            // Add browser-like headers for better compatibility
            requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            requestBuilder.header("Accept", "application/json, */*");
            requestBuilder.header("Accept-Encoding", "gzip, deflate");

            // Add Authorization header if API key is provided
            String apiKey = apiKeyField.getText();
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            java.net.http.HttpRequest request = requestBuilder.build();
            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseModelsResponse(response.body());
            } else {
                api.logging().logToError("Models endpoint returned status " + response.statusCode());
                return null;
            }
        } catch (java.net.http.HttpTimeoutException e) {
            api.logging().logToError("Request timed out: " + e.getMessage());
            return null;
        } catch (java.net.ConnectException e) {
            api.logging().logToError("Connection refused: " + e.getMessage());
            return null;
        } catch (Exception e) {
            api.logging().logToError("Failed to query models: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private java.util.List<String> parseModelsResponse(String responseBody) {
        java.util.List<String> models = new java.util.ArrayList<>();

        try {
            // Find the data array
            int dataStart = responseBody.indexOf("\"data\"");
            if (dataStart == -1) {
                api.logging().logToError("Response format doesn't match OpenAI /v1/models");
                return models;
            }

            int arrayStart = responseBody.indexOf("[", dataStart);
            int arrayEnd = responseBody.indexOf("]", arrayStart);
            if (arrayStart == -1 || arrayEnd == -1) {
                return models;
            }

            String dataArray = responseBody.substring(arrayStart + 1, arrayEnd);
            String[] parts = dataArray.split("\\{");

            // Extract model IDs
            for (String part : parts) {
                if (part.trim().isEmpty()) continue;

                int idStart = part.indexOf("\"id\"");
                if (idStart == -1) continue;

                int valueStart = part.indexOf("\"", idStart + 4);
                if (valueStart == -1) continue;

                int valueEnd = part.indexOf("\"", valueStart + 1);
                if (valueEnd == -1) continue;

                String modelId = part.substring(valueStart + 1, valueEnd);
                if (!modelId.isEmpty() && !models.contains(modelId)) {
                    models.add(modelId);
                }
            }

        } catch (Exception e) {
            api.logging().logToError("Failed to parse models response: " + e.getMessage());
        }

        return models;
    }

    private void loadSettings() {
        // Database path
        String dbPath = api.persistence().preferences()
                .getString("cookiedb.path");
        if (dbPath == null || dbPath.isEmpty()) {
            dbPath = cookieService.getDatabasePath();
        }
        databasePathField.setText(dbPath);

        // Auto-processing
        Boolean autoEnabled = api.persistence().preferences()
                .getBoolean("cookiedb.autoProcess");
        autoProcessEnabled.setSelected(autoEnabled != null ? autoEnabled : true);

        // Worker threads
        Integer workers = api.persistence().preferences()
                .getInteger("cookiedb.workerThreads");
        workerThreadsSpinner.setValue(workers != null ? workers : 3);

        // Rate limit
        Integer rateLimit = api.persistence().preferences()
                .getInteger("cookiedb.queriesPerMinute");
        rateLimitSpinner.setValue(rateLimit != null ? rateLimit : 10);

        // Domain filter mode
        String filterModeStr = api.persistence().preferences()
                .getString("cookiedb.domainFilter.mode");
        String filterMode = "All Domains"; // Default
        if (filterModeStr != null) {
            switch (filterModeStr) {
                case "ALL":
                    filterMode = "All Domains";
                    break;
                case "IN_SCOPE":
                    filterMode = "In-Scope Only";
                    break;
                case "CUSTOM_LIST":
                    filterMode = "Custom List";
                    break;
            }
        }
        domainFilterModeCombo.setSelectedItem(filterMode);

        // Domain list
        String domainList = api.persistence().preferences()
                .getString("cookiedb.domainFilter.domains");
        domainListField.setText(domainList != null ? domainList : "");

        // Update UI based on mode
        updateDomainFilterUI();

        // Provider
        String provider = api.persistence().preferences()
                .getString("cookiedb.ai.provider");
        providerCombo.setSelectedItem(provider != null ? provider : "OpenAI");

        // API Endpoint
        String endpoint = api.persistence().preferences()
                .getString("cookiedb.ai.endpoint");
        if (endpoint == null || endpoint.isEmpty()) {
            endpoint = "https://api.openai.com/v1";
        }
        apiEndpointField.setText(endpoint);

        // API key
        String apiKey = api.persistence().preferences()
                .getString("cookiedb.openai.apiKey");
        apiKeyField.setText(apiKey != null ? apiKey : "");

        // Model
        String model = api.persistence().preferences()
                .getString("cookiedb.openai.model");
        if (model == null || model.isEmpty()) {
            model = "gpt-4-turbo";
        }
        modelCombo.setSelectedItem(model);

        // Update fields based on provider
        updateProviderFields();
    }

    @Override
    public JComponent uiComponent() {
        return mainPanel;
    }

    /**
     * Update domain filter UI based on selected mode
     */
    private void updateDomainFilterUI() {
        String mode = (String) domainFilterModeCombo.getSelectedItem();
        boolean enableList = "Custom List".equals(mode);
        domainListField.setEnabled(enableList);
        domainListLabel.setEnabled(enableList);

        if (enableList) {
            domainListField.setBackground(Color.WHITE);
        } else {
            domainListField.setBackground(UIManager.getColor("TextField.inactiveBackground"));
        }
    }

    /**
     * Save settings (called from UI buttons)
     */
    public void save() {
        // Save all settings
        api.persistence().preferences()
                .setString("cookiedb.path", databasePathField.getText());

        api.persistence().preferences()
                .setBoolean("cookiedb.autoProcess", autoProcessEnabled.isSelected());

        api.persistence().preferences()
                .setInteger("cookiedb.workerThreads",
                        (Integer) workerThreadsSpinner.getValue());

        api.persistence().preferences()
                .setInteger("cookiedb.queriesPerMinute",
                        (Integer) rateLimitSpinner.getValue());

        api.persistence().preferences()
                .setString("cookiedb.ai.provider",
                        (String) providerCombo.getSelectedItem());

        api.persistence().preferences()
                .setString("cookiedb.ai.endpoint",
                        apiEndpointField.getText());

        api.persistence().preferences()
                .setString("cookiedb.openai.apiKey", apiKeyField.getText());

        api.persistence().preferences()
                .setString("cookiedb.openai.model",
                        (String) modelCombo.getSelectedItem());

        // Save domain filter settings
        String filterModeUI = (String) domainFilterModeCombo.getSelectedItem();
        DomainFilter.FilterMode filterMode = DomainFilter.FilterMode.ALL;
        switch (filterModeUI) {
            case "All Domains":
                filterMode = DomainFilter.FilterMode.ALL;
                break;
            case "In-Scope Only":
                filterMode = DomainFilter.FilterMode.IN_SCOPE;
                break;
            case "Custom List":
                filterMode = DomainFilter.FilterMode.CUSTOM_LIST;
                break;
        }

        Set<String> domains = DomainFilter.parseDomainsFromString(domainListField.getText());
        autoProcessor.setDomainFilter(filterMode, domains);

        // Apply auto-processing setting
        autoProcessor.setEnabled(autoProcessEnabled.isSelected());

        api.logging().logToOutput("Cookie Info settings saved");

        // Show restart notice if worker threads changed
        JOptionPane.showMessageDialog(mainPanel,
                "Settings saved. Note: Worker thread changes require extension reload.",
                "Settings Saved",
                JOptionPane.INFORMATION_MESSAGE);
    }
}
