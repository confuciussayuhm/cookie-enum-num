package com.github.cookieenum.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import com.github.cookieenum.models.CookieInfo;
import com.github.cookieenum.service.CookieInfoService;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Custom editor tab that displays cookie information
 */
public class CookieInfoEditorTab implements ExtensionProvidedHttpRequestEditor {
    private final MontoyaApi api;
    private final CookieInfoService cookieService;
    private final JPanel mainPanel;
    private final JPanel cookiesPanel;
    private final JLabel statusLabel;
    private HttpRequestResponse currentRequestResponse;

    public CookieInfoEditorTab(MontoyaApi api, CookieInfoService service) {
        this.api = api;
        this.cookieService = service;

        // Create main panel
        mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Status bar at top
        statusLabel = new JLabel("No cookies");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mainPanel.add(statusLabel, BorderLayout.NORTH);

        // Scrollable cookies panel
        cookiesPanel = new JPanel();
        cookiesPanel.setLayout(new BoxLayout(cookiesPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(cookiesPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Apply Burp theme
        api.userInterface().applyThemeToComponent(mainPanel);
    }

    @Override
    public HttpRequest getRequest() {
        return currentRequestResponse != null ? currentRequestResponse.request() : null;
    }

    @Override
    public void setRequestResponse(HttpRequestResponse requestResponse) {
        this.currentRequestResponse = requestResponse;

        // Clear previous content
        cookiesPanel.removeAll();

        if (requestResponse == null || requestResponse.request() == null) {
            statusLabel.setText("No request");
            mainPanel.revalidate();
            mainPanel.repaint();
            return;
        }

        // Extract cookies
        List<ParsedHttpParameter> cookies = requestResponse.request()
                .parameters(HttpParameterType.COOKIE);

        if (cookies.isEmpty()) {
            statusLabel.setText("No cookies in this request");
            mainPanel.revalidate();
            mainPanel.repaint();
            return;
        }

        // Show loading status
        statusLabel.setText(String.format("Loading information for %d cookie(s)...",
                cookies.size()));

        // Load cookie info asynchronously
        loadCookieInfoAsync(cookies, requestResponse.request().httpService().host());
    }

    /**
     * Load cookie information in background
     */
    private void loadCookieInfoAsync(List<ParsedHttpParameter> cookies, String domain) {
        CompletableFuture.runAsync(() -> {
            int cached = 0;
            int queried = 0;

            for (ParsedHttpParameter cookie : cookies) {
                try {
                    CookieInfo info = cookieService.getCookieInfo(cookie.name(), domain);

                    if (info != null) {
                        // Update UI on EDT
                        SwingUtilities.invokeLater(() -> addCookiePanel(info));

                        if ("ai".equals(info.getSource())) {
                            queried++;
                        } else {
                            cached++;
                        }
                    }
                } catch (Exception e) {
                    api.logging().logToError("Error loading cookie info: " + e.getMessage());
                }
            }

            // Update status
            final int finalCached = cached;
            final int finalQueried = queried;
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText(String.format(
                        "%d cookie(s) | %d cached | %d AI queries",
                        cookies.size(), finalCached, finalQueried));
                mainPanel.revalidate();
                mainPanel.repaint();
            });
        });
    }

    /**
     * Add a panel for one cookie
     */
    private void addCookiePanel(CookieInfo info) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        // Cookie name header
        JLabel nameLabel = new JLabel(info.getName());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(nameLabel);

        panel.add(Box.createVerticalStrut(5));

        // Vendor and category
        panel.add(createInfoLabel("Vendor: " + info.getVendor()));
        panel.add(createInfoLabel("Category: " + info.getCategory().getDisplayName()));

        // Purpose
        if (info.getPurpose() != null && !info.getPurpose().isEmpty()) {
            JTextArea purposeArea = new JTextArea(info.getPurpose());
            purposeArea.setWrapStyleWord(true);
            purposeArea.setLineWrap(true);
            purposeArea.setOpaque(false);
            purposeArea.setEditable(false);
            purposeArea.setFocusable(false);
            purposeArea.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
            panel.add(purposeArea);
        }

        // Privacy info
        if (info.getPrivacyImpact() != null) {
            JLabel privacyLabel = createInfoLabel("Privacy Impact: " +
                    info.getPrivacyImpact().getDisplayName() +
                    (info.isThirdParty() ? " | Third-party" : " | First-party"));

            // Color code by privacy impact
            switch (info.getPrivacyImpact()) {
                case HIGH:
                case CRITICAL:
                    privacyLabel.setForeground(Color.RED);
                    break;
                case MEDIUM:
                    privacyLabel.setForeground(Color.ORANGE);
                    break;
                default:
                    privacyLabel.setForeground(Color.GREEN.darker());
            }

            panel.add(privacyLabel);
        }

        // Expiration
        if (info.getTypicalExpiration() != null) {
            panel.add(createInfoLabel("Expires: " + info.getTypicalExpiration()));
        }

        // Confidence score
        panel.add(createInfoLabel(String.format("Confidence: %.0f%%",
                info.getConfidenceScore() * 100)));

        cookiesPanel.add(panel);
        cookiesPanel.add(Box.createVerticalStrut(10));
    }

    /**
     * Create a formatted info label
     */
    private JLabel createInfoLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(12f));
        return label;
    }

    @Override
    public boolean isEnabledFor(HttpRequestResponse requestResponse) {
        // Enable tab if request has cookies
        if (requestResponse == null || requestResponse.request() == null) {
            return false;
        }

        return requestResponse.request().hasParameters(HttpParameterType.COOKIE);
    }

    @Override
    public String caption() {
        return "Cookie Info";
    }

    @Override
    public Component uiComponent() {
        return mainPanel;
    }

    @Override
    public Selection selectedData() {
        // No selection support for now
        return null;
    }

    @Override
    public boolean isModified() {
        // Read-only tab
        return false;
    }
}
