package com.github.cookieenum;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import burp.api.montoya.ui.editor.extension.HttpRequestEditorProvider;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.github.cookieenum.ai.AIProvider;
import com.github.cookieenum.ai.OpenAIProvider;
import com.github.cookieenum.async.CookieAutoProcessor;
import com.github.cookieenum.async.CookieProcessingQueue;
import com.github.cookieenum.database.CookieDatabaseManager;
import com.github.cookieenum.service.CookieInfoService;
import com.github.cookieenum.ui.CookieInfoEditorTab;
import com.github.cookieenum.ui.CookieInfoSettingsPanel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CookieEnumExtension implements BurpExtension {
    private MontoyaApi api;
    private CookieEnumTab cookieEnumTab;

    // AI-powered components
    private CookieDatabaseManager dbManager;
    private AIProvider aiProvider;
    private CookieInfoService cookieService;
    private CookieProcessingQueue processingQueue;
    private CookieAutoProcessor autoProcessor;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;

        // Set extension name
        api.extension().setName("Cookie Enumerator");

        try {
            // Initialize AI-powered cookie classification system first
            initializeAISystem();

            // Create and register the original UI tab (with AI settings tab)
            cookieEnumTab = new CookieEnumTab(api, cookieService, autoProcessor);
            api.userInterface().registerSuiteTab("Cookie Enum", cookieEnumTab.getComponent());

            // Register context menu provider
            api.userInterface().registerContextMenuItemsProvider(new CookieContextMenuProvider());

            // Log successful loading
            api.logging().logToOutput("Cookie Enumerator extension loaded successfully!");

        } catch (Exception e) {
            api.logging().logToError("Failed to initialize extension: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Initialize AI-powered cookie classification system
     */
    private void initializeAISystem() {
        api.logging().logToOutput("Initializing AI-powered cookie classification...");

        // 1. Initialize database
        dbManager = new CookieDatabaseManager(api);

        // 2. Initialize AI provider
        aiProvider = new OpenAIProvider(api);

        // 3. Initialize service layer
        cookieService = new CookieInfoService(api, dbManager, aiProvider);

        // 4. Initialize async processing queue
        processingQueue = new CookieProcessingQueue(api, cookieService);

        // 5. Initialize auto-processor
        autoProcessor = new CookieAutoProcessor(api, processingQueue);

        // 6. Register HTTP handler for passive monitoring
        // Only enable auto-processing if explicitly configured AND AI provider is configured
        Boolean autoEnabled = api.persistence().preferences()
                .getBoolean("cookiedb.autoProcess");
        boolean enabled = (autoEnabled != null) ? autoEnabled : false; // Default to OFF

        if (enabled && aiProvider.isConfigured()) {
            api.http().registerHttpHandler(autoProcessor);
            api.logging().logToOutput("Automatic cookie discovery enabled");
        } else if (enabled && !aiProvider.isConfigured()) {
            api.logging().logToOutput("Auto-processing disabled: AI provider not configured");
        }

        // 7. Register custom editor tab provider
        api.userInterface().registerHttpRequestEditorProvider(new CookieInfoEditorProvider());

        // 8. Register settings panel
        CookieInfoSettingsPanel settingsPanel = new CookieInfoSettingsPanel(
                api, cookieService, autoProcessor);
        api.userInterface().registerSettingsPanel(settingsPanel);

        // 9. Register shutdown hook
        api.extension().registerUnloadingHandler(this::shutdown);

        api.logging().logToOutput("AI-powered cookie classification system initialized");
        api.logging().logToOutput("Database: " + cookieService.getDatabasePath());
    }

    /**
     * Shutdown hook
     */
    private void shutdown() {
        api.logging().logToOutput("Shutting down Cookie Enumerator...");

        if (processingQueue != null) {
            processingQueue.shutdown();
        }

        if (dbManager != null) {
            dbManager.close();
        }

        api.logging().logToOutput("Cookie Enumerator shut down cleanly");
    }

    /**
     * Provider for cookie info editor tab
     */
    private class CookieInfoEditorProvider implements HttpRequestEditorProvider {
        @Override
        public ExtensionProvidedHttpRequestEditor provideHttpRequestEditor(
                EditorCreationContext creationContext) {
            return new CookieInfoEditorTab(api, cookieService);
        }
    }

    private class CookieContextMenuProvider implements ContextMenuItemsProvider {
        @Override
        public List<Component> provideMenuItems(ContextMenuEvent event) {
            List<Component> menuItems = new ArrayList<>();

            // Check if we have request/response data available
            // This works for Proxy History, Repeater, Intruder, Logger, etc.
            boolean hasRequestResponse = !event.selectedRequestResponses().isEmpty();

            // For tools like Repeater that might not use selectedRequestResponses(),
            // we can also check if messageEditorRequestResponse is available
            if (!hasRequestResponse && event.messageEditorRequestResponse().isPresent()) {
                hasRequestResponse = true;
            }

            if (hasRequestResponse) {
                JMenuItem analyzeMenuItem = new JMenuItem("Analyze Required Cookies");
                analyzeMenuItem.addActionListener(e -> {
                    HttpRequestResponse requestResponse = null;

                    // Try to get from selected items first (Proxy, Logger, etc.)
                    if (!event.selectedRequestResponses().isEmpty()) {
                        requestResponse = event.selectedRequestResponses().get(0);
                    }
                    // Otherwise get from message editor (Repeater, etc.)
                    else if (event.messageEditorRequestResponse().isPresent()) {
                        MessageEditorHttpRequestResponse editorRequestResponse =
                                event.messageEditorRequestResponse().get();
                        requestResponse = editorRequestResponse.requestResponse();
                    }

                    if (requestResponse != null) {
                        final HttpRequestResponse finalRequestResponse = requestResponse;

                        // Start the analysis in a background thread
                        SwingUtilities.invokeLater(() -> {
                            // Switch to Cookie Enum tab
                            Component cookieTabComponent = cookieEnumTab.getComponent();
                            Component parent = cookieTabComponent.getParent();

                            // Try to switch to the tab if it's in a tabbed pane
                            if (parent instanceof JTabbedPane) {
                                JTabbedPane tabbedPane = (JTabbedPane) parent;
                                for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                                    if (tabbedPane.getComponentAt(i) == cookieTabComponent) {
                                        tabbedPane.setSelectedIndex(i);
                                        break;
                                    }
                                }
                            }

                            // Start analysis
                            cookieEnumTab.analyzeRequest(finalRequestResponse);
                        });
                    }
                });
                menuItems.add(analyzeMenuItem);
            }

            return menuItems;
        }
    }
}
