package soma.ghostrunner.domain.member.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MemberSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "member_id", nullable = false)
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Member member;

    @Column(nullable = false)
    private boolean pushAlarmEnabled;

    @Column(nullable = false)
    private boolean vibrationEnabled;

    public void updateSettings(Boolean pushAlarmEnabled, Boolean vibrationEnabled) {
        this.pushAlarmEnabled = pushAlarmEnabled != null ? pushAlarmEnabled : this.pushAlarmEnabled;
        this.vibrationEnabled = vibrationEnabled != null ? vibrationEnabled : this.vibrationEnabled;
    }


    @Builder(access = AccessLevel.PRIVATE)
    protected MemberSettings(Member member, Boolean pushAlarmEnabled,
                             Boolean vibrationEnabled) {
        this.member = member;
        this.pushAlarmEnabled = pushAlarmEnabled;
        this.vibrationEnabled = vibrationEnabled;
    }

    public static MemberSettings of(Member member) {
        if (member == null) throw new IllegalArgumentException("Member cannot be null");
        return MemberSettings.builder()
                .member(member)
                .pushAlarmEnabled(true)
                .vibrationEnabled(true)
                .build();
    }

    public static MemberSettings of(Member member, boolean pushAlarmEnabled, boolean vibrationEnabled) {
        if (member == null) throw new IllegalArgumentException("Member cannot be null");
        return MemberSettings.builder()
                .member(member)
                .pushAlarmEnabled(pushAlarmEnabled)
                .vibrationEnabled(vibrationEnabled)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MemberSettings other)) return false;
        return (this.member.getId().equals(other.member.getId()))
            && (this.pushAlarmEnabled == other.pushAlarmEnabled)
            && (this.vibrationEnabled == other.vibrationEnabled);
    }

}
