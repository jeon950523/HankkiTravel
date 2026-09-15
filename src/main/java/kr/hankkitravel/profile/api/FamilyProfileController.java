package kr.hankkitravel.profile.api;

import java.util.List;
import java.util.NoSuchElementException;
import kr.hankkitravel.profile.application.FamilyProfileApplicationService;
import kr.hankkitravel.profile.application.FamilyProfileSnapshot;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/guests/{guestPublicId}/profiles")
public class FamilyProfileController {
    private final FamilyProfileApplicationService profiles;

    public FamilyProfileController(FamilyProfileApplicationService profiles) { this.profiles = profiles; }

    @GetMapping
    public List<ProfileResponse> list(@PathVariable String guestPublicId) {
        return profiles.list(guestPublicId).stream().map(ProfileResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<ProfileResponse> create(@PathVariable String guestPublicId, @RequestBody ProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(profiles.create(guestPublicId, request.command())));
    }

    @GetMapping("/{profileId}")
    public ProfileResponse get(@PathVariable String guestPublicId, @PathVariable long profileId) {
        return ProfileResponse.from(profiles.get(guestPublicId, profileId));
    }

    @PutMapping("/{profileId}")
    public ProfileResponse update(@PathVariable String guestPublicId, @PathVariable long profileId,
            @RequestBody ProfileRequest request) {
        return ProfileResponse.from(profiles.update(guestPublicId, profileId, request.command()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiError> missing() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("PROFILE_NOT_FOUND", "요청한 프로필을 찾을 수 없습니다."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalid() {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_PROFILE", "프로필 입력값을 확인해 주세요."));
    }

    public record ProfileRequest(String name, String transportMode, String parkingPreference,
            String walkingBurdenPreference, String transferPreference, boolean stairsAvoidance,
            List<MemberRequest> members) {
        FamilyProfileApplicationService.ProfileCommand command() {
            return new FamilyProfileApplicationService.ProfileCommand(name, transportMode, parkingPreference,
                    walkingBurdenPreference, transferPreference, stairsAvoidance,
                    members == null ? null : members.stream().map(MemberRequest::command).toList());
        }
    }
    public record MemberRequest(String nickname, int continuousWalkingMinutes, String stairsPreference,
            List<String> mealCautions) {
        FamilyProfileApplicationService.MemberCommand command() {
            return new FamilyProfileApplicationService.MemberCommand(nickname, continuousWalkingMinutes,
                    stairsPreference, mealCautions);
        }
    }
    public record ProfileResponse(long profileId, String name, String transportMode, String parkingPreference,
            String walkingBurdenPreference, String transferPreference, boolean stairsAvoidance, List<MemberResponse> members) {
        static ProfileResponse from(FamilyProfileSnapshot source) {
            return new ProfileResponse(source.profileId(), source.name(), source.transportMode(), source.parkingPreference(),
                    source.walkingBurdenPreference(), source.transferPreference(), source.stairsAvoidance(),
                    source.members().stream().map(member -> new MemberResponse(member.memberId(), member.nickname(),
                            member.continuousWalkingMinutes(), member.stairsPreference(), member.mealCautions())).toList());
        }
    }
    public record MemberResponse(long memberId, String nickname, int continuousWalkingMinutes,
            String stairsPreference, List<String> mealCautions) { }
    public record ApiError(String code, String message) { }
}
