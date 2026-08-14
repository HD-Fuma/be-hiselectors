package com.fuma.hiselectors.stt;

public record SttResult(String speech, String caption) {

    public static SttResult empty() {
        return new SttResult("", "");
    }
}
