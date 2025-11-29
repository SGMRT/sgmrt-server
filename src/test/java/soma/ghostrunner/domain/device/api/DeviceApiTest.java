package soma.ghostrunner.domain.device.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import soma.ghostrunner.ApiTestSupport;
import soma.ghostrunner.domain.device.api.dto.DeviceRegistrationRequest;
import soma.ghostrunner.domain.device.api.dto.PushTokenSaveRequest;
import soma.ghostrunner.global.security.jwt.JwtUserDetails;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeviceApiTest extends ApiTestSupport {

    @DisplayName("디바이스 정보를 등록한다.")
    @Test
    void registerDeviceInfo() throws Exception {
        // given
        String memberUuid = UUID.randomUUID().toString();
        DeviceRegistrationRequest request = new DeviceRegistrationRequest("device-uuid", "1.0.0", "push-token", "iOS", "26", "iPhone 16 Pro");

        given(authService.isOwner(any(), any())).willReturn(true);

        JwtUserDetails userDetails = new JwtUserDetails(memberUuid);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        // when & then
        mockMvc.perform(post("/v1/members/{memberUuid}/devices", memberUuid)
                        .with(authentication(authentication))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(deviceService).registerDevice(memberUuid, request);
    }

    @DisplayName("디바이스 정보 등록 시 token이 null이어도 허용한다.")
    @Test
    void registerDeviceInfo_allowNullToken() throws Exception {
        // given
        String memberUuid = UUID.randomUUID().toString();
        DeviceRegistrationRequest request = new DeviceRegistrationRequest("device-uuid", "1.0.0", null, "iOS", "26", "iPhone 16 Pro");
        given(authService.isOwner(any(), any())).willReturn(true);
        JwtUserDetails userDetails = new JwtUserDetails(memberUuid);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        // when & then
        mockMvc.perform(post("/v1/members/{memberUuid}/devices", memberUuid)
                        .with(authentication(authentication))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk());
        verify(deviceService).registerDevice(memberUuid, request);
    }

    @DisplayName("디바이스 정보 등록 시 uuid가 null이면 불허한다.")
    @Test
    void registerDeviceInfo_disallowNullUuid() throws Exception {
        // given
        String memberUuid = UUID.randomUUID().toString();
        DeviceRegistrationRequest request = new DeviceRegistrationRequest(null, "1.0.0", "push-token", "iOS", "26", "iPhone 16 Pro");
        given(authService.isOwner(any(), any())).willReturn(true);
        JwtUserDetails userDetails = new JwtUserDetails(memberUuid);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        // when & then
        mockMvc.perform(post("/v1/members/{memberUuid}/devices", memberUuid)
                        .with(authentication(authentication))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @DisplayName("푸시 토큰을 저장한다.")
    @Test
    void updatePushToken() throws Exception {
        // given
        String memberUuid = UUID.randomUUID().toString();
        PushTokenSaveRequest request = new PushTokenSaveRequest("test-push-token");

        given(authService.isOwner(any(), any())).willReturn(true);

        JwtUserDetails userDetails = new JwtUserDetails(memberUuid);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        // when & then
        mockMvc.perform(post("/v1/member/{memberUuid}/push-token", memberUuid)
                        .with(authentication(authentication))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(deviceService).saveMemberPushToken(memberUuid, request.getPushToken());
    }
}