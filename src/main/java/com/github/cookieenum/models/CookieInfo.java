package com.github.cookieenum.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Complete information about a cookie
 */
public class CookieInfo {
    private Long id;
    private String name;
    private String vendor;
    private CookieCategory category;
    private String purpose;
    private PrivacyImpact privacyImpact;
    private boolean isThirdParty;
    private String typicalExpiration;
    private List<String> commonDomains;
    private String notes;
    private double confidenceScore;
    private Instant createdAt;
    private Instant updatedAt;
    private String source;

    public CookieInfo() {
        this.commonDomains = new ArrayList<>();
        this.confidenceScore = 0.0;
        this.source = "ai";
    }

    public CookieInfo(String name, String vendor, CookieCategory category, String purpose) {
        this();
        this.name = name;
        this.vendor = vendor;
        this.category = category;
        this.purpose = purpose;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public CookieCategory getCategory() {
        return category;
    }

    public void setCategory(CookieCategory category) {
        this.category = category;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public PrivacyImpact getPrivacyImpact() {
        return privacyImpact;
    }

    public void setPrivacyImpact(PrivacyImpact privacyImpact) {
        this.privacyImpact = privacyImpact;
    }

    public boolean isThirdParty() {
        return isThirdParty;
    }

    public void setThirdParty(boolean thirdParty) {
        isThirdParty = thirdParty;
    }

    public String getTypicalExpiration() {
        return typicalExpiration;
    }

    public void setTypicalExpiration(String typicalExpiration) {
        this.typicalExpiration = typicalExpiration;
    }

    public List<String> getCommonDomains() {
        return commonDomains;
    }

    public void setCommonDomains(List<String> commonDomains) {
        this.commonDomains = commonDomains;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @Override
    public String toString() {
        return String.format("CookieInfo{name='%s', vendor='%s', category=%s, privacy=%s}",
                name, vendor, category, privacyImpact);
    }
}
