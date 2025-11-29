package soma.ghostrunner.domain.running.application.dto;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.running.domain.RunningType;
import soma.ghostrunner.domain.running.domain.formula.WorkoutType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkoutDtoTest {

    @DisplayName("훈련표 최적화 프롬프트를 만들기 위해 Json 형태의 String으로 변환한다.")
    @Test
    void of() {
        // given
        WorkoutSetDto s1 = WorkoutSetDto.of(1, WorkoutType.E, "5:10", 0.0, 1.5);
        WorkoutSetDto s2 = WorkoutSetDto.of(2, WorkoutType.E, "5:05", 1.5, 7.5);
        WorkoutSetDto s3 = WorkoutSetDto.of(2, WorkoutType.E, "7:30", 7.5, 8.5);
        WorkoutSetDto s4 = WorkoutSetDto.of(3, WorkoutType.E, "4:40", 8.5, 10.0);

        WorkoutDto dto = WorkoutDto.of(RunningType.E, 10.0, List.of(s1, s2, s3, s4));

        // when
        String json = dto.toStringForWorkoutImprovementPrompt();
        System.out.println(json);

        // then
        assertTrue(json.contains("\"type\": \"E\""));
        assertTrue(json.contains("\"goal_km\": 10.0"));
        assertTrue(json.contains("\"sets\""));
        assertTrue(json.contains("\"pace_min/km\": \"5:10\""));
        assertTrue(json.contains("\"end_km\": 10.0"));
    }

    @DisplayName("LLM으로 부터 받은 가공된 훈련표(문자열)을 역직렬화한다.")
    @Test
    void toStringFromLlmWorkoutResponse() {
        // given
        String json = """
        {
          "type": "M",
          "goal_km": 10.0,
          "expected_minutes": 74,
          "sets": [
            {
              "setNum": 1,
              "pace_min/km": "8:00",
              "start_km": 0.0,
              "end_km": 2.0,
              "feedback": "전문가1..."
            }
          ]
        }
        """;

        // when
        WorkoutDto dto = WorkoutDto.fromProcessedWorkoutDto(json);

        // then
        Assertions.assertThat(dto.getType()).isEqualTo(RunningType.M);
        Assertions.assertThat(dto.getGoalKm()).isEqualTo(10.0);
        Assertions.assertThat(dto.getExpectedMinutes()).isEqualTo(74);
        Assertions.assertThat(dto.getInitialMessage()).isNull();
        Assertions.assertThat(dto.getSets().get(0).getPace()).isEqualTo("8:00");
        Assertions.assertThat(dto.getSets().get(0).getFeedback()).isEqualTo("전문가1...");
    }

    @DisplayName("음성안내 프롬프트를 만들기 위해 Json 형태의 String으로 변환한다.")
    @Test
    void toStringForVoiceGuidancePrompt() {
        // given
        WorkoutSetDto s1 = WorkoutSetDto.of(1, WorkoutType.E, "5:10", 0.0, 1.5, "피드백1");
        WorkoutSetDto s2 = WorkoutSetDto.of(2, WorkoutType.E, "5:05", 1.5, 7.5, "피드백2");
        WorkoutSetDto s3 = WorkoutSetDto.of(2, WorkoutType.E, "7:30", 7.5, 8.5, "피드백3");
        WorkoutSetDto s4 = WorkoutSetDto.of(3, WorkoutType.E, "4:40", 8.5, 10.0, "피드백4");

        WorkoutDto dto = WorkoutDto.of(RunningType.E, 10.0, List.of(s1, s2, s3, s4), 35);

        // when
        String json = dto.toStringForVoiceGuidancePrompt();
        System.out.println(json);

        // 간단 검증
        assertTrue(json.contains("\"type\": \"E\""));
        assertTrue(json.contains("\"goal_km\": 10.0"));
        assertTrue(json.contains("\"sets\""));
        assertTrue(json.contains("\"pace_min/km\": \"5:10\""));
        assertTrue(json.contains("\"end_km\": 10.0"));
        assertTrue(json.contains("\"expected_minutes\": 35"));
    }

    @DisplayName("LLM으로 부터 받은 음성안내가 포함된 훈련표(문자열)을 역직렬화한다.")
    @Test
    void fromVoiceGuidanceGeneratedWorkoutDto() {
        // given
        String json = """
                        {
                           "workout": {
                             "type": "M",
                             "goal_km": 10.0,
                             "expected_minutes": 73,
                             "summary": "10km M 페이스 러닝: 2km 워밍업, 5km 7:05/km, 2km 6:55/km, 1km 쿨다운으로 구성됩니다. 일정한 호흡과 네거티브 스플릿으로 효율을 높입니다.",
                             "initial_message": "오늘은 10km M 페이스 러닝이에요. 구성은 워밍업 2km 8:00/km, 본훈련 5km 7:05/km, 여유 있으면 2km 6:55/km, 그리고 쿨다운 1km입니다. 목표는 RPE 5–6, 호흡 3-3로 일정한 리듬 유지예요. 기온 22도이니 3~4km 지점에서 물 한 모금, 햇볕이 강하면 1km당 5~10초 여유를 주세요. 통증이나 어지러움이 느껴지면 바로 속도를 낮추거나 중단하세요. 준비되면 워밍업부터 시작합니다.",
                             "sets": [
                               {
                                 "setNum": 1,
                                 "pace_min/km": "8:00",
                                 "start_km": 0.0,
                                 "end_km": 2.0,
                                 "feedback": "전문가1: 28세·VDOT 29에서 근골격 부담을 줄이기 위해 워밍업을 2km로 확보, 8:00으로 안정 시작. 전문가2: 메인 M 블록 대비 심박·보폭 정렬 목적. 전문가3: 22℃로 무난하나 초반 과열 방지와 수분 상태 점검.",
                                 "message": "워밍업 2km 시작! 페이스 8:00/km로 아주 가볍게 갑니다. 보폭은 짧게, 어깨와 손은 힘 빼고 호흡 4-4로 몸을 깨워요. 첫 500m는 천천히, 1km 지점에서 자세 점검 후 페이스만 맞춰 주세요."
                               },
                               {
                                 "setNum": 2,
                                 "pace_min/km": "7:05",
                                 "start_km": 2.0,
                                 "end_km": 7.0,
                                 "feedback": "전문가1: 컨디션 ‘매우 좋음’을 반영하되 VDOT 29의 M 페이스 권장 범위(≈7:00–7:15/km) 내에서 7:05로 설정해 과부하 위험 낮춤. 전문가2: 중심 5km를 일정 페이스로 유지(RPE 5–6, 호흡 3-3), 변동 ±5초 허용. 전문가3: 햇볕 강하거나 습하면 5–10초/km 완화, 3–4km 지점에서 급수.",
                                 "message": "지금부터 메인 5km, 목표 페이스 7:05/km입니다. ±5초 범위에서 리듬 유지, 호흡 3-3과 RPE 5–6을 기억하세요. 오르막·맞바람은 힘 고정, 내리막은 과속 주의. 3~4km에서 물 한 모금, 햇볕·습하면 5~10초 느리게 가도 좋아요."
                               },
                               {
                                 "setNum": 3,
                                 "pace_min/km": "6:55",
                                 "start_km": 7.0,
                                 "end_km": 9.0,
                                 "feedback": "전문가1: 통증·호흡 여유 시 2km만 소폭 상향(6:55)으로 자극, 과도한 젖산 축적 방지. 전문가2: 네거티브 스플릿 전략으로 효율 확보, 후반 HR drift 3–5bpm 이내 관리. 전문가3: 맞바람/노면 불량 시 7:05로 회귀, 갈증·어지러움 시 즉시 완화.",
                                 "message": "후반 2km는 여유 있으면 6:55/km로 살짝 올립니다. 힘들면 7:05로 유지해도 괜찮아요. 상체 곧게, 팔치기로 리듬, 호흡은 편안하게 유지하며 과호흡은 금지. 맞바람이나 노면이 나쁘면 즉시 페이스 완화하세요."
                               },
                               {
                                 "setNum": 4,
                                 "pace_min/km": "8:00",
                                 "start_km": 9.0,
                                 "end_km": 10.0,
                                 "feedback": "전문가1: 회복 촉진을 위해 1km 쿨다운 8:00으로 마무리. 전문가2: 보폭 축소·케이던스 일정, 스트라이드 없이 종료. 전문가3: 쿨다운 중 수분 섭취·그늘 이동, 어지러움·통증 시 걷기 전환 및 훈련 중단.",
                                 "message": "쿨다운 1km, 8:00/km로 정리합니다. 보폭을 줄이고 케이던스만 가볍게, 호흡 4-4로 심박을 낮춰요. 그늘로 이동해 수분을 보충하고, 불편하면 걷기로 전환 후 천천히 마무리합시다."
                               }
                             ]
                           }
                        }
                """;

        // when
        WorkoutDto dto = WorkoutDto.fromVoiceGuidanceGeneratedWorkoutDto(json);

        // then
        System.out.println(dto.toString());
    }

}
