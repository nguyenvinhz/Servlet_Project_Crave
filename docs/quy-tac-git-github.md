# Quy tắc Git và GitHub của nhóm

## 1. Mục đích

Tài liệu này là quy chuẩn chung khi làm việc với repository Servlet Project Crave. Mục tiêu là:

- Không mất code hoặc ghi đè thay đổi của thành viên khác.
- Nhánh main luôn ở trạng thái build và chạy được.
- Lịch sử commit thể hiện rõ ai làm gì.
- Mỗi thay đổi đều có báo cáo, kiểm thử và Pull Request để review.
- Có thể truy vết công việc backend, frontend và API của từng thành viên.

Repository chính:

https://github.com/nguyenvinhz/Servlet_Project_Crave.git

## 2. Nguyên tắc bắt buộc

1. Không code và không push trực tiếp lên main.
2. Mỗi task dùng một branch riêng.
3. Một Pull Request chỉ giải quyết một mục tiêu chính.
4. Mỗi commit chỉ chứa một thay đổi logic có thể giải thích rõ.
5. Trước mỗi buổi code phải đồng bộ repository.
6. Trước commit phải xem lại diff và chạy kiểm tra phù hợp.
7. Mỗi commit công việc phải có một file báo cáo Markdown đi kèm.
8. Không commit credential, secret, file build hoặc dữ liệu cá nhân thật.
9. Không force-push nhánh dùng chung hoặc nhánh đang được review.
10. Không merge khi build/test thất bại hoặc còn conflict.

## 3. Tên viết tắt của thành viên

| Thành viên | Tên dùng trong branch/report |
|---|---|
| Nguyễn Quang Vinh | vinh |
| Nguyễn Minh Huân | huan |
| Ung Văn Trí | tri |
| Nguyễn Đức Phát | phat |

Không dùng dấu tiếng Việt, khoảng trắng hoặc ký tự đặc biệt trong tên branch và tên file.

## 4. Quy tắc đặt tên

### 4.1. Tên branch

Định dạng:

~~~text
<loai>/<ten-thanh-vien>/<mo-ta-ngan>
~~~

Loại branch được phép:

| Loại | Mục đích |
|---|---|
| feature | Chức năng mới |
| fix | Sửa lỗi |
| refactor | Cải tổ code nhưng không đổi hành vi |
| test | Bổ sung hoặc sửa test |
| docs | Tài liệu |
| chore | Công việc bảo trì |
| hotfix | Lỗi khẩn cấp trên main |

Ví dụ:

~~~text
feature/vinh/auth-login
feature/huan/menu-management
feature/tri/cart-voucher
feature/phat/order-payment
fix/tri/cart-total
docs/vinh/github-rules
~~~

Không dùng tên branch mơ hồ:

~~~text
vinh-code
branch-moi
test
fix-bug
final
code-moi
~~~

### 4.2. Tên trong source code

| Thành phần | Quy tắc | Ví dụ |
|---|---|---|
| Java class/interface/enum | PascalCase | OrderService, LoginServlet |
| Java package | lowercase, không gạch nối | foodoption, repository |
| Method/variable | camelCase | calculateTotal, orderId |
| Constant | UPPER_SNAKE_CASE | MAX_CART_QUANTITY |
| JSP/CSS/JS/image | kebab-case | order-detail.jsp |
| Database table/column | snake_case | order_detail, created_at |
| Markdown | kebab-case, không dấu | quy-tac-git-github.md |
| API path | lowercase, danh từ rõ nghĩa | /api/orders, /api/cart/items |

Tránh các tên như new, temp, final2, data1, test123 hoặc codeMoi.

## 5. Thiết lập Git lần đầu trên máy

Lưu ý: nội dung đặt trong dấu `<...>` ở các lệnh bên dưới là giá trị mẫu cần thay thế; không gõ nguyên dấu ngoặc nhọn khi chạy lệnh.

### 5.1. Kiểm tra Git

~~~powershell
git --version
~~~

### 5.2. Khai báo danh tính cá nhân

Mỗi người phải dùng tên thật và email đã liên kết với tài khoản GitHub của mình.

~~~powershell
git config --global user.name "Nguyen Quang Vinh"
git config --global user.email "email-da-dang-ky-github@example.com"
~~~

Kiểm tra:

~~~powershell
git config --global user.name
git config --global user.email
~~~

Không dùng chung tài khoản hoặc email của thành viên khác.

### 5.3. Clone dự án lần đầu

~~~powershell
git clone https://github.com/nguyenvinhz/Servlet_Project_Crave.git
cd Servlet_Project_Crave
git remote -v
git status
~~~

Chỉ clone một lần. Những lần làm việc sau dùng fetch/pull, không tải ZIP và không clone lại thành nhiều bản để ghép code thủ công.

## 6. Quy trình bắt buộc trước mỗi lần code

### 6.1. Kiểm tra thay đổi hiện tại

~~~powershell
git status
~~~

Nếu có file chưa commit:

- Hoàn thiện và commit nếu thay đổi đã đủ.
- Hoặc lưu tạm bằng git stash nếu thật sự cần đổi nhánh.
- Không pull hoặc chuyển branch khi chưa hiểu các thay đổi đang có.

Lưu tạm:

~~~powershell
git stash push -m "wip: mo-ta-ngan"
~~~

Khôi phục sau đó:

~~~powershell
git stash list
git stash pop
~~~

Không dùng stash như nơi lưu code lâu dài.

### 6.2. Cập nhật main mới nhất

~~~powershell
git switch main
git fetch origin --prune
git pull --ff-only origin main
~~~

Nếu git pull --ff-only báo lỗi, dừng lại và kiểm tra lịch sử; không tự dùng force hoặc reset --hard.

### 6.3. Tạo branch cho task mới

Ví dụ Nguyễn Quang Vinh làm đăng nhập:

~~~powershell
git switch -c feature/vinh/auth-login
git branch --show-current
~~~

Branch mới phải được tạo từ main vừa cập nhật.

### 6.4. Tiếp tục branch đã có

~~~powershell
git switch feature/vinh/auth-login
git pull --ff-only origin feature/vinh/auth-login
git fetch origin --prune
git merge origin/main
~~~

Nếu branch chưa từng push thì bỏ qua lệnh pull branch đó và chỉ merge origin/main.

## 7. Quy trình trong khi code

- Chỉ sửa file thuộc task đang làm.
- Không format hoặc đổi tên hàng loạt file không liên quan.
- Thường xuyên chạy git status và git diff.
- Chia công việc thành các commit nhỏ, có ý nghĩa.
- Chạy ứng dụng/test phù hợp trước khi commit.
- Trao đổi trước khi sửa file dùng chung như pom.xml, cấu hình database, layout hoặc API response chung.

Các lệnh kiểm tra:

~~~powershell
git status
git diff
git diff --check
~~~

git diff --check phải không báo trailing whitespace hoặc lỗi định dạng patch.

## 8. Quy tắc commit

### 8.1. Định dạng Conventional Commits

~~~text
<type>(<scope>): <mo-ta-ngan>
~~~

Type được phép:

| Type | Mục đích |
|---|---|
| feat | Chức năng mới |
| fix | Sửa lỗi |
| refactor | Cải tổ code, không đổi chức năng |
| test | Thêm/sửa test |
| docs | Tài liệu |
| style | Chỉ format, không đổi logic |
| chore | Bảo trì |
| build | Maven, dependency, build |
| ci | GitHub Actions hoặc CI |
| perf | Cải thiện hiệu năng |
| revert | Hoàn tác một commit |

Scope gợi ý:

~~~text
auth, user, menu, cart, order, promotion, payment,
admin, api, ui, database, config, test, docs
~~~

Ví dụ tốt:

~~~text
feat(auth): thêm đăng nhập bằng session
feat(menu): hiển thị tùy chọn món ăn
fix(cart): sửa tổng tiền khi đổi số lượng
fix(api): trả mã 401 khi chưa đăng nhập
test(order): bổ sung test tạo đơn hàng
docs(git): thêm hướng dẫn pull request
~~~

Ví dụ không được dùng:

~~~text
update code
fix bug
done
final version
code ngay 2
sua loi
~~~

### 8.2. Nội dung commit

- Dòng tiêu đề nên ngắn, rõ, mô tả việc đã hoàn thành.
- Dùng cùng một ngôn ngữ nhất quán trong cả repository.
- Không kết thúc tiêu đề bằng dấu chấm.
- Nếu cần giải thích thêm, dùng phần body để nói lý do và cách xử lý.
- Thay đổi phá vỡ tương thích phải ghi BREAKING CHANGE trong footer.
- Issue có thể được liên kết bằng Refs #12 hoặc Closes #12.

Ví dụ có body:

~~~text
feat(order): thêm tạo đơn từ giỏ hàng

Kiểm tra lại giá món và voucher tại server trước khi lưu đơn.
Bao toàn bộ thao tác tạo đơn trong một transaction.

Closes #24
~~~

## 9. Báo cáo Markdown cho mỗi commit

### 9.1. Vị trí và tên file

Mỗi commit công việc do thành viên chủ động tạo phải kèm một báo cáo tại:

~~~text
docs/reports/YYYY-MM-DD/<ten-thanh-vien>/<so-thu-tu>-<task>.md
~~~

Ví dụ:

~~~text
docs/reports/2026-10-02/vinh/01-auth-login.md
docs/reports/2026-10-02/huan/02-menu-filter.md
docs/reports/2026-10-02/tri/01-cart-voucher.md
docs/reports/2026-10-02/phat/03-order-status.md
~~~

Quy tắc:

- Số thứ tự gồm hai chữ số: 01, 02, 03.
- Tên file viết thường, không dấu, dùng dấu gạch nối.
- Báo cáo phải nằm trong cùng commit với code mà nó mô tả.
- Không ghi commit SHA vì SHA chỉ được tạo sau khi commit.
- Không tạo commit riêng chỉ để báo cáo cho commit ngay trước đó.
- Merge commit chỉ dùng để đồng bộ origin/main, merge commit do GitHub tạo, và commit chỉ sửa chính hệ thống report được miễn tạo report mới để tránh vòng lặp vô hạn.

### 9.2. Cách tạo file báo cáo

Có thể tạo bằng IDE hoặc PowerShell:

~~~powershell
New-Item -ItemType Directory -Force "docs/reports/2026-10-02/vinh"
New-Item -ItemType File "docs/reports/2026-10-02/vinh/01-auth-login.md"
~~~

Thay ngày, tên thành viên, số thứ tự và task cho đúng công việc thực tế.

### 9.3. Template báo cáo commit

Sao chép mẫu sau vào file mới:

~~~markdown
# Báo cáo: Tên công việc

- Người thực hiện:
- Ngày:
- Branch:
- Task/Issue:
- Commit dự kiến:

## Mục tiêu

Mô tả ngắn vấn đề cần giải quyết.

## Đã thực hiện

### Backend

- Các thay đổi backend.
- Ghi Không thay đổi nếu commit không liên quan.

### Frontend

- Các thay đổi JSP/CSS/JavaScript.
- Ghi Không thay đổi nếu commit không liên quan.

### API

- Endpoint hoặc contract đã thêm/sửa.
- Ghi Không thay đổi nếu commit không liên quan.

### Database

- Schema/query/dữ liệu đã thêm/sửa.
- Ghi Không thay đổi nếu commit không liên quan.

## File hoặc khu vực đã thay đổi

- backend/...
- frontend/...
- database/...

## Cách kiểm tra

- Lệnh hoặc các bước đã chạy.
- Dữ liệu test đã sử dụng.

## Kết quả kiểm tra

- Passed/Failed.
- Ghi rõ lỗi còn tồn tại nếu có.

## Ảnh hưởng và lưu ý

- Breaking change:
- Migration cần chạy:
- Cấu hình cần bổ sung:
- Giới hạn hiện tại:

## Công việc còn lại

- Không có, hoặc liệt kê TODO/blocker.
~~~

Không ghi Passed nếu chưa thật sự chạy kiểm tra.

## 10. Các bước tạo một commit an toàn

### Bước 1 — Xem thay đổi

~~~powershell
git status
git diff
~~~

### Bước 2 — Tạo/cập nhật report

Tạo report theo mục 9 và điền đúng kết quả thực tế.

### Bước 3 — Stage đúng file

Ưu tiên stage đường dẫn cụ thể:

~~~powershell
git add backend/src/main/java/com/foodordering/servlet
git add frontend/auth
git add docs/reports/2026-10-02/vinh/01-auth-login.md
~~~

Có thể dùng chế độ chọn từng phần:

~~~powershell
git add -p
~~~

Không dùng git add . một cách máy móc. Nếu dùng, bắt buộc kiểm tra lại toàn bộ staged files.

### Bước 4 — Kiểm tra staged diff

~~~powershell
git status
git diff --cached
git diff --cached --check
~~~

### Bước 5 — Commit

~~~powershell
git commit -m "feat(auth): thêm đăng nhập bằng session"
~~~

### Bước 6 — Kiểm tra commit

~~~powershell
git log -1 --stat
git status
~~~

## 11. Push branch lên GitHub

Lần push đầu tiên:

~~~powershell
git push -u origin feature/vinh/auth-login
~~~

Các lần tiếp theo:

~~~powershell
git push
~~~

Trước lần push để mở hoặc cập nhật Pull Request:

~~~powershell
git fetch origin --prune
git merge origin/main
~~~

Sau khi merge main vào branch, phải chạy lại build/test trước khi push.

Không dùng git push --force. Nếu có tình huống đặc biệt, phải trao đổi với cả nhóm trước.

## 12. Tạo GitHub Issue trước khi làm task

Nếu nhóm sử dụng Issues:

1. Mở tab Issues trên GitHub.
2. Chọn New issue.
3. Tiêu đề mô tả một công việc rõ ràng.
4. Ghi mục tiêu, phạm vi, tiêu chí hoàn thành và người phụ trách.
5. Gắn label phù hợp như backend, frontend, api, bug hoặc documentation.
6. Assign đúng thành viên.
7. Dùng số issue trong report, commit body và Pull Request.

Một issue không nên chứa nhiều chức năng không liên quan.

## 13. Hướng dẫn tạo Pull Request

### 13.1. Mở Pull Request

Sau khi push branch:

1. Mở repository trên GitHub.
2. Chọn Compare & pull request hoặc tab Pull requests → New pull request.
3. Chọn base là main.
4. Chọn compare là branch của task.
5. Kiểm tra Files changed để chắc chắn không có file ngoài phạm vi.
6. Điền tiêu đề và mô tả theo template.
7. Chọn reviewer.
8. Chọn Create pull request.

Nếu chưa hoàn thành, tạo Draft Pull Request.

### 13.2. Tiêu đề Pull Request

Dùng cùng format với Conventional Commit:

~~~text
feat(auth): hoàn thiện đăng nhập và quản lý session
~~~

### 13.3. Template mô tả Pull Request

~~~markdown
## Mục tiêu

Mô tả vấn đề và kết quả mong muốn.

## Thay đổi chính

### Backend

- ...

### Frontend

- ...

### API

- ...

### Database

- ...

## API thay đổi

- Method/path:
- Request:
- Response:
- Mã lỗi:

## Cách kiểm tra

1. ...
2. ...
3. ...

## Kết quả test

- [ ] Build thành công
- [ ] Unit test thành công
- [ ] Integration test thành công
- [ ] Đã test thủ công

## Báo cáo commit

- Link đến docs/reports/...

## Ảnh giao diện

Thêm ảnh trước/sau nếu PR thay đổi UI.

## Database/config

- Migration hoặc schema cần chạy:
- Biến cấu hình cần có:
- Không đưa giá trị secret thật vào PR.

## Checklist

- [ ] Đã đồng bộ main
- [ ] Không còn conflict
- [ ] Không có file ngoài phạm vi
- [ ] Không có credential/secret
- [ ] API và UI xử lý lỗi
- [ ] Đã tự review Files changed
- [ ] Đã cập nhật tài liệu/report
~~~

## 14. Quy tắc review và merge Pull Request

- Tác giả không tự approve PR của mình.
- Cần ít nhất một approval trước khi merge.
- Thay đổi authentication, payment hoặc database nên có hai người review nếu thời gian cho phép.
- Không merge khi checks fail, còn conflict hoặc còn comment BLOCKER.
- Tác giả phải phản hồi từng comment và yêu cầu review lại sau khi sửa.
- Reviewer kiểm tra cả logic, bảo mật, validation, UI, API, test và file report.
- Dùng merge commit để giữ lịch sử commit và sự tương ứng với từng report.
- Sau khi merge, xóa branch trên GitHub.

Quy ước comment review:

- BLOCKER: bắt buộc sửa trước khi merge.
- SUGGESTION: đề xuất cải thiện, có thể thảo luận.
- QUESTION: cần giải thích hoặc làm rõ.

## 15. Sửa code sau khi nhận review

Không tạo PR mới. Tiếp tục sửa trên cùng branch:

~~~powershell
git switch feature/vinh/auth-login
git status
~~~

Sau khi sửa:

~~~powershell
git add <cac-file-da-sua>
git add docs/reports/<duong-dan-report-moi>.md
git diff --cached
git commit -m "fix(auth): xử lý góp ý validation đăng nhập"
git push
~~~

Pull Request sẽ tự cập nhật. Mỗi commit sửa review vẫn cần report tương ứng.

## 16. Cập nhật branch và xử lý conflict

### 16.1. Lấy main mới nhất vào feature branch

~~~powershell
git fetch origin --prune
git switch feature/vinh/auth-login
git merge origin/main
git status
~~~

### 16.2. Khi có conflict

1. Mở từng file được Git đánh dấu.
2. Tìm các marker conflict.
3. Trao đổi với tác giả liên quan nếu conflict chạm code của họ.
4. Giữ đúng logic cần thiết từ cả hai phía.
5. Xóa marker conflict.
6. Stage file đã xử lý.
7. Hoàn tất merge và chạy lại test.

~~~powershell
git add <file-da-xu-ly>
git status
git commit
git push
~~~

Nếu chưa muốn tiếp tục merge:

~~~powershell
git merge --abort
~~~

Không chọn toàn bộ ours hoặc theirs một cách máy móc. Không xóa code của người khác chỉ để hết conflict.

## 17. Sau khi Pull Request được merge

~~~powershell
git switch main
git fetch origin --prune
git pull --ff-only origin main
git branch -d feature/vinh/auth-login
~~~

Nếu GitHub chưa tự xóa remote branch, xóa bằng giao diện GitHub sau khi chắc chắn PR đã merge.

Task tiếp theo phải tạo branch mới từ main mới nhất; không tái sử dụng branch cũ.

## 18. Những thứ không được commit

- Password database, token, API key, private key hoặc secret.
- File .env chứa giá trị thật.
- Thư mục target hoặc artifact build.
- Log runtime.
- Metadata IDE cá nhân không cần thiết.
- File backup database hoặc dữ liệu người dùng thật.
- File tạm, file copy, file có tên final2 hoặc temp.
- Dependency/cache tải về máy.
- Source placeholder rỗng không dùng đến.

Nếu lỡ commit secret:

1. Không chỉ xóa file rồi coi như an toàn.
2. Báo ngay cho cả nhóm.
3. Thu hồi/đổi secret.
4. Nhờ người phụ trách xử lý lịch sử Git nếu cần.

## 19. Các hành động bị cấm

- Push trực tiếp lên main.
- Force-push branch đang chia sẻ hoặc review.
- Dùng reset --hard khi chưa hiểu rõ hậu quả.
- Rewrite lịch sử branch đã push bằng rebase/amend mà chưa thống nhất.
- Merge PR của mình khi chưa có approval.
- Merge khi test fail hoặc còn conflict.
- Commit thay đổi không liên quan vào cùng task.
- Xóa branch/commit của thành viên khác.
- Ghi đè conflict bằng cách bỏ toàn bộ code của người khác.
- Dùng chung tài khoản GitHub.

## 20. Sửa một số lỗi Git thường gặp

### Stage nhầm file

~~~powershell
git restore --staged <duong-dan-file>
~~~

Lệnh này bỏ file khỏi staged area nhưng giữ nội dung đang sửa.

### Muốn bỏ thay đổi chưa commit

~~~powershell
git restore <duong-dan-file>
~~~

Cảnh báo: lệnh này xóa thay đổi chưa commit của file. Chỉ chạy khi chắc chắn không cần phần code đó.

### Sai commit message và chưa push

~~~powershell
git commit --amend
~~~

Nếu commit đã push hoặc đã có người khác dựa vào, không amend; tạo commit sửa mới.

### Lỡ code trên main

- Không push.
- Dừng thay đổi tiếp.
- Báo người phụ trách Git.
- Tạo branch bảo toàn công việc trước khi chỉnh lại main.

### Pull bị từ chối hoặc lịch sử diverged

- Không force.
- Chạy git status và git log để kiểm tra.
- Nhờ thành viên phụ trách review trước khi dùng lệnh thay đổi lịch sử.

## 21. Checklist nhanh trước mỗi buổi code

- [ ] git status sạch hoặc đã hiểu toàn bộ thay đổi.
- [ ] Đã switch main.
- [ ] Đã fetch origin --prune.
- [ ] Đã pull --ff-only origin main.
- [ ] Đã tạo/switch đúng branch.
- [ ] Branch có đúng type, tên thành viên và task.

## 22. Checklist trước mỗi commit

- [ ] Commit chỉ chứa một thay đổi logic.
- [ ] Đã chạy build/test phù hợp.
- [ ] Đã tạo report Markdown.
- [ ] Đã kiểm tra git diff.
- [ ] Đã stage đúng file.
- [ ] Đã kiểm tra git diff --cached.
- [ ] Commit message đúng Conventional Commits.
- [ ] Không có secret, artifact hoặc file ngoài phạm vi.

## 23. Checklist trước Pull Request

- [ ] Đã đồng bộ origin/main vào branch.
- [ ] Build/test vẫn thành công sau đồng bộ.
- [ ] Đã push toàn bộ commit.
- [ ] PR base là main và compare đúng branch.
- [ ] Tiêu đề PR đúng convention.
- [ ] Mô tả có backend, frontend, API, database và cách test.
- [ ] Có link đến các report.
- [ ] Có screenshot nếu thay đổi UI.
- [ ] Đã tự review Files changed.
- [ ] Đã chọn reviewer.

## 24. Cấu hình GitHub đề xuất cho main

Người quản lý repository nên bật branch protection:

- Require a pull request before merging.
- Require ít nhất một approval.
- Require conversation resolution.
- Require status checks nếu đã có CI.
- Không cho force push.
- Không cho xóa main.
- Hạn chế bypass rule.

Những quy tắc này giúp main luôn ổn định và bảo vệ lịch sử đóng góp của cả nhóm.
