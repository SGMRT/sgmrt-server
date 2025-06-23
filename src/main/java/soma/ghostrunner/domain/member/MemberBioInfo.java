package soma.ghostrunner.domain.member;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class MemberBioInfo {

    @Column(name = "gender")
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(name = "weight")
    private double weight;

    @Column(name = "height")
    private double height;
}
