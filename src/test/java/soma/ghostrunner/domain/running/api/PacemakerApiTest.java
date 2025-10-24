package soma.ghostrunner.domain.running.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import soma.ghostrunner.ApiTestSupport;
import soma.ghostrunner.domain.running.api.dto.request.CreatePacemakerRequest;
import soma.ghostrunner.domain.running.api.dto.request.PacemakerPatchAfterRunningRequest;
import soma.ghostrunner.domain.running.api.support.PacemakerType;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PacemakerApiTest extends ApiTestSupport  {

    @DisplayName("페이스메이커를 생성한다.")
    @Test
    void createPacemaker() throws Exception {
        // given
        CreatePacemakerRequest request = createPacemakerRequestBody(
                PacemakerType.RECOVERY_JOGGING, 5.0, 3, 36);

        // when // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/pacemaker")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .queryParam("courseId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andDo(print())
                .andExpect(status().isOk())
        ;
    }

    private CreatePacemakerRequest createPacemakerRequestBody(PacemakerType type, Double targetDistance,
                                                              Integer condition, Integer temperature) {
        return new CreatePacemakerRequest(type, targetDistance, condition, temperature, 1L);
    }

    @DisplayName("페이스메이커를 생성할 때 HTTP BODY의 Empty/Null 조건을 검증한다.")
    @Test
    void createPacemakerWithNullRequests() throws Exception{
        // given
        CreatePacemakerRequest request = createPacemakerRequestBody(null, null, 3, 36);

        // when // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/pacemaker")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .queryParam("courseId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
        ;
    }

    @DisplayName("페이스메이커를 생성할 때 양수인 값을 검증한다.")
    @Test
    void createPacemakerWithNegativeRequests() throws Exception{
        // given
        CreatePacemakerRequest request = createPacemakerRequestBody(
                PacemakerType.RECOVERY_JOGGING, -5.0, 3, 36);

        // when // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/pacemaker")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
        ;
    }

    @DisplayName("페이스메이커를 생성할 때 온도는 -50 이상 50 이하이다.")
    @Test
    void createPacemakerWithInvalidTemperatureRequests() throws Exception{
        // given
        CreatePacemakerRequest request = createPacemakerRequestBody(
                PacemakerType.RECOVERY_JOGGING, 5.0, 3, 136);

        // when // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/pacemaker")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .queryParam("courseId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
        ;
    }

    @DisplayName("페이스메이커를 조회한다.")
    @Test
    void getPacemaker() throws Exception {
        // when // then
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/pacemaker/1")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                )
                .andDo(print())
                .andExpect(status().isOk())
        ;
    }

    @DisplayName("페이스메이커를 생성하기 위한 일일 제한 횟수를 조회한다.")
    @Test
    void getRateLimitCounterToMakePacemaker() throws Exception {
        // when // then
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/pacemaker/rate-limit")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                )
                .andDo(print())
                .andExpect(status().isOk())
        ;
    }

    @DisplayName("러닝 후 페이스메이커를 업대이트한다.")
    @Test
    void patchPacemakerAfterRunning() throws Exception {
        // given
        PacemakerPatchAfterRunningRequest request = new PacemakerPatchAfterRunningRequest(1L, 1L);

        // when // then
        mockMvc.perform(MockMvcRequestBuilders.patch("/v1/pacemaker/after-running")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andDo(print())
                .andExpect(status().isOk())
        ;
    }

    @DisplayName("러닝 후 페이스메이커를 업대이트할 때 필수 파라미터(페이스메이커ID, 러닝ID)를 검증한다.")
    @Test
    void patchPacemakerAfterRunningWithoutParams() throws Exception{
        // given
        PacemakerPatchAfterRunningRequest request = new PacemakerPatchAfterRunningRequest(null, 1L);

        // when // then
        mockMvc.perform(MockMvcRequestBuilders.patch("/v1/pacemaker/after-running")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
        ;
    }

}
