package kr.hankkitravel.identity.model;

public record ProfileOwnership(Long userId, Long guestId) {
    public ProfileOwnership {
        if ((userId == null) == (guestId == null)
                || (userId != null && userId <= 0) || (guestId != null && guestId <= 0)) {
            throw new IllegalArgumentException("프로필에는 사용자 또는 게스트 소유자 한 명이 필요합니다.");
        }
    }
}
