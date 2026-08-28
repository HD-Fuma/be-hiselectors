SET NAMES utf8mb4;

UPDATE violation_type
SET description = '광고·수수료 안내 문구 확인 필요'
WHERE code = 'AD_DISCLOSURE_INVALID';

UPDATE violation_type
SET description = '제휴 링크 확인 필요'
WHERE code = 'AFFILIATE_LINK_INVALID';
