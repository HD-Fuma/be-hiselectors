package com.fuma.hiselectors.proposal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.creator.model.CreatorPool;
import com.fuma.hiselectors.proposal.config.ProposalMailProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class ProposalMailServiceTest {

    @Test
    void 편집한_제목과_본문의_변수를_치환해_평문으로_발송한다() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        ProposalMailService service = new ProposalMailService(
                mailSender,
                new ProposalMailProperties("매니저", "admin@example.com", "https://example.com"));
        ReflectionTestUtils.setField(service, "senderAddress", "sender@example.com");
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        CreatorPool creator = CreatorPool.builder()
                .snsCode("YOUTUBE")
                .accountId("UC-1")
                .email("creator@example.com")
                .build();
        Admin admin = Admin.builder().loginId("mgr").name("홍길동").role("ADMIN").build();

        service.send(creator, admin, "${creatorName}님 제안", "담당자: ${adminName}");

        assertThat(message.getSubject()).isEqualTo("UC-1님 제안");
        assertThat(message.getContent()).isEqualTo("담당자: 홍길동");
        verify(mailSender).send(message);
    }
}
