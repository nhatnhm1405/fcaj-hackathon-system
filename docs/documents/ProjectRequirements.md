ĐỀ THI DỰ ÁN SWP391 - TỔNG HỢP HOÀN CHỈNH
Mã đề tài: SU26SWP04
Tên tiếng Việt: Hệ thống quản lý cuộc thi SEAL Hackathon ngành Kỹ thuật Phần mềm
Tên tiếng Anh: SEAL – Software Engineering Hackathon Management System
Giảng viên/Người phụ trách: ChiLTQ6

============================================================
1. TỔNG QUAN ĐỀ TÀI
============================================================

Software Engineering Agile League (SEAL) là cuộc thi hackathon học thuật thường niên do Khoa Kỹ thuật Phần mềm phối hợp với PDP tổ chức tại Trường Đại học FPT TP.HCM. Mỗi năm, SEAL tổ chức ba mùa hackathon gồm Spring, Summer và Fall.

Mỗi sự kiện hackathon có thể bao gồm nhiều vòng thi, ví dụ: vòng sơ khảo, vòng loại và vòng chung kết. Các sự kiện SEAL mở cửa cho nhiều trường đại học cùng tham gia. Đội thi có thể gồm toàn sinh viên FPT, sinh viên FPT kết hợp với sinh viên ngoài trường, hoặc toàn bộ sinh viên đến từ các trường đối tác.

Hiện tại, công tác quản lý sự kiện chủ yếu được thực hiện thủ công, dễ xảy ra sai sót và thiếu minh bạch. Bên cạnh việc phát triển hệ thống quản lý cuộc thi, đề tài còn có hướng nghiên cứu về tính nhất quán trong chấm điểm của giám khảo tại các cuộc thi hackathon. Đây là yếu tố quan trọng liên quan đến sự công bằng trong thi cử nhưng chưa được nghiên cứu đầy đủ trong bối cảnh đánh giá kỹ thuật phần mềm.

Hệ thống đóng vai trò vừa là nền tảng quản lý cuộc thi, vừa là công cụ thu thập dữ liệu phục vụ nghiên cứu về độ tin cậy liên đánh giá viên trong đánh giá kỹ thuật phần mềm.

============================================================
2. BỐI CẢNH VÀ VẤN ĐỀ HIỆN TẠI
============================================================

Quy trình quản lý sự kiện hiện tại đang tồn tại nhiều vấn đề:

- Đăng ký đội thi và quản lý hạng mục còn thủ công, dẫn đến chậm trễ và sai sót dữ liệu.
- Chấm điểm được thực hiện qua các file Excel riêng lẻ của từng giám khảo. Ban tổ chức phải thu thập và nhập lại toàn bộ kết quả thủ công, gây mất thời gian và dễ xảy ra sai sót.
- Kênh thông tin liên lạc giữa ban tổ chức, mentor, đội thi và người tham gia còn hạn chế.
- Không có nhật ký kiểm tra cho các quyết định chấm điểm, loại đội hoặc hủy kết quả, làm giảm tính minh bạch và độ tin cậy của cuộc thi.
- Việc phân tích sự nhất quán giữa các giám khảo gần như chưa được hỗ trợ bằng hệ thống.

============================================================
3. MỤC TIÊU DỰ ÁN
============================================================

Dự án hướng đến xây dựng một hệ thống web giúp quản lý toàn bộ quy trình tổ chức cuộc thi SEAL Hackathon, bao gồm:

- Quản lý sự kiện hackathon theo từng mùa.
- Quản lý nhiều vòng thi trong một sự kiện.
- Quản lý hạng mục thi đấu.
- Quản lý đội thi và thành viên.
- Quản lý mentor, giám khảo nội bộ và giám khảo khách mời.
- Hỗ trợ đội thi nộp bài theo từng vòng.
- Hỗ trợ giám khảo chấm điểm theo tiêu chí.
- Tự động tổng hợp điểm, xếp hạng và xác định đội vào vòng tiếp theo.
- Ghi nhận lịch sử thao tác để tăng tính minh bạch.
- Xuất báo cáo kết quả dưới dạng CSV/Excel.
- Nếu chọn hướng RBL, hệ thống có thể thu thập dữ liệu phục vụ phân tích độ tin cậy giữa các giám khảo.

============================================================
4. ACTORS / ROLES TRONG HỆ THỐNG
============================================================

4.1. Team Member
- Là thành viên của đội thi.
- Có thể tham gia đội, xem thông tin sự kiện, xem lịch thi, xem thông báo và theo dõi kết quả.

4.2. Team Leader
- Là người đại diện đội thi.
- Có quyền tạo đội, mời/thêm thành viên, đăng ký đội vào hạng mục, nộp bài và theo dõi kết quả của đội.

4.3. Mentor
- Là người hỗ trợ chuyên môn cho các đội thuộc hạng mục được phân công.
- Có thể xem danh sách đội được mentor, theo dõi tiến độ và hỗ trợ đội thi.

4.4. Judge
- Là giám khảo chấm điểm bài nộp.
- Có thể là giám khảo nội bộ hoặc giám khảo khách mời.
- Chỉ được chấm điểm các vòng/hạng mục được ban tổ chức phân công.

4.5. Event Coordinator (SE Dept / PDP Staff)
- Là ban tổ chức hoặc điều phối viên sự kiện.
- Có quyền quản lý sự kiện, vòng thi, hạng mục, tiêu chí chấm điểm, phân công mentor/giám khảo, duyệt tài khoản, theo dõi nộp bài, tổng hợp điểm, xếp hạng và công bố kết quả.

4.6. Admin (nên bổ sung khi triển khai hệ thống)
- Quản lý tài khoản hệ thống, phân quyền, cấu hình hệ thống và hỗ trợ ban tổ chức.

============================================================
5. DANH SÁCH THỰC THỂ CHÍNH
============================================================

Các thực thể chính trong hệ thống gồm:

1. Hackathon Event
- Đại diện cho một sự kiện SEAL Hackathon cụ thể.
- Ví dụ: SEAL Spring 2026, SEAL Summer 2026.

2. Track / Competition Category
- Hạng mục thi đấu trong một sự kiện.
- Ví dụ: Web Application, AI Solution, Education Tech, Social Impact.

3. Round
- Vòng thi trong một sự kiện.
- Ví dụ: vòng sơ khảo, vòng loại, vòng chung kết.

4. Team
- Đội thi tham gia hackathon.
- Một đội có từ 3 đến 5 thành viên.

5. Team Member
- Thành viên thuộc một đội thi.

6. Mentor
- Người hướng dẫn hoặc hỗ trợ đội thi theo hạng mục.

7. Judge
- Giám khảo chấm điểm bài nộp.
- Có thể là Internal Judge hoặc Guest Judge.

8. Submission
- Bài nộp của đội thi theo từng vòng.
- Có thể gồm link repository, link demo, link báo cáo/slide.

9. Score / Ranking
- Điểm số và thứ hạng của đội thi theo vòng, hạng mục và toàn sự kiện.

10. Prize
- Giải thưởng được trao dựa trên kết quả xếp hạng.

11. Scoring Criteria
- Tiêu chí chấm điểm.
- Mỗi tiêu chí có thể có trọng số khác nhau.

12. Audit Log
- Nhật ký ghi nhận các thao tác quan trọng như chấm điểm, chỉnh sửa điểm, loại đội, công bố kết quả.

============================================================
6. CHỨC NĂNG CHÍNH CỦA HỆ THỐNG
============================================================

6.1. Quản lý sự kiện và vòng thi

- Tạo, cập nhật, xóa hoặc đóng/mở sự kiện hackathon.
- Cấu hình nhiều vòng thi trong mỗi sự kiện.
- Thiết lập thời gian bắt đầu, thời gian kết thúc và hạn nộp bài cho từng vòng.
- Phân công giám khảo cho từng vòng.
- Gán tiêu chí chấm điểm cho từng vòng.
- Định nghĩa quy tắc thăng vòng, ví dụ: top N đội của mỗi hạng mục được vào vòng tiếp theo.

6.2. Quản lý tiêu chí chấm điểm

- Tạo bộ tiêu chí mặc định để tái sử dụng qua nhiều sự kiện.
- Cho phép mỗi sự kiện kế thừa bộ tiêu chí mặc định.
- Cho phép ban tổ chức thêm, xóa hoặc điều chỉnh tiêu chí theo từng sự kiện.
- Thiết lập trọng số cho từng tiêu chí.
- Ví dụ tiêu chí: ý tưởng, tính sáng tạo, kỹ thuật, UI/UX, mức độ hoàn thiện, khả năng trình bày.

6.3. Quản lý hạng mục thi đấu

- Tạo hạng mục trong mỗi sự kiện.
- Cập nhật thông tin hạng mục.
- Phân công mentor cho từng hạng mục.
- Cho phép một giảng viên có thể là Mentor ở một hạng mục và là Judge ở hạng mục khác trong cùng sự kiện.

6.4. Quản lý đội thi

- Cho phép Team Leader tạo đội.
- Đội thi gồm 3 đến 5 thành viên.
- Team Leader đăng ký đội vào một hạng mục cụ thể.
- Quản lý trạng thái đội: đang chờ duyệt, đã duyệt, bị từ chối, bị loại.
- Xem danh sách đội theo sự kiện, hạng mục và vòng thi.

6.5. Đăng ký và xác thực người dùng

- Người dùng đăng ký bằng Email/Mật khẩu.
- Hệ thống sử dụng JWT để xác thực người dùng.
- Khi đăng ký, người tham gia tự phân loại:
  + Sinh viên FPT: cung cấp mã số sinh viên FPT.
  + Sinh viên ngoài trường: cung cấp mã số sinh viên và tên trường.
- Tài khoản cần được ban tổ chức phê duyệt trước khi tham gia thi.
- Giám khảo khách mời có thể được tạo tài khoản tạm thời bởi ban tổ chức.
- Tài khoản giám khảo khách mời chỉ có quyền chấm điểm các vòng được phân công.

6.6. Nộp bài

- Đội thi nộp bài theo từng vòng.
- Bài nộp gồm các đường dẫn URL như:
  + Repository dự án.
  + Link demo.
  + Link báo cáo hoặc slide thuyết trình.
- Hệ thống kiểm tra hạn nộp bài.
- Có thể cho phép cập nhật bài nộp trước deadline.
- Tích hợp GitHub/GitLab API để tự động lấy metadata repository là tính năng optional.

6.7. Đánh giá và chấm điểm

- Giám khảo chấm điểm theo bộ tiêu chí được cấu hình cho từng sự kiện/vòng thi.
- Điểm số từng tiêu chí của từng giám khảo được ghi lại riêng biệt.
- Ban tổ chức có thể phân công giám khảo nội bộ và giám khảo khách mời vào từng vòng.
- Hệ thống hỗ trợ lưu nháp điểm trước khi submit chính thức.
- Sau khi submit, việc chỉnh sửa điểm cần được ghi nhận vào audit log.

6.8. Chấm điểm, xếp hạng và loại đội

- Tự động tính tổng điểm dựa trên tiêu chí và trọng số.
- Tự động xếp hạng đội theo:
  + Từng vòng.
  + Từng hạng mục.
  + Toàn bộ sự kiện.
- Tự động xác định đội đủ điều kiện vào vòng tiếp theo theo quy tắc top N.
- Ban tổ chức có thể loại đội hoặc bài nộp vi phạm quy chế.
- Khi loại đội, hệ thống cần lưu lý do và thời gian loại.
- Nhật ký kiểm tra cần ghi lại tất cả hành động liên quan đến chấm điểm, chỉnh sửa điểm, loại đội và công bố kết quả.

6.9. Giải thưởng và công bố kết quả

- Trao giải dựa trên kết quả xếp hạng.
- Tạo danh sách giải thưởng theo từng hạng mục hoặc toàn sự kiện.
- Thông báo kết quả đến người tham gia.
- Công bố bảng xếp hạng.
- Xuất báo cáo điểm, ranking và kết quả dưới dạng CSV/Excel.

6.10. Thu thập dữ liệu nghiên cứu RBL (tính năng cộng điểm nếu triển khai)

- Ghi lại điểm số của từng giám khảo theo từng tiêu chí cho từng bài nộp.
- Không chỉ lưu điểm trung bình cuối cùng, mà cần lưu dữ liệu chấm riêng của từng giám khảo.
- Hỗ trợ vòng hiệu chuẩn, trong đó giám khảo chấm bài mẫu trước khi chấm thật.
- Hệ thống hiển thị phân bố điểm để hỗ trợ đồng thuận giữa các giám khảo.
- Xuất bộ dữ liệu chấm điểm đã ẩn danh dưới dạng CSV.
- Dashboard hiển thị phương sai điểm giữa các giám khảo theo từng tiêu chí.

============================================================
7. MAIN USE CASES ĐỀ XUẤT
============================================================

Bảng main use cases nên dùng trong tài liệu SRS hoặc báo cáo:

| ID | Main Use Case | Actor chính | Mô tả ngắn |
|----|---------------|-------------|------------|
| UC01 | Đăng ký tài khoản | Team Member, Team Leader | Người dùng đăng ký tài khoản và khai báo loại sinh viên. |
| UC02 | Duyệt tài khoản | Event Coordinator | Ban tổ chức duyệt tài khoản trước khi người dùng được tham gia. |
| UC03 | Quản lý sự kiện | Event Coordinator | Tạo và cấu hình sự kiện hackathon. |
| UC04 | Quản lý vòng thi | Event Coordinator | Tạo vòng thi, deadline, quy tắc thăng vòng. |
| UC05 | Quản lý hạng mục | Event Coordinator | Tạo hạng mục và phân công mentor. |
| UC06 | Quản lý tiêu chí chấm điểm | Event Coordinator | Tạo bộ tiêu chí, trọng số và áp dụng cho sự kiện/vòng thi. |
| UC07 | Tạo và đăng ký đội thi | Team Leader | Tạo đội, thêm thành viên và đăng ký vào hạng mục. |
| UC08 | Nộp bài dự thi | Team Leader | Nộp repository, demo, báo cáo/slide theo từng vòng. |
| UC09 | Phân công giám khảo | Event Coordinator | Phân công giám khảo cho vòng thi/hạng mục. |
| UC10 | Chấm điểm bài nộp | Judge | Giám khảo chấm điểm theo tiêu chí được phân công. |
| UC11 | Xem bảng xếp hạng | Team, Mentor, Judge, Event Coordinator | Xem ranking theo vòng, hạng mục hoặc toàn sự kiện. |
| UC12 | Xử lý thăng vòng | Event Coordinator | Hệ thống xác định đội vào vòng tiếp theo theo quy tắc top N. |
| UC13 | Loại đội vi phạm | Event Coordinator | Loại đội/bài nộp vi phạm và ghi nhận lý do. |
| UC14 | Công bố giải thưởng | Event Coordinator | Trao giải và công bố kết quả. |
| UC15 | Xuất báo cáo | Event Coordinator | Xuất điểm, ranking, danh sách đội dưới dạng CSV/Excel. |
| UC16 | Phân tích độ nhất quán chấm điểm | Event Coordinator / Researcher | Xem dashboard và xuất dữ liệu RBL đã ẩn danh. |

============================================================
8. PHẠM VI MVP NÊN LÀM TRƯỚC
============================================================

Để dự án khả thi với nhóm sinh viên SWP391, nên ưu tiên MVP như sau:

8.1. Giai đoạn 1 - Core system

- Đăng ký, đăng nhập, phân quyền bằng JWT.
- Quản lý user và duyệt tài khoản.
- Quản lý sự kiện hackathon.
- Quản lý hạng mục.
- Quản lý vòng thi.
- Quản lý đội thi.
- Đăng ký đội vào hạng mục.
- Nộp bài bằng URL.

8.2. Giai đoạn 2 - Scoring system

- Quản lý tiêu chí chấm điểm.
- Phân công giám khảo.
- Giám khảo chấm điểm bài nộp.
- Tính điểm trung bình hoặc điểm có trọng số.
- Xếp hạng đội theo vòng và hạng mục.
- Xác định đội vào vòng tiếp theo.

8.3. Giai đoạn 3 - Reporting and transparency

- Xuất báo cáo CSV/Excel.
- Công bố kết quả.
- Quản lý giải thưởng.
- Audit log cho các hành động quan trọng.

8.4. Giai đoạn 4 - RBL / Advanced features

- Lưu điểm riêng của từng giám khảo theo từng tiêu chí.
- Dashboard phân tích độ lệch điểm.
- Xuất dữ liệu ẩn danh.
- Vòng hiệu chuẩn giám khảo.
- Tích hợp GitHub/GitLab API.

============================================================
9. GỢI Ý PHÂN CÔNG CHO NHÓM 4 NGƯỜI
============================================================

Giả sử nhóm gồm 2 Frontend, 1 Backend và 1 Fullstack Lead:

9.1. Frontend 1
- Thiết kế layout tổng thể.
- Làm trang đăng nhập, đăng ký.
- Làm dashboard cho Team Leader/Team Member.
- Làm màn hình tạo đội, quản lý đội, đăng ký hạng mục.
- Làm màn hình nộp bài.

9.2. Frontend 2
- Làm dashboard cho Event Coordinator.
- Làm màn hình quản lý sự kiện, vòng thi, hạng mục.
- Làm màn hình quản lý tiêu chí chấm điểm.
- Làm màn hình bảng xếp hạng, kết quả, giải thưởng.
- Làm màn hình dashboard thống kê nếu có RBL.

9.3. Backend
- Thiết kế database.
- Xây dựng API authentication/authorization bằng JWT.
- Xây dựng API quản lý user, role, approval.
- Xây dựng API event, round, track, team, submission.
- Xây dựng API scoring, ranking, export CSV/Excel.

9.4. Fullstack Lead
- Chốt requirement và scope MVP.
- Thiết kế kiến trúc tổng thể.
- Review code và merge branch.
- Làm các phần khó như phân quyền, scoring logic, ranking logic, audit log.
- Hỗ trợ cả frontend và backend khi bị blocking.
- Chuẩn bị demo flow và tài liệu báo cáo.

============================================================
10. GỢI Ý CÔNG NGHỆ
============================================================

Frontend:
- ReactJS.
- React Router.
- Tailwind CSS.
- Axios.
- React Hook Form.
- Chart library nếu làm dashboard thống kê.

Backend:
- Spring Boot.
- Spring Security.
- JWT Authentication.
- Spring Data JPA.
- RESTful API.

Database:
- MySQL hoặc PostgreSQL.

Tools:
- GitHub/GitLab để quản lý source code.
- Postman để test API.
- Figma để thiết kế giao diện.
- Docker nếu nhóm có thời gian.

Optional:
- GitHub/GitLab API để lấy metadata repository.
- Apache POI hoặc thư viện tương đương để export Excel.

============================================================
11. GỢI Ý CÁC MÀN HÌNH CHÍNH
============================================================

11.1. Public pages
- Landing page giới thiệu SEAL Hackathon.
- Danh sách sự kiện đang mở.
- Chi tiết sự kiện.
- Đăng nhập.
- Đăng ký.

11.2. Team pages
- Team dashboard.
- Tạo đội.
- Quản lý thành viên đội.
- Đăng ký hạng mục.
- Nộp bài.
- Xem trạng thái bài nộp.
- Xem kết quả và ranking.

11.3. Mentor pages
- Mentor dashboard.
- Danh sách đội được phân công.
- Chi tiết đội.
- Theo dõi bài nộp và tiến độ.

11.4. Judge pages
- Judge dashboard.
- Danh sách bài nộp cần chấm.
- Chi tiết bài nộp.
- Form chấm điểm theo tiêu chí.
- Lịch sử điểm đã chấm.

11.5. Event Coordinator pages
- Admin/Coordinator dashboard.
- Quản lý sự kiện.
- Quản lý vòng thi.
- Quản lý hạng mục.
- Quản lý tiêu chí chấm điểm.
- Quản lý người dùng và duyệt tài khoản.
- Quản lý đội thi.
- Phân công mentor.
- Phân công giám khảo.
- Theo dõi bài nộp.
- Xem và công bố ranking.
- Quản lý giải thưởng.
- Xuất báo cáo.
- Audit log.

============================================================
12. RESEARCH-BASED LEARNING (RBL)
============================================================

Đề tài có hướng nghiên cứu về độ nhất quán trong chấm điểm giữa các giám khảo.

Research Question chính:
How consistent are hackathon evaluation scores across different judges evaluating the same submission in academic software engineering competitions?

Các câu hỏi nghiên cứu phụ:

RQ1: What is the overall inter-rater reliability (ICC, Krippendorff's alpha) of SEAL hackathon scoring?

RQ2: Which scoring criteria show the highest and lowest inter-rater agreement?
Ví dụ: so sánh tiêu chí kỹ thuật với các tiêu chí mềm/chủ quan.

RQ3: Does judge type affect scoring consistency?
Ví dụ: so sánh SE Faculty với Guest Judge.

Để hỗ trợ RBL, hệ thống nên lưu dữ liệu chấm điểm chi tiết theo cấu trúc:

- Event ID.
- Round ID.
- Track ID.
- Team ID.
- Submission ID.
- Judge ID đã ẩn danh.
- Judge type: Internal hoặc Guest.
- Criteria ID.
- Score.
- Timestamp.

Dữ liệu xuất ra cần được ẩn danh để phục vụ nghiên cứu mà không lộ thông tin cá nhân.

============================================================
13. ĐÁNH GIÁ ĐỘ KHẢ THI
============================================================

Đề tài này phù hợp với SWP391 nếu nhóm biết giới hạn phạm vi và ưu tiên MVP. Hệ thống có nhiều nghiệp vụ nhưng có thể chia nhỏ rõ ràng cho frontend và backend.

Điểm phù hợp:
- Có nhiều màn hình để frontend triển khai.
- Có nghiệp vụ backend rõ ràng nhưng không quá nặng nếu giới hạn scope.
- Có CRUD đầy đủ cho event, team, round, track, criteria, submission, score.
- Có phân quyền rõ ràng theo role.
- Có điểm cộng nếu làm thêm RBL/dashboard/export.
- Phù hợp với stack React + Spring Boot.

Rủi ro:
- Nếu ôm toàn bộ chức năng nâng cao ngay từ đầu thì scope sẽ lớn.
- Logic scoring, ranking và thăng vòng cần thiết kế kỹ.
- Phân quyền nhiều role có thể gây phức tạp nếu không thống nhất từ đầu.
- RBL là phần nâng cao, nên chỉ làm sau khi core system ổn định.

Kết luận:
Đề tài khả thi cho nhóm SWP391 nếu chia thành các giai đoạn rõ ràng. Nên hoàn thành core flow trước: tạo sự kiện -> tạo đội -> đăng ký hạng mục -> nộp bài -> phân công giám khảo -> chấm điểm -> xếp hạng -> công bố kết quả.

============================================================
14. CORE FLOW DEMO NÊN CHUẨN BỊ
============================================================

Luồng demo chính nên trình bày như sau:

1. Event Coordinator đăng nhập.
2. Event Coordinator tạo sự kiện SEAL Hackathon.
3. Event Coordinator tạo hạng mục và vòng thi.
4. Event Coordinator tạo bộ tiêu chí chấm điểm.
5. Team Leader đăng ký tài khoản và được duyệt.
6. Team Leader tạo đội và thêm thành viên.
7. Team Leader đăng ký đội vào một hạng mục.
8. Team Leader nộp bài cho vòng thi.
9. Event Coordinator phân công giám khảo.
10. Judge đăng nhập và chấm điểm bài nộp.
11. Hệ thống tính điểm và xếp hạng.
12. Event Coordinator công bố kết quả.
13. Team xem ranking và kết quả.
14. Event Coordinator xuất báo cáo CSV/Excel.

Nếu có RBL:
15. Event Coordinator xem dashboard phân tích độ lệch điểm giữa các giám khảo.
16. Event Coordinator xuất dữ liệu chấm điểm đã ẩn danh.

============================================================
15. KẾT LUẬN TỔNG HỢP
============================================================

SEAL – Software Engineering Hackathon Management System là một đề tài có tính thực tế cao, phù hợp để triển khai thành sản phẩm web hoàn chỉnh trong môn SWP391. Hệ thống giải quyết các vấn đề hiện tại của việc tổ chức hackathon như đăng ký thủ công, quản lý đội thi rời rạc, chấm điểm bằng Excel, thiếu minh bạch trong kết quả và thiếu dữ liệu phục vụ nghiên cứu.

Đề tài có nhiều actor, nhiều use case rõ ràng và có thể chia task tốt cho nhóm phát triển. Với nhóm dùng React và Spring Boot, nên ưu tiên hoàn thiện các chức năng cốt lõi trước, sau đó mới mở rộng sang dashboard, export báo cáo, audit log và RBL.

Phạm vi nên tập trung:
- Quản lý sự kiện.
- Quản lý đội thi.
- Quản lý vòng thi và hạng mục.
- Nộp bài.
- Chấm điểm.
- Xếp hạng.
- Công bố kết quả.
- Xuất báo cáo.

Các tính năng nâng cao như GitHub/GitLab API, phân tích ICC/Krippendorff's alpha, dashboard phương sai điểm và vòng hiệu chuẩn giám khảo nên được xem là phần mở rộng hoặc điểm cộng.
