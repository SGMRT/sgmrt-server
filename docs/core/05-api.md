# 05. 외부 API 인벤토리

> **⚠️ 불변 계약**: 앱 클라이언트가 배포되어 있으므로 아래 API의 경로·요청·응답 스펙은 리팩토링 중 절대 변경 금지. `@Deprecated` 표기된 엔드포인트도 구버전 클라이언트 호환을 위해 유지해야 한다.

인증: 별도 표기가 없으면 JWT Bearer. `[Admin]`은 `@AdminOnly`.

## running (`RunningApi`)

| 메서드 | 경로 | 용도 |
|---|---|---|
| GET | `/` | 헬스체크 (ALB) |
| POST | `/v1/runs` | 러닝 종료 → 코스+러닝 동시 생성 (multipart) |
| POST | `/v1/runs/courses/{courseId}` | 기존 코스에서 러닝 생성 |
| PATCH | `/v1/runs/{id}/name` | 러닝 이름 변경 |
| PATCH | `/v1/runs/{id}/isPublic` | 공개 여부 토글 |
| GET | `/v1/runs/{id}/telemetries` | 텔레메트리 조회 |
| GET | `/v1/runs/{id}` | 솔로 러닝 상세 |
| GET | `/v1/runs/{myId}/ghosts/{ghostId}` | 고스트 러닝 비교 상세 |
| DELETE | `/v1/runs` | 러닝 벌크 삭제 |
| GET | `/v1/runs` | 내 러닝 목록 |
| GET | `/v1/runs/courses/{courseId}` | 코스별 내 러닝 목록 |
| GET | `/v1/runs/monthly/status` | 월별 러닝 통계 |

(일부 조회 엔드포인트에 `@Deprecated` 표기 있음 — 유지 필요)

## course (`CourseApi`, prefix `/v1`)

| 메서드 | 경로 | 용도 |
|---|---|---|
| GET | `/courses` | 위치(바운딩박스) 기반 지도 코스 조회 (캐싱) |
| GET | `/courses/{id}` | 코스 상세 |
| PATCH | `/courses/{id}` | 코스 수정 |
| DELETE | `/courses/{id}` | 코스 삭제 |
| GET | `/courses/{id}/ghosts` | 코스의 고스트 목록 (페이징) |
| GET | `/courses/{id}/ranking` | 코스 랭킹 |
| GET | `/courses/{id}/top-ranking` | 상위 랭킹 (읽기모델) |
| GET | `/courses/{id}/top-percentage` | 내 기록 상위 % |
| GET | `/courses/{id}/statistics` | 코스 통계 |
| GET | `/members/{uuid}/courses` | 특정 회원이 만든 코스 |

## pacemaker (`PacemakerApi`)

| 메서드 | 경로 | 용도 |
|---|---|---|
| POST | `/v1/pacemaker` | 페이스메이커 생성 (비동기, 하루 3회 제한) |
| GET | `/v1/pacemaker/{id}` | 생성 상태/결과 폴링 |
| GET | `/v1/pacemaker` | 코스뷰 폴링 |
| DELETE | `/v1/pacemaker/{id}` | 삭제 |
| PATCH | `/v1/pacemaker/after-running` | 러닝 후 사용 여부 갱신 |
| GET | `/v1/pacemaker/rate-limit` | 남은 생성 횟수 |

## member (`MemberApi`, prefix `/v1/members`)

| 메서드 | 경로 | 용도 |
|---|---|---|
| GET/PATCH/DELETE | `/{uuid}` | 프로필 조회/수정/탈퇴 |
| POST | `/{uuid}/terms-agreement` | 약관 동의 |
| PATCH | `/{uuid}/settings` | 푸시/진동/음성 설정 |
| GET | `/vdot` | 내 VDOT 조회 |
| POST | `/vdot?level=` | 러닝 레벨로 VDOT 초기 설정 |

## auth (`AuthApi`, prefix `/v1/auth`) — Authorization 헤더 기반

| 메서드 | 경로 | 용도 |
|---|---|---|
| POST | `/firebase-signin` | Firebase idToken → JWT 발급 |
| POST | `/firebase-signup` | 회원가입 |
| POST | `/reissue` | 리프레시 토큰으로 재발급 (탈취 감지 포함) |
| POST | `/logout` | 로그아웃 (리프레시 토큰 삭제) |

## device (`DeviceApi`)

| 메서드 | 경로 | 용도 |
|---|---|---|
| POST | `/v1/members/{uuid}/devices` | 기기 등록/갱신 |
| POST | `/v1/member/{uuid}/push-token` | @Deprecated 구버전 호환 (member 단수형 주의) |

## notification (`NotificationApi`)

| 메서드 | 경로 | 용도 |
|---|---|---|
| POST | `/v1/push/{messageUuid}` | 푸시 읽음 처리 |
| POST | `/v1/admin/push` | [Admin] 개별 푸시 |
| POST | `/v1/admin/push/broadcast` | [Admin] 브로드캐스트 (multipart) |

## notice (`NoticeApi`)

| 메서드 | 경로 | 용도 |
|---|---|---|
| GET | `/v2/notices`, `/v2/notices/active`, `/v2/notices/{id}` | 공지 조회 (v2) |
| POST | `/v2/notices/{id}/dismissal` | 다시 보지 않기 |
| GET | `/v1/notices...` (4종) | @Deprecated — 클라 v1.0.3 이하 호환, 유지 필요 |
| POST | `/v1/admin/notices` | [Admin] 공지 생성 (multipart) |
| GET | `/v1/admin/notices/deactivated` | [Admin] 비활성 공지 목록 |
| POST | `/v1/admin/notices/activate`, `/deactivate` | [Admin] 활성/비활성 |
| PATCH/DELETE | `/v1/admin/notices/{id}` | [Admin] 수정/삭제 |

## common (`CommonApi`)

| 메서드 | 경로 | 용도 |
|---|---|---|
| GET | `/v1/common/presign-url?type=&fileName=` | S3 업로드 presign URL (현재 MEMBER_PROFILE만) |
