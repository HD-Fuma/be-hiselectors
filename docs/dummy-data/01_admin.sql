    -- hi_selectors 성능 테스트용 관리자 더미 데이터
    --
    -- kakao_sender_connection_id = 1 연결을 보장한 뒤,
-- admin 데이터를 전부 삭제하고 admin1~admin10을 다시 생성한다.
-- 관리자 FK를 참조하는 하위 데이터를 먼저 삭제한 뒤 실행해야 한다.
-- 기존 카카오 연결이 있으면 토큰이나 상태를 덮어쓰지 않는다.
-- 주의: 아래에는 암호화된 실제 카카오 토큰이 포함되어 있으므로 커밋하지 않는다.

    USE `hi_selectors`;

    -- 실제 연결이 이미 있으면 그대로 유지한다.
-- 연결이 없으면 원본 연결 정보를 생성한다.
    INSERT INTO kakao_sender_connection (
        kakao_sender_connection_id,
        kakao_user_id,
        sender_name,
        access_token_encrypted,
        refresh_token_encrypted,
        access_token_expires_at,
        refresh_token_expires_at,
        status,
        created_at,
        updated_at
    )
    SELECT
        1,
        5037392895,
        '위재원',
    'PVJtVw5XaxORZwD76ZFQi1f5vmzen9nq2pBq7VXvI1qWezATCEt0cVxtEecy8vcUw5yh3gQDdW1mK3jFehHqkOJR7yn29mpWaEa0wmJyDncMI3vafBE+RdM1ciI=',
    'diwlD5k9zVPpOkicarM5fS33t/WRR5mkc75I7ek+e+TWMUMisbHg62XGWDWhNVX1+2U8XxXh5nyVO4Dyq2NdMjuKq50EyzukqxuXsfgkcHkJmwBPkx7y4P9RIeA=',
        '2026-08-31 08:23:16',
        '2026-10-12 19:07:13',
        'CONNECTED',
        '2026-08-13 19:07:14',
        '2026-08-31 02:23:17'
    WHERE NOT EXISTS (
        SELECT 1
        FROM kakao_sender_connection
        WHERE kakao_sender_connection_id = 1
);

    DELETE FROM admin;
    ALTER TABLE admin AUTO_INCREMENT = 1;

    INSERT INTO admin (
        admin_id,
        login_id,
        password,
        name,
        role,
        kakao_sender_connection_id,
        created_at,
        updated_at,
        is_deleted
    )
    VALUES
        (1, 'admin1', '$2a$10$yqyTDn5aDasCKPMZTGc3LuF6SaFqcZsYKG6G2D4yFhtjslZeJcpaW', '이유경', 'ADMIN', 1, '2026-01-02 09:00:00', '2026-08-11 07:47:27', 0),
        (2, 'admin2', '$2a$10$yqyTDn5aDasCKPMZTGc3LuF6SaFqcZsYKG6G2D4yFhtjslZeJcpaW', '홍준표', 'ADMIN', 1, '2026-01-02 09:00:00', '2026-08-11 07:47:27', 0),
        (3, 'admin3', '$2a$10$yqyTDn5aDasCKPMZTGc3LuF6SaFqcZsYKG6G2D4yFhtjslZeJcpaW', '공채연', 'ADMIN', 1, '2026-01-02 09:00:00', '2026-08-11 07:47:27', 0),
        (4, 'admin4', '$2a$10$yqyTDn5aDasCKPMZTGc3LuF6SaFqcZsYKG6G2D4yFhtjslZeJcpaW', '위재원', 'ADMIN', 1, '2026-01-02 09:00:00', '2026-08-13 19:07:14', 0),
        (5, 'admin5', '$2a$10$yqyTDn5aDasCKPMZTGc3LuF6SaFqcZsYKG6G2D4yFhtjslZeJcpaW', '홍길동', 'ADMIN', 1, '2026-08-11 06:47:40', '2026-08-11 01:30:00', 1),
        (6, 'admin6', '$2a$10$yqyTDn5aDasCKPMZTGc3LuF6SaFqcZsYKG6G2D4yFhtjslZeJcpaW', '김서준', 'ADMIN', 1, '2026-08-31 09:00:00', '2026-08-31 09:00:00', 0),
        (7, 'admin7', '$2a$10$yqyTDn5aDasCKPMZTGc3LuF6SaFqcZsYKG6G2D4yFhtjslZeJcpaW', '박지우', 'ADMIN', 1, '2026-08-31 09:00:00', '2026-08-31 09:00:00', 0),
        (8, 'admin8', '$2a$10$yqyTDn5aDasCKPMZTGc3LuF6SaFqcZsYKG6G2D4yFhtjslZeJcpaW', '이도윤', 'ADMIN', 1, '2026-08-31 09:00:00', '2026-08-31 09:00:00', 0),
        (9, 'admin9', '$2a$10$yqyTDn5aDasCKPMZTGc3LuF6SaFqcZsYKG6G2D4yFhtjslZeJcpaW', '최하린', 'ADMIN', 1, '2026-08-31 09:00:00', '2026-08-31 09:00:00', 0),
        (10, 'admin10', '$2a$10$yqyTDn5aDasCKPMZTGc3LuF6SaFqcZsYKG6G2D4yFhtjslZeJcpaW', '정민재', 'ADMIN', 1, '2026-08-31 09:00:00', '2026-08-31 09:00:00', 0);

    SELECT
        admin_id,
        login_id,
        name,
        role,
        kakao_sender_connection_id,
        created_at,
        updated_at,
        is_deleted
    FROM admin
    ORDER BY admin_id;
