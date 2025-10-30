package soma.ghostrunner.domain.notice.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import soma.ghostrunner.ApiTestSupport;
import soma.ghostrunner.domain.notice.api.dto.request.NoticeDismissRequest;
import soma.ghostrunner.domain.notice.api.dto.response.NoticeDetailedResponse;
import soma.ghostrunner.domain.notice.domain.enums.NoticeType;
import soma.ghostrunner.global.security.jwt.JwtUserDetails;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NoticeApiTest extends ApiTestSupport {

    private final LocalDateTime NOW = LocalDateTime.of(2025, 8, 8, 12, 0);

    @DisplayName("전체 공지사항 목록을 페이지네이션하여 조회한다.")
    @Test
    void getAllNotices_success() throws Exception {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        given(noticeService.findAllNotices(0, 10, null))
                .willReturn(new PageImpl<>(List.of(), pageRequest, 0));

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.get("/v2/notices")
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @DisplayName("활성 공지사항 목록을 조회한다.")
    @Test
    void getActiveNotices_success() throws Exception {
        // given
        String userId = UUID.randomUUID().toString();
        given(authService.isOwner(any(), any())).willReturn(true);
        given(noticeService.findActiveNotices(userId, NOW, NoticeType.GENERAL_V2)).willReturn(List.of());

        JwtUserDetails userDetails = new JwtUserDetails(userId);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.get("/v2/notices/active")
                        .with(authentication(authentication))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @DisplayName("특정 공지사항을 ID로 조회한다.")
    @Test
    void getNotice_success() throws Exception {
        // given
        Long noticeId = 1L;
        NoticeDetailedResponse response = new NoticeDetailedResponse(noticeId, "제목", NoticeType.GENERAL_V2, "내용", null,
                null, NOW, null);
        given(noticeService.findNotice(noticeId)).willReturn(response);

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.get("/v2/notices/{noticeId}", noticeId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(noticeId))
                .andExpect(jsonPath("$.title").value("제목"));
    }

    @DisplayName("특정 공지사항을 숨김 처리한다.")
    @Test
    void dismissNotice_success() throws Exception {
        // given
        Long noticeId = 1L;
        String userId = UUID.randomUUID().toString();
        NoticeDismissRequest request = new NoticeDismissRequest(1);

        JwtUserDetails userDetails = new JwtUserDetails(userId);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/notices/{noticeId}/dismissal", noticeId)
                        .with(authentication(authentication))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(String.valueOf(MediaType.APPLICATION_JSON))
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk());
    }

    /* * * * * * * * * *
     * Admin API Tests *
     * * * * * * * * * */

    @DisplayName("새로운 공지사항을 생성한다.")
    @Test
    void createNotice_success() throws Exception {
        // given
        MockMultipartFile imagePart = new MockMultipartFile("image", "image.png", "image/png", "<<png data>>".getBytes(StandardCharsets.UTF_8));

        given(noticeService.saveNotice(any())).willReturn(1L);

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.multipart("/v1/admin/notices")
                        .file(imagePart)
                        .param("title", "새 공지")
                        .param("content", "공지 내용입니다.")
                        .param("priority", "1")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    @DisplayName("공지사항을 수정한다.")
    @Test
    void updateNotice_success() throws Exception {
        // given
        Long noticeId = 1L;
        MockMultipartFile titlePart = new MockMultipartFile("title", "", "text/plain", "수정된 공지".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile updateAttrsPart = new MockMultipartFile("updateAttrs", "", "text/plain", "TITLE".getBytes(StandardCharsets.UTF_8));

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.multipart("/v1/admin/notices/{noticeId}", noticeId)
                        .file(titlePart)
                        .file(updateAttrsPart)
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        })
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @DisplayName("공지사항을 삭제한다.")
    @Test
    void deleteNotice_success() throws Exception {
        // given
        Long noticeId = 1L;

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.delete("/v1/admin/notices/{noticeId}", noticeId)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @DisplayName("공지사항 활성화 요청을 처리한다.")
    @Test
    void activateNotices_success() throws Exception {
        // given
        List<Long> noticeIds = List.of(1L, 2L, 3L);
        LocalDateTime endAt = NOW.plusDays(7);
        String endAtStr = endAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        given(noticeService.activateNotices(eq(noticeIds), any(LocalDateTime.class), eq(endAt))).willReturn(noticeIds);
        String requestBody = """
                {
                    "noticeIds": [1, 2, 3],
                    "endAt": "%s"
                }
                """.formatted(endAtStr);
        // when & then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/admin/notices/activate")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(1))
                .andExpect(jsonPath("$[1]").value(2))
                .andExpect(jsonPath("$[2]").value(3));
    }

    @DisplayName("공지사항 비활성화 요청을 처리한다.")
    @Test
    void deactivateNotices_success() throws Exception {
        // given
        List<Long> noticeIds = List.of(1L, 2L, 3L);
        given(noticeService.deactivateNotices(noticeIds)).willReturn(noticeIds);
        String requestBody = """
                {
                    "noticeIds": [1, 2, 3]
                }
                """;
        // when & then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/admin/notices/deactivate")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(1))
                .andExpect(jsonPath("$[1]").value(2))
                .andExpect(jsonPath("$[2]").value(3));
    }

    /* * * * * * * * * * * * *
     * Deprecated API Tests  *
     * * * * * * * * * * * * */

    @Deprecated
    @DisplayName("전체 공지사항 목록을 페이지네이션하여 조회한다. (v1)")
    @Test
    void getAllNotices_success_V1() throws Exception {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        given(noticeService.findAllNotices(0, 10, null))
                .willReturn(new PageImpl<>(List.of(), pageRequest, 0));

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/notices")
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Deprecated
    @DisplayName("활성 공지사항 목록을 조회한다. (v1)")
    @Test
    void getActiveNotices_success_V1() throws Exception {
        // given
        String userId = UUID.randomUUID().toString();
        given(authService.isOwner(any(), any())).willReturn(true);
        given(noticeService.findActiveNotices(userId, NOW, NoticeType.GENERAL_V2)).willReturn(List.of());

        JwtUserDetails userDetails = new JwtUserDetails(userId);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/notices/active")
                        .with(authentication(authentication))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Deprecated
    @DisplayName("특정 공지사항을 ID로 조회한다. (v1)")
    @Test
    void getNotice_success_V1() throws Exception {
        // given
        Long noticeId = 1L;
        NoticeDetailedResponse response = new NoticeDetailedResponse(noticeId, "제목", NoticeType.GENERAL_V2, "내용", null,
                null, NOW, null);
        given(noticeService.findNotice(noticeId)).willReturn(response);

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/notices/{noticeId}", noticeId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(noticeId))
                .andExpect(jsonPath("$.title").value("앱을 업데이트해주세요!"));
    }

    @Deprecated
    @DisplayName("특정 공지사항을 숨김 처리한다. (v1)")
    @Test
    void dismissNotice_success_V1() throws Exception {
        // given
        Long noticeId = 1L;
        String userId = UUID.randomUUID().toString();
        NoticeDismissRequest request = new NoticeDismissRequest(1);

        JwtUserDetails userDetails = new JwtUserDetails(userId);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/notices/{noticeId}/dismissal", noticeId)
                        .with(authentication(authentication))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(String.valueOf(MediaType.APPLICATION_JSON))
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk());
    }


}