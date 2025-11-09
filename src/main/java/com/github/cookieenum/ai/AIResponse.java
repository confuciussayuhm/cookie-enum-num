package com.github.cookieenum.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Model for AI response JSON structure
 */
public class AIResponse {
    private String vendor;
    private String category;
    private String purpose;
    private String privacyImpact;
    private Boolean isThirdParty;
    private String typicalExpiration;
    private List<String> commonDomains;
    private Double confidence;
    private String notes;

    public AIResponse() {
        this.commonDomains = new ArrayList<>();
        this.confidence = 0.0;
    }

    // Getters and Setters
    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getPrivacyImpact() {
        return privacyImpact;
    }

    public void setPrivacyImpact(String privacyImpact) {
        this.privacyImpact = privacyImpact;
    }

    public Boolean getIsThirdParty() {
        return isThirdParty;
    }

    public void setIsThirdParty(Boolean isThirdParty) {
        this.isThirdParty = isThirdParty;
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

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        return String.format("AIResponse{vendor='%s', category='%s', confidence=%.2f}",
                vendor, category, confidence);
    }
}
