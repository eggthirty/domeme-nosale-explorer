# 고객관리 (통화기록 연동 CRM)

안드로이드 폰의 통화기록을 읽어 고객별로 자동 연결해 주는 개인용 고객관리 앱입니다.

- 고객 등록/편집 (이름, 번호, 회사, 상태, 태그, 메모)
- 통화기록을 번호로 자동 매칭 → 고객별 통화 횟수 · 총 통화시간 · 마지막 연락일
- 고객별 활동 타임라인 (통화 + 메모)
- 최근통화 탭에서 미등록 번호를 바로 고객으로 등록
- 30일 이상 연락 안 한 고객 필터
- 백업 내보내기/가져오기 (다운로드 폴더 JSON)

데이터는 폰 안에만 저장되며 외부로 전송되지 않습니다.

## 설치
1. 저장소의 Releases → `crm-latest` 에서 `CallCRM.apk` 를 폰으로 다운로드
2. "출처를 알 수 없는 앱 설치" 허용 후 설치
3. 앱 실행 → 통화기록 권한 허용

## 구조
- `app/src/main/assets/index.html` — 화면 전체 (HTML/JS)
- `app/src/main/java/com/sjob/crm/MainActivity.java` — 통화기록 읽기, 파일 저장, 전화 연결
- 빌드는 GitHub Actions (`.github/workflows/crm-android.yml`) 가 수행
- 브라우저 미리보기: `index.html?demo`
