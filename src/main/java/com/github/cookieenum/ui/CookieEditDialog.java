package com.github.cookieenum.ui;

import burp.api.montoya.MontoyaApi;
import com.github.cookieenum.models.CookieCategory;
import com.github.cookieenum.models.CookieInfo;
import com.github.cookieenum.models.PrivacyImpact;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog for editing cookie information
 */
public class CookieEditDialog extends JDialog {
    private final CookieInfo originalCookie;
    private final MontoyaApi api;
    private boolean confirmed = false;

    // Form fields
    private JTextField nameField;
    private JTextField vendorField;
    private JComboBox<CookieCategory> categoryCombo;
    private JTextArea purposeArea;
    private JComboBox<PrivacyImpact> privacyCombo;
    private JCheckBox thirdPartyCheckbox;
    private JTextField expirationField;
    private JSpinner confidenceSpinner;

    public CookieEditDialog(JFrame parent, CookieInfo cookie, MontoyaApi api) {
        super(parent, "Edit Cookie: " + cookie.getName(), true);
        this.originalCookie = cookie;
        this.api = api;

        initializeUI();
        populateFields();
        pack();
        setLocationRelativeTo(parent);
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Create form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        // Cookie Name (read-only)
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Cookie Name:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        nameField = new JTextField(30);
        nameField.setEditable(false);
        nameField.setBackground(Color.LIGHT_GRAY);
        formPanel.add(nameField, gbc);

        // Vendor
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Vendor:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        vendorField = new JTextField(30);
        formPanel.add(vendorField, gbc);

        // Category
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Category:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        categoryCombo = new JComboBox<>(CookieCategory.values());
        formPanel.add(categoryCombo, gbc);

        // Purpose
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        formPanel.add(new JLabel("Purpose:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        purposeArea = new JTextArea(4, 30);
        purposeArea.setLineWrap(true);
        purposeArea.setWrapStyleWord(true);
        JScrollPane purposeScroll = new JScrollPane(purposeArea);
        formPanel.add(purposeScroll, gbc);

        // Privacy Impact
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        formPanel.add(new JLabel("Privacy Impact:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        privacyCombo = new JComboBox<>(PrivacyImpact.values());
        formPanel.add(privacyCombo, gbc);

        // Third Party
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Third Party:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        thirdPartyCheckbox = new JCheckBox("This is a third-party cookie");
        formPanel.add(thirdPartyCheckbox, gbc);

        // Expiration
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Typical Expiration:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        expirationField = new JTextField(30);
        expirationField.setToolTipText("e.g., Session, 1 year, 30 days");
        formPanel.add(expirationField, gbc);

        // Confidence Score
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Confidence Score:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(0.0, 0.0, 1.0, 0.01);
        confidenceSpinner = new JSpinner(spinnerModel);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(confidenceSpinner, "0.00");
        confidenceSpinner.setEditor(editor);
        formPanel.add(confidenceSpinner, gbc);

        add(formPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        buttonPanel.add(saveButton);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> {
            confirmed = false;
            dispose();
        });
        buttonPanel.add(cancelButton);

        add(buttonPanel, BorderLayout.SOUTH);

        // Apply Burp theme
        api.userInterface().applyThemeToComponent(this);
    }

    private void populateFields() {
        nameField.setText(originalCookie.getName());
        vendorField.setText(originalCookie.getVendor());

        if (originalCookie.getCategory() != null) {
            categoryCombo.setSelectedItem(originalCookie.getCategory());
        }

        purposeArea.setText(originalCookie.getPurpose());

        if (originalCookie.getPrivacyImpact() != null) {
            privacyCombo.setSelectedItem(originalCookie.getPrivacyImpact());
        }

        thirdPartyCheckbox.setSelected(originalCookie.isThirdParty());
        expirationField.setText(originalCookie.getTypicalExpiration());
        confidenceSpinner.setValue(originalCookie.getConfidenceScore());
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public CookieInfo getUpdatedCookie() {
        CookieInfo updatedCookie = new CookieInfo(
            originalCookie.getName(),
            vendorField.getText(),
            (CookieCategory) categoryCombo.getSelectedItem(),
            purposeArea.getText()
        );

        // Set additional fields
        updatedCookie.setPrivacyImpact((PrivacyImpact) privacyCombo.getSelectedItem());
        updatedCookie.setThirdParty(thirdPartyCheckbox.isSelected());
        updatedCookie.setTypicalExpiration(expirationField.getText());
        updatedCookie.setConfidenceScore((Double) confidenceSpinner.getValue());
        updatedCookie.setSource(originalCookie.getSource()); // Keep original source

        return updatedCookie;
    }
}
