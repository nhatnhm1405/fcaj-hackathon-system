# Hackathon Event Validation Review

## Pham vi da sua

Da bo sung validation cho `HackathonEventService` va cac file lien quan de dam bao khi tao/cap nhat event thi `season`, `year`, 4 moc ngay va duplicate/overlap duoc kiem tra dong bo.

## Quy uoc mua dang ap dung

Quy uoc nay duoc suy ra tu `seal_seed.sql`:

- `SPRING`: tu `01-01` den `04-30` cua `year`.
- `SUMMER`: tu `05-01` den `08-31` cua `year`.
- `FALL`: tu `09-01` den `12-31` cua `year`.

Neu nghiep vu chinh thuc quy dinh moc mua khac, chi can doi helper `seasonWindow(...)` trong `HackathonEventService`.

## Rule khi create event

- `name` bat buoc, trim, khong vuot qua 255 ky tu.
- `season` bat buoc va chi chap nhan `SPRING`, `SUMMER`, `FALL`.
- `year` bat buoc, tu `2026` den `3000`.
- Bat buoc co du 4 ngay: `registrationStart`, `registrationEnd`, `startDate`, `endDate`.
- Thu tu ngay: `registrationStart <= registrationEnd <= startDate <= endDate`.
- Ca 4 ngay phai nam trong dung `season` va dung `year`.
- Ca 4 ngay khong duoc nam truoc ngay hien tai.
- Chi cho phep 1 event active trong moi cap `(season, year)`.
- Khong cho tao event active trung cap `(season, year)` voi event active khac.
- Khong cho tao event active bi overlap khoang thi dau `startDate/endDate` voi event active khac.
- Event `CANCELLED` khong chiem slot `(season, year)` va duoc bo qua trong duplicate/overlap active.

## Rule khi update event

- Controller update da dung `@Valid` cho `UpdateEventRequest`.
- Service tao mot "effective event" tu entity hien tai + field trong request, roi validate tren bo du lieu sau khi patch.
- Neu request gui `season`, `year`, hoac bat ky field ngay nao, service se validate lai toan bo lich effective:
  - Du 4 ngay.
  - Dung thu tu ngay.
  - Ca 4 ngay nam trong dung `season/year`.
- Chi cac field ngay duoc gui trong request moi bi check "khong duoc o qua khu".
- Neu request gui `registrationStart` thi service se lay `registrationStart` tu request, 3 ngay con lai tu event hien tai, kem `season/year` effective de validate.
- Neu update `season` hoac `year`, cac ngay hien co cung phai hop le voi `season/year` moi, tru khi request dong thoi gui ngay moi hop le.
- Khi doi `CANCELLED -> DRAFT` hoac bat ky trang thai non-`CANCELLED` nao duoc transition cho phep, service validate lich day du truoc khi event active lai.
- Khi reactivate tu `CANCELLED`, service kiem tra cap `(season, year)` da co event active nao chua; neu co thi khong cho update, neu khong co thi update binh thuong.
- Neu update `season` hoac `year`, service se kiem tra cap `(season, year)` moi da co event active nao chua.
- Neu cap `(season, year)` moi chi co event `CANCELLED`, update van duoc phep.
- Duplicate `(season, year)` va overlap active duoc check khi `season/year/startDate/endDate` thay doi hoac khi reactivate tu `CANCELLED`.

## File da thay doi

- `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/HackathonEventService.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/HackathonEventRepository.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/request/CreateEventRequest.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/request/UpdateEventRequest.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/HackathonEventController.java`
- `back-end/src/seal-api/src/test/java/com/seal/hackathon/service/HackathonEventServiceTest.java`

## Test da bo sung

- Create thanh cong khi ngay nam trong mua, khong trung `(season, year)`, khong overlap.
- Create fail khi co ngay trong qua khu.
- Create fail khi ngay khong thuoc `season/year`.
- Create fail khi trung active `(season, year)`.
- Create fail khi overlap active event khac.
- Update fail khi season khong hop le.
- Update fail khi ngay request bi lech thu tu voi ngay hien co cua event.
- Update fail khi ngay request nam trong qua khu.
- Update fail khi doi season lam ngay hien co khong con thuoc mua moi.
- Update fail khi doi `year` vao cap `(season, year)` da co event active.
- Update thanh cong khi doi `season` vao cap `(season, year)` chi co event `CANCELLED`.
- Update fail khi doi status `CANCELLED -> DRAFT` nhung cap `(season, year)` da co event active.
- Update thanh cong khi doi status `CANCELLED -> DRAFT` va cap `(season, year)` chua co event active.
- Update fail khi doi ngay thi dau gay overlap active event khac.
- Update thanh cong khi chi doi 1 ngay nhung lich effective van hop le.
