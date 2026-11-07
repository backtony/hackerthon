---
name: review
description: 변경사항을 리뷰하고 후보 이슈를 독립 검증해 심각도별로 정리한다. 사용자가 /review 또는 리뷰해줘, 코드 리뷰, 변경사항 검토, 이 브랜치 봐줘, 이 diff 리뷰 등을 요청할 때 사용한다.
---

# Code Review

기본은 리뷰어 1명과 후보 이슈가 있을 때 검증자 1명으로 검토한다. 보안·데이터 무결성·마이그레이션·동시성 등 영향이 큰 변경이나 사용자가 반복 리뷰를 요청한 경우에만 2회 사이클을 실행한다. 검증을 통과한 이슈만 심각도별 Markdown으로 출력한다.

## 핵심 원칙

- Reviewer와 Validator를 호출할 때는 반드시 서로 다른 새 subagent로 실행한다.
- Reviewer의 원본 결과와 Validator의 중간 결과는 사용자에게 노출하지 않는다.
- 현재 코드, 데이터, 도메인에서 실제 영향을 확인할 수 있는 문제만 보고한다.
- 스타일 취향, 가설적 미래 위험, 테스트 누락은 보고하지 않는다.
- 테스트 파일은 모든 사이클에서 리뷰 대상에서 제외한다.
- `file`과 `line`은 변경 후 파일의 diff 좌표를 사용한다.
- 리뷰 대상이 모호하면 기본 브랜치나 범위를 추측하지 말고 사용자에게 한 번만 확인한다.

## 입력 범위

사용자 요청을 다음 순서로 해석한다.

- 인자가 없으면 현재 작업 트리의 staged, unstaged, untracked 변경사항을 대상으로 한다.
- 브랜치가 지정되면 기본 브랜치와 해당 브랜치의 비교 범위를 사용한다. 기본 브랜치를 판단할 수 없으면 확인한다.
- 커밋 범위(`abc123..def456`)가 지정되면 해당 범위를 사용한다.
- 파일 또는 디렉터리 경로가 지정되면 해당 경로로 diff를 제한한다.
- PR URL 또는 번호가 지정되면 `gh`가 설치되고 인증된 경우에만 `gh pr view`와 `gh pr diff`를 사용한다.

테스트 파일은 다음 패턴에 해당하면 리뷰 대상에서 제외한다.

- `*Test.java`
- `src/test/`
- `__tests__/`
- `*.spec.ts`
- `*_test.go`
- `test_*.py`

## Step 1: 변경 컨텍스트 수집

변경 범위를 한 번만 수집하고 이후 모든 사이클에서 재사용한다. 인자가 없으면 `HEAD`와 작업 트리를 비교하고 새 파일 목록을 수집한다.

```sh
git diff HEAD
git diff --stat HEAD
git ls-files --others --exclude-standard
```

새 파일은 테스트 파일 패턴을 제외한 뒤 각각 `git diff --no-index -- /dev/null <path>`로 변경 구간을 만들어 `DIFF`와 `FILES`에 포함한다. 차이가 있을 때의 종료 코드 1은 정상이다. 작업 트리 리뷰의 제목은 사용자 요청이 없으면 `Uncommitted changes`로 둔다.

커밋 범위나 브랜치가 지정되면 해당 범위로 수집한다.

```sh
git diff <range>
git diff --stat <range>
git log --oneline <range>
```

PR인 경우 로컬 diff 명령 대신 다음 명령으로 PR의 메타데이터와 diff를 수집한다. `gh`를 사용할 수 없으면 상태를 보고하고 멈춘다.

```sh
gh pr view <PR> --json title,body,baseRefName,headRefName
gh pr diff <PR>
```

다음 컨텍스트를 유지한다.

- `CHANGE_META`: 제목, 본문, 커밋 로그, base/head ref
- `DIFF`: 전체 변경 diff
- `FILES`: 변경 파일 목록과 통계

git 명령 실패, diff가 비어 있음, diff가 컨텍스트에 들어오지 않을 정도로 큰 경우에는 추측하거나 축약된 상태로 진행하지 않는다. 상태를 사용자에게 보고하고, 큰 diff는 파일 단위 분할 리뷰 또는 범위 축소를 제안한 뒤 멈춘다.

## Step 2: 리뷰-검증 사이클

기본은 아래 관점을 한 번에 검토한다. 영향이 큰 변경이나 명시적 반복 리뷰 요청에는 아래 관점을 나눠 2회 실행한다.

각 사이클은 다음 순서로 진행한다.

1. 새 Reviewer subagent를 호출한다.
2. 후보 이슈가 있으면 각 이슈의 관련 diff hunk와 주변 코드를 모아 새 Validator subagent에 전달한다. 후보가 없으면 검증자를 호출하지 않는다.
3. 검증자를 호출했다면 `verdict == "PASS"`인 이슈만 `accumulated_issues`에 추가한다.

사이클별 강조 관점은 다음과 같다.

| 사이클 | 강조 관점 |
| --- | --- |
| 1 | 요구사항·설계 일관성, 아키텍처, 기능 동작, 가독성, 추상화 적절성, 회귀 위험, 호환성 |
| 2 | 동시성·스레드 안전성, 성능, 보안, 엣지 케이스, null/예외 처리, 운영 안정성, 로깅/관측, 데이터 마이그레이션 |

기본 1회에서는 두 관점을 모두 Reviewer에게 전달한다.

### 2-A. Reviewer subagent

새로운 일반 목적 subagent에 다음 정보를 제공한다.

```text
당신은 시니어 코드 리뷰어입니다. 아래 변경사항을 리뷰하세요.

## 변경 정보
- 제목/커밋 메시지: {CHANGE_META.title 또는 커밋 로그}
- 본문: {CHANGE_META.body — 있을 때만}
- Base: {base_ref}
- Head: {head_ref}

## 변경 diff
{DIFF}

## 이번 사이클 강조 시야 (Cycle {N})
{사이클별 강조 관점}

## 이미 검증된 이슈
{accumulated_issues 요약. 첫 사이클이면 "없음"}

## 출력 형식
JSON만 반환하세요. 다른 텍스트는 반환하지 마세요.
{
  "issues": [
    {
      "file": "경로/Foo.java",
      "line": 42,
      "severity": "Critical|Major|Minor",
      "category": "bug|security|concurrency|performance|architecture|style|other",
      "title": "한 줄 요약",
      "description": "왜 문제인지와 어떤 상황에서 영향이 있는지",
      "suggestion": "권장 수정 방향"
    }
  ]
}
```

Reviewer에게 다음 규칙을 적용한다.

- 제목, 본문, 커밋 의도와 코드가 불일치하면 지적한다.
- diff에 명시된 사실과 저장소의 주변 코드로 확인 가능한 내용만 보고한다.
- 현재 코드, 데이터, 도메인에서 실제로 잘못된 결과가 발생하는 문제만 보고한다.
- "신규 타입이 들어오면" 또는 "외부 호출자가 잘못 사용하면"처럼 현재 상태에 근거하지 않은 위험은 보고하지 않는다.
- 스타일 취향이나 주석 추가 같은 제안은 보고하지 않는다.
- 테스트 파일, 테스트 누락, 테스트 보강은 보고하지 않는다.
- 강조 관점과 무관해도 명백한 버그는 보고한다.
- `description`과 `suggestion`은 명령형을 사용하지 않는다. 권유 또는 관찰 어조로 작성한다.

Reviewer가 유효한 JSON을 반환하지 않으면 같은 subagent에 한 번만 재시도한다. 그래도 실패하면 리뷰가 미완료임을 보고하고 멈춘다.

### 2-B. Validator subagent

Reviewer와 다른 새 일반 목적 subagent를 호출한다. 후보 이슈마다 해당 변경 hunk, 판단에 필요한 주변 코드, Reviewer의 JSON, 이전 사이클의 `accumulated_issues` 요약만 제공한다. 전체 `DIFF`를 다시 전달하지 않는다. 연결된 다른 파일이 필요하면 해당 구간만 추가한다.

```text
당신은 코드 리뷰 검증자입니다. 아래 리뷰 코멘트가 실제로 유효한지 제공된 변경 구간 및 저장소 코드와 대조하세요. 문맥이 부족하면 필요한 파일과 diff 구간을 직접 확인하세요.

## 후보와 관련된 변경 구간 및 주변 코드
{candidate_hunks_and_context}

## 검증 대상 리뷰 코멘트
{cycle_N_raw_issues}

## 누적 검증 통과 이슈
{accumulated_issues. 첫 사이클이면 "없음"}

## 검증 기준
모든 기준을 만족하는 경우에만 PASS입니다.

1. `file`과 `line`이 diff의 실제 변경 라인을 가리키는가?
2. 지적한 문제가 실제로 존재하며 이미 처리된 케이스를 누락으로 주장하지 않는가?
3. 현재 코드, 데이터, 도메인에서 실제 영향이 있는가? 현재 상태에서 어떤 잘못된 결과가 나오는지 한 줄로 설명할 수 있어야 한다.
4. 스타일 취향이 아니라 실제 영향이 있는가?
5. `severity`가 적절한가? 과대 또는 과소 평가라면 조정한다.
6. 누적 이슈에 동일하거나 유사한 항목이 없는가?
7. 테스트 파일을 가리키는 이슈가 아닌가?

## 출력 형식
JSON만 반환하세요. 다른 텍스트는 반환하지 마세요.
{
  "validated": [
    {
      "file": "...",
      "line": 42,
      "severity": "Critical|Major|Minor",
      "category": "bug|security|concurrency|performance|architecture|style|other",
      "title": "...",
      "description": "...",
      "suggestion": "...",
      "verdict": "PASS",
      "verdict_reason": "유효한 이슈인 이유 한 줄"
    }
  ]
}
```

Validator에게 다음 거절 사유를 사용하게 한다.

- `REJECT-FALSE-POSITIVE`
- `REJECT-DUPLICATE`
- `REJECT-STYLE`
- `REJECT-WRONG-LINE`
- `REJECT-HYPOTHETICAL`
- `REJECT-OUT-OF-SCOPE`

Validator는 Reviewer의 판단을 권위로 취급하지 않는다. 의심스러운 항목은 `PASS`보다 거절을 우선한다. severity를 조정했다면 `verdict_reason`에 이유를 포함한다.

Validator 결과가 유효한 JSON이 아니면 한 번만 재시도한다. 그래도 실패하면 리뷰가 미완료임을 보고하고 멈춘다.

### 2-C. 누적 갱신

Validator 결과에서 `verdict == "PASS"`인 항목만 누적한다. 거절된 항목은 다음 Reviewer에게 전달하지 않는다. 다음 Reviewer에는 `accumulated_issues` 요약만 중복 회피 목적으로 제공한다.

사용자에게는 실제 실행하는 사이클의 리뷰·검증 진행만 한 줄씩 표시한다.

## Step 3: 최종 출력

`accumulated_issues`를 `Critical`, `Major`, `Minor` 순서로 그룹화한다. 같은 그룹 안에서는 파일 경로와 라인 번호 오름차순으로 정렬한다.

중간 결과, Reviewer 원본, Validator 원본, 누적 과정은 출력하지 않는다. 최종 결과는 다음 형식을 사용한다.

```markdown
## 🔴 Critical (N)

### 1. {title}
- 위치: `{file}:{line}`
- 분류: {category}
- 문제: {description}
- 권장: {suggestion}

---

## 🟠 Major (N)
...

---

## 🟡 Minor (N)
...
```

이슈가 없는 심각도 그룹은 다음 한 줄로 표시한다.

```markdown
## 🟢 {심각도} (해당 없음)
```

최종 `description`과 `suggestion`에도 명령형을 사용하지 않는다. `~해보시면 좋을 것 같습니다`, `~를 고려해볼 수 있을 것 같습니다`, `~가 필요해 보입니다`와 같은 권유 또는 관찰 어조를 사용한다.

## 한계 처리

- 수천 라인 이상의 diff는 컨텍스트 한계를 확인한다. 전체 변경을 검토할 수 없으면 파일 단위로 분할하거나 사용자에게 범위 축소를 권한다.
- subagent도 완벽하지 않으므로 최종 이슈는 사용자가 직접 확인할 수 있는 형태로 위치와 근거를 포함한다.
