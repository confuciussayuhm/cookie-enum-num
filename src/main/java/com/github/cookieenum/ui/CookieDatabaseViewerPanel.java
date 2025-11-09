package com.github.cookieenum.ui;

import burp.api.montoya.MontoyaApi;
import com.github.cookieenum.models.CookieInfo;
import com.github.cookieenum.service.CookieInfoService;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Panel for viewing and filtering cookies from the database
 */
public class CookieDatabaseViewerPanel extends JPanel {
    private final MontoyaApi api;
    private final CookieInfoService cookieService;
    private JTable cookieTable;
    private TableRowSorter<CookieTableModel> rowSorter;
    private CookieTableModel tableModel;
    private JTextField filterField;
    private JComboBox<String> categoryFilter;
    private JComboBox<String> privacyFilter;
    private JLabel statusLabel;

    public CookieDatabaseViewerPanel(MontoyaApi api, CookieInfoService cookieService) {
        this.api = api;
        this.cookieService = cookieService;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create filter panel at the top
        add(createFilterPanel(), BorderLayout.NORTH);

        // Create table in the center
        add(createTablePanel(), BorderLayout.CENTER);

        // Create status bar at the bottom
        add(createStatusPanel(), BorderLayout.SOUTH);

        // Initial load
        refreshData();
    }

    private JPanel createFilterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Filters"));

        // Text filter
        panel.add(new JLabel("Search:"));
        filterField = new JTextField(20);
        filterField.setToolTipText("Filter by cookie name, vendor, or purpose");
        filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
        });
        panel.add(filterField);

        // Category filter
        panel.add(new JLabel("Category:"));
        categoryFilter = new JComboBox<>(new String[]{
            "All", "Essential", "Analytics", "Advertising", "Functional",
            "Performance", "Social Media", "Security", "Personalization"
        });
        categoryFilter.addActionListener(e -> applyFilters());
        panel.add(categoryFilter);

        // Privacy filter
        panel.add(new JLabel("Privacy Impact:"));
        privacyFilter = new JComboBox<>(new String[]{
            "All", "Low", "Medium", "High", "Critical"
        });
        privacyFilter.addActionListener(e -> applyFilters());
        panel.add(privacyFilter);

        // Refresh button
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshData());
        panel.add(refreshButton);

        // Clear filters button
        JButton clearButton = new JButton("Clear Filters");
        clearButton.addActionListener(e -> clearFilters());
        panel.add(clearButton);

        // Edit button
        JButton editButton = new JButton("Edit Selected");
        editButton.addActionListener(e -> editSelectedCookie());
        panel.add(editButton);

        // Delete button
        JButton deleteButton = new JButton("Delete Selected");
        deleteButton.addActionListener(e -> deleteSelectedCookie());
        panel.add(deleteButton);

        return panel;
    }

    private JScrollPane createTablePanel() {
        // Create custom table model
        tableModel = new CookieTableModel();

        // Create table
        cookieTable = new JTable(tableModel);
        cookieTable.setAutoCreateRowSorter(false); // We'll use custom sorter
        cookieTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        cookieTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // Set column widths
        cookieTable.getColumnModel().getColumn(0).setPreferredWidth(150); // Name
        cookieTable.getColumnModel().getColumn(1).setPreferredWidth(150); // Vendor
        cookieTable.getColumnModel().getColumn(2).setPreferredWidth(120); // Category
        cookieTable.getColumnModel().getColumn(3).setPreferredWidth(300); // Purpose
        cookieTable.getColumnModel().getColumn(4).setPreferredWidth(100); // Privacy Impact
        cookieTable.getColumnModel().getColumn(5).setPreferredWidth(80);  // Third Party
        cookieTable.getColumnModel().getColumn(6).setPreferredWidth(120); // Expiration
        cookieTable.getColumnModel().getColumn(7).setPreferredWidth(80);  // Confidence
        cookieTable.getColumnModel().getColumn(8).setPreferredWidth(80);  // Source

        // Create and set row sorter
        rowSorter = new TableRowSorter<>(tableModel);
        cookieTable.setRowSorter(rowSorter);

        // Set custom comparators for specific columns
        rowSorter.setComparator(7, Comparator.comparingDouble(o -> {
            if (o instanceof Double) return (Double) o;
            return 0.0;
        }));

        JScrollPane scrollPane = new JScrollPane(cookieTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Cookie Database"));

        return scrollPane;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Ready");
        panel.add(statusLabel);
        return panel;
    }

    private void refreshData() {
        SwingWorker<List<CookieInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<CookieInfo> doInBackground() {
                statusLabel.setText("Loading cookies from database...");
                return cookieService.getAllCookies();
            }

            @Override
            protected void done() {
                try {
                    List<CookieInfo> cookies = get();
                    tableModel.setCookies(cookies);
                    statusLabel.setText("Loaded " + cookies.size() + " cookie(s)");
                } catch (Exception e) {
                    statusLabel.setText("Error loading cookies: " + e.getMessage());
                    api.logging().logToError("Failed to load cookies: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void applyFilters() {
        List<RowFilter<Object, Object>> filters = new ArrayList<>();

        // Text filter
        String text = filterField.getText();
        if (text != null && !text.trim().isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + text));
        }

        // Category filter
        String category = (String) categoryFilter.getSelectedItem();
        if (category != null && !"All".equals(category)) {
            filters.add(RowFilter.regexFilter(category, 2)); // Column 2 is category
        }

        // Privacy filter
        String privacy = (String) privacyFilter.getSelectedItem();
        if (privacy != null && !"All".equals(privacy)) {
            filters.add(RowFilter.regexFilter(privacy, 4)); // Column 4 is privacy impact
        }

        // Combine filters with AND logic
        if (filters.isEmpty()) {
            rowSorter.setRowFilter(null);
        } else {
            rowSorter.setRowFilter(RowFilter.andFilter(filters));
        }

        // Update status
        int visibleRows = cookieTable.getRowCount();
        int totalRows = tableModel.getRowCount();
        statusLabel.setText(String.format("Showing %d of %d cookie(s)", visibleRows, totalRows));
    }

    private void clearFilters() {
        filterField.setText("");
        categoryFilter.setSelectedIndex(0);
        privacyFilter.setSelectedIndex(0);
        rowSorter.setRowFilter(null);
        statusLabel.setText("Showing " + tableModel.getRowCount() + " cookie(s)");
    }

    private void editSelectedCookie() {
        int selectedRow = cookieTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this,
                "Please select a cookie to edit",
                "No Selection",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Convert view row to model row (important for filtering/sorting)
        int modelRow = cookieTable.convertRowIndexToModel(selectedRow);
        CookieInfo cookie = tableModel.getCookie(modelRow);

        // Show edit dialog
        CookieEditDialog dialog = new CookieEditDialog(
            (JFrame) SwingUtilities.getWindowAncestor(this),
            cookie,
            api);

        dialog.setVisible(true);

        // If the dialog was confirmed, update the database
        if (dialog.isConfirmed()) {
            CookieInfo updatedCookie = dialog.getUpdatedCookie();

            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    statusLabel.setText("Updating cookie...");
                    cookieService.updateCookie(updatedCookie);
                    return null;
                }

                @Override
                protected void done() {
                    statusLabel.setText("Cookie updated successfully");
                    refreshData();
                }
            };
            worker.execute();
        }
    }

    private void deleteSelectedCookie() {
        int selectedRow = cookieTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this,
                "Please select a cookie to delete",
                "No Selection",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Convert view row to model row
        int modelRow = cookieTable.convertRowIndexToModel(selectedRow);
        CookieInfo cookie = tableModel.getCookie(modelRow);

        // Confirm deletion
        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to delete the cookie '" + cookie.getName() + "'?\n" +
            "This action cannot be undone.",
            "Confirm Deletion",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    statusLabel.setText("Deleting cookie...");
                    cookieService.deleteCookie(cookie.getName());
                    return null;
                }

                @Override
                protected void done() {
                    statusLabel.setText("Cookie deleted successfully");
                    refreshData();
                }
            };
            worker.execute();
        }
    }

    /**
     * Custom table model for cookies
     */
    private static class CookieTableModel extends AbstractTableModel {
        private final String[] columnNames = {
            "Name", "Vendor", "Category", "Purpose", "Privacy Impact",
            "Third Party", "Expiration", "Confidence", "Source"
        };

        private List<CookieInfo> cookies = new ArrayList<>();

        public void setCookies(List<CookieInfo> cookies) {
            this.cookies = cookies;
            fireTableDataChanged();
        }

        public CookieInfo getCookie(int rowIndex) {
            return cookies.get(rowIndex);
        }

        @Override
        public int getRowCount() {
            return cookies.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            switch (columnIndex) {
                case 5: return Boolean.class; // Third Party
                case 7: return Double.class;  // Confidence
                default: return String.class;
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            CookieInfo cookie = cookies.get(rowIndex);

            switch (columnIndex) {
                case 0: return cookie.getName();
                case 1: return cookie.getVendor();
                case 2: return cookie.getCategory() != null ? cookie.getCategory().name() : "";
                case 3: return cookie.getPurpose();
                case 4: return cookie.getPrivacyImpact() != null ? cookie.getPrivacyImpact().name() : "";
                case 5: return cookie.isThirdParty();
                case 6: return cookie.getTypicalExpiration();
                case 7: return cookie.getConfidenceScore();
                case 8: return cookie.getSource();
                default: return "";
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }
}
