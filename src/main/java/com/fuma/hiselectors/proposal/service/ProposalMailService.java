package com.fuma.hiselectors.proposal.service;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.creator.model.CreatorPool;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.proposal.config.ProposalMailProperties;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

/** 제안 메일 본문을 템플릿에서 만들어 Gmail SMTP 로 발송한다. */
@Service
@RequiredArgsConstructor
public class ProposalMailService {

    private static final String TEMPLATE_PATH = "mail/proposal-email.txt";
    private static final String FROM_NAME = "셀렉터스 크리에이터 팀";
    private static final String SUBJECT_DELIMITER = "\n---\n";

    private final JavaMailSender mailSender;
    private final ProposalMailProperties properties;

    @Value("${spring.mail.username:}")
    private String senderAddress;

    private String subjectTemplate;
    private String bodyTemplate;

    @PostConstruct
    void loadTemplate() {
        String raw = read(TEMPLATE_PATH);
        int split = raw.indexOf(SUBJECT_DELIMITER);
        if (split < 0) {
            throw new IllegalStateException("제안 메일 템플릿에 제목/본문 구분자(---)가 없습니다.");
        }
        this.subjectTemplate = raw.substring(0, split).trim();
        this.bodyTemplate = raw.substring(split + SUBJECT_DELIMITER.length());
    }

    /** 크리에이터에게 제안 메일을 보낸다. 실패하면 예외를 던져 이력 저장까지 롤백시킨다. */
    public void send(CreatorPool creator, Admin admin) {
        send(creator, admin, subjectTemplate, bodyTemplate);
    }

    /** 관리자가 편집한 제목과 본문으로 제안 메일을 보낸다. */
    public void send(CreatorPool creator, Admin admin, String subjectTemplate, String bodyTemplate) {
        Map<String, String> vars = Map.of(
                "${creatorName}", creatorName(creator),
                "${adminName}", nullToEmpty(admin.getName()),
                "${adminPosition}", nullToEmpty(properties.adminPosition()),
                "${adminEmail}", nullToEmpty(properties.adminEmail()),
                "${proposalLink}", nullToEmpty(properties.applyUrl()));

        String subject = substitute(subjectTemplate.trim(), vars);
        String body = substitute(bodyTemplate, vars);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(senderAddress, FROM_NAME);
            helper.setTo(creator.getEmail());
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
        } catch (MessagingException | java.io.UnsupportedEncodingException | MailException e) {
            throw new BusinessException(ErrorCode.PROPOSAL_MAIL_SEND_FAILED);
        }
    }

    private static String substitute(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String creatorName(CreatorPool creator) {
        return creator.getCreatorName() == null || creator.getCreatorName().isBlank()
                ? nullToEmpty(creator.getAccountId())
                : creator.getCreatorName();
    }

    private static String read(String path) {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("제안 메일 템플릿을 읽을 수 없습니다: " + path, e);
        }
    }
}
