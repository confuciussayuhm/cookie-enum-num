package com.github.cookieenum.models;

import burp.api.montoya.http.message.HttpRequestResponse;

/**
 * Represents a single row in the analysis results table with associated request/response
 */
public class AnalysisResultRow {
    private final String cookieName;
    private String status;
    private String required;
    private String responseCode;
    private String details;
    private HttpRequestResponse requestResponse;

    public AnalysisResultRow(String cookieName, String status, String required,
                           String responseCode, String details, HttpRequestResponse requestResponse) {
        this.cookieName = cookieName;
        this.status = status;
        this.required = required;
        this.responseCode = responseCode;
        this.details = details;
        this.requestResponse = requestResponse;
    }

    public String getCookieName() { return cookieName; }
    public String getStatus() { return status; }
    public String getRequired() { return required; }
    public String getResponseCode() { return responseCode; }
    public String getDetails() { return details; }
    public HttpRequestResponse getRequestResponse() { return requestResponse; }

    public void setStatus(String status) { this.status = status; }
    public void setRequired(String required) { this.required = required; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }
    public void setDetails(String details) { this.details = details; }
    public void setRequestResponse(HttpRequestResponse requestResponse) {
        this.requestResponse = requestResponse;
    }

    public Object[] toTableRow() {
        return new Object[]{cookieName, status, required, responseCode, details};
    }
}
