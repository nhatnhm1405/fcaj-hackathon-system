# Event Validation Review

Ngay review: 2026-06-13

Pham vi kiem tra:
- `src/main/java/com/seal/hackathon/controller/HackathonEventController.java`
- `src/main/java/com/seal/hackathon/service/HackathonEventService.java`
- `src/main/java/com/seal/hackathon/dto/request/CreateEventRequest.java`
- `src/main/java/com/seal/hackathon/dto/request/UpdateEventRequest.java`
- `src/main/java/com/seal/hackathon/entity/HackathonEvent.java`
- `src/main/java/com/seal/hackathon/repository/HackathonEventRepository.java`
- `src/main/java/com/seal/hackathon/exception/GlobalExceptionHandler.java`

Khong sua source code trong qua trinh review. Chi tao file report nay.

## Ket luan ngan

Validation ngay/season trong service hien tai da kha dung voi rule moi:
- 4 moc ngay deu bat buoc co.
- Thu tu ngay duoc check chat: `registrationStart < registrationEnd < startDate < endDate`.
- 4 moc ngay deu phai thuoc dung `event.year`.
- Season chi check `registrationStart` va `endDate`; voi dieu kien thu tu ngay o tren, 2 ngay o giua se tu dong nam trong cung khoang.
- Create da check trung event active trong cung `year + season`, bo qua cac event `CANCELLED`.

Nhung chua the coi la on hoan toan. Co 5 diem can chu y:
1. Update khong check trung active event theo `year + season`, trong khi request hien van cho update `year`, `season`, `status`.
2. Update request rong hoac field blank dang bi bo qua va van tra success.
3. DTO va entity khong dong bo voi service: `registrationStart` va `registrationEnd` khong `@NotNull`/nullable, nhung service lai bat buoc co.
4. Invalid JSON/date format co kha nang bi tra 500 do chua handle `HttpMessageNotReadableException`.
5. Unique check chi nam o application layer, nen van co race condition neu 2 request create cung luc.

## Controller CRUD

### GET `/api/events`

File: `HackathonEventController.java`

Flow:
- Goi `hackathonEventService.getAllHackathonEvents()`.
- Service lay tat ca event sort theo `createdAt DESC`.
- Khong co validation request vi endpoint khong nhan input.

Nhan xet:
- On cho read-only endpoint.
- Neu DB co record loi/null bat thuong thi loi se phat sinh o layer mapping/service, khong phai controller.

### GET `/api/events/{eventId}`

Flow:
- Goi `hackathonEventService.getEventById(eventId)`.
- Service dung `findById`.
- Neu khong thay thi throw `ResourceNotFoundException`.

Nhan xet:
- On o muc co ban.
- Chua co validation rieng cho `eventId <= 0`. Neu truyen id am/0 thi van query DB roi bao not found. Dieu nay chap nhan duoc, nhung neu muon strict thi can validate.

### POST `/api/events`

Flow:
- Controller co `@Valid @RequestBody CreateEventRequest`.
- DTO check `name`, `season`, `year`, `startDate`, `endDate`.
- Service build entity.
- Service normalize status, validate date/season, check unique active event, roi save.

Nhan xet:
- Day la endpoint validation tot nhat hien tai.
- Da khong con truyen user id vao create, phu hop voi viec entity event da bo `created_by`.
- Van con import `User` va inject `UserRepository` trong service nhung khong dung nua. Day khong phai bug validation, nhung la no code/dependency thua.

Rui ro:
- `registrationStart` va `registrationEnd` khong co `@NotNull` trong DTO, nhung service bat buoc co. Ket qua la missing registration date se qua duoc DTO validation va bi bat sau do trong service. Khong sai ve behavior, nhung khong nhat quan.
- Neu request body la JSON `null`, service co the gap `NullPointerException` khi goi `request.getName()`. Truong hop nay tuy hiem, nhung validation boundary chua chat.

### PUT `/api/events/{eventId}`

Flow:
- Controller nhan `UpdateEventRequest`, khong co `@Valid`.
- Service tim event cu bang `eventId`.
- Field nao request gui va hop dieu kien thi set vao entity.
- Sau khi merge, service validate lai date/season.
- Save event.

Nhan xet:
- Cach validate sau merge la dung. Neu FE chi update 1 ngay, service van check lai tong the 4 ngay.
- Neu FE doi `season` hoac `year`, validation date/season se bat loi neu ngay khong khop.

Rui ro lon:
- Update khong goi `validateUniqueActiveSeasonEvent`. Trong khi `UpdateEventRequest` cho sua `year`, `season`, `status`.
- Vi vay co the tao duplicate active event bang update, du create da chan.

Vi du:
- DB co event A: `2026 SPRING DRAFT`.
- DB co event B: `2026 SUMMER DRAFT`.
- Update B thanh `season = SPRING`.
- Code hien tai chi check ngay co nam trong SPRING khong, khong check da co A cung `2026 SPRING`.
- Neu ngay cua B hop le voi SPRING thi save duoc, lam hong rule "mot mua trong mot nam chi co mot event active".

Rui ro khac:
- Request rong `{}` van pass: service khong check "at least one field must be provided".
- `name = ""`, `season = ""`, `status = ""` bi ignore, khong bao loi. FE co the tuong update thanh cong nhung du lieu khong doi.
- Neu request la JSON `null`, service co the gap `NullPointerException`.

### DELETE

Khong thay `@DeleteMapping` trong `HackathonEventController.java` va khong thay delete method trong service.

Nhan xet:
- Neu CRUD theo nghia day du thi delete chua duoc implement.
- Neu business khong cho hard delete event thi nen ghi ro la khong co delete, dung status `CANCELLED` thay the.

## DTO Validation

### CreateEventRequest

Dang co:
- `name`: `@NotBlank`
- `season`: `@NotBlank`
- `year`: `@NotNull`
- `startDate`: `@NotNull`
- `endDate`: `@NotNull`

Chua co:
- `registrationStart`: khong `@NotNull`
- `registrationEnd`: khong `@NotNull`
- `status`: khong validate enum o DTO
- `year`: khong validate min/max

Nhan xet:
- Service da validate bu lai `registrationStart`, `registrationEnd`, `status`.
- Tuy nhien, DTO va service nen dong bo de loi validation nhat quan hon.
- `year` hien co the la nam qua xa, nam am, hoac nam 0 neu client gui du lieu bat thuong va parser chap nhan. Service chi check date.getYear() == year, khong check business range.

### UpdateEventRequest

Dang khong co annotation validation nao.

Nhan xet:
- Chap nhan duoc neu update la partial update.
- Nhung service nen phan biet "field khong gui" va "field gui sai". Hien tai blank string bi ignore, de gay hieu nham.
- Nen co rule ro: blank la invalid hay la ignore. Hien code dang chon ignore.

## Service Validation Detail

### `createEvent`

Dang lam dung:
- Trim name.
- Normalize status, default `DRAFT`.
- Goi `validateEventDateAndSeason`.
- Goi `validateUniqueActiveSeasonEvent`.
- Save sau khi validation pass.

Diem can xem lai:
- Service dang gia dinh `request` khong null.
- Service con dependency thua `UserRepository`.
- Neu name null ma service duoc goi truc tiep khong qua controller `@Valid`, se `NullPointerException` truoc khi nem `BadRequestException`.

### `updateEvent`

Dang lam dung:
- Tim event truoc, neu khong co thi 404.
- Merge partial fields.
- Validate tong the sau merge.

Diem can xem lai:
- Khong validate unique active event sau update.
- Khong check request rong.
- Blank `name`, `season`, `status` bi ignore thay vi bao loi.
- Service gia dinh `request` khong null.

### `validateEventDateAndSeason`

Flow hien tai:
1. Normalize season bang `normalizeSeason`.
2. Check `year != null`.
3. Bat buoc 4 ngay khong null:
   - `registrationStart`
   - `registrationEnd`
   - `startDate`
   - `endDate`
4. Check thu tu:
   - `registrationStart < registrationEnd`
   - `registrationEnd < startDate`
   - `startDate < endDate`
5. Check ca 4 ngay deu thuoc `year`.
6. Check season range bang:
   - `registrationStart`
   - `endDate`

Nhan xet:
- Logic check season bang 2 bien ngoai la dung voi dieu kien da co thu tu ngay.
- Vi dung `isBefore`, neu 2 moc ngay bang nhau thi invalid. Day la strict validation, hop ly neu business yeu cau cac phase tach biet.
- `validateDateInSeason` doi `LocalDateTime` sang `LocalDate`, nen gio/phut/giay khong anh huong. Vi du `2026-08-31T23:59:59` van nam trong SUMMER.

Diem can chu y:
- Neu business cho phep registration mo truoc mua, rule hien tai se reject. Vi rule hien tai bat `registrationStart` nam trong season.
- Neu DB co record cu voi registration date null, update bat ky field nao cung co the fail vi service validate lai 4 ngay.

### `validateUniqueActiveSeasonEvent`

Flow hien tai:
1. Goi repository:
   `existsByYearAndSeasonIgnoreCaseAndStatusNotIgnoreCase(year, season, "CANCELLED")`
2. Neu ton tai event cung `year + season` va status khac `CANCELLED`, throw:
   `Already has event in {season} of {year}`

Nhan xet:
- Dung voi rule create: neu da co event active/non-cancelled trong cung nam + mua thi khong cho tao them.
- Cho phep tao event moi neu cac event cu cung nam + mua deu la `CANCELLED`.
- Cho phep ton tai nhieu event `CANCELLED` cung nam + mua.

Diem can chu y:
- Chi create moi goi function nay. Update khong goi.
- Khong co DB constraint nen 2 request create dong thoi co the cung pass check va cung save.

### `normalizeSeason`

Dang lam dung:
- Null/blank bi reject.
- Trim va uppercase.
- Chi cho `SPRING`, `SUMMER`, `FALL`.

Nhan xet:
- Tot.

### `normalizeStatus`

Dang lam dung:
- Null/blank thi tra default.
- Trim va uppercase.
- Chi cho `DRAFT`, `OPEN`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`.

Nhan xet:
- Tot ve mat validation.
- Can dam bao list status nay khop voi flow toan he thong. Code khac dang dung `OPEN`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `DRAFT`, nen hien tai co ve khop.

### `requiredDate`

Dang lam dung:
- Null date -> `BadRequestException`.

Nhan xet:
- Tot.
- Nhung DTO/entity nen dong bo neu business yeu cau registration dates bat buoc.

### `validateDateInEventYear`

Dang lam dung:
- Check tung date co cung nam voi `event.year`.

Nhan xet:
- Tot.
- Chua check year co nam trong khoang hop ly. Day la business validation con thieu neu he thong khong cho nam qua khu/nam am.

### `validateDateInSeason`

Dang lam dung:
- Lay season start/end theo `year`.
- Check inclusive range:
  - `value >= seasonStart`
  - `value <= seasonEnd`

Nhan xet:
- Tot.

## Entity va DB Constraint

### HackathonEvent entity

Dang co:
- `name`, `season`, `year`, `startDate`, `endDate`, `status`, `createdAt` la nullable false o entity.
- `registrationStart`, `registrationEnd` khong nullable false.

Nhan xet:
- Service yeu cau registration dates bat buoc, nhung entity/DB mapping lai cho null.
- Vi `spring.jpa.hibernate.ddl-auto=none`, constraint that su phu thuoc vao schema DB hien tai. Annotation entity khong tu cap nhat DB.

Rui ro:
- Du service chan request API, user/process khac ghi truc tiep vao DB van co the tao data khong hop le neu DB khong co constraint.
- Khong co unique constraint cho active event theo `year + season`.

### HackathonEventRepository

Dang co:
- `findAllByStatus`
- `existsByYearAndSeasonIgnoreCaseAndStatusNotIgnoreCase`

Nhan xet:
- Method unique create dung y do.
- Import `Repository` dang thua.

## Exception Handling

Dang co handler:
- `BadRequestException` -> 400
- `ResourceNotFoundException` -> 404
- `MethodArgumentNotValidException` -> 400
- Generic `Exception` -> 500

Rui ro:
- Sai format date/time trong JSON thuong nem `HttpMessageNotReadableException`, hien chua co handler rieng. Do do co the bi generic handler tra 500 thay vi 400.
- JSON malformed cung co the roi vao generic 500.

Day la validation boundary quan trong vi FE se gui date string.

## Build/Verification

Da thu compile:
- `.\mvnw.cmd -q -DskipTests compile`
- `cmd /c mvnw.cmd -q -DskipTests compile`
- `mvn -q -DskipTests compile`

Ket qua:
- Maven wrapper loi truoc khi compile Java: `Cannot start maven from wrapper`.
- Global `mvn` khong co trong PATH.

Vi vay report nay la static code review, chua xac minh compile/runtime bang Maven.

## Muc do uu tien can xem lai

### Cao

1. Update co the pha rule unique active event theo `year + season`.
   - Vi request update cho sua `year`, `season`, `status`.
   - Create co check unique nhung update khong check.

2. Invalid JSON/date format co the tra 500.
   - Nen coi day la validation error 400.

### Trung binh

3. Request update rong van success.
   - De FE/user tuong update thanh cong trong khi khong doi gi.

4. Blank field o update bi ignore.
   - `name = ""`, `season = ""`, `status = ""` nen co rule ro la reject hay ignore.

5. DTO/entity khong dong bo voi service cho registration dates.
   - Service bat buoc, DTO/entity lai khong the hien bat buoc.

6. Unique check khong an toan voi concurrent create.
   - Can DB-level protection neu business rule nay rat quan trong.

### Thap

7. Service con import/inject `UserRepository` khong dung.
   - Khong lam sai validation, nhung nen don de tranh nham rang event van lien quan `created_by`.

8. Chua validate business range cua `year`.
   - Tuy khong bat buoc neu cho tao event lich su, nhung nen co rule ro.

## Goi y cau hoi can confirm truoc khi sua

1. Update co duoc phep doi `year`, `season`, `status` khong?
   - Neu co, update bat buoc can unique check giong create, nhung exclude chinh event hien tai.
   - Neu khong, service nen chan update cac field nay.

2. Registration co bat buoc phai nam trong season khong?
   - Hien code bat `registrationStart` nam trong season.
   - Neu business cho mo dang ky truoc mua, rule nay can doi.

3. Registration dates co bat buoc khong?
   - Hien service bat buoc.
   - DTO/entity chua the hien dieu do.

4. Event co duoc tao san voi status `CANCELLED` khong?
   - Hien code cho phep neu khong co active event cung nam/mua.

5. Delete event co nam trong CRUD that su khong?
   - Hien khong co delete endpoint.
   - Co the business dang dung `CANCELLED` thay cho delete.
