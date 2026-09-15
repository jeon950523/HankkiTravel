package kr.hankkitravel.profile.application;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import kr.hankkitravel.identity.application.GuestApplicationService;
import kr.hankkitravel.identity.model.ProfileOwnership;
import kr.hankkitravel.profile.model.FamilyMember;
import kr.hankkitravel.profile.model.FamilyMemberCaution;
import kr.hankkitravel.profile.model.FamilyProfile;
import kr.hankkitravel.profile.model.FamilyProfileId;
import kr.hankkitravel.profile.persistence.FamilyMemberCautionMapper;
import kr.hankkitravel.profile.persistence.FamilyMemberMapper;
import kr.hankkitravel.profile.persistence.FamilyProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamilyProfileApplicationService {
    private static final Set<String> TRANSPORTS = Set.of("CAR", "PUBLIC_TRANSIT");
    private static final Set<String> PARKING = Set.of("REQUIRED", "PREFERRED", "NO_PREFERENCE");
    private static final Set<String> WALKING = Set.of("LOW", "NORMAL", "HIGH");
    private static final Set<String> TRANSFERS = Set.of("AVOID", "PREFERRED", "NO_PREFERENCE");
    private static final Set<String> STAIRS = Set.of("AVOID", "NEUTRAL");
    private static final Set<String> CAUTIONS = Set.of("SODIUM", "SUGAR", "CARBOHYDRATE", "SPICY", "INGREDIENT_CHECK", "NONE");

    private final GuestApplicationService guests;
    private final FamilyProfileMapper profiles;
    private final FamilyMemberMapper members;
    private final FamilyMemberCautionMapper cautions;

    public FamilyProfileApplicationService(GuestApplicationService guests, FamilyProfileMapper profiles,
            FamilyMemberMapper members, FamilyMemberCautionMapper cautions) {
        this.guests = guests;
        this.profiles = profiles;
        this.members = members;
        this.cautions = cautions;
    }

    @Transactional
    public FamilyProfileSnapshot create(String guestPublicId, ProfileCommand command) {
        validate(command);
        var guest = guests.requirePublicId(guestPublicId);
        var profile = new FamilyProfile(new ProfileOwnership(null, guest.getId()), command.name(), command.transportMode(),
                command.parkingPreference(), command.walkingBurdenPreference(), command.transferPreference(),
                command.stairsAvoidance());
        profiles.insert(profile);
        replaceMembers(profile.getId(), command.members());
        return snapshot(profile);
    }

    public List<FamilyProfileSnapshot> list(String guestPublicId) {
        var guest = guests.requirePublicId(guestPublicId);
        return profiles.findByOwnerGuestId(guest.getId()).stream().map(this::snapshot).toList();
    }

    public FamilyProfileSnapshot get(String guestPublicId, long profileId) {
        return owned(guestPublicId, profileId);
    }

    @Transactional
    public FamilyProfileSnapshot update(String guestPublicId, long profileId, ProfileCommand command) {
        validate(command);
        var guest = guests.requirePublicId(guestPublicId);
        var profile = profiles.findByIdAndOwnerGuestId(profileId, guest.getId());
        if (profile == null) throw new NoSuchElementException("가족 프로필을 찾을 수 없습니다.");
        profile.change(command.name(), command.transportMode(), command.parkingPreference(),
                command.walkingBurdenPreference(), command.transferPreference(), command.stairsAvoidance());
        if (profiles.update(profile) != 1) throw new NoSuchElementException("가족 프로필을 찾을 수 없습니다.");
        replaceMembers(profileId, command.members());
        return snapshot(profile);
    }

    public FamilyProfileSnapshot owned(String guestPublicId, long profileId) {
        var guest = guests.requirePublicId(guestPublicId);
        var profile = profiles.findByIdAndOwnerGuestId(profileId, guest.getId());
        if (profile == null) throw new NoSuchElementException("가족 프로필을 찾을 수 없습니다.");
        return snapshot(profile);
    }

    private void replaceMembers(long profileId, List<MemberCommand> commands) {
        members.deleteByProfileId(profileId);
        for (int index = 0; index < commands.size(); index++) {
            var command = commands.get(index);
            var member = new FamilyMember(new FamilyProfileId(profileId), command.nickname(), index,
                    command.continuousWalkingMinutes(), command.stairsPreference());
            members.insert(member);
            for (String caution : command.mealCautions()) {
                cautions.insert(new FamilyMemberCaution(member.getId(), caution));
            }
        }
    }

    private FamilyProfileSnapshot snapshot(FamilyProfile profile) {
        var memberSnapshots = members.findByProfileId(profile.getId()).stream().map(member ->
                new FamilyProfileSnapshot.Member(member.getId(), member.getNickname(), member.getContinuousWalkingMinutes(),
                        member.getStairsPreference(), cautions.findByFamilyMemberId(member.getId()).stream()
                                .map(FamilyMemberCaution::caution).toList())).toList();
        return new FamilyProfileSnapshot(profile.getId(), profile.getName(), profile.getTransportMode(),
                profile.getParkingPreference(), profile.getWalkingBurdenPreference(), profile.getTransferPreference(),
                profile.isStairsAvoidance(), memberSnapshots);
    }

    private void validate(ProfileCommand command) {
        if (command == null || blank(command.name()) || command.members() == null || command.members().isEmpty()) {
            throw new IllegalArgumentException("프로필 이름과 가족 구성원을 입력해 주세요.");
        }
        require(TRANSPORTS, command.transportMode());
        require(PARKING, command.parkingPreference());
        require(WALKING, command.walkingBurdenPreference());
        require(TRANSFERS, command.transferPreference());
        for (MemberCommand member : command.members()) {
            if (member == null || blank(member.nickname()) || member.continuousWalkingMinutes() < 0
                    || member.continuousWalkingMinutes() > 480 || member.mealCautions() == null
                    || member.mealCautions().isEmpty()) {
                throw new IllegalArgumentException("구성원 정보와 주의요소를 확인해 주세요.");
            }
            require(STAIRS, member.stairsPreference());
            var selections = member.mealCautions();
            if (!CAUTIONS.containsAll(selections) || (selections.contains("NONE") && selections.size() > 1)) {
                throw new IllegalArgumentException("주의요소 선택을 확인해 주세요.");
            }
        }
    }

    private static void require(Set<String> allowed, String value) {
        if (value == null || !allowed.contains(value)) {
            throw new IllegalArgumentException("선택 값을 확인해 주세요.");
        }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record ProfileCommand(String name, String transportMode, String parkingPreference,
            String walkingBurdenPreference, String transferPreference, boolean stairsAvoidance,
            List<MemberCommand> members) {
        public ProfileCommand { members = members == null ? null : List.copyOf(members); }
    }
    public record MemberCommand(String nickname, int continuousWalkingMinutes, String stairsPreference,
            List<String> mealCautions) {
        public MemberCommand {
            mealCautions = mealCautions == null ? null : mealCautions.stream()
                    .filter(java.util.Objects::nonNull).map(value -> value.trim().toUpperCase(Locale.ROOT)).distinct().toList();
        }
    }
}
