
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `hi_selectors` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `hi_selectors`;
DROP TABLE IF EXISTS `admin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin` (
  `admin_id` bigint NOT NULL AUTO_INCREMENT COMMENT '관리자ID',
  `login_id` varchar(30) DEFAULT NULL COMMENT '아이디',
  `password` varchar(255) DEFAULT NULL COMMENT '비밀번호',
  `name` varchar(50) DEFAULT NULL COMMENT '회원명',
  `role` varchar(20) DEFAULT NULL COMMENT '권한',
  `kakao_sender_connection_id` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL COMMENT '생성 일시',
  `updated_at` timestamp NULL DEFAULT NULL COMMENT '수정 일시',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft-delete flag (0=active, 1=deleted)',
  PRIMARY KEY (`admin_id`),
  KEY `fk_admin_kakao_sender_connection` (`kakao_sender_connection_id`),
  CONSTRAINT `fk_admin_kakao_sender_connection` FOREIGN KEY (`kakao_sender_connection_id`) REFERENCES `kakao_sender_connection` (`kakao_sender_connection_id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `application`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `application` (
  `application_id` bigint NOT NULL AUTO_INCREMENT COMMENT '지원서 ID',
  `user_id` bigint DEFAULT NULL COMMENT '사용자 ID',
  `sns_code` varchar(20) DEFAULT NULL COMMENT 'SNS 코드',
  `generation_id` bigint NOT NULL COMMENT '기수 ID',
  `admin_id` bigint DEFAULT NULL,
  `sns_account_id` varchar(200) NOT NULL,
  `policy_agreed_at` timestamp NULL DEFAULT NULL COMMENT '약관동의일시',
  `alarm_yn` bit(1) NOT NULL,
  `follower_count` bigint DEFAULT NULL COMMENT '팔로워수',
  `content_count` bigint DEFAULT NULL COMMENT 'í”Œëž«í¼ì´ ì œê³µí•œ ì „ì²´ ê³µê°œ ì½˜í…ì¸  ìˆ˜',
  `last_content_at` timestamp NULL DEFAULT NULL COMMENT '최근 활동일',
  `engagement_rate` decimal(8,2) DEFAULT NULL,
  `inspected_at` timestamp NULL DEFAULT NULL COMMENT '검수 일시',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '생성 일시',
  `updated_at` timestamp NULL DEFAULT NULL COMMENT '수정 일시',
  `status` varchar(20) DEFAULT NULL COMMENT 'Review status code (PENDING_REVIEW / APPROVED / REJECTED)',
  `media_collection_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / DONE / FAILED',
  `media_collection_retry_count` int NOT NULL DEFAULT '0',
  `media_collected_at` datetime(6) DEFAULT NULL,
  `media_collection_error` varchar(500) DEFAULT NULL,
  `analysis_status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `analysis_retry_count` int NOT NULL DEFAULT '0',
  `analyzed_at` datetime(6) DEFAULT NULL,
  `analysis_error` varchar(500) DEFAULT NULL,
  `profile_url` varchar(500) DEFAULT NULL,
  `profile_image_url` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`application_id`),
  UNIQUE KEY `uq_application_user_generation` (`user_id`,`generation_id`),
  KEY `FK_SNSCode_TO_Application_1` (`sns_code`),
  KEY `FK_Generation_TO_Application_1` (`generation_id`),
  KEY `FK_Admin_TO_Application_1` (`admin_id`),
  KEY `idx_application_media_collection` (`media_collection_status`,`media_collection_retry_count`,`application_id`),
  CONSTRAINT `FK_Admin_TO_Application_1` FOREIGN KEY (`admin_id`) REFERENCES `admin` (`admin_id`),
  CONSTRAINT `FK_Generation_TO_Application_1` FOREIGN KEY (`generation_id`) REFERENCES `generation` (`generation_id`),
  CONSTRAINT `FK_SNSCode_TO_Application_1` FOREIGN KEY (`sns_code`) REFERENCES `sns_code` (`sns_code`),
  CONSTRAINT `FK_Users_TO_Application_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=386 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `application_content_analysis`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `application_content_analysis` (
  `content_analysis_id` bigint NOT NULL AUTO_INCREMENT,
  `applicant_id` bigint NOT NULL,
  `content_key` varchar(200) NOT NULL,
  `source` varchar(20) DEFAULT NULL,
  `stt` longtext,
  `ocr` longtext,
  `category` varchar(30) DEFAULT NULL,
  `keywords` longtext,
  `hate_suspected` bit(1) NOT NULL DEFAULT b'0',
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`content_analysis_id`),
  UNIQUE KEY `uq_aca_content_key` (`content_key`),
  KEY `idx_aca_applicant` (`applicant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1402 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `application_media`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `application_media` (
  `application_media_id` bigint NOT NULL AUTO_INCREMENT,
  `application_id` bigint NOT NULL,
  `sns_code` varchar(20) NOT NULL COMMENT 'YOUTUBE / INSTAGRAM',
  `sns_content_id` varchar(200) NOT NULL COMMENT 'SNS ì›ë³¸ ì½˜í…ì¸  ID. ì¤‘ë³µ ìˆ˜ì§‘ ë°©ì§€ìš©',
  `content_url` text COMMENT 'YouTube watch URL 또는 Instagram permalink',
  `media_url` text COMMENT 'Instagram 이미지·영상 CDN URL. YouTube는 NULL',
  `content_type` varchar(20) DEFAULT NULL COMMENT 'SHORT_FORM / LONG_FORM / SHORTS / FEED',
  `sequence_no` int NOT NULL COMMENT 'ìˆ˜ì§‘ ìˆœì„œ. ìµœì‹ ìˆœ 0ë¶€í„°',
  `published_at` datetime(6) DEFAULT NULL COMMENT 'ê²Œì‹œ ì‹œê°',
  `view_count` bigint DEFAULT NULL,
  `like_count` bigint DEFAULT NULL,
  `comment_count` bigint DEFAULT NULL,
  `duration_seconds` bigint DEFAULT NULL,
  `collected_at` datetime(6) NOT NULL COMMENT 'ìˆ˜ì§‘ ì‹œê°',
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `sns_media_id` varchar(200) DEFAULT NULL,
  `thumbnail_url` text,
  `media_type` varchar(20) DEFAULT NULL,
  `caption` text,
  `title` text,
  `description` text,
  `media_sequence_no` int NOT NULL,
  PRIMARY KEY (`application_media_id`),
  UNIQUE KEY `uq_application_media` (`application_id`,`sns_content_id`,`sns_media_id`),
  KEY `idx_application_media_application_id` (`application_id`)
) ENGINE=InnoDB AUTO_INCREMENT=17377 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ì§€ì›ìž ì‹¬ì‚¬ìš© ìˆ˜ì§‘ ë¯¸ë””ì–´';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `application_media_url`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `application_media_url` (
  `application_media_id` bigint NOT NULL,
  `sequence_no` int NOT NULL,
  `url_type` varchar(20) NOT NULL,
  `url` text NOT NULL,
  PRIMARY KEY (`application_media_id`,`sequence_no`),
  CONSTRAINT `FK_ApplicationMedia_TO_ApplicationMediaUrl_1` FOREIGN KEY (`application_media_id`) REFERENCES `application_media` (`application_media_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `application_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `application_report` (
  `application_report_id` bigint NOT NULL AUTO_INCREMENT COMMENT '지원서리포트 ID',
  `application_id` bigint NOT NULL COMMENT '지원서 ID',
  `quantity_score` decimal(5,2) DEFAULT NULL COMMENT '정량 평가 점수',
  `quality_score` decimal(5,2) DEFAULT NULL COMMENT '정성 평가 점수',
  `summary` json DEFAULT NULL COMMENT '분석 요약',
  `category` varchar(20) DEFAULT NULL COMMENT 'Category code (UPPER_SNAKE_CASE)',
  `keywords` varchar(500) DEFAULT NULL COMMENT '키워드',
  `target` varchar(19) DEFAULT NULL COMMENT '타겟층(W1, W2 ... M1, M2 ... M6)',
  `content_style` varchar(19) DEFAULT NULL COMMENT 'Content style code (UPPER_SNAKE_CASE)',
  `tone` varchar(500) DEFAULT NULL COMMENT 'Korean tone tags separated by commas',
  `brand_history` varchar(500) DEFAULT NULL COMMENT 'Korean narrative of prior brand collaborations',
  `strength` varchar(500) DEFAULT NULL COMMENT 'Korean narrative of creator strengths',
  `cautions` varchar(500) DEFAULT NULL,
  `risks` varchar(500) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL COMMENT '생성 일시',
  `status` varchar(20) DEFAULT NULL COMMENT 'Report status code (PENDING / IN_PROGRESS / COMPLETED / FAILED)',
  `representative_content_url` text,
  `representative_content_type` varchar(20) DEFAULT NULL,
  `representative_view_count` bigint DEFAULT NULL,
  `representative_category` varchar(20) DEFAULT NULL,
  `representative_keywords` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`application_report_id`),
  KEY `FK_Application_TO_ApplicationReport_1` (`application_id`),
  CONSTRAINT `FK_Application_TO_ApplicationReport_1` FOREIGN KEY (`application_id`) REFERENCES `application` (`application_id`)
) ENGINE=InnoDB AUTO_INCREMENT=194 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `best_selectors`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `best_selectors` (
  `best_selectors_id` bigint NOT NULL AUTO_INCREMENT COMMENT '우수활동자 이력 ID',
  `best_selectors_code` varchar(20) NOT NULL COMMENT '우수활동자 이력 코드',
  `selectors_generation_id` bigint NOT NULL COMMENT '셀렉터스기수ID',
  `best_selectors_type_id` bigint NOT NULL COMMENT '우수활동자종류ID',
  `total_sales` bigint DEFAULT NULL COMMENT '총매출액',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '생성일시',
  PRIMARY KEY (`best_selectors_id`),
  UNIQUE KEY `UK_best_selectors_code` (`best_selectors_code`),
  KEY `FK_SelectorsGeneration_TO_BestSelectors_sequence_1` (`selectors_generation_id`),
  KEY `FK_BestSelectorsType_TO_BestSelectors_sequence_1` (`best_selectors_type_id`),
  CONSTRAINT `FK_BestSelectorsType_TO_BestSelectors_sequence_1` FOREIGN KEY (`best_selectors_type_id`) REFERENCES `best_selectors_type` (`best_selectors_type_id`),
  CONSTRAINT `FK_SelectorsGeneration_TO_BestSelectors_sequence_1` FOREIGN KEY (`selectors_generation_id`) REFERENCES `selectors_generation` (`selectors_generation_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `best_selectors_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `best_selectors_type` (
  `best_selectors_type_id` bigint NOT NULL AUTO_INCREMENT COMMENT '우수활동자종류ID',
  `code` varchar(20) DEFAULT NULL COMMENT '우수활동자종류코드',
  `name` varchar(20) DEFAULT NULL COMMENT '우수활동자종류명',
  PRIMARY KEY (`best_selectors_type_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `blacklist_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `blacklist_history` (
  `blacklist_history_id` bigint NOT NULL AUTO_INCREMENT COMMENT '블랙리스트 ID',
  `selectors_id` bigint NOT NULL COMMENT '셀렉터스 회원 ID',
  `reason` varchar(500) DEFAULT NULL COMMENT '등록 사유',
  `status` varchar(20) DEFAULT NULL COMMENT '블랙리스트 상태 (활성화/해제)',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '등록 일시',
  `updated_at` timestamp NULL DEFAULT NULL COMMENT '수정 일시',
  PRIMARY KEY (`blacklist_history_id`),
  KEY `FK_Selectors_TO_BlacklistHistory_1` (`selectors_id`),
  CONSTRAINT `FK_Selectors_TO_BlacklistHistory_1` FOREIGN KEY (`selectors_id`) REFERENCES `selectors` (`selectors_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `campaign`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campaign` (
  `campaign_id` bigint NOT NULL AUTO_INCREMENT COMMENT '캠페인 ID',
  `title` varchar(100) DEFAULT NULL COMMENT '캠페인 제목',
  `description` varchar(2000) DEFAULT NULL COMMENT '캠페인 설명',
  `start_date` date DEFAULT NULL COMMENT '시작일',
  `end_date` date DEFAULT NULL COMMENT '종료일',
  `thumbnail_url` varchar(400) DEFAULT NULL COMMENT '썸네일url',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '생성 일시',
  `updated_at` timestamp NULL DEFAULT NULL COMMENT '수정 일시',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft-delete flag (0=active, 1=deleted)',
  PRIMARY KEY (`campaign_id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `campaign_performance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campaign_performance` (
  `campaign_performance_id` bigint NOT NULL AUTO_INCREMENT COMMENT '캠페인 성과 ID',
  `campaign_id` bigint DEFAULT NULL COMMENT '캠페인 ID',
  `total_click` bigint DEFAULT NULL COMMENT '총 클릭 수',
  `total_purchase` bigint DEFAULT NULL COMMENT '총 구매 수',
  `total_sales` bigint DEFAULT NULL COMMENT '총 매출액',
  `calculated_at` timestamp NULL DEFAULT NULL COMMENT '집계 일시',
  PRIMARY KEY (`campaign_performance_id`),
  KEY `FK_Campaign_TO_CampaignPerformance_1` (`campaign_id`),
  CONSTRAINT `FK_Campaign_TO_CampaignPerformance_1` FOREIGN KEY (`campaign_id`) REFERENCES `campaign` (`campaign_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `campaign_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campaign_product` (
  `campaign_product_id` bigint NOT NULL AUTO_INCREMENT COMMENT '캠페인 상품 ID',
  `campaign_id` bigint DEFAULT NULL COMMENT '캠페인 ID',
  `product_id` bigint DEFAULT NULL COMMENT '상품 ID',
  PRIMARY KEY (`campaign_product_id`),
  KEY `FK_Campaign_TO_CampaignProduct_1` (`campaign_id`),
  KEY `FK_Product_TO_CampaignProduct_1` (`product_id`),
  CONSTRAINT `FK_Campaign_TO_CampaignProduct_1` FOREIGN KEY (`campaign_id`) REFERENCES `campaign` (`campaign_id`),
  CONSTRAINT `FK_Product_TO_CampaignProduct_1` FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`)
) ENGINE=InnoDB AUTO_INCREMENT=72 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category` (
  `category_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `category_code` varchar(20) NOT NULL,
  `display_order` int NOT NULL,
  `enabled` bit(1) NOT NULL,
  `name` varchar(50) NOT NULL,
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `uk_category_code` (`category_code`),
  UNIQUE KEY `uk_category_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `click_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `click_log` (
  `click_log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '클릭이벤트 ID',
  `selectors_id` bigint DEFAULT NULL COMMENT '셀렉터스 회원 ID',
  `link_type` varchar(19) DEFAULT NULL COMMENT '링크 유형(SHOP / GROUP / PRODUCT / DEFAULT)',
  `reference_id` bigint DEFAULT NULL COMMENT '참조 ID (그룹 번호 or 상품 번호)',
  `viewer_user_id` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL COMMENT '생성 일시',
  `updated_at` timestamp NULL DEFAULT NULL COMMENT '수정 일시',
  PRIMARY KEY (`click_log_id`),
  KEY `FK_Selectors_TO_ClickLog_1` (`selectors_id`),
  KEY `fk_click_log_viewer` (`viewer_user_id`),
  CONSTRAINT `fk_click_log_viewer` FOREIGN KEY (`viewer_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `FK_Selectors_TO_ClickLog_1` FOREIGN KEY (`selectors_id`) REFERENCES `selectors` (`selectors_id`)
) ENGINE=InnoDB AUTO_INCREMENT=212 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `content`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `content` (
  `content_id` bigint NOT NULL AUTO_INCREMENT COMMENT '콘텐츠 ID',
  `selectors_id` bigint DEFAULT NULL COMMENT '셀렉터스 회원 ID',
  `sns_code` varchar(20) DEFAULT NULL COMMENT 'SNS 코드',
  `content_url` varchar(500) DEFAULT NULL COMMENT '콘텐츠 URL',
  `content_type` varchar(20) DEFAULT NULL COMMENT '콘텐츠 유형 (숏폼/롱폼/릴스/피드)',
  `last_version_no` bigint DEFAULT NULL COMMENT '최종버전ID',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '생성 일시',
  `updated_at` timestamp NULL DEFAULT NULL COMMENT '수정 일시',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft-delete flag (0=active, 1=deleted)',
  `sns_content_id` varchar(200) NOT NULL,
  PRIMARY KEY (`content_id`),
  UNIQUE KEY `uq_content_sns` (`sns_code`,`sns_content_id`),
  KEY `FK_Selectors_TO_Content_1` (`selectors_id`),
  CONSTRAINT `FK_Selectors_TO_Content_1` FOREIGN KEY (`selectors_id`) REFERENCES `selectors` (`selectors_id`),
  CONSTRAINT `FK_SNSCode_TO_Content_1` FOREIGN KEY (`sns_code`) REFERENCES `sns_code` (`sns_code`)
) ENGINE=InnoDB AUTO_INCREMENT=259 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `content_engagement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `content_engagement` (
  `content_engagement_id` bigint NOT NULL AUTO_INCREMENT,
  `content_id` bigint NOT NULL,
  `view_count` bigint DEFAULT NULL,
  `like_count` bigint DEFAULT NULL,
  `comment_count` bigint DEFAULT NULL,
  `share_count` bigint DEFAULT NULL,
  `created_at` timestamp NOT NULL,
  PRIMARY KEY (`content_engagement_id`),
  UNIQUE KEY `uq_content_engagement_content_created` (`content_id`,`created_at`),
  CONSTRAINT `FK_Content_TO_ContentEngagement_1` FOREIGN KEY (`content_id`) REFERENCES `content` (`content_id`)
) ENGINE=InnoDB AUTO_INCREMENT=510 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `content_media`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `content_media` (
  `content_media_id` bigint NOT NULL AUTO_INCREMENT COMMENT '미디어 ID',
  `content_version_id` bigint NOT NULL COMMENT '콘텐츠버전 ID',
  `media_url` text,
  `thumbnail_url` text,
  `media_type` enum('IMAGE','TEXT','VIDEO') NOT NULL,
  `body` json DEFAULT NULL,
  `sequence_no` int NOT NULL,
  `sns_media_id` varchar(200) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `extracted_with_policy_id` bigint DEFAULT NULL,
  `extraction_input_hash` varchar(64) DEFAULT NULL,
  `extracted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`content_media_id`),
  UNIQUE KEY `uq_content_media_version_sequence` (`content_version_id`,`sequence_no`),
  KEY `idx_content_media_version_id` (`content_version_id`),
  KEY `fk_content_media_extracted_policy` (`extracted_with_policy_id`),
  CONSTRAINT `fk_content_media_extracted_policy` FOREIGN KEY (`extracted_with_policy_id`) REFERENCES `inspection_policy` (`inspection_policy_id`),
  CONSTRAINT `FK_ContentVersion_TO_ContentMedia_1` FOREIGN KEY (`content_version_id`) REFERENCES `content_version` (`content_version_id`)
) ENGINE=InnoDB AUTO_INCREMENT=554 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `content_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `content_report` (
  `content_report_id` bigint NOT NULL AUTO_INCREMENT COMMENT '콘텐츠 리포트 ID',
  `content_version_id` bigint NOT NULL COMMENT '콘텐츠버전 ID',
  `summary` text,
  `created_at` timestamp NULL DEFAULT NULL COMMENT '생성 일시',
  `updated_at` timestamp NULL DEFAULT NULL COMMENT '수정 일시',
  `purpose` text,
  `flow` text,
  `overall_assessment` text,
  `inspection_policy_id` bigint DEFAULT NULL,
  `report_schema_version` varchar(20) DEFAULT NULL COMMENT 'content_report.analysis JSON 스키마 버전',
  `analysis` json DEFAULT NULL COMMENT '콘텐츠 상세 분석 결과',
  `execution_metadata` json DEFAULT NULL COMMENT '모델, 요청 ID, 소요 시간 등 실행 메타데이터',
  PRIMARY KEY (`content_report_id`),
  UNIQUE KEY `uq_content_report_version_policy` (`content_version_id`,`inspection_policy_id`),
  KEY `FK_ContentVersion_TO_ContentReport_1` (`content_version_id`),
  KEY `ix_content_report_version_latest` (`content_version_id`,`content_report_id`),
  KEY `fk_content_report_policy` (`inspection_policy_id`),
  CONSTRAINT `fk_content_report_policy` FOREIGN KEY (`inspection_policy_id`) REFERENCES `inspection_policy` (`inspection_policy_id`),
  CONSTRAINT `FK_ContentVersion_TO_ContentReport_1` FOREIGN KEY (`content_version_id`) REFERENCES `content_version` (`content_version_id`)
) ENGINE=InnoDB AUTO_INCREMENT=283 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `content_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `content_version` (
  `content_version_id` bigint NOT NULL AUTO_INCREMENT COMMENT '콘텐츠버전 ID',
  `content_id` bigint NOT NULL COMMENT '콘텐츠 ID',
  `admin_id` bigint DEFAULT NULL,
  `version_no` bigint NOT NULL,
  `content_hash` varchar(64) NOT NULL,
  `creation_reason` varchar(30) NOT NULL,
  `created_at` timestamp NOT NULL,
  `status` varchar(20) DEFAULT NULL COMMENT '검수상태(검수전 / 승인 / 반려)',
  `inspection_decision` varchar(20) DEFAULT NULL,
  `inspected_at` timestamp NULL DEFAULT NULL COMMENT '검수일자',
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`content_version_id`),
  UNIQUE KEY `uq_content_version_content_no` (`content_id`,`version_no`),
  KEY `FK_Admin_TO_ContentVersion_1` (`admin_id`),
  CONSTRAINT `FK_Admin_TO_ContentVersion_1` FOREIGN KEY (`admin_id`) REFERENCES `admin` (`admin_id`),
  CONSTRAINT `FK_Content_TO_ContentVersion_1` FOREIGN KEY (`content_id`) REFERENCES `content` (`content_id`)
) ENGINE=InnoDB AUTO_INCREMENT=315 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `creator_discovery_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `creator_discovery_info` (
  `creator_pool_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `brand_hits` varchar(200) DEFAULT NULL,
  `brand_score` int NOT NULL,
  `discovered_at` datetime(6) NOT NULL,
  `ig_confidence` decimal(3,2) DEFAULT NULL,
  `ig_handle` varchar(30) DEFAULT NULL,
  `recent_90_day_content_count` int DEFAULT NULL,
  `profile_image_url` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`creator_pool_id`),
  CONSTRAINT `FK_CreatorPool_TO_CreatorDiscoveryInfo_1` FOREIGN KEY (`creator_pool_id`) REFERENCES `creator_pool` (`creator_pool_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `creator_discovery_source`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `creator_discovery_source` (
  `source_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `discovered_at` datetime(6) NOT NULL,
  `view_share` decimal(6,5) DEFAULT NULL,
  `creator_pool_id` bigint NOT NULL,
  `keyword_id` bigint NOT NULL,
  PRIMARY KEY (`source_id`),
  UNIQUE KEY `uk_discovery_source` (`creator_pool_id`,`keyword_id`),
  KEY `FK_DiscoveryKeyword_TO_CreatorDiscoverySource_1` (`keyword_id`),
  CONSTRAINT `FK_CreatorPool_TO_CreatorDiscoverySource_1` FOREIGN KEY (`creator_pool_id`) REFERENCES `creator_pool` (`creator_pool_id`),
  CONSTRAINT `FK_DiscoveryKeyword_TO_CreatorDiscoverySource_1` FOREIGN KEY (`keyword_id`) REFERENCES `discovery_keyword` (`keyword_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1859 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `creator_pool`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `creator_pool` (
  `creator_pool_id` bigint NOT NULL AUTO_INCREMENT COMMENT '크리에이터 ID',
  `sns_code` varchar(20) DEFAULT NULL COMMENT 'SNS 코드',
  `account_id` varchar(100) DEFAULT NULL COMMENT '계정 ID',
  `creator_name` varchar(100) DEFAULT NULL COMMENT '크리에이터명',
  `email` varchar(100) DEFAULT NULL COMMENT '이메일 주소',
  `follower_count` bigint DEFAULT NULL COMMENT '팔로워 수',
  `last_content_at` timestamp NULL DEFAULT NULL COMMENT '최근 활동일',
  `engagement_rate` decimal(5,2) DEFAULT NULL COMMENT 'ER지수',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '생성 일시',
  `updated_at` timestamp NULL DEFAULT NULL COMMENT '수정 일시',
  `category` varchar(20) DEFAULT NULL COMMENT '카테고리',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft-delete flag (0=active, 1=deleted)',
  PRIMARY KEY (`creator_pool_id`),
  UNIQUE KEY `uk_creator_pool_sns_account` (`sns_code`,`account_id`),
  CONSTRAINT `FK_SNSCode_TO_CreatorPool_1` FOREIGN KEY (`sns_code`) REFERENCES `sns_code` (`sns_code`)
) ENGINE=InnoDB AUTO_INCREMENT=1085 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `creator_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `creator_report` (
  `creator_report_id` bigint NOT NULL AUTO_INCREMENT COMMENT '크리에이터 리포트 ID',
  `creator_id` bigint DEFAULT NULL COMMENT '크리에이터 ID',
  `quantity_score` decimal(5,2) DEFAULT NULL COMMENT '정량 평가 점수',
  `quality_score` decimal(5,2) DEFAULT NULL COMMENT '정성 평가 점수',
  `summary` json DEFAULT NULL COMMENT '분석 요약',
  `category` varchar(20) DEFAULT NULL COMMENT '카테고리',
  `keywords` varchar(500) DEFAULT NULL COMMENT '키워드',
  `target` varchar(19) DEFAULT NULL COMMENT '타겟층(W1, W2 ... M1, M2 ... M6)',
  `content_style` varchar(19) DEFAULT NULL COMMENT '콘텐츠 유형',
  `brand_history` varchar(500) DEFAULT NULL COMMENT '협업 내역',
  `tone` varchar(500) DEFAULT NULL COMMENT '콘텐츠 톤',
  `strength` varchar(500) DEFAULT NULL COMMENT '강점',
  `warning` varchar(500) DEFAULT NULL COMMENT '유의점',
  `status` varchar(20) DEFAULT NULL COMMENT '리포트상태 (대기 / 생성중 / 생성 성공 / 생성 실패)',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '생성 일시',
  PRIMARY KEY (`creator_report_id`),
  KEY `FK_CreatorPool_TO_CreatorReport_1` (`creator_id`),
  CONSTRAINT `FK_CreatorPool_TO_CreatorReport_1` FOREIGN KEY (`creator_id`) REFERENCES `creator_pool` (`creator_pool_id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `discovery_keyword`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `discovery_keyword` (
  `keyword_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `keyword` varchar(100) NOT NULL,
  `last_run_at` datetime(6) DEFAULT NULL,
  `priority` int NOT NULL,
  `category_id` bigint NOT NULL,
  PRIMARY KEY (`keyword_id`),
  UNIQUE KEY `uk_keyword_category` (`category_id`,`keyword`),
  CONSTRAINT `FK97eifjekqt4bqtmft5dqn7g12` FOREIGN KEY (`category_id`) REFERENCES `category` (`category_id`)
) ENGINE=InnoDB AUTO_INCREMENT=66 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `generation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `generation` (
  `generation_id` bigint NOT NULL AUTO_INCREMENT COMMENT '기수 ID',
  `generation_name` varchar(30) DEFAULT NULL COMMENT '기수명',
  `start_date` timestamp NULL DEFAULT NULL COMMENT '기수 시작일',
  `end_date` timestamp NULL DEFAULT NULL COMMENT '기수 종료일',
  `activity_start_date` datetime(6) DEFAULT NULL,
  `activity_end_date` datetime(6) DEFAULT NULL,
  `selector_excellence_selected_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) DEFAULT NULL COMMENT '기수 상태(활성, 비활성)',
  `generation_status` enum('ACTIVE','INACTIVE') DEFAULT NULL,
  PRIMARY KEY (`generation_id`),
  KEY `idx_generation_activity_period` (`activity_start_date`,`activity_end_date`),
  KEY `idx_generation_excellence_candidate` (`selector_excellence_selected_at`,`activity_end_date`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `inspection_policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inspection_policy` (
  `inspection_policy_id` bigint NOT NULL AUTO_INCREMENT,
  `platform` varchar(20) NOT NULL,
  `version` varchar(40) NOT NULL,
  `rule_config` json NOT NULL,
  `rule_config_hash` varchar(64) NOT NULL,
  `ai_model_name` varchar(100) NOT NULL,
  `ai_prompt_version` varchar(40) NOT NULL,
  `ai_prompt` text NOT NULL,
  `ai_config_hash` varchar(64) NOT NULL,
  `stt_model_name` varchar(100) DEFAULT NULL,
  `ocr_model_name` varchar(100) DEFAULT NULL,
  `extraction_prompt_version` varchar(40) DEFAULT NULL,
  `extraction_prompt` text,
  `extraction_config_hash` varchar(64) NOT NULL,
  `config_hash` varchar(64) NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '0',
  `activated_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`inspection_policy_id`),
  UNIQUE KEY `uq_inspection_policy_platform_version` (`platform`,`version`),
  UNIQUE KEY `uq_inspection_policy_config_hash` (`config_hash`),
  KEY `ix_inspection_policy_platform_active` (`platform`,`is_active`)
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `kakao_sender_connection`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kakao_sender_connection` (
  `kakao_sender_connection_id` bigint NOT NULL AUTO_INCREMENT,
  `kakao_user_id` bigint NOT NULL,
  `sender_name` varchar(50) NOT NULL,
  `access_token_encrypted` text NOT NULL,
  `refresh_token_encrypted` text NOT NULL,
  `access_token_expires_at` timestamp NOT NULL,
  `refresh_token_expires_at` timestamp NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'CONNECTED',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`kakao_sender_connection_id`),
  UNIQUE KEY `uk_kakao_sender_connection_kakao_user` (`kakao_user_id`),
  UNIQUE KEY `uk_kakao_sender_connection_kakao_user_id` (`kakao_user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification` (
  `notification_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `body` varchar(400) NOT NULL,
  `notification_channel` enum('KAKAO_MESSAGE') NOT NULL,
  `notification_purpose_code` varchar(20) NOT NULL,
  `receiver` varchar(255) NOT NULL,
  `reference_id` bigint DEFAULT NULL,
  `request_at` datetime(6) NOT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `status` enum('FAILED','REQUESTED','SENT') NOT NULL,
  `initiated_by_type` varchar(20) NOT NULL DEFAULT 'SYSTEM',
  `initiated_by_id` bigint DEFAULT NULL,
  PRIMARY KEY (`notification_id`),
  KEY `FK_NotificationPurpose_TO_Notification_1` (`notification_purpose_code`),
  CONSTRAINT `FK_NotificationPurpose_TO_Notification_1` FOREIGN KEY (`notification_purpose_code`) REFERENCES `notification_purpose` (`notification_purpose_code`)
) ENGINE=InnoDB AUTO_INCREMENT=149 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `notification_purpose`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_purpose` (
  `notification_purpose_code` varchar(20) NOT NULL COMMENT '발송목적코드',
  `notification_purpose_name` varchar(20) DEFAULT NULL COMMENT '발송목적',
  PRIMARY KEY (`notification_purpose_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `penalty_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `penalty_history` (
  `penalty_history_id` bigint NOT NULL AUTO_INCREMENT COMMENT '페널티 ID',
  `selectors_id` bigint DEFAULT NULL COMMENT '셀렉터스 회원 ID',
  `generation_id` bigint DEFAULT NULL,
  `content_version_id` bigint DEFAULT NULL,
  `reason` varchar(500) DEFAULT NULL COMMENT '페널티 사유',
  `source` varchar(20) NOT NULL,
  `granted_by_admin_id` bigint DEFAULT NULL,
  `released_by_admin_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` timestamp NULL DEFAULT NULL COMMENT '생성 일시',
  `updated_at` timestamp NULL DEFAULT NULL COMMENT '수정 일시',
  `started_at` timestamp NULL DEFAULT NULL,
  `ended_at` timestamp NULL DEFAULT NULL,
  `violation_type_id` bigint NOT NULL,
  PRIMARY KEY (`penalty_history_id`),
  KEY `FK_Selectors_TO_PenaltyHistory_1` (`selectors_id`),
  KEY `FK_ContentVersion_TO_PenaltyHistory_1` (`content_version_id`),
  KEY `idx_penalty_history_selectors_status` (`selectors_id`,`status`),
  KEY `idx_penalty_selectors_generation` (`selectors_id`,`generation_id`),
  KEY `fk_penalty_generation` (`generation_id`),
  KEY `fk_penalty_granted_admin` (`granted_by_admin_id`),
  KEY `fk_penalty_released_admin` (`released_by_admin_id`),
  CONSTRAINT `FK_ContentVersion_TO_PenaltyHistory_1` FOREIGN KEY (`content_version_id`) REFERENCES `content_version` (`content_version_id`),
  CONSTRAINT `fk_penalty_generation` FOREIGN KEY (`generation_id`) REFERENCES `generation` (`generation_id`),
  CONSTRAINT `fk_penalty_granted_admin` FOREIGN KEY (`granted_by_admin_id`) REFERENCES `admin` (`admin_id`),
  CONSTRAINT `fk_penalty_released_admin` FOREIGN KEY (`released_by_admin_id`) REFERENCES `admin` (`admin_id`),
  CONSTRAINT `FK_Selectors_TO_PenaltyHistory_1` FOREIGN KEY (`selectors_id`) REFERENCES `selectors` (`selectors_id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product` (
  `product_id` bigint NOT NULL AUTO_INCREMENT COMMENT '상품 ID',
  `product_code` varchar(50) NOT NULL,
  `product_name` varchar(200) DEFAULT NULL COMMENT '상품명',
  `brand_name` varchar(100) DEFAULT NULL COMMENT '브랜드명',
  `category` varchar(100) DEFAULT NULL COMMENT '상품 카테고리',
  `regular_price` decimal(19,2) NOT NULL,
  `sale_price` decimal(19,2) NOT NULL,
  `status` varchar(20) DEFAULT NULL COMMENT '상품 상태 코드 (판매중 / 판매 중지 / 품절)',
  `thumbnail_url` varchar(500) DEFAULT NULL COMMENT '썸네일 URL',
  `detail_url` varchar(500) DEFAULT NULL COMMENT '상세정보 이미지 URL',
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`product_id`),
  UNIQUE KEY `uk_product_product_code` (`product_code`)
) ENGINE=InnoDB AUTO_INCREMENT=201 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `product_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_group` (
  `product_group_id` bigint NOT NULL AUTO_INCREMENT COMMENT '상품 그룹 ID',
  `selectors_id` bigint DEFAULT NULL COMMENT '셀렉터스 회원 ID',
  `campaign_id` bigint DEFAULT NULL COMMENT '캠페인 ID',
  `group_no` smallint DEFAULT NULL COMMENT '그룹 번호',
  `title` varchar(100) DEFAULT NULL COMMENT '상품 그룹 제목',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '생성 일시',
  `updated_at` timestamp NULL DEFAULT NULL COMMENT '수정 일시',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft-delete flag (0=active, 1=deleted)',
  PRIMARY KEY (`product_group_id`),
  KEY `FK_Selectors_TO_ProductGroup_1` (`selectors_id`),
  KEY `FK_Campaign_TO_ProductGroup_1` (`campaign_id`),
  CONSTRAINT `FK_Campaign_TO_ProductGroup_1` FOREIGN KEY (`campaign_id`) REFERENCES `campaign` (`campaign_id`),
  CONSTRAINT `FK_Selectors_TO_ProductGroup_1` FOREIGN KEY (`selectors_id`) REFERENCES `selectors` (`selectors_id`)
) ENGINE=InnoDB AUTO_INCREMENT=146 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `product_group_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_group_item` (
  `product_group_item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '상품 그룹 항목 ID',
  `group_id` bigint DEFAULT NULL COMMENT '상품 그룹 ID',
  `product_id` bigint DEFAULT NULL COMMENT '상품 ID',
  `display_order` smallint DEFAULT NULL COMMENT '표시 순서',
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`product_group_item_id`),
  UNIQUE KEY `uk_product_group_item_product` (`group_id`,`product_id`),
  KEY `FK_ProductGroup_TO_ProductGroupItem_1` (`group_id`),
  KEY `FK_Product_TO_ProductGroupItem_1` (`product_id`),
  CONSTRAINT `FK_Product_TO_ProductGroupItem_1` FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`),
  CONSTRAINT `FK_ProductGroup_TO_ProductGroupItem_1` FOREIGN KEY (`group_id`) REFERENCES `product_group` (`product_group_id`)
) ENGINE=InnoDB AUTO_INCREMENT=308 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `proposal_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `proposal_history` (
  `proposal_history_id` bigint NOT NULL AUTO_INCREMENT COMMENT '제안 이력 ID',
  `creator_id` bigint DEFAULT NULL COMMENT '크리에이터 ID',
  `admin_id` bigint NOT NULL COMMENT '관리자ID',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '제안 일시',
  PRIMARY KEY (`proposal_history_id`),
  KEY `FK_CreatorPool_TO_ProposalHistory_1` (`creator_id`),
  KEY `FK_Admin_TO_ProposalHistory_1` (`admin_id`),
  CONSTRAINT `FK_Admin_TO_ProposalHistory_1` FOREIGN KEY (`admin_id`) REFERENCES `admin` (`admin_id`),
  CONSTRAINT `FK_CreatorPool_TO_ProposalHistory_1` FOREIGN KEY (`creator_id`) REFERENCES `creator_pool` (`creator_pool_id`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `purchase_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_history` (
  `purchase_history_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `confirmed_at` datetime(6) DEFAULT NULL,
  `discount_amount` decimal(19,2) NOT NULL,
  `order_no` varchar(100) NOT NULL,
  `paid_amount` decimal(19,2) NOT NULL,
  `product_id` bigint NOT NULL,
  `purchased_at` datetime(6) NOT NULL,
  `quantity` int NOT NULL,
  `regular_unit_price` decimal(19,2) NOT NULL,
  `sale_unit_price` decimal(19,2) NOT NULL,
  `selectors_id` bigint DEFAULT NULL,
  `status` enum('CANCELED','CANCEL_REQUESTED','PURCHASED','PURCHASE_CONFIRMED','RETURNED','RETURN_REQUESTED') NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`purchase_history_id`),
  UNIQUE KEY `uk_purchase_order_product` (`order_no`,`product_id`),
  KEY `FK_Product_TO_PurchaseHistory_1` (`product_id`),
  KEY `FK_Users_TO_PurchaseHistory_1` (`user_id`),
  KEY `idx_purchase_selector_status_purchased` (`selectors_id`,`status`,`purchased_at`,`confirmed_at`),
  CONSTRAINT `FK_Product_TO_PurchaseHistory_1` FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`),
  CONSTRAINT `FK_Selectors_TO_PurchaseHistory_1` FOREIGN KEY (`selectors_id`) REFERENCES `selectors` (`selectors_id`),
  CONSTRAINT `FK_Users_TO_PurchaseHistory_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1535 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `selector_excellence_selection`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `selector_excellence_selection` (
  `selection_id` bigint NOT NULL AUTO_INCREMENT,
  `generation_id` bigint NOT NULL,
  `selectors_id` bigint NOT NULL,
  `selection_type` varchar(30) NOT NULL,
  `generation_sales` decimal(19,2) NOT NULL,
  `confirmed_order_count` bigint NOT NULL,
  `rank_no` int DEFAULT NULL,
  `reward_type` varchar(30) NOT NULL,
  `reward_value` bigint NOT NULL,
  `reward_quantity` int NOT NULL,
  `selected_at` datetime(6) NOT NULL,
  PRIMARY KEY (`selection_id`),
  UNIQUE KEY `uq_selector_excellence_generation_selector_type` (`generation_id`,`selectors_id`,`selection_type`),
  KEY `idx_selector_excellence_selector_generation` (`selectors_id`,`generation_id`),
  KEY `idx_selector_excellence_generation_type_rank` (`generation_id`,`selection_type`,`rank_no`),
  CONSTRAINT `fk_selector_excellence_generation` FOREIGN KEY (`generation_id`) REFERENCES `generation` (`generation_id`),
  CONSTRAINT `fk_selector_excellence_selectors` FOREIGN KEY (`selectors_id`) REFERENCES `selectors` (`selectors_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `selectors`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `selectors` (
  `selectors_id` bigint NOT NULL AUTO_INCREMENT COMMENT '셀렉터스 회원 ID',
  `application_id` bigint DEFAULT NULL COMMENT '지원서 ID',
  `user_id` bigint DEFAULT NULL COMMENT '사용자 ID',
  `selectors_role_id` varchar(20) NOT NULL COMMENT '셀렉터스권한ID',
  `selectors_code` varchar(20) DEFAULT NULL COMMENT '셀렉터스 코드',
  `selectors_nickname` varchar(20) DEFAULT NULL COMMENT '셀렉터스 닉네임',
  `category` varchar(20) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL COMMENT '생성 일시',
  `updated_at` timestamp NULL DEFAULT NULL COMMENT '수정 일시',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft-delete flag (0=active, 1=deleted)',
  PRIMARY KEY (`selectors_id`),
  UNIQUE KEY `uq_selectors_user` (`user_id`),
  KEY `FK_Application_TO_Selectors_1` (`application_id`),
  KEY `FK_Users_TO_Selectors_1` (`user_id`),
  KEY `FK_SelectorsRole_TO_Selectors_1` (`selectors_role_id`),
  CONSTRAINT `FK_Application_TO_Selectors_1` FOREIGN KEY (`application_id`) REFERENCES `application` (`application_id`),
  CONSTRAINT `FK_SelectorsRole_TO_Selectors_1` FOREIGN KEY (`selectors_role_id`) REFERENCES `selectors_role` (`selectors_role_id`),
  CONSTRAINT `FK_Users_TO_Selectors_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=204 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `selectors_generation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `selectors_generation` (
  `selectors_generation_id` bigint NOT NULL AUTO_INCREMENT COMMENT '셀렉터스기수ID',
  `selectors_id` bigint NOT NULL COMMENT '셀렉터스 회원 ID',
  `generation_id` bigint NOT NULL COMMENT '기수 ID',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '생성 일시',
  `total_sales` bigint NOT NULL DEFAULT '0',
  `confirmed_purchase_count` bigint NOT NULL DEFAULT '0',
  `paid_commission_amount` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`selectors_generation_id`),
  UNIQUE KEY `uq_selectors_generation` (`selectors_id`,`generation_id`),
  KEY `FK_Selectors_TO_SelectorsGeneration_1` (`selectors_id`),
  KEY `FK_Generation_TO_SelectorsGeneration_1` (`generation_id`),
  KEY `idx_selectors_generation_generation_selector` (`generation_id`,`selectors_id`),
  CONSTRAINT `FK_Generation_TO_SelectorsGeneration_1` FOREIGN KEY (`generation_id`) REFERENCES `generation` (`generation_id`),
  CONSTRAINT `FK_Selectors_TO_SelectorsGeneration_1` FOREIGN KEY (`selectors_id`) REFERENCES `selectors` (`selectors_id`)
) ENGINE=InnoDB AUTO_INCREMENT=210 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `selectors_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `selectors_role` (
  `selectors_role_id` varchar(20) NOT NULL COMMENT '셀렉터스권한ID',
  `role_name` varchar(20) DEFAULT NULL COMMENT '셀렉터스권한명',
  PRIMARY KEY (`selectors_role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `selectors_sns_account`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `selectors_sns_account` (
  `selectors_sns_account_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `account_id` varchar(100) DEFAULT NULL,
  `profile_url` varchar(500) DEFAULT NULL,
  `is_deleted` bit(1) NOT NULL,
  `follower_count` bigint DEFAULT NULL,
  `last_collected_at` datetime(6) DEFAULT NULL,
  `profile_image_url` varchar(500) DEFAULT NULL,
  `selectors_id` bigint NOT NULL,
  `sns_code` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`selectors_sns_account_id`),
  UNIQUE KEY `uq_selectors_sns_account_selectors_id` (`selectors_id`),
  KEY `FK_SNSCode_TO_SelectorsSNSAccount_1` (`sns_code`),
  CONSTRAINT `FK_Selectors_TO_SelectorsSNSAccount_1` FOREIGN KEY (`selectors_id`) REFERENCES `selectors` (`selectors_id`),
  CONSTRAINT `FK_SNSCode_TO_SelectorsSNSAccount_1` FOREIGN KEY (`sns_code`) REFERENCES `sns_code` (`sns_code`)
) ENGINE=InnoDB AUTO_INCREMENT=240 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `settlement_account`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `settlement_account` (
  `settlement_account_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `account_holder` varchar(50) DEFAULT NULL,
  `account_number` varchar(255) DEFAULT NULL,
  `bank_name` varchar(20) DEFAULT NULL,
  `business_number` varchar(255) DEFAULT NULL,
  `is_deleted` bit(1) NOT NULL,
  `selectors_id` bigint NOT NULL,
  `settlement_type` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`settlement_account_id`),
  UNIQUE KEY `uk_settlement_account_selectors` (`selectors_id`),
  CONSTRAINT `FK_Selectors_TO_SettlementAccount_1` FOREIGN KEY (`selectors_id`) REFERENCES `selectors` (`selectors_id`)
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `settlement_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `settlement_history` (
  `settlement_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `settlement_month` datetime(6) NOT NULL,
  `activity_year_month` int NOT NULL COMMENT '활동 년월(YYYYMM, 예: 202607)',
  `calculated_at` datetime(6) NOT NULL,
  `confirmed_purchase_count` bigint DEFAULT NULL,
  `selectors_id` bigint NOT NULL,
  `settled_at` datetime(6) DEFAULT NULL,
  `scheduled_payment_year_month` int DEFAULT NULL,
  `commission` bigint NOT NULL,
  `commission_rate` decimal(5,2) DEFAULT NULL,
  `status` enum('CALCULATING','EXPIRED','PAYMENT_HOLD_BLACK','PAYMENT_HOLD_INFO','PAYMENT_PENDING','SETTLED') NOT NULL,
  `total_sales` bigint NOT NULL,
  PRIMARY KEY (`settlement_id`),
  UNIQUE KEY `uk_settlement_selectors_activity_year_month` (`selectors_id`,`activity_year_month`),
  CONSTRAINT `FK_Selectors_TO_SettlementHistory_1` FOREIGN KEY (`selectors_id`) REFERENCES `selectors` (`selectors_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1046 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sns_code`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sns_code` (
  `sns_code` varchar(20) NOT NULL COMMENT 'SNS 코드',
  `sns_name` varchar(50) DEFAULT NULL COMMENT 'SNS명',
  `sns_base_url` varchar(200) DEFAULT NULL COMMENT 'SNS 기본 URL',
  PRIMARY KEY (`sns_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `task_run`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_run` (
  `task_run_id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `task_type` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `trigger_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `current_step` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `progress_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `step_progress` json DEFAULT NULL,
  `total_count` bigint DEFAULT NULL,
  `processed_count` bigint NOT NULL DEFAULT '0',
  `succeeded_count` bigint NOT NULL DEFAULT '0',
  `failed_count` bigint NOT NULL DEFAULT '0',
  `skipped_count` bigint NOT NULL DEFAULT '0',
  `started_by_admin_id` bigint DEFAULT NULL,
  `concurrency_key` varchar(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `idempotency_key` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_fingerprint` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `lease_token` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `heartbeat_at` datetime(6) NOT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  `error_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`task_run_id`),
  UNIQUE KEY `uq_task_run_run_id` (`run_id`),
  UNIQUE KEY `uq_task_run_idempotency_key` (`idempotency_key`),
  UNIQUE KEY `uq_task_run_concurrency_key` (`concurrency_key`),
  KEY `idx_task_run_status_started_at` (`status`,`started_at`),
  KEY `idx_task_run_finished_at` (`finished_at`),
  CONSTRAINT `chk_task_run_failed_count` CHECK ((`failed_count` >= 0)),
  CONSTRAINT `chk_task_run_processed_count` CHECK ((`processed_count` >= 0)),
  CONSTRAINT `chk_task_run_processed_sum` CHECK ((`processed_count` = ((`succeeded_count` + `failed_count`) + `skipped_count`))),
  CONSTRAINT `chk_task_run_processed_total` CHECK (((`total_count` is null) or (`processed_count` <= `total_count`))),
  CONSTRAINT `chk_task_run_skipped_count` CHECK ((`skipped_count` >= 0)),
  CONSTRAINT `chk_task_run_succeeded_count` CHECK ((`succeeded_count` >= 0)),
  CONSTRAINT `chk_task_run_total_count` CHECK (((`total_count` is null) or (`total_count` >= 0)))
) ENGINE=InnoDB AUTO_INCREMENT=142 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_kakao_recipient`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_kakao_recipient` (
  `user_kakao_recipient_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `kakao_message_uuid` varchar(255) NOT NULL,
  `kakao_user_id` bigint NOT NULL,
  `status` enum('INACTIVE','READY','REAUTH_REQUIRED') NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`user_kakao_recipient_id`),
  UNIQUE KEY `uk_user_kakao_recipient_user` (`user_id`),
  UNIQUE KEY `uk_user_kakao_recipient_kakao_user` (`kakao_user_id`),
  UNIQUE KEY `uk_user_kakao_recipient_uuid` (`kakao_message_uuid`),
  CONSTRAINT `fk_user_kakao_recipient_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '사용자 ID',
  `hi_id` varchar(20) DEFAULT NULL COMMENT '더현대HI 아이디',
  `hi_password` varchar(255) DEFAULT NULL COMMENT '더현대HI 비밀번호',
  `name` varchar(50) DEFAULT NULL COMMENT '회원명',
  `birth_date` date DEFAULT NULL COMMENT '생년월일',
  `gender` char(2) DEFAULT NULL COMMENT '성별',
  `email` varchar(100) DEFAULT NULL COMMENT '이메일',
  `phone` varchar(20) DEFAULT NULL COMMENT '전화번호',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '생성 일시',
  `updated_at` timestamp NULL DEFAULT NULL COMMENT '수정 일시',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft-delete flag (0=active, 1=deleted)',
  `alimtalk` varchar(1) NOT NULL DEFAULT 'N',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=273 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `violation_evidence_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `violation_evidence_history` (
  `violation_evidence_history_id` bigint NOT NULL AUTO_INCREMENT,
  `violation_item_id` bigint NOT NULL,
  `content_version_id` bigint NOT NULL,
  `inspection_policy_id` bigint NOT NULL,
  `content_report_id` bigint DEFAULT NULL COMMENT '근거 스냅샷을 생성한 콘텐츠 리포트 ID',
  `evidence` json NOT NULL,
  `detected_at` datetime NOT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`violation_evidence_history_id`),
  UNIQUE KEY `uq_violation_evidence_snapshot` (`violation_item_id`,`content_version_id`,`inspection_policy_id`),
  KEY `fk_violation_evidence_history_policy` (`inspection_policy_id`),
  KEY `idx_violation_evidence_history_report` (`content_report_id`),
  CONSTRAINT `fk_violation_evidence_history_item` FOREIGN KEY (`violation_item_id`) REFERENCES `violation_item` (`violation_item_id`),
  CONSTRAINT `fk_violation_evidence_history_policy` FOREIGN KEY (`inspection_policy_id`) REFERENCES `inspection_policy` (`inspection_policy_id`),
  CONSTRAINT `fk_violation_evidence_history_report` FOREIGN KEY (`content_report_id`) REFERENCES `content_report` (`content_report_id`)
) ENGINE=InnoDB AUTO_INCREMENT=140 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `violation_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `violation_item` (
  `violation_item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '위반항목ID',
  `content_id` bigint NOT NULL,
  `content_version_id` bigint NOT NULL,
  `last_detected_content_version_id` bigint NOT NULL,
  `resolved_content_version_id` bigint DEFAULT NULL,
  `content_media_id` bigint DEFAULT NULL,
  `violation_type_id` bigint NOT NULL COMMENT '위반유형ID',
  `evidence` json NOT NULL,
  `status` varchar(30) NOT NULL,
  `created_at` timestamp NULL DEFAULT NULL COMMENT '생성 일시',
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`violation_item_id`),
  UNIQUE KEY `uq_violation_item_content_type` (`content_id`,`violation_type_id`),
  KEY `FK_ContentMedia_TO_ViolationItem_1` (`content_media_id`),
  KEY `FK_ViolationType_TO_ViolationItem_1` (`violation_type_id`),
  KEY `idx_violation_item_first_version_status` (`content_version_id`,`status`),
  KEY `idx_violation_item_last_version` (`last_detected_content_version_id`),
  CONSTRAINT `FK_ContentMedia_TO_ViolationItem_1` FOREIGN KEY (`content_media_id`) REFERENCES `content_media` (`content_media_id`),
  CONSTRAINT `fk_violation_item_content` FOREIGN KEY (`content_id`) REFERENCES `content` (`content_id`),
  CONSTRAINT `FK_ViolationType_TO_ViolationItem_1` FOREIGN KEY (`violation_type_id`) REFERENCES `violation_type` (`violation_type_id`)
) ENGINE=InnoDB AUTO_INCREMENT=94 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `violation_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `violation_type` (
  `violation_type_id` bigint NOT NULL AUTO_INCREMENT COMMENT '위반유형ID',
  `code` varchar(50) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL COMMENT '설명',
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`violation_type_id`),
  UNIQUE KEY `uk_violation_type_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
