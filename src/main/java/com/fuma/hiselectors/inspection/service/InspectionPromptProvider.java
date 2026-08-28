package com.fuma.hiselectors.inspection.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class InspectionPromptProvider {

    public static final String AI_PROMPT_VERSION = "content-inspection-v3";
    public static final String YOUTUBE_EXTRACTION_PROMPT_VERSION = "youtube-extraction-v2";

    private final String aiPrompt = read("prompts/content-inspection.txt");
    private final String youtubeExtractionPrompt = read("prompts/youtube-extraction.txt");
    private final String youtubeSttOutputFormat = read("prompts/youtube-stt-output-format.txt");

    public String aiPrompt() {
        return aiPrompt;
    }

    public String youtubeExtractionPrompt() {
        return youtubeExtractionPrompt;
    }

    public String youtubeSttPrompt() {
        return youtubeExtractionPrompt + "\n\n" + youtubeSttOutputFormat;
    }

    private String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("검수 프롬프트를 읽을 수 없습니다: " + path, e);
        }
    }
}
