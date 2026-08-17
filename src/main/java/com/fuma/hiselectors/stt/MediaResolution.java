package com.fuma.hiselectors.stt;

public enum MediaResolution {
    LOW, MEDIUM, HIGH;

    public String toApiValue() {
        return "MEDIA_RESOLUTION_" + name();
    }
}
