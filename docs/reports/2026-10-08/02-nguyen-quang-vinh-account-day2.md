# Báo cáo: Hoàn thiện tài khoản Ngày 2

- Người thực hiện: Nguyễn Quang Vinh
- Ngày: 2026-10-08
- Rà soát lần hai: 2026-10-09
- Chuẩn bị pull request: 2026-10-10
- Branch: `feature/account-management`
- Task: Ngày 2 — tài khoản, xác thực, hồ sơ và địa chỉ theo `docs/phan-cong-nhiem-vu-3-ngay.md`

## 1. Phạm vi và hiện trạng project

Đã rà soát cấu trúc Maven WAR, cấu hình JPA/HikariCP, schema/seed MySQL, entity,
repository/service, các Servlet API/page, JSP/JavaScript và bộ test hiện có.
Phần tài khoản Ngày 1 đã có domain và giao diện khung; auth/profile/address API
chưa xử lý nghiệp vụ và trả `501`. Task này hoàn thiện vertical slice của Vinh.
Các contract và scaffold order/payment/admin menu của thành viên khác được giữ nguyên;
filter dùng chung bảo vệ các route đó theo tài khoản và chức vụ.

## 2. Công việc đã thực hiện

### Backend

- Thêm `AccountService`, `AccountRepository`, `JpaAccountRepository`, DTO request/response
  và `AccountException`; Servlet gọi service, không trả JPA entity cho client.
- Đăng ký khách hàng; chuẩn hóa email, kiểm tra dữ liệu và mật khẩu xác nhận.
  Email/số điện thoại là duy nhất trên cả tài khoản khách hàng và nhân viên.
  Xung đột trả `409` cùng lỗi theo field; lỗi constraint của MySQL được xử lý sau rollback.
- Băm mật khẩu bằng PBKDF2 HMAC SHA256, 600.000 vòng và salt ngẫu nhiên 16 byte.
  So sánh hash bằng `MessageDigest.isEqual`; hash mẫu hoặc sai định dạng không đăng nhập được.
- Đăng nhập khách hàng/nhân viên đang hoạt động, đổi session ID hiện có; đăng xuất
  hủy session. Đăng ký không tự đăng nhập. Session mặc định hết hạn sau 30 phút
  không hoạt động; `rememberMe=true` trên API tăng lên 7 ngày, cookie vẫn theo phiên trình duyệt.
- `SessionAuth` lưu `currentUser`, `userId`, `accountType`, `customerId` hoặc
  `employeeId`/`role` phục vụ tích hợp. Cookie HttpOnly và SameSite=Lax.
- `AuthenticationFilter` kiểm tra lại trạng thái tài khoản và chức vụ từ database,
  bảo vệ trang/API khách hàng và quản trị. Khách hàng chỉ sửa hồ sơ/địa chỉ của mình;
  nhân viên được xem hồ sơ, các route quản trị cần ADMIN hoặc chức vụ phù hợp.
  Filter dùng servlet path đã chuẩn hóa của Tomcat để kiểm tra URL mã hóa,
  dot segment và matrix parameter. Mutation khác Origin/cross-site bị từ chối.
- CRUD địa chỉ kiểm tra chủ sở hữu, trả `404` khi không thuộc khách hàng hiện tại.
  Mỗi mutation khóa customer trong transaction. Địa chỉ đầu tiên tự thành mặc định;
  đổi mặc định flush địa chỉ cũ trước khi đặt địa chỉ mới để tuân thủ unique index.
  Bỏ/xóa mặc định tự chọn địa chỉ còn lại; địa chỉ duy nhất luôn là mặc định.
  Sửa/xóa địa chỉ không thay đổi snapshot địa chỉ đã lưu trong đơn hàng.

### Frontend

- Kết nối form đăng ký/đăng nhập/hồ sơ/địa chỉ với API JSON qua fetch.
- Validation client, xác nhận mật khẩu, lỗi server theo field, focus vào field lỗi,
  trạng thái đang xử lý, vô hiệu hóa thao tác lặp, loading/retry/empty state.
- Đăng ký thành công chuyển tới đăng nhập; đăng nhập chuyển về trang được bảo vệ
  đã yêu cầu hoặc hồ sơ. `returnTo` được giới hạn trong context hiện tại.
- Hồ sơ tải dữ liệu thật; nhân viên xem ở chế độ chỉ đọc. Thanh điều hướng theo session,
  có đăng xuất và xử lý phiên hết hạn.
- Sổ địa chỉ có thêm/sửa/hủy/xóa/chọn mặc định; render dữ liệu bằng `textContent`
  và DOM API để dữ liệu người dùng không được diễn giải thành HTML.

### API và tài liệu

- Hoàn thiện `/api/auth/register`, `/api/auth/login`, `/api/auth/logout`,
  `/api/auth/session`, `/api/profile`, `/api/addresses` và `/api/addresses/{addressId}`.
- Profile hỗ trợ GET, PUT và POST tương thích form JavaScript; address DELETE trả `204`.
  JSON sai trả `400`, chưa đăng nhập `401`, sai quyền `403`, không tìm thấy `404`,
  sai method `405` cùng Allow, xung đột `409`, sai Content-Type `415`.
- Giữ envelope `success/data/error`, trả lỗi hệ thống chung để tránh lộ exception nội bộ;
  timestamp JSON là chuỗi ISO, response tài khoản có `Cache-Control: no-store`.
- Cập nhật OpenAPI Ngày 2, README hướng dẫn demo và chú thích hash trong seed.
  Không thay đổi cấu trúc database hoặc dữ liệu seed hiện có.

## 3. File chính

| Khu vực | File/nhóm file |
|---|---|
| Service/repository | `AccountService.java`, `AccountRepository.java`, `JpaAccountRepository.java` |
| Auth/session | `PasswordHasher.java`, `SessionAuth.java`, `AuthenticationFilter.java`, `SessionConfigurationListener.java` |
| API | `AccountApiServlet.java`, `AuthApiServlet.java`, `ProfileApiServlet.java`, `AddressApiServlet.java`, `BaseApiServlet.java` |
| Model API | `LoginRequest`, `RegisterRequest`, `ProfileUpdateRequest`, `ProfileResponse`, `AddressWriteRequest`, `AddressResponse`, `AccountException` |
| JSON | `JsonProvider.java` |
| Frontend | `account.js`, `styles.css`, `header.jspf`, login/register/profile/addresses JSP |
| Tests | Account API/filter/service/password tests, MySQL integration tests và cập nhật form/route contract tests |
| Tài liệu | `docs/api-auth-profile-address.yaml`, `README.md`, `database/seed.sql` (chú thích) |

## 4. Kiểm thử

Môi trường: JDK 17.0.18, MySQL 8.0.43, Tomcat 10.1.48.

Các số liệu bên dưới là kết quả xác minh trước lần đồng bộ main sau PR #17. Kết quả chạy lại trên baseline mới ngày 2026-10-09 được cập nhật ở phần 8, không mặc định dùng lại số liệu cũ.

```powershell
$env:JAVA_HOME = '<đường dẫn JDK 17 hoặc 21>'
mvn -f backend/pom.xml verify
node --check frontend/assets/js/account.js
node frontend/tests/account-browser-smoke.cjs
git diff --check
```

- Maven `verify`: **102/102 tests đạt**, không failure/error/skipped; tạo `backend/target/crave.war`.
- Có 13 test tích hợp MySQL: 5 test mapping/kết nối có sẵn và 8 test Ngày 2 cho
  đăng ký/đăng nhập/profile/address CRUD, quyền sở hữu, rollback uniqueness,
  snapshot đơn hàng và hai thao tác địa chỉ đồng thời.
- Chrome headless: **28/28 kịch bản giao diện đạt**, dùng fetch giả lập cho validation,
  lỗi field, hồ sơ, CRUD địa chỉ, nội dung HTML không tin cậy và kiểm tra redirect.
- Tomcat HTTP/JSP: **67/67 bước kiểm tra đạt** trực tiếp trên WAR với MySQL, bao gồm render 4 trang,
  cookie/session rotation, luồng demo, quyền nhân viên, chủ sở hữu địa chỉ,
  session hết hạn thật, tài khoản khóa/inactive, JSON sai kiểu và URL mã hóa/chuẩn hóa.
  Dữ liệu kiểm thử được dọn sau khi chạy.
- OpenAPI YAML hợp lệ, không key trùng, 8 paths, tất cả `$ref` nội bộ tồn tại.
- Kiểm tra cú pháp JavaScript và `git diff --check` đạt.

Java 25 mặc định trên máy không tương thích phiên bản Byte Buddy của project;
đã dùng JDK 17 để kiểm thử, không thay dependency/build chỉ để phục vụ môi trường máy.

## 5. Cách demo

1. Deploy `backend/target/crave.war` lên Tomcat có cấu hình database cục bộ.
2. Mở `/crave/auth/register`, đăng ký email/số điện thoại mới.
3. Đăng nhập, sửa hồ sơ và tải lại để kiểm tra dữ liệu lưu.
4. Vào Địa chỉ, thêm hai địa chỉ, sửa, chọn mặc định và xóa địa chỉ mặc định.
5. Đăng xuất; trang khách hàng chuyển về đăng nhập, API bảo vệ trả `401`.

Mật khẩu seed là `REPLACE_WITH_PASSWORD_HASH`, không dùng được để đăng nhập;
demo bằng tài khoản vừa đăng ký. Nhân viên có thể đăng nhập khi được cấp hash PBKDF2 hợp lệ.

## 6. Trạng thái bàn giao

Đã hoàn thành task Ngày 2 của Nguyễn Quang Vinh ở backend, frontend và API.
Gate 2 của cả nhóm cần kết quả demo/review của ba vertical slice còn lại;
báo cáo này không tự xác nhận hoặc đánh dấu hoàn thành Gate của toàn nhóm.

## 7. Rà soát lần hai — 2026-10-09

Đã đối chiếu yêu cầu Ngày 2, đọc lại code và bổ sung regression tests cho các tình huống
chưa được bộ test đầu kiểm tra. Các lỗi tìm thấy đã được sửa:

- Jackson mặc định chuyển số/boolean thành chuỗi, chuyển chuỗi/số thành boolean,
  và nhận khóa JSON trùng. Parser riêng cho API tài khoản giờ từ chối các trường hợp
  này bằng `400`; request body thiếu Content-Type hoặc sai media type trả `415`.
  Parser dùng bản sao cấu hình, không đổi cách đọc JSON của các domain khác.
- Tải lại danh sách địa chỉ chậm vẫn cho phép gửi thao tác mới. UI giờ khóa form và
  nút thao tác trong lúc tải, tránh response cũ ghi đè kết quả mutation.
- Đổi mặc định khi đang sửa địa chỉ có thể làm mất nội dung chưa lưu hoặc để checkbox
  sai trạng thái. UI giữ bản nháp và đồng bộ checkbox sau khi đổi/xóa mặc định.
- Địa chỉ đang sửa bị xóa ở nơi khác vẫn còn trong form; refresh nhận `403` vẫn giữ
  danh sách/nút cũ. UI giờ reset mục không còn tồn tại với thông báo rõ và xóa trạng thái
  cũ khi quyền truy cập bị từ chối.
- Đăng nhập email không tồn tại hoặc hash không hỗ trợ bỏ qua bước PBKDF2, tạo chênh
  lệch công việc rõ rệt với email hợp lệ. Các trường hợp này giờ cũng thực hiện một
  phép dẫn xuất giả và luôn từ chối đăng nhập; test kiểm tra số lần gọi, không dùng
  ngưỡng thời gian thiếu ổn định.
- Cleanup test trước đây chỉ dựa vào ID ngẫu nhiên; trường hợp hiếm bị trùng ID có thể
  tác động bản ghi không thuộc test. Cleanup giờ kiểm tra thêm email duy nhất/chủ sở hữu.

Sáu lỗi giao diện được tái hiện thất bại trước bản sửa và đạt sau bản sửa bằng Chrome.
Runner được lưu tại `frontend/tests/account-browser-smoke.cjs`, chạy bằng Node và
Chrome/Edge, có thể đặt biến `CHROME_PATH`. Các ca MySQL mới kiểm tra create/update/delete
đồng thời, xóa địa chỉ cuối cùng trong lúc tạo mới, rollback sau khi đã flush bỏ mặc định,
tranh chấp số điện thoại, tài khoản nhân viên/ngừng hoạt động và constraint phone thực tế.
API tests cũng kiểm tra chuyển tài khoản không giữ role/customer ID cũ và từ chối
request cố chèn role hoặc chủ sở hữu.

## 8. Đồng bộ main sau PR #17 — 2026-10-09

Đã lấy main sau khi [PR #17](https://github.com/nguyenvinhz/Servlet_Project_Crave/pull/17) tích hợp nhánh cart/voucher Ngày 1, rồi đồng bộ vào `feature/account-management`. Main giữ cart/voucher hoạt động đã có; việc đồng bộ bổ sung report và contract test phù hợp với main, không đổi các API đó về scaffold `501`.

Nhánh tài khoản giữ phần Ngày 2 đã triển khai: đăng ký/đăng nhập/đăng xuất, session và filter xác thực, profile và CRUD địa chỉ, các form gọi API và demo đăng ký → đăng nhập → sửa profile → thêm/chọn địa chỉ. Các kiểm tra quyền và tích hợp session với cart/voucher được giữ cùng phần tài khoản; không đưa công việc order-payment hoặc phần setter Ngày 3 của nhánh cart/voucher vào task này.

Kết quả chạy lại trên baseline mới:

- `mvn -B -f backend/pom.xml clean verify`: 156 test đạt, 0 failure/error/skipped; tạo WAR thành công, bao gồm kiểm thử tích hợp MySQL.
- Chrome headless: 33/33 kịch bản giao diện đạt.
- Tomcat/MySQL trực tiếp: 87 kiểm tra HTTP đạt, exit code 0; bao phủ đăng ký, đăng nhập, profile, CRUD/chọn mặc định địa chỉ, session, xác thực cart và quyền sở hữu dữ liệu.
- Các bản ghi được tạo riêng cho kiểm thử được dọn trong `finally`.
- Main cục bộ khớp `origin/main` sau PR #17 và đã chứa lịch sử nhánh Trí Ngày 1. Sau khi đồng bộ, mã nguồn `backend/src/main` và `frontend` của phần tài khoản giữ nguyên so với trước lần pull này; không thêm thay đổi nghiệp vụ ngoài task.

Task này chỉ commit và push nhánh `feature/account-management` để review, không merge phần tài khoản Ngày 2 vào main. Phạm vi bàn giao của Vinh không tự xác nhận Gate 2 của cả nhóm.

## 9. Hoàn thiện kiểm tra và chuẩn bị pull request — 2026-10-10

Đã hoàn tất lần merge main đang chờ commit, đồng bộ phần đính chính báo cáo cart/voucher Ngày 1. Phần thay đổi này khớp main; không thêm nghiệp vụ cart/voucher vào task tài khoản.

Các thay đổi tài khoản được review và hoàn thiện trước khi push:

- Gắn snapshot người dùng và revision xác thực vào từng request. Login đổi revision, còn refresh profile chỉ cập nhật khi snapshot vẫn khớp session. Request cũ không ghi đè hoặc hủy login mới; logout đồng thời trả `401` thay vì lỗi server khi session đã bị hủy.
- Từ chối mật khẩu chứa NUL hoặc surrogate UTF-16 không hợp lệ trước khi hash/kiểm tra đăng nhập; tiếp tục hỗ trợ Unicode hợp lệ, gồm tiếng Việt và emoji. Kiểm tra hash vẫn thực hiện một lần dẫn xuất giả cho đầu vào không hợp lệ.
- Đồng bộ checkbox địa chỉ mặc định khi giá trị chưa được người dùng sửa; giữ lựa chọn chưa lưu khi xóa địa chỉ khác. Mọi lỗi `403` ở trang địa chỉ đều xóa danh sách/form cũ và khóa trường nhập.
- Bổ sung regression tests cho session đổi tài khoản/logout đồng thời, mật khẩu và trạng thái địa chỉ. Sửa fixture `FormApiContractTest` để lưu session/request attributes như servlet container; giữ nguyên assertions thành công cho cả PUT và POST.
- Cập nhật OpenAPI về session thay đổi giữa request và ký tự mật khẩu không hợp lệ.

Kết quả xác minh trên nhánh đã đồng bộ main:

- `mvn -B -f backend/pom.xml clean verify` với JDK 21: **174 test đạt, 0 failure/error/skipped**, gồm 13 test tích hợp MySQL; tạo `backend/target/crave.war` thành công.
- `node frontend/tests/account-browser-smoke.cjs`: **36/36 kịch bản Chrome đạt**. Runner dùng JSP/JavaScript thật với API fixture; kết quả này không thay thế demo HTTP trên Tomcat.
- `node --check frontend/assets/js/account.js` và `node --check frontend/tests/account-browser-smoke.cjs`: đạt.
- `git diff --check`: đạt. Không chạy lại bộ kiểm tra HTTP/Tomcat trực tiếp trong lần chuẩn bị PR này; kết quả 87 kiểm tra ở phần 8 thuộc lần xác minh trước.

Nhánh nguồn bàn giao vẫn là `feature/account-management`, nhánh đích pull request là `main`. Pull request bàn giao phạm vi tài khoản Ngày 2 của Nguyễn Quang Vinh và chưa xác nhận Gate 2 của toàn nhóm.
