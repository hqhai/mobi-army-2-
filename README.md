# MobiArmy 2 LAN

Source gộp client Java/LibGDX, server Java/MariaDB và script vận hành bản LAN.

## Cấu trúc

- `client/`: source client upstream cùng các sửa lỗi desktop.
- `server/`: source, thư viện và tài nguyên server.
- `server/army.sql`: schema và dữ liệu game mặc định; **không có tài khoản/tiến trình người chơi**.
- `desktop-build/`: cấu hình Gradle để build desktop từ `client/`.
- `scripts/`: build, chạy/dừng server, tạo tài khoản và cấu hình LAN trên Windows.
- `web/`: trang đăng ký tài khoản, trang nhân vật cho người chơi `web/nhan-vat/` và trang quản trị `web/admin/` (HTML/CSS/JS) do server phục vụ ở cổng 8123.
- `docs/PHIM-TAT-GAME.md`: bảng phím điều khiển hiện tại.

Không chứa Java/MariaDB portable, file credential, database vận hành, log, cache đăng nhập, bản build hoặc file kiểm thử.

## Yêu cầu

- Java **21** (server biên dịch với `--release 20`).
- MariaDB **11.4**; tạo database `army`, nhập `server/army.sql`.
- Gradle wrapper trong `client/`; build lần đầu cần Internet tải dependency.
- Game nghe TCP **8122**, trang đăng ký TCP **8123**. Database chỉ nên nghe loopback.

## Build

### Windows

Đặt JDK trong `tools/jdk-*/`, sau đó chạy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Build.ps1
```

Server classes sinh trong `runtime/server-classes`; client trong `desktop-build/build/install/army2-lan-desktop`.

Các script `Start-Server.cmd` / `Stop-Server.cmd` yêu cầu MariaDB portable trong `tools/mariadb-*-winx64`, database tại `runtime/database`, cấu hình `my.ini` trỏ đúng datadir/port 3307 và credential DPAPI tại `runtime/db-credential.xml`. Đây là script vận hành, không tự cài database.

Sau khi DevOps tạo database, đặt mật khẩu root trên máy đích và nhập schema, lưu credential bằng tài khoản Windows vận hành:

```powershell
New-Item -ItemType Directory -Force runtime,logs | Out-Null
Get-Credential -UserName root -Message 'MariaDB credential' |
    Export-Clixml .\runtime\db-credential.xml
```

Chạy Start/Stop/Create-Account bằng cùng tài khoản Windows đó. Người chơi tự tạo tài khoản ở [trang đăng ký](#trang-đăng-ký-tài-khoản); admin vẫn có thể dùng `Create-Account.cmd`. Không có tài khoản/mật khẩu mặc định trong repo. `Start-Client.cmd` mở game local, `Enable-LAN.cmd` mở firewall cho TCP 8122 và 8123 (Administrator; chạy lại nếu trước đó chỉ mở 8122).

### Linux — lệnh tham khảo, chưa nghiệm thu trên Linux

Cài Java 21 và MariaDB từ package manager; tạo/import database và tài khoản DB trước.

```bash
mkdir -p runtime/server-classes
find server/src -name '*.java' > runtime/server-sources.txt
find scripts -maxdepth 1 -name '*.java' >> runtime/server-sources.txt
javac -encoding UTF-8 --release 20 -cp 'server/lib/*' \
  -d runtime/server-classes @runtime/server-sources.txt
bash client/gradlew -p desktop-build --no-daemon installDist
```

Chạy server từ thư mục `server` để đọc đúng `res/`. Cấp biến `ARMY_DB_PASSWORD` qua môi trường vận hành bảo mật:

```bash
cd server
java -Djava.awt.headless=true \
  -Darmy.db.url=jdbc:mysql://127.0.0.1:3306/army \
  -Darmy.db.user=army -Darmy.port=8122 -Darmy.bot.count=0 \
  -cp '../runtime/server-classes:lib/*' LanServer
```

DevOps cần cấu hình service, firewall và sao lưu riêng trên Linux. Chưa có Docker image/systemd unit trong repo.

## Trang đăng ký tài khoản

`LanServer` chạy kèm một trang web nhỏ (HTTP server có sẵn trong JDK, không thêm thư viện) để người chơi tự tạo tài khoản, giao diện theo phong cách trang chủ Mobi Army 2:

- Địa chỉ `http://<IP-máy-chủ>:8123/`; `Start-Server.cmd` in sẵn địa chỉ sau khi khởi động.
- Tên đăng nhập 3–20 chữ cái không dấu hoặc chữ số, lưu bằng chữ thường; mật khẩu 6–30 chữ cái không dấu hoặc chữ số, vì ô nhập trong game chỉ gõ được chữ và số. Tên trùng (không phân biệt hoa/thường) bị từ chối. Mật khẩu lưu BCrypt như `Create-Account`.
- Tài khoản dùng được ngay, không cần khởi động lại server.
- Muốn trang hiện nút tải game PC, đặt bộ client vào `web/downloads/MobiArmy2-PC.zip` (thư mục này không đưa vào git).
- Tùy chọn JVM: `-Darmy.web.port` (mặc định 8123, `0` để tắt), `-Darmy.web.root` (mặc định `../web`, tính từ thư mục `server`).
- Mã nguồn: `scripts/LanWeb.java` (HTTP), `scripts/LanAccounts.java` (kiểm tra và ghi tài khoản), giao diện trong `web/`. Hình ảnh lấy từ tài nguyên game trong repo.

## Trang nhân vật (cho người chơi)

- Địa chỉ `http://<IP-máy-chủ>:8123/nhan-vat/` (tab "Nhân Vật" trên trang chủ). Người chơi đăng nhập bằng tài khoản game, trên giao diện cùng phong cách trang chủ.
- Làm được trên web thay vì trong game: chọn nhân vật đã mở, cộng điểm tiềm năng, xem rương đồ, mặc/tháo trang bị (đúng nhân vật, đủ cấp, còn hạn), mua trang bị, vật phẩm, đồ đặc biệt bằng xu hoặc lượng (có hộp xác nhận trước khi mua).
- Mọi thao tác chạy trên game server đang chạy, theo đúng luật của game (giá, giới hạn 99 vật phẩm mỗi loại, Skin Thor chỉ mua một lần). Người đang online thấy thay đổi ngay trong game. Khi người chơi đang ở phòng chờ hoặc trong trận thì web tạm không cho mua/đổi đồ, giống trong game. Rương giới hạn 120 món.
- Sai mật khẩu 5 lần liên tiếp thì IP đó bị khóa 5 phút.
- Ảnh trang bị cắt từ `server/res/bigImage*.png`, icon đồ đặc biệt từ `server/res/icon/item/`, phục vụ qua đường `/game-res/` (chỉ hai loại file này).
- Mã nguồn: `scripts/LanPlayer.java` (API, đăng nhập), `scripts/LanPlayerGame.java` (thao tác trên dữ liệu game), `scripts/LanApi.java` (phần dùng chung với trang quản trị), giao diện `web/nhan-vat/`.

## Trang quản trị

- Địa chỉ `http://<IP-máy-chủ>:8123/admin/`. Tài khoản mặc định **admin / admin123**: đổi ngay trong trang (nút "Đổi mật khẩu"). Sai mật khẩu 5 lần liên tiếp thì IP đó bị khóa 5 phút.
- **Người chơi:** cộng/trừ xu, lượng (không xuống dưới 0), tăng cấp nhân vật (cộng 3 điểm tiềm năng mỗi cấp như trong game). Thay đổi áp dụng thẳng vào game server đang chạy, người đang online thấy ngay, rồi server tự lưu xuống database. Đừng sửa dữ liệu người chơi trực tiếp trong database khi server đang chạy: server ghi đè chúng mỗi 30 giây.
- **Đặt lại mật khẩu** người chơi (gõ hoặc bấm "Ngẫu nhiên", rồi báo cho người chơi) và **đổi tên** (đổi cả tên đăng nhập lẫn tên trong game, lưu chữ thường, không trùng tên người khác). Cả hai theo luật của trang đăng ký và đăng xuất người chơi khỏi trang Nhân vật trên web. Người đang online trong game thấy tên mới sau khi đăng nhập lại.
- **Nhân vật, trang bị, vật phẩm, đồ đặc biệt:** sửa giá và chỉ số. Lưu vào database và có hiệu lực ngay, không cần khởi động lại: shop dùng giá mới từ lần mở tiếp theo; sau khi sửa nhân vật hoặc trang bị, client tự tải lại dữ liệu đồ ở lần đăng nhập sau. Đồ đã mua giữ chỉ số cũ nhưng bán lại/gia hạn theo giá mới. Ma sát, góc, tầm, đạn của nhân vật chỉ để xem vì server không dùng.
- Server tự tạo hai bảng: `admin_account` (mật khẩu BCrypt) và `lan_setting` (khóa `data_revision`, số lần sửa dữ liệu để client tải lại cache).
- Quên mật khẩu admin: xóa các dòng trong `admin_account` rồi khởi động lại server, tài khoản admin/admin123 sẽ được tạo lại.
- Mã nguồn: `scripts/LanAdmin.java` (API, đăng nhập, phiên), `scripts/LanGameAdmin.java` (thao tác trên dữ liệu game, chạy trên luồng game qua `Server.callOnGameThread`), giao diện `web/admin/`. Thiết kế: `docs/superpowers/specs/2026-10-20-admin-page-design.md`.

## Thay đổi bản LAN

- Cấu hình địa chỉ server qua `army.host` / `army.port`, chạy desktop bằng Java portable.
- Thêm trang đăng ký tài khoản trên web (cổng 8123) để người chơi không phải nhờ admin tạo tài khoản.
- Thêm trang quản trị `/admin/`: cộng xu/lượng, tăng cấp cho người chơi; sửa giá và chỉ số nhân vật, đồ.
- Thêm trang nhân vật `/nhan-vat/`: người chơi mua đồ, mặc đồ, cộng điểm trên web.
- Sửa lưu dữ liệu, kiểm tra giao dịch, quyền chủ phòng và vòng đời trận.
- Quy tắc cược đã thống nhất: thắng +W, thua -W, hòa 0, rời trận -W; boss W=100.
- Thêm luyện tập riêng từng session: vào map, bắn, thoát/vào lại.
- Sửa treo nhập liệu, lỗi resize, popup ping sai và khóa bắn sau lượt luyện tập đầu.
- Phím desktop: ←/→ hoặc A/D di chuyển; ↑/↓ hoặc W/S nhấn nhanh 1°, giữ để chỉnh góc liên tục; giữ/thả Enter căn lực/bắn, Esc menu, Space kỹ năng khi đủ điều kiện, Q túi vật phẩm, 1–8 dùng ô tương ứng.
- R bật/tắt camera tự do; trong chế độ này WASD/mũi tên di chuyển khung nhìn. Chuột desktop không kéo đổi góc bắn nữa.

Kiểm thử trên Windows: 90 kiểm tra giữ/thả phím, WASD, camera và xử lý chuột/cảm ứng trên client LibGDX. Các kiểm tra Thor/Apache và điều khiển nằm trong `scripts/tests/`; chạy `scripts/tests/Run-Thor-Checks.ps1`. Chưa benchmark tải hoặc nghiệm thu mọi chế độ/súng.

### Skin Thor cho Apache

Chọn Apache → Cửa hàng → Shop trang bị → **Skin Thor**, giá **1.000 lượng**, vĩnh viễn. Mua rồi mặc ở ô giáp và lưu để nhận **+10.000 HP, +1.000 sức mạnh, +10.000 may mắn**. Menu Trang bị có **Tháo Skin Thor**. Chân bước luân phiên; búa mẹ vẫn tách ba theo cơ chế Apache. Các client cần dùng bản build mới; không cần bật cờ JVM. Chi tiết triển khai, giới hạn animation và kiểm chứng: [docs/APACHE-THOR.md](docs/APACHE-THOR.md).

## Nguồn gốc

- Client: https://github.com/vantu03/MobiArmy2-Client — nền commit `6eda0eebff2195312a1f850719f2364120320023`.
- Server: https://github.com/vantu03/MobiArmy2-Server — nền commit `c337e832a97d023dfba3693b08d033fec5ca760f`.

Giữ attribution/thông tin upstream trong các thư mục. Các tài liệu upstream có thể mô tả cấu hình cũ; README gốc repo này mô tả bản LAN hiện tại. Repo này không bổ sung hoặc thay đổi giấy phép cho mã nguồn/tài nguyên upstream.
