# MobiArmy 2 LAN

Source gộp client Java/LibGDX, server Java/MariaDB và script vận hành bản LAN.

## Cấu trúc

- `client/`: source client upstream cùng các sửa lỗi desktop.
- `server/`: source, thư viện và tài nguyên server.
- `server/army.sql`: schema và dữ liệu game mặc định; **không có tài khoản/tiến trình người chơi**.
- `desktop-build/`: cấu hình Gradle để build desktop từ `client/`.
- `scripts/`: build, chạy/dừng server, tạo tài khoản và cấu hình LAN trên Windows.
- `docs/PHIM-TAT-GAME.md`: bảng phím điều khiển hiện tại.

Không chứa Java/MariaDB portable, file credential, database vận hành, log, cache đăng nhập, bản build hoặc file kiểm thử.

## Yêu cầu

- Java **21** (server biên dịch với `--release 20`).
- MariaDB **11.4**; tạo database `army`, nhập `server/army.sql`.
- Gradle wrapper trong `client/`; build lần đầu cần Internet tải dependency.
- Game nghe TCP **8122**. Database chỉ nên nghe loopback.

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

Chạy Start/Stop/Create-Account bằng cùng tài khoản Windows đó. Dùng `Create-Account.cmd` để tạo người chơi; không có tài khoản/mật khẩu mặc định trong repo. `Start-Client.cmd` mở game local, `Enable-LAN.cmd` mở firewall (Administrator).

### Linux — lệnh tham khảo, chưa nghiệm thu trên Linux

Cài Java 21 và MariaDB từ package manager; tạo/import database và tài khoản DB trước.

```bash
mkdir -p runtime/server-classes
find server/src scripts -name '*.java' > runtime/server-sources.txt
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

## Thay đổi bản LAN

- Cấu hình địa chỉ server qua `army.host` / `army.port`, chạy desktop bằng Java portable.
- Sửa lưu dữ liệu, kiểm tra giao dịch, quyền chủ phòng và vòng đời trận.
- Quy tắc cược đã thống nhất: thắng +W, thua -W, hòa 0, rời trận -W; boss W=100.
- Thêm luyện tập riêng từng session: vào map, bắn, thoát/vào lại.
- Sửa treo nhập liệu, lỗi resize, popup ping sai và khóa bắn sau lượt luyện tập đầu.
- Phím desktop: ←/→ di chuyển, ↑/↓ mỗi nhấn 1°, giữ/thả Enter căn lực/bắn, Esc menu, Space kỹ năng khi đủ điều kiện, Q túi vật phẩm, 1–8 dùng ô tương ứng.

Kiểm thử gần nhất trên Windows: 34 kiểm tra bàn phím bằng client LibGDX thật, build thành công. Các kiểm thử cục bộ không đưa vào repo. Chưa benchmark tải hoặc nghiệm thu mọi chế độ/súng.

## Nguồn gốc

- Client: https://github.com/vantu03/MobiArmy2-Client — nền commit `6eda0eebff2195312a1f850719f2364120320023`.
- Server: https://github.com/vantu03/MobiArmy2-Server — nền commit `c337e832a97d023dfba3693b08d033fec5ca760f`.

Giữ attribution/thông tin upstream trong các thư mục. Các tài liệu upstream có thể mô tả cấu hình cũ; README gốc repo này mô tả bản LAN hiện tại. Repo này không bổ sung hoặc thay đổi giấy phép cho mã nguồn/tài nguyên upstream.
