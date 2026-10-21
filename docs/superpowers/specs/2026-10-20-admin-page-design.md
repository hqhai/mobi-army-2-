# Trang quản trị Mobi Army 2 LAN — Thiết kế

Ngày: 20/10/2026. Trạng thái: đã triển khai.

Khác với bản thiết kế khi làm thật (vì một phiên khác cùng lúc thêm "Skin Thor" vào danh mục trang bị trong bộ nhớ):

- Lưu danh mục **không nạp lại** cả danh mục từ database (nạp lại sẽ làm mất Skin Thor). Thay vào đó cập nhật trực tiếp đúng món trong bộ nhớ: `Glass.entrys`, `Equip.entrys`, `ShopEquipment.entrys`, `Item.entrys`, `LinhTinh.entrys`, `ShopLinhTinh.entrys`, cùng bản sao trong người chơi như mục 8.
- Số phiên bản dữ liệu: `ControlHandler` vẫn giữ số gốc (hiện là 13, do phiên kia đặt). `SessionHandler.dataInfo` cộng thêm `Server.dataRevision` (số lần admin sửa nhân vật/trang bị, lưu ở `lan_setting.data_revision`) và giữ kết quả trong 1..127.

## 1. Mục đích

Chủ server LAN (admin) cần quản lý người chơi và dữ liệu game qua web, thay vì sửa database bằng tay. Đây là bước đầu của một trang "cấu hình mọi thứ". Bản này gồm:

- Cộng xu, cộng lượng, tăng cấp nhân vật cho người chơi.
- Xem và sửa nhân vật cùng đồ (trang bị trong shop, vật phẩm, đồ đặc biệt), dữ liệu đọc từ database.

Người dùng là một admin duy nhất (chủ server), dùng trong mạng LAN công ty, thường thao tác trong lúc anh em đang chơi.

## 2. Bối cảnh kỹ thuật quyết định thiết kế

Các điểm sau là sự thật trong code hiện tại (đã kiểm chứng). Thiết kế phải tuân theo:

1. **Server giữ toàn bộ người chơi trong bộ nhớ và ghi đè database.** `SessionManager.loadUser()` nạp mọi tài khoản lúc khởi động. `Server.update()` gọi `SessionManager.saveUsers()` mỗi 30 giây và khi có `Server.requestSave()`. Server cũng lưu khi người chơi thoát và khi dừng. Mỗi lần lưu là `DELETE` rồi `INSERT` toàn bộ `user_`, `user_glass`, `user_item`, `user_equip`... từ đối tượng `User` trong bộ nhớ. **Hệ quả:** sửa thẳng database dữ liệu người chơi sẽ bị ghi đè trong vòng 30 giây. Mọi thay đổi người chơi phải sửa đối tượng `User` trong bộ nhớ, rồi gọi `Server.requestSave()`.
2. **Logic game chạy trên một luồng.** Tin nhắn client được xếp vào `Server.CMD` và xử lý tuần tự trong `Server.update()` (`synchronized`, vòng lặp 10 ms). Thao tác admin lên `User` hoặc danh mục phải chạy trên luồng này.
3. **Tài khoản đăng ký sau khi server khởi động** chưa có trong bộ nhớ cho tới lần đăng nhập đầu. `ControlHandler` (case 1) tạo chúng bằng `new User(id, username)` rồi `SessionManager.addUser(user)`, với giá trị mặc định: xu 1000, lượng 1000, mọi nhân vật cấp 1.
4. **Danh mục nạp lúc khởi động, người chơi giữ bản sao:**

   | Danh mục | Bảng | Nạp vào | Bản sao trong người chơi | Client lấy dữ liệu từ đâu |
   |---|---|---|---|---|
   | Nhân vật | `glass` (10) | `Glass.entrys` | `user.glass[i]` (giá, `att`) | Giá: lúc đăng nhập (`loadInfoAll`: `xu/1000`, `luong` dạng `short`). `att`: cache `dataInfo` |
   | Trang bị shop | `shop_equipment` (340) | `ShopEquipment.entrys` (bản sao của `Equip` + giá, chỉ số từ `shop_equipment`) | Đồ đã mua `user.equips`, dựng lại từ bảng **`equip`** khi khởi động (giá lấy từ `equip`, chỉ số lấy từ `user_equip`) | Tên, giá, hạn, cấp: mỗi lần mở shop (`equipShop`). **Chỉ số trong shop: cache `dataInfo` dựng từ bảng `equip`**. Chỉ số đồ trong túi: server gửi từng món (`updateRuong`) |
   | Vật phẩm | `item` (36) | `Item.entrys` | `user.items` (giá khi mua lấy từ bản sao) | Giá: lúc đăng nhập (`loadInfoAll`) |
   | Đồ đặc biệt | `linhtinh` (69) | `LinhTinh.entrys`, rồi `ShopLinhTinh.entrys` (bản sao) | `user.linhtinhs` (giá bán lại = `xu/2` của bản sao; mua thêm thì gộp vào bản sao) | Mỗi lần mở shop (`shopLinhTinh(ShopLinhTinh.entrys)`) |

5. **`shop_equipment` và `equip` khớp 1–1** (mỗi dòng shop trỏ đúng một dòng `equip` theo `glassID`, `equipId`, không dòng nào dùng chung) và hiện có giá, chỉ số, tên giống hệt nhau.
6. **Giá đồ đã sở hữu đọc từ bảng `equip`:** bán lại (`Equip.calculatePrice`: `xu/2` hoặc `luong×500`), gia hạn (`Equip.renewalPrice`: `xu` hoặc `luong×1000`, cộng 5% giá ngọc). Giá ngọc khảm (`Equip.priceSlot`) đọc trực tiếp `LinhTinh.entrys`.
7. **Cache dữ liệu của client:** `dataInfo` (sát thương nhân vật, dữ liệu và chỉ số trang bị từ bảng `equip`) được client lưu kèm một số phiên bản. Mỗi lần đăng nhập, client gửi số phiên bản đang có; server (`ControlHandler`, `dataInfo(12, version)`) gửi lại dữ liệu nếu **khác** số của server. Hiện số này cố định là 12, nên client không bao giờ tải lại.
8. **Chỉ số nhân vật thực sự có tác dụng:** chỉ `xu`, `luong` (giá mở khóa) và `att` (sát thương, tính phía server qua `Glass.createAbility()` khi vào trận). `friction`, `distance`, `bullet` không được server dùng. `angle` chỉ bot dùng (bot đang tắt).
9. **Cấp độ** tính từ exp qua bảng `exp`. Cấp 1–140 liên tục, sau đó chỉ có các mốc 150, 160, …, 250, 254. `Exp.getLevelExp(exp)` trả về `level` của dòng đầu tiên có `exp > exp hiện tại`. `Glass.addExp(add, flag)` cập nhật cấp, cộng `point += 3 × (số cấp tăng)` và báo cho client nếu `flag`.
10. `User.addXu` và `User.addLuong` chỉ chặn tràn trên, không chặn âm. Với `flag = true`, chúng gửi gói tin qua `this.session` và sẽ lỗi `NullPointerException` nếu người chơi offline.

## 3. Mục tiêu

1. Đăng nhập admin, đăng xuất, đổi mật khẩu.
2. **Người chơi:** xem danh sách, cộng/trừ xu, cộng/trừ lượng, tăng cấp nhân vật. Có hiệu lực ngay trên server đang chạy và được lưu bền.
3. **Danh mục:** xem và sửa nhân vật, trang bị shop, vật phẩm, đồ đặc biệt. Ghi vào database và có hiệu lực mà không cần khởi động lại server. Người chơi nhìn thấy đúng số mới (có thể cần đăng nhập lại, xem mục 8).

## 4. Không làm ở bản này

- Thêm hoặc xóa món đồ, nhân vật (hình ảnh và sprite nằm trong client).
- Tặng đồ cho người chơi. Sửa chỉ số đồ người chơi đã mua (đồ đã mua giữ chỉ số cũ).
- Sửa tên đồ, cấp yêu cầu, hạn dùng, tọa độ sprite.
- Nhiều tài khoản admin, phân quyền. Khóa/xóa tài khoản người chơi, đặt lại mật khẩu người chơi.
- Các mục "cấu hình" khác (sẽ làm sau).

## 5. Những điều không được xảy ra

- Thay đổi của admin bị server ghi đè hoặc mất.
- Game server treo, lỗi, hay người chơi bị ngắt kết nối vì admin thao tác.
- Người không biết mật khẩu admin thao tác được, kể cả thông qua một trang web khác gửi request hộ (CSRF).
- Mật khẩu admin lưu dạng rõ.
- Số xu/lượng âm, hoặc vượt giới hạn kiểu dữ liệu làm hỏng gói tin gửi client.
- Sửa giá tạo ra lỗ hổng mua rẻ bán đắt.

## 6. Ràng buộc

- Java 21 (biên dịch `--release 20`). Không thêm thư viện, chỉ dùng thứ đã có: HTTP server của JDK (đang dùng trong `LanWeb`), Gson, jBCrypt, HikariCP qua `Server.dbManager`.
- Chạy chung tiến trình với game server, cùng cổng 8123 với trang đăng ký. Đường dẫn `/admin/`.
- Truy cập từ mọi máy trong LAN, bảo vệ bằng mật khẩu.
- Theo lệ của repo: không commit khi chưa được yêu cầu, kiểm thử cục bộ không đưa vào repo.
- Một phiên làm việc khác đang sửa code client: không đụng vào `client/`.

## 7. Người dùng đã quyết

- Giao diện **kiểu B**: bảng điều khiển rộng, thanh trên cùng, menu bên trái (Người chơi, Nhân vật, Trang bị, Vật phẩm, Đồ đặc biệt), bảng dày, ô tìm kiếm. Ở mục Người chơi, chọn một người thì mở khung thao tác bên phải.
- Tài khoản admin mặc định: **admin / admin123**.
- Chức năng như mục 3. Đã xác nhận: chỉ sửa món có sẵn; đồ đã mua giữ nguyên chỉ số; chưa có tặng đồ; sửa giá và chỉ số có hiệu lực từ lần mở shop tiếp theo.

## 8. Hành vi

### 8.1 Đăng nhập (`/admin/`)

- Chưa đăng nhập thì thấy form đăng nhập. Đăng nhập đúng thì vào bảng điều khiển.
- Tài khoản admin lưu trong bảng mới `admin_account (username VARCHAR(50) PRIMARY KEY, password VARCHAR(255) NOT NULL)`. Lúc khởi động, server tự tạo bảng nếu chưa có và gieo `admin` / BCrypt(`admin123`) khi bảng trống.
- Phiên: token ngẫu nhiên 32 byte (`SecureRandom`), lưu trong bộ nhớ, cookie `HttpOnly; SameSite=Strict; Path=/`, hết hạn sau 12 giờ không hoạt động. Khởi động lại server thì phải đăng nhập lại.
- **Đổi mật khẩu:** yêu cầu mật khẩu cũ, mật khẩu mới tối thiểu 6 ký tự. Khi vẫn dùng mật khẩu mặc định, bảng điều khiển hiện dải nhắc đổi.
- **Chống dò mật khẩu:** sai 5 lần liên tiếp từ một IP thì khóa IP đó 5 phút.
- Mọi API admin (trừ đăng nhập) trả 401 nếu không có phiên hợp lệ. Request thay đổi dữ liệu phải có `Content-Type: application/json`, và header `Origin` (nếu có) phải trùng host của request, nếu không thì trả 403.

### 8.2 Người chơi

- Danh sách gồm mọi dòng trong bảng `user`. Người đã nạp trong bộ nhớ: lấy số liệu thật (online nếu `user.session != null`, xu, lượng, cúp, nhân vật đang chọn và cấp, danh sách nhân vật đã mở cùng cấp). Người chưa nạp: hiện giá trị mặc định, ghi chú "chưa vào game".
- Có ô tìm theo tên. Chọn một người thì khung bên phải hiện:
  - **Cộng xu / cộng lượng:** số nguyên, số âm để trừ. Kết quả kẹp trong [0, 2.147.483.647]. Báo lại số trước và sau.
  - **Tăng cấp:** chọn một nhân vật **đã mở** của người chơi và một cấp mục tiêu trong các mốc của bảng `exp`, lớn hơn cấp hiện tại. Đặt exp bằng ngưỡng của mốc đó (exp của dòng ngay trước mốc trong bảng; cấp 1 là 0) bằng `Glass.addExp(delta, online)`, nên người chơi được cộng điểm tiềm năng như lên cấp bình thường.
- Mọi thao tác chạy **trên luồng game**. Nếu người chơi chưa nạp, tạo `User` như `ControlHandler` case 1. Áp dụng với `flag = online` để người đang online thấy ngay trong game. Sau đó gọi `Server.requestSave()` và trả về trạng thái mới.
- Ghi một dòng log cho mỗi thao tác, ví dụ: `Admin: +10000 xu for player2 (1000 -> 11000) from 192.168.1.20`.

### 8.3 Nhân vật

- Bảng 10 nhân vật. **Sửa được:** giá mở khóa xu, giá mở khóa lượng, sát thương (`att`). **Chỉ xem:** ma sát, góc, tầm, đạn, kèm chú thích "server không dùng".
- Ràng buộc: giá xu là bội số của 1000, từ 0 đến 32.767.000 (client nhận `xu/1000` dạng `short`); giá lượng 0–32.767; sát thương 1–32.767.
- Bấm Lưu thì:
  1. `UPDATE glass`.
  2. Trên luồng game: nạp lại `Glass.entrys`, rồi cập nhật `xu`, `luong`, `att` trong `user.glass[id]` của mọi `User` đang nạp (không đụng cấp, exp, điểm, trang bị, trạng thái mở).
  3. Tăng số phiên bản dữ liệu (mục 8.7).
- Server tính giá mới ngay và tính sát thương mới từ trận sau. Client hiện giá mới và sát thương mới từ lần đăng nhập sau.

### 8.4 Trang bị (shop)

- Bảng 340 món trong `shop_equipment`, ghép với `equip` để hiện loại (`type`), cấp yêu cầu, hạn dùng (chỉ xem). Lọc theo nhân vật và loại, tìm theo tên.
- **Sửa được:** giá xu, giá lượng (−1 = không bán theo cách đó), 5 chỉ số cộng `inv_ability` và 5 chỉ số % `inv_percen` (thứ tự: máu, sức mạnh, phòng thủ, may mắn, đồng đội; mỗi giá trị −128..127).
- Giá: −1 hoặc 0..2.147.483.647. Cảnh báo (vẫn cho lưu) nếu cả hai giá đều −1, vì khi đó món không mua được.
- Bấm Lưu thì:
  1. Trong **một transaction**, ghi cùng giá và chỉ số vào cả `shop_equipment` và dòng `equip` tương ứng (giữ hai bảng khớp nhau như hiện tại).
  2. Trên luồng game: nạp lại `Equip` (`Equip.loadEquip()`, gồm cả `equipsByGlassIDAndType`), rồi `ShopEquipment.loadShopEquipment()`. Sau đó cập nhật **giá** (không đụng chỉ số) của các bản sao món đó trong `user.equips` của mọi `User` đang nạp.
  3. Tăng số phiên bản dữ liệu (mục 8.7).
- Kết quả:
  - Người chơi mua lần mở shop tiếp theo thì trả giá mới, nhận chỉ số mới.
  - Shop hiện chỉ số mới sau khi người chơi đăng nhập lại (client tải lại cache).
  - Đồ đã mua giữ chỉ số cũ (túi đồ vẫn hiện đúng chỉ số riêng của món), nhưng bán lại và gia hạn tính theo giá hiện tại.

### 8.5 Vật phẩm

- Bảng 36 món trong `item`. **Sửa được:** giá xu, giá lượng (−1 = không bán theo cách đó).
- Bấm Lưu thì: `UPDATE item`, rồi trên luồng game nạp lại `Item.entrys` và cập nhật `xu`, `luong` trong bản sao `user.items` của mọi `User` đang nạp. Server tính tiền theo giá mới ngay. Client hiện giá mới từ lần đăng nhập sau.

### 8.6 Đồ đặc biệt

- Bảng 69 món trong `linhtinh` (tên, mô tả chỉ xem). **Sửa được:** giá xu, giá lượng (−1 = không bán theo cách đó; cột `luong` là `smallint`, nên 0–32.767).
- Bấm Lưu thì: `UPDATE linhtinh`, rồi trên luồng game nạp lại `LinhTinh.entrys`, rồi `ShopLinhTinh.entrys`, và cập nhật `xu`, `luong` trong bản sao `user.linhtinhs` của mọi `User` đang nạp.
- Có hiệu lực từ lần mở shop tiếp theo. Giá bán lại ngọc đang có, cùng phí tháo ngọc (25%) và phí gia hạn (5% giá ngọc khảm), đổi theo giá mới ngay. Giao diện ghi chú điều này.

### 8.7 Số phiên bản dữ liệu (để client tải lại cache)

- Thay số 12 cố định trong `ControlHandler` bằng một số phiên bản lưu trong bảng mới `lan_setting (name VARCHAR(50) PRIMARY KEY, value VARCHAR(255) NOT NULL)`, khóa `data_version`, giá trị ban đầu 12 (giữ nguyên hành vi với client hiện tại).
- Mỗi lần lưu nhân vật hoặc trang bị thì tăng số này (vòng trong khoảng của `byte`, bỏ qua giá trị hiện tại). Vì client tải lại khi số **khác** số nó đang giữ, mọi client sẽ tải lại dữ liệu đúng một lần ở lần đăng nhập kế tiếp. Số được lưu bền nên không trở về 12 sau khi khởi động lại.
- Bảng `lan_setting` cũng là chỗ chứa các cấu hình sau này.

### 8.8 Lỗi chung

- Dữ liệu sai: 400, thông báo tiếng Việt chỉ rõ ô nào sai. Không tìm thấy: 404. Lỗi database hoặc luồng game không phản hồi trong 5 giây: 500/503 với thông báo chung, chi tiết chỉ ghi log server.
- Nếu ghi database thất bại thì không nạp lại bộ nhớ và không tăng số phiên bản.
- Giao diện báo thành công hoặc thất bại ngay tại dòng/khung vừa thao tác, và cập nhật số liệu hiển thị theo phản hồi của server.

## 9. Kiến trúc (hướng dẫn cho người làm)

- `web/admin/`: trang tĩnh (`index.html`, CSS, JS), không dùng script/style inline (CSP của `LanWeb` chỉ cho `'self'`). Gọi API JSON.
- `scripts/`: lớp xử lý `/api/admin/*` (phiên, đăng nhập, người chơi, danh mục), đăng ký vào `LanWeb`. `LanServer` nối các phần lại và tạo các bảng `admin_account`, `lan_setting` nếu chưa có.
- `server/src/mobiarmy/server/`: chỉ hai chỗ cần sửa:
  - `Server.java`: thêm cách chạy một tác vụ trên luồng game và chờ kết quả có timeout (ví dụ một loại `Command` mới trong `Server.update()`).
  - `ControlHandler.java`: dùng số phiên bản dữ liệu thay cho số 12 cố định.

## 10. Kiểm thử

Theo cách đã làm với trang đăng ký: bộ test cục bộ (không đưa vào repo) chạy trên database thử nghiệm `army_webtest`, viết test trước rồi mới viết code. Phải có test cho:

- Đăng nhập đúng/sai, khóa sau 5 lần sai, API không có phiên trả 401, Origin lạ trả 403, đổi mật khẩu.
- Cộng/trừ xu và lượng cho người online/offline/chưa nạp: không âm, có lưu xuống database sau khi server lưu.
- Tăng cấp cộng điểm đúng, cấp không hợp lệ bị từ chối.
- Từng loại danh mục có hiệu lực đúng như mục 8: bản sao trong người chơi được cập nhật, hai bảng trang bị khớp nhau, số phiên bản tăng.
- Không tạo được lỗ hổng mua rẻ bán đắt sau khi hạ giá.

Thêm một kiểm tra end-to-end trên `LanServer` thật ở cổng phụ, và kiểm tra giao diện bằng Edge headless.

## 11. Quyết định của người làm (người dùng có thể đổi)

- Có đổi mật khẩu admin, kèm dải nhắc khi còn dùng mật khẩu mặc định.
- Khóa IP 5 phút sau 5 lần đăng nhập sai.
- Cộng/trừ cho phép số âm, kẹp ở 0. Chỉ tăng cấp cho nhân vật đã mở, chỉ chọn mốc có trong bảng `exp`.
- Ma sát, góc, tầm, đạn của nhân vật chỉ xem (server không dùng).
- Để shop hiện chỉ số mới: ghi cả bảng `equip` và đổi số phiên bản dữ liệu. Hệ quả: client tải lại dữ liệu đồ một lần ở lần đăng nhập sau khi sửa.
- Đồ đã sở hữu bán lại và gia hạn theo **giá hiện tại** (chỉ số thì giữ nguyên), để không ai mua rẻ bán đắt được.

## 12. Để người làm tự quyết

Tên lớp và chia file, đường dẫn API chi tiết, cấu trúc JSON, bố cục CSS cụ thể, cách cuộn hay phân trang bảng dài, cách viết log.
