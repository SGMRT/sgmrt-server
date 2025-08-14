package soma.ghostrunner.domain.member.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.member.api.dto.response.MemberResponse;
import soma.ghostrunner.domain.member.domain.Member;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    @Query("SELECT m FROM Member m " +
            "INNER JOIN MemberAuthInfo mai ON m.id = mai.member.id WHERE mai.externalAuthUid = :authUid")
    Optional<Member> findByExternalAuthUid(String authUid);

    boolean existsByNickname(String nickname);

    Optional<Member> findByUuid(String uuid);

    @Query("SELECT new soma.ghostrunner.domain.member.api.dto.response.MemberResponse(" +
                "m.uuid, m.nickname, m.profilePictureUrl, m.bioInfo.gender, m.bioInfo.age, m.bioInfo.weight, " +
                "m.bioInfo.height, ms.pushAlarmEnabled, ms.vibrationEnabled, ms.voiceGuidanceEnabled" +
            ") FROM Member m LEFT JOIN MemberSettings ms ON m.id = ms.member.id " +
            "WHERE m.uuid = :uuid")
    Optional<MemberResponse> findMemberDtoByUuid(String uuid);

    void deleteByUuid(String memberUuid);

    @Query(value = "SELECT * FROM member m WHERE m.uuid = :uuid AND m.deleted_at IS NOT NULL", nativeQuery = true)
    Optional<Member> findSoftDeletedMemberByUuid(String uuid);

    boolean existsByUuid(String memberUuid);

}
