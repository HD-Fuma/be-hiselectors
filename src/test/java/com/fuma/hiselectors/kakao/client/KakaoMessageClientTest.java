package com.fuma.hiselectors.kakao.client;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fuma.hiselectors.kakao.dto.DefaultTextTemplate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoMessageClientTest {

    @Test
    @DisplayName("친구 UUID와 TEXT 템플릿을 친구 메시지 API로 전송한다")
    void sendFriend() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoMessageClient client = new KakaoMessageClient(builder.build(), null);
        server.expect(requestTo("https://kapi.kakao.com/v1/api/talk/friends/message/default/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andExpect(content().string(containsString("receiver_uuids")))
                .andExpect(content().string(containsString("template_object")))
                .andExpect(content().string(containsString("uuid-1")))
                .andRespond(withSuccess("{\"successful_receiver_uuids\":[\"uuid-1\"]}",
                        MediaType.APPLICATION_JSON));

        client.sendFriend("access-token", "uuid-1", template());
        server.verify();
    }

    @Test
    @DisplayName("TEXT 템플릿을 나에게 보내기 API로 전송한다")
    void sendMe() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoMessageClient client = new KakaoMessageClient(builder.build(), null);
        server.expect(requestTo("https://kapi.kakao.com/v2/api/talk/memo/default/send"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"result_code\":0}", MediaType.APPLICATION_JSON));
        client.sendMe("access-token", template());
        server.verify();
    }

    private DefaultTextTemplate template() {
        DefaultTextTemplate.Link link = new DefaultTextTemplate.Link(
                "https://example.com", "https://m.example.com");
        return new DefaultTextTemplate("text", "제목\n\n설명", link,
                List.of(new DefaultTextTemplate.Button("확인", link)));
    }
}
