package kr.hankkitravel.identity.application;

import java.util.NoSuchElementException;
import java.util.UUID;
import kr.hankkitravel.identity.model.Guest;
import kr.hankkitravel.identity.persistence.GuestMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestApplicationService {
    private final GuestMapper guests;

    public GuestApplicationService(GuestMapper guests) { this.guests = guests; }

    @Transactional
    public Guest create() {
        var guest = new Guest(UUID.randomUUID());
        guests.insert(guest);
        return guest;
    }

    public Guest requirePublicId(String publicId) {
        try {
            UUID.fromString(publicId);
        } catch (RuntimeException exception) {
            throw new NoSuchElementException("게스트를 찾을 수 없습니다.");
        }
        var guest = guests.findByPublicId(publicId);
        if (guest == null) throw new NoSuchElementException("게스트를 찾을 수 없습니다.");
        return guest;
    }
}
