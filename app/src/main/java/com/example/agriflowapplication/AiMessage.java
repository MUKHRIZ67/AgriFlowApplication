package com.example.agriflowapplication;

public class AiMessage {
    public static final int TYPE_USER = 0;
    public static final int TYPE_AI = 1;

    private int type;
    private String text;
    private long timestamp;
    private boolean isLoading;

    public AiMessage() {}

    public AiMessage(int type, String text) {
        this.type = type;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
        this.isLoading = false;
    }

    public AiMessage(int type, String text, boolean isLoading) {
        this.type = type;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
        this.isLoading = isLoading;
    }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isLoading() { return isLoading; }
    public void setLoading(boolean loading) { isLoading = loading; }
}
