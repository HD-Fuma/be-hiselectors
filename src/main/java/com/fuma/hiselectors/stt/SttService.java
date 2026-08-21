package com.fuma.hiselectors.stt;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SttService {

    private final YoutubeSttClient youtubeClient;
    private final InstagramSttClient instagramClient;

    public SttResult transcribe(String snsCode, String snsContentId) {
        if (!SnsPlatform.YOUTUBE.name().equalsIgnoreCase(snsCode)) {
            throw new BusinessException(ErrorCode.STT_SNS_NOT_SUPPORTED);
        }
        return youtubeClient.transcribe(snsContentId);
    }

    /** 인스타 릴스: 파이썬 워커가 취득·STT·OCR·분석까지 수행. */
    public InstagramAnalysisResult analyzeInstagramReel(String reelUrl) {
        return instagramClient.analyze(reelUrl);
    }
}
