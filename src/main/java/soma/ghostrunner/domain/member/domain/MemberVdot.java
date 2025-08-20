package soma.ghostrunner.domain.member.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberVdot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vdot")
    private Integer vdot;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Builder(access = AccessLevel.PRIVATE)
    public MemberVdot(Integer vdot, Member member) {
        this.vdot = vdot;
        this.member = member;
    }

    public static MemberVdot of(Integer vdot, Member member) {
        return MemberVdot.builder()
                .vdot(vdot)
                .member(member)
                .build();
    }

}
