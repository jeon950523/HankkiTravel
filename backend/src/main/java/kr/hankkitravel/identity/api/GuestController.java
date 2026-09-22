package kr.hankkitravel.identity.api;

import java.time.Instant;
import kr.hankkitravel.identity.application.GuestApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/guests")
public class GuestController {
    private final GuestApplicationService guests;

    public GuestController(GuestApplicationService guests) { this.guests = guests; }

    @PostMapping
    public ResponseEntity<GuestResponse> create() {
        var guest = guests.create();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new GuestResponse(guest.getPublicId(), guest.getCreatedAt()));
    }

    public record GuestResponse(String publicId, Instant createdAt) { }
}
