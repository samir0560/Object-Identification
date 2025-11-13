package com.example.demo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "identification_results")
public class IdentificationResult {
    
    @Id
    private String id;
    
    private String result;
    private String fileName;
    private String mimeType;
    private LocalDateTime createdAt;
    private String userId; // In a real app, this would link to a user
    
    public IdentificationResult() {
        this.createdAt = LocalDateTime.now();
    }
    
    public IdentificationResult(String result, String fileName, String mimeType) {
        this.result = result;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.createdAt = LocalDateTime.now();
    }
    
    public IdentificationResult(String result, String fileName, String mimeType, String userId) {
        this.result = result;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.userId = userId;
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getResult() {
        return result;
    }
    
    public void setResult(String result) {
        this.result = result;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public String getMimeType() {
        return mimeType;
    }
    
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
}

