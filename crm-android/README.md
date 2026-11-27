# 고객관리 (통화기록 연동 CRM)

안드로이드 폰의 통화기록을 읽어 고객별로 자동 연결해 주는 개인용 고객관리 앱입니다.

- 고객 등록/편집 (이름, 번호, 회사, 상태, 태그, 메모)
- 통화기록을 번호로 자동 매칭 → 고객별 통화 횟수 · 총 통화시간 · 마지막 연락일
- 고객별 활동 타임라인 (통화 + 메모)
- 최근통화 탭에서 미등록 번호를 바로 고객으로 등록
- 30일 이상 연락 안 한 고객 필터
- 백업 내보내기/가져오기 (다운로드 폴더 JSON)
- **구글 드라이브 동기화**: 폰과 PC에서 같은 데이터 보기/수정 (`고객관리_데이터.json`)

데이터는 기기와 본인 구글 드라이브에만 저장되며, 그 밖의 서버로는 전송되지 않습니다.

## PC 주소
GitHub Pages: `https://eggthirty.github.io/domeme-nosale-explorer/crm/` (main 브랜치 기준)

## 구글 드라이브 연동 설정 (최초 1회, Google Cloud Console)
1. 새 프로젝트 생성 → **Google Drive API** 사용 설정
2. **OAuth 동의 화면**(Google 인증 플랫폼): 외부(External), 앱 이름/이메일 입력, 범위 `.../auth/drive.file`
   - 테스트 상태면 7일마다 재로그인이 필요하므로 **앱 게시(프로덕션)** 권장 (drive.file 은 심사 불필요)
3. **OAuth 클라이언트 ID – Android**: 패키지 `com.sjob.crm`, SHA-1 `07:CF:BE:D8:FA:FE:61:18:02:9D:60:E1:67:1D:92:AC:3A:99:FF:C1`
4. **OAuth 클라이언트 ID – 웹 애플리케이션**: 승인된 자바스크립트 원본 `https://eggthirty.github.io`
5. 웹 클라이언트 ID를 `app/src/main/assets/index.html` 의 `DRIVE.webClientId` 에 입력

폰 앱과 웹 클라이언트는 반드시 **같은 프로젝트**에 만들어야 같은 파일을 함께 볼 수 있습니다.

## 설치
1. 저장소의 Releases → `crm-latest` 에서 `CallCRM.apk` 를 폰으로 다운로드
2. "출처를 알 수 없는 앱 설치" 허용 후 설치
3. 앱 실행 → 통화기록 권한 허용

## 구조
- `app/src/main/assets/index.html` — 화면 전체 (HTML/JS)
- `app/src/main/java/com/sjob/crm/MainActivity.java` — 통화기록 읽기, 파일 저장, 전화 연결
- 빌드는 GitHub Actions (`.github/workflows/crm-android.yml`) 가 수행
- 브라우저 미리보기: `index.html?demo`
