# BE
# 🚀 Convention

우리 팀의 원활한 협업과 일관성 있는 코드 관리를 위한 규칙입니다. 모든 팀원은 개발 시작 전에 반드시 숙지해 주세요.

---

## 🌿 Git 브랜치 전략

우리 프로젝트는 **Feature-Driven Workflow**를 따릅니다. **`main` 브랜치로의 직접적인 Push는 절대 금지**합니다.

### 📌 브랜치 명명 규칙
`종류/기능명` 형태로 생성하며, 단어 구문은 하이픈(`-`)을 사용합니다.

| 종류         | 설명                       | 예시                                        |
|:-----------|:-------------------------|:------------------------------------------|
| `feature`  | 새로운 기능 구현                | `feature/post-crud`, `feature/comment`    |
| `fix`      | 버그 및 에러 수정               | `fix/db-connection`, `fix/error-response` |
| `docs`     | 문서 수정 (README, API 명세 등) | `docs/readme-update`                      |
| `refactor` | 코드 리팩토링                  | `refactor/dto-separation`                 |
| `chore`    | 기타                       | `chore/unuse-file-delete`                 |

> ⚠️ **주의:** 브랜치를 생성하기 전에 항상 `main` 브랜치에서 `git pull`을 진행하여 최신 상태를 유지하세요.

---

## 💬 커밋 메시지 컨벤션 (Commit Convention)

커밋 메시지는 다른 팀원이 변경 사항을 쉽게 알아볼 수 있도록 아래 규칙을 준수합니다.

### 📌 메시지 구조
```text
태그: 변경 내용 요약 (한글로 간결하게)
```

---