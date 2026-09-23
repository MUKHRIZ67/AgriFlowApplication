package com.example.agriflowapplication;

import com.google.firebase.Timestamp;

public class IrrigationHistoryRecord {
    private String id;
    private String irrigationType;
    private String action;
    private String startedBy;
    private String source;
    private Timestamp timestamp;

    public IrrigationHistoryRecord() {}

    public IrrigationHistoryRecord(String irrigationType, String action, String startedBy, String source, Timestamp timestamp) {
        this.irrigationType = irrigationType;
        this.action = action;
        this.startedBy = startedBy;
        this.source = source;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getIrrigationType() { return irrigationType; }
    public void setIrrigationType(String irrigationType) { this.irrigationType = irrigationType; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getStartedBy() { return startedBy; }
    public void setStartedBy(String startedBy) { this.startedBy = startedBy; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }
}
