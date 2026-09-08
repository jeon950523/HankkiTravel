package kr.hankkitravel.identity.model;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Guest {
    private Long id;
    private String publicId;
    private Instant createdAt;
    private Instant updatedAt;

    public Guest(java.util.UUID publicId) {
        this.publicId = java.util.Objects.requireNonNull(publicId).toString();
    }
}
