package com.fuma.hiselectors.inspection.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class InspectionPromptProvider {

    public static final String AI_PROMPT_VERSION = "content-inspection-v8";
    public static final String YOUTUBE_EXTRACTION_PROMPT_VERSION = "youtube-extraction-v6";
    public static final String CONTENT_REPORT_PROMPT_VERSION = "content-report-v1";

    private final String aiPrompt = read("prompts/content-inspection.txt");
    private final String contentReportPrompt = read("prompts/content-report.txt");
    private final String youtubeExtractionPrompt = read("prompts/youtube-extraction.txt");
    private final String youtubeReportExtractionPrompt =
            read("prompts/youtube-report-extraction.txt");

    public String aiPrompt() {
        return aiPrompt;
    }

    public String contentReportPrompt() {
        return contentReportPrompt;
    }

    public String youtubeExtractionPrompt() {
        return youtubeExtractionPrompt;
    }

    public String youtubeExtractionPrompt(Long durationMs) {
        String duration = durationMs == null || durationMs <= 0
                ? "unknown"
                : Long.toString(durationMs);
        return youtubeExtractionPrompt.formatted(duration);
    }

    public String youtubeSttPrompt() {
        return youtubeReportExtractionPrompt;
    }

    private String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("프롬프트를 읽을 수 없습니다: " + path, e);
        }
    }
}
