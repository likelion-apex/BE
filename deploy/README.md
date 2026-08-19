# Production deployment

The production service runs the executable JAR from `/srv/mutsa/current` with systemd and exposes it only on port 8082 behind Apache.

## One-time server setup

1. Copy this `deploy` directory to the server and run `sudo ./bootstrap-server.sh`.
2. Replace every `CHANGE_ME` in `/etc/mutsa/mutsa.env` and create the `mutsa` MariaDB database and `mutsa_app` account.
3. Add the GitHub Actions deployment public key to `/home/mutsa-deploy/.ssh/authorized_keys`.
4. Add `PROD_HOST`, `PROD_USER`, `PROD_SSH_PRIVATE_KEY`, and `PROD_SSH_KNOWN_HOSTS` to the GitHub `production` environment.
5. Run the workflow on `main`. After the local 8082 check succeeds, enable the site with `sudo a2ensite mutsa.conf`, validate with `sudo apache2ctl configtest`, and reload Apache.

The server environment file must contain the two exact CORS origins without trailing slashes:

```text
CORS_ALLOWED_ORIGINS=https://ssoak.my,http://localhost:3000
```

Application secrets stay on the server and are not copied into GitHub Actions.

## 환경변수 설명

### `SHORTFORM_PRODUCT_CACHE_ENABLED`

제품명 정규화와 전성분 조회 결과 캐시를 제어합니다.

```dotenv
SHORTFORM_PRODUCT_CACHE_ENABLED=true
```

- DB의 `shortform_product_enrichments` 테이블에서 기존 결과 조회
- 캐시가 있으면 OpenAI/Gemini 호출 없이 재사용
- 캐시 미스면 AI를 호출하고 결과를 DB에 저장
- 성분을 확보한 결과는 30일
- 미확인·실패 결과는 1일 동안 유지
- 운영 권장값입니다. API 비용과 응답 시간을 줄여줍니다.

```dotenv
SHORTFORM_PRODUCT_CACHE_ENABLED=false
```

- 기존 제품·전성분 캐시를 읽지 않음
- 새 AI 결과도 캐시 테이블에 저장하지 않음
- OpenAI/Gemini 응답을 새로 비교할 때 사용

중요하게도 `false`로 바꿔도 기존 DB 데이터가 삭제되지는 않습니다. 다시 `true`로 켜면 만료되지 않은 과거 캐시가 다시 사용될 수 있습니다.

### `SHORTFORM_GEMINI_FALLBACK_ENABLED`

OpenAI가 전성분을 확보하지 못했을 때 Gemini로 재시도할지를 결정합니다.

```dotenv
SHORTFORM_GEMINI_FALLBACK_ENABLED=true
```

호출 순서는 다음과 같습니다.

```text
OpenAI 기본 모델
→ OpenAI 보완 모델
→ Gemini Google Search
→ Gemini 일반 지식 추정
```

마지막 Gemini 일반 지식 결과는 출처가 없으므로 `ESTIMATED`로 표시되고, 안전도도 확정 `SAFE`가 아닌 `UNKNOWN`으로 제한됩니다.

```dotenv
SHORTFORM_GEMINI_FALLBACK_ENABLED=false
```

- OpenAI까지만 사용
- OpenAI가 성분을 못 찾으면 미확인 상태로 종료
- Gemini 비용은 줄지만 성분 확보율도 낮아짐

MVP 서버에서는 다음 설정을 권장합니다.

```dotenv
SHORTFORM_PRODUCT_CACHE_ENABLED=true
SHORTFORM_GEMINI_FALLBACK_ENABLED=true
```

매번 제품·성분 조회 결과를 새로 확인할 때는:

```dotenv
SHORTFORM_PRODUCT_CACHE_ENABLED=false
SHORTFORM_GEMINI_FALLBACK_ENABLED=true
```

환경변수 변경 후에는 서버를 재시작해야 합니다.

```bash
sudo nano /etc/mutsa/mutsa.env
sudo systemctl restart mutsa.service
sudo systemctl status mutsa.service --no-pager
```

## 서버 재시작 후 캐시

운영 서버에서는 MySQL DB에 저장되므로 서버나 애플리케이션을 재시작해도 캐시가 유지됩니다.

저장되는 정보는 다음과 같습니다.

- 제품·브랜드 정규화 결과
- 전체 성분 JSON
- 사용 모델과 프롬프트 버전
- 입력·출력 토큰 수
- 만료 시각
- 제품 단서와 모델 버전으로 만든 캐시 키

단, 로컬 기본 프로필은 메모리 H2 DB를 사용하기 때문에 로컬 Spring 서버를 종료하면 캐시가 사라집니다.

그리고 현재 스위치는 정확히는 ‘제품·전성분 보강 캐시’만 제어합니다. 아래 두 캐시는 별도로 DB에 유지됩니다.

- 같은 회원·영상·피부 프로필의 완료된 전체 분석 결과
- Gemini 영상 단계 추출 결과

따라서 같은 조건으로 완전히 새로운 전체 파이프라인을 실행하려면 완료 분석 재사용 여부까지 별도로 고려해야 합니다. 제품·전성분 AI 단계에 실제로 진입한 뒤에는 `SHORTFORM_PRODUCT_CACHE_ENABLED=false`가 정상적으로 캐시 조회와 저장을 모두 우회합니다.