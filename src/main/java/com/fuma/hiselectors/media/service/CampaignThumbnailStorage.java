package com.fuma.hiselectors.media.service;

public interface CampaignThumbnailStorage {

    String store(String key, String contentType, byte[] bytes);
}
