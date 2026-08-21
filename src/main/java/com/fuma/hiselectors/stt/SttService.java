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

    /**
     * 인스타 릴스: 파이썬 워커가 Graph API media_url(공식 API)로 취득·STT·OCR·분석까지 수행.
     * media_url 없으면(저작권 릴스) thumbnail_url 로 폴백. 스크래핑(yt-dlp) 미사용.
     */
    public InstagramAnalysisResult analyzeInstagramReel(String mediaUrl, String thumbnailUrl) {
        return instagramClient.analyze(mediaUrl, thumbnailUrl);
    }
}
