package com.fuma.hiselectors.user.dto;

import com.fuma.hiselectors.user.model.User;

public record UserMeResponse(
        String hiId,
        String name,
        String email,
        String phone,
        String alimtalk
) {
    public static UserMeResponse from(User user) {
        return new UserMeResponse(
                user.getHiId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getAlimtalk()
        );
    }
}
