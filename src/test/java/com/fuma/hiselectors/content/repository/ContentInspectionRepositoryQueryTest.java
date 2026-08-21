package com.fuma.hiselectors.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.content.dto.ContentInspectionQueryRow;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsGeneration;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

class ContentInspectionRepositoryQueryTest {

    @Test
    void findsOnlyLiveContentsForGenerationWithLatestVersionAndMatchingAccount() throws Exception {
        String databaseName = "content_inspection_query_"
                + UUID.randomUUID().toString().replace("-", "");
        String jdbcUrl = "jdbc:h2:mem:" + databaseName;

        try (Connection keeperConnection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            createSchema(keeperConnection);
            seedRows(keeperConnection);

            StandardServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                    .applySetting("jakarta.persistence.jdbc.driver", "org.h2.Driver")
                    .applySetting("jakarta.persistence.jdbc.url", jdbcUrl)
                    .applySetting("jakarta.persistence.jdbc.user", "sa")
                    .applySetting("jakarta.persistence.jdbc.password", "")
                    .applySetting("hibernate.hbm2ddl.auto", "validate")
                    .applySetting("hibernate.show_sql", "false")
                    .build();
            try {
                try (SessionFactory sessionFactory = new MetadataSources(serviceRegistry)
                        .addAnnotatedClass(Application.class)
                        .addAnnotatedClass(Selectors.class)
                        .addAnnotatedClass(SelectorsGeneration.class)
                        .addAnnotatedClass(SelectorsSnsAccount.class)
                        .addAnnotatedClass(Content.class)
                        .addAnnotatedClass(ContentVersion.class)
                        .buildMetadata()
                        .buildSessionFactory()) {
                    EntityManager sharedEntityManager = SharedEntityManagerCreator
                            .createSharedEntityManager(sessionFactory);
                    ContentRepository repository = new JpaRepositoryFactory(sharedEntityManager)
                            .getRepository(ContentRepository.class);

                    Page<ContentInspectionQueryRow> firstPage = repository
                            .findInspectionRowsByGenerationId(10L, PageRequest.of(0, 2));

                    assertThat(firstPage.getTotalElements()).isEqualTo(3);
                    assertThat(firstPage.getTotalPages()).isEqualTo(2);
                    assertThat(firstPage.getContent())
                            .extracting(ContentInspectionQueryRow::contentId)
                            .containsExactly(60L, 20L);
                    assertThat(firstPage.getContent().get(1)).satisfies(row -> {
                        assertThat(row.latestVersionId()).isEqualTo(202L);
                        assertThat(row.latestVersionNo()).isEqualTo(1L);
                        assertThat(row.accountId()).isEqualTo("active-instagram");
                        assertThat(row.profileImageUrl())
                                .isEqualTo("https://cdn.example.com/profile.jpg");
                    });

                    Page<ContentInspectionQueryRow> secondPage = repository
                            .findInspectionRowsByGenerationId(10L, PageRequest.of(1, 2));
                    assertThat(secondPage.getContent())
                            .extracting(ContentInspectionQueryRow::contentId)
                            .containsExactly(10L);
                    assertThat(secondPage.getContent().getFirst().latestVersionId())
                            .isEqualTo(102L);
                    assertThat(secondPage.getContent().getFirst().latestVersionNo())
                            .isEqualTo(2L);
                }
            } finally {
                StandardServiceRegistryBuilder.destroy(serviceRegistry);
            }
        }
    }

    private void createSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table application (
                        application_id bigint generated by default as identity primary key,
                        user_id bigint not null,
                        generation_id bigint not null,
                        sns_code enum ('YOUTUBE', 'INSTAGRAM') not null,
                        sns_account_id varchar(200) not null,
                        follower_count bigint,
                        last_content_at timestamp,
                        engagement_rate numeric(5, 2),
                        alarm_yn boolean not null,
                        policy_agreed_at timestamp not null,
                        status enum ('PENDING', 'APPROVED', 'REJECTED') not null,
                        media_collection_status varchar(20) not null,
                        media_collection_retry_count integer not null,
                        media_collected_at timestamp,
                        media_collection_error varchar(500),
                        created_at timestamp,
                        updated_at timestamp,
                        constraint uq_application_user_generation unique (user_id, generation_id)
                    )
                    """);
            statement.execute("""
                    create table selectors (
                        selectors_id bigint generated by default as identity primary key,
                        application_id bigint,
                        user_id bigint,
                        selectors_role_id varchar(20) not null,
                        selectors_code varchar(20),
                        selectors_nickname varchar(20),
                        is_deleted boolean not null,
                        created_at timestamp,
                        updated_at timestamp
                    )
                    """);
            statement.execute("""
                    create table selectors_generation (
                        selectors_generation_id bigint generated by default as identity primary key,
                        selectors_id bigint not null,
                        generation_id bigint not null,
                        created_at timestamp
                    )
                    """);
            statement.execute("""
                    create table selectors_sns_account (
                        selectors_sns_account_id bigint generated by default as identity primary key,
                        selectors_id bigint not null,
                        sns_code enum ('YOUTUBE', 'INSTAGRAM'),
                        account_id varchar(100),
                        follower_count bigint,
                        is_deleted boolean not null,
                        last_collected_at timestamp,
                        profile_image_url varchar(500),
                        created_at timestamp,
                        updated_at timestamp
                    )
                    """);
            statement.execute("""
                    create table content (
                        content_id bigint generated by default as identity primary key,
                        selectors_id bigint not null,
                        sns_code enum ('YOUTUBE', 'INSTAGRAM') not null,
                        sns_content_id varchar(200) not null,
                        content_url varchar(500) not null,
                        content_type enum ('SHORT_FORM', 'LONG_FORM', 'SHORTS', 'FEED') not null,
                        last_version_no bigint not null,
                        is_deleted boolean not null,
                        created_at timestamp,
                        updated_at timestamp,
                        constraint uq_content_sns unique (sns_code, sns_content_id)
                    )
                    """);
            statement.execute("""
                    create table content_version (
                        content_version_id bigint generated by default as identity primary key,
                        content_id bigint not null,
                        admin_id bigint,
                        version_no bigint not null,
                        content_hash varchar(64) not null,
                        created_at timestamp not null,
                        status varchar(20),
                        inspected_at timestamp,
                        constraint uq_content_version_content_no unique (content_id, version_no)
                    )
                    """);
        }
    }

    private void seedRows(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into application (
                        application_id, user_id, generation_id, sns_code, sns_account_id,
                        alarm_yn, policy_agreed_at, status,
                        media_collection_status, media_collection_retry_count
                    ) values
                        (100, 1, 10, 'INSTAGRAM', 'current', false,
                            timestamp '2026-08-01 00:00:00', 'APPROVED', 'DONE', 0),
                        (200, 2, 20, 'INSTAGRAM', 'other', false,
                            timestamp '2026-08-01 00:00:00', 'APPROVED', 'DONE', 0)
                    """);
            statement.executeUpdate("""
                    insert into selectors (
                        selectors_id, application_id, user_id, selectors_role_id,
                        selectors_nickname, is_deleted
                    ) values
                        (101, 100, 1, 'MEMBER', '현재 셀렉터', false),
                        (201, 200, 2, 'MEMBER', '이전 셀렉터', false)
                    """);
            statement.executeUpdate("""
                    insert into selectors_generation (
                        selectors_generation_id, selectors_id, generation_id
                    ) values
                        (10001, 101, 10),
                        (20001, 201, 20)
                    """);
            statement.executeUpdate("""
                    insert into selectors_sns_account (
                        selectors_sns_account_id, selectors_id, sns_code, account_id,
                        is_deleted, profile_image_url
                    ) values
                        (1001, 101, 'INSTAGRAM', 'active-instagram', false,
                            'https://cdn.example.com/profile.jpg'),
                        (1002, 101, 'YOUTUBE', 'deleted-youtube', true, null),
                        (2001, 201, 'INSTAGRAM', 'other-instagram', false, null)
                    """);
            statement.executeUpdate("""
                    insert into content (
                        content_id, selectors_id, sns_code, sns_content_id, content_url,
                        content_type, last_version_no, is_deleted, created_at
                    ) values
                        (10, 101, 'INSTAGRAM', 'old', 'https://instagram.com/p/old',
                            'FEED', 2, false, timestamp '2026-08-10 10:00:00'),
                        (20, 101, 'INSTAGRAM', 'new', 'https://instagram.com/p/new',
                            'SHORT_FORM', 1, false, timestamp '2026-08-11 10:00:00'),
                        (30, 201, 'INSTAGRAM', 'other-generation',
                            'https://instagram.com/p/other', 'FEED', 1, false,
                            timestamp '2026-08-12 10:00:00'),
                        (40, 101, 'INSTAGRAM', 'deleted', 'https://instagram.com/p/deleted',
                            'FEED', 1, true, timestamp '2026-08-13 10:00:00'),
                        (50, 101, 'YOUTUBE', 'no-live-account',
                            'https://youtube.com/watch?v=no-live-account', 'LONG_FORM', 1, false,
                            timestamp '2026-08-14 10:00:00'),
                        (60, 101, 'INSTAGRAM', 'tie-breaker',
                            'https://instagram.com/p/tie-breaker', 'FEED', 1, false,
                            timestamp '2026-08-11 10:00:00')
                    """);
            statement.executeUpdate("""
                    insert into content_version (
                        content_version_id, content_id, version_no, content_hash, created_at,
                        status, inspected_at
                    ) values
                        (101, 10, 1, repeat('a', 64), timestamp '2026-08-10 11:00:00',
                            null, null),
                        (102, 10, 2, repeat('b', 64), timestamp '2026-08-12 11:00:00',
                            'APPROVED', timestamp '2026-08-12 12:00:00'),
                        (202, 20, 1, repeat('c', 64), timestamp '2026-08-11 11:00:00',
                            null, null),
                        (302, 30, 1, repeat('d', 64), timestamp '2026-08-12 11:00:00',
                            null, null),
                        (402, 40, 1, repeat('e', 64), timestamp '2026-08-13 11:00:00',
                            null, null),
                        (502, 50, 1, repeat('f', 64), timestamp '2026-08-14 11:00:00',
                            null, null),
                        (602, 60, 1, repeat('0', 64), timestamp '2026-08-11 11:00:00',
                            null, null)
                    """);
        }
    }
}
