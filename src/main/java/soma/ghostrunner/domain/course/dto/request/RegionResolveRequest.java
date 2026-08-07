package soma.ghostrunner.domain.course.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 지역 등록(resolve) 요청. {@code name}은 "서울특별시 강남구 역삼동" 형태의 전체 경로로,
 * 동명 지역 충돌을 막기 위해 클라이언트가 시·구·동을 결합해 보낸다.
 *
 * 설계 문서: docs/refactoring/course-read-model/cache/05-cache-key-design.md §5-1, §6-4
 */
public record RegionResolveRequest(

        /**
         * 지역 이름의 모든 값이 새 행 + 새 캐시 키가 되므로 문자 공간을 좁혀 키 카디널리티를 지킨다.
         * 실제 OS 리버스 지오코딩 결과는 한글/영문/숫자/공백을 벗어나지 않는다.
         *
         * <p><b>자모 범위(U+1100~U+11FF)를 반드시 포함한다.</b> 검증은 DTO 바인딩 시점,
         * 즉 {@code RegionService}의 NFC 정규화 <i>이전</i>에 평가된다. iOS가 보내는 자모 분해형(NFD) 한글은
         * {@code 가-힣}에 걸리지 않으므로, 자모를 빼면 iOS 등록이 전량 400으로 막힌다.</p>
         */
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[가-힣\\u1100-\\u11FFA-Za-z0-9 ]+$", message = "지역 이름 형식이 올바르지 않습니다")
        String name,

        @NotNull
        @Min(-90)
        @Max(90)
        Double lat,

        @NotNull
        @Min(-180)
        @Max(180)
        Double lng
) {}
