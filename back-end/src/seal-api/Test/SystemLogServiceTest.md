# SystemLogService Validation & Test Report

File service: `src/main/java/com/seal/hackathon/service/SystemLogService.java`

File test: `src/test/java/com/seal/hackathon/service/SystemLogServiceTest.java`

Files lien quan truc tiep:

- `src/main/java/com/seal/hackathon/controller/AdminController.java`
- `src/main/java/com/seal/hackathon/dto/response/SystemLogResponse.java`
- `src/main/java/com/seal/hackathon/entity/SystemLog.java`
- `src/main/java/com/seal/hackathon/repository/SystemLogRepository.java`
- `src/main/java/com/seal/hackathon/repository/UserRepository.java`
- `src/main/java/com/seal/hackathon/service/AdminService.java`

Quy uoc status:

- `Valid`: service xu ly thanh cong, khong nem exception.
- `Invalid`: service chan input khong hop le bang no-op theo thiet ke.

Tong so public function trong `SystemLogService`: **2**

Tong so JUnit executions trong `SystemLogServiceTest`: **18**

## Business Rules Implemented

- `record` no-op neu `actorUserId` null.
- `record` no-op neu actor khong ton tai.
- `action` null/empty/blank no-op.
- `action` duoc trim, whitespace group -> `_`, uppercase.
- `action` dai hon 50 ky tu no-op; dung 50 ky tu duoc chap nhan.
- `detail` null/blank -> `null`.
- `detail` duoc trim.
- `detail` dung 5000 ky tu duoc giu nguyen; > 5000 bi truncate ve 5000.
- `getAllLogs` map log id, actor id/name, action, detail, createdAt.
- SystemLog khong con IP field/column trong service/response.

## Implementation Summary

| Area | Coverage |
|---|---|
| Record valid | Normalize action/detail, null detail, blank detail, long detail, max action, whitespace action. |
| Record no-op | Actor id null, action null/blank/too long, actor missing. |
| Read logs | Single log mapping, empty list, multiple log mapping. |

## Public Function Coverage Summary

| Function | JUnit executions | Coverage chinh |
|---|---:|---|
| `record` | 15 | Valid normalize, detail boundaries, action boundaries, invalid/no-op branches |
| `getAllLogs` | 3 | Single, empty, multiple log response mapping |

## Detailed Test Cases

| # | Function | Test case | JUnit count | Status | Expected result |
|---:|---|---|---:|---|---|
| 1 | `record` | `record_shouldNormalizeActionAndDetail_whenInputIsValid` | 1 | Valid | `" grant role "` -> `GRANT_ROLE`, detail trim. |
| 2 | `record` | `record_shouldSaveWithNullDetail_whenDetailIsNull` | 1 | Valid | Null detail duoc save null. |
| 3 | `record` | `record_shouldStoreNullDetail_whenDetailIsBlank` | 1 | Valid | Blank detail -> null. |
| 4 | `record` | `record_shouldTrimLongDetailToSafeLength` | 1 | Valid | Detail 5001 -> 5000. |
| 5 | `record` | `record_shouldAcceptActionAtExactly50Characters` | 1 | Valid | Action 50 ky tu duoc save. |
| 6 | `record` | `record_shouldReplaceMultipleWhitespacesWithSingleUnderscore` | 1 | Valid | Multiple spaces -> single underscore. |
| 7 | `record` | `record_shouldSaveDetailAtExactly5000Characters` | 1 | Valid | Detail 5000 giu nguyen. |
| 8 | `record` | `record_shouldNoOp_whenActorIdIsNull` | 1 | Invalid | Khong lookup user, khong save. |
| 9 | `record` | `record_shouldNoOp_whenActionIsNullOrBlank` | 5 | Invalid | Null/empty/blank/tab/newline action no-op. |
| 10 | `record` | `record_shouldNoOp_whenActionExceeds50Characters` | 1 | Invalid | Action 51 ky tu no-op. |
| 11 | `record` | `record_shouldNoOp_whenActorDoesNotExist` | 1 | Invalid | Actor missing no-op. |
| 12 | `getAllLogs` | `getAllLogs_shouldMapActorAndLogFields` | 1 | Valid | Map day du fields. |
| 13 | `getAllLogs` | `getAllLogs_shouldReturnEmptyList_whenNoLogsExist` | 1 | Valid | Tra list rong. |
| 14 | `getAllLogs` | `getAllLogs_shouldMapMultipleLogs` | 1 | Valid | Map nhieu log dung thu tu repository tra ve. |

## Test Commands Run

Targeted suite:

```powershell
& 'C:\Users\DAO HOANG NHAT\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd' '-Dtest=SubmissionServiceTest,ScoringServiceTest,SystemLogServiceTest' test
```

Result for this file:

- Tests run: 18
- Failures: 0
- Errors: 0
- Skipped: 0

Full suite:

- Tests run: 285
- Failures: 0
- Errors: 0
- Skipped: 0

## Notes For Review

- `record` van co the bubble DB save exception; neu can "never blocks" that su thi can catch/log controlled exception hoac transaction rieng.
- `action` moi validate length/blank va normalize; chua co allow-list action taxonomy.
- `getAllLogs` chua paging/filter.
