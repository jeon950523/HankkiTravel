package kr.hankkitravel.identity.model;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    private Long id;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public User(String status) {
        if (status == null || status.isBlank()) throw new IllegalArgumentException("사용자 상태가 필요합니다.");
        this.status = status;
    }
}
