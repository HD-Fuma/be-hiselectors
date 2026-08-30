package com.fuma.hiselectors.stt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class YoutubeSttClientTest {

    @Test
    void 롱폼은_전체_타임라인에서_총_5분을_분산_선택한다() {
        assertThat(YoutubeSttClient.windows(1_200L)).containsExactly(
                new YoutubeSttClient.VideoWindow(0, 60),
                new YoutubeSttClient.VideoWindow(285, 345),
                new YoutubeSttClient.VideoWindow(570, 630),
                new YoutubeSttClient.VideoWindow(855, 915),
                new YoutubeSttClient.VideoWindow(1_140, 1_200));
    }

    @Test
    void 영상이_5분_이하이거나_길이_미상이면_기존처럼_한_구간만_선택한다() {
        assertThat(YoutubeSttClient.windows(180L))
                .isEqualTo(List.of(new YoutubeSttClient.VideoWindow(0, 180)));
        assertThat(YoutubeSttClient.windows(null))
                .isEqualTo(List.of(new YoutubeSttClient.VideoWindow(0, 300)));
    }
}
