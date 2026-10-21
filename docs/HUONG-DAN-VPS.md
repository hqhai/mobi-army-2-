# Hướng dẫn MobiArmy 2 trên VPS 101.99.7.167

## Dành cho người chơi

1. Tải game PC: http://101.99.7.167:8123/downloads/MobiArmy2-PC.zip
2. Giải nén **toàn bộ** file zip ra một thư mục (ví dụ `D:\MobiArmy2`). Không chạy trực tiếp bên trong file zip.
3. Đăng ký tài khoản tại http://101.99.7.167:8123/
   - Tên đăng nhập: 3–20 ký tự, chỉ chữ không dấu hoặc số.
   - Mật khẩu: 6–30 ký tự, chỉ chữ không dấu hoặc số.
4. Mở thư mục đã giải nén, chạy **`MobiArmy2.cmd`**, đăng nhập bằng tài khoản vừa tạo.
5. Quản lý nhân vật trên web (mua đồ, mặc đồ, cộng điểm): http://101.99.7.167:8123/nhan-vat/

Không cần cài Java, trong gói game đã có sẵn.

Phím điều khiển: xem `docs/PHIM-TAT-GAME.md`. Tóm tắt:
- ←/→ hoặc A/D: di chuyển. ↑/↓ hoặc W/S: chỉnh góc bắn.
- Giữ Enter để căn lực, thả ra để bắn. Space: kỹ năng. Q: túi vật phẩm. Phím 1–8: dùng ô vật phẩm.
- R: bật/tắt camera tự do. Esc: menu.

### Lỗi thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| Không kết nối được server | Kiểm tra Internet; báo admin xem server có đang chạy không. |
| Bấm `MobiArmy2.cmd` mà không thấy gì | Chưa giải nén hết file zip, hoặc máy không có driver card màn hình (OpenGL). Cập nhật driver card màn hình. |
| Windows báo "Windows protected your PC" | Bấm **More info → Run anyway**. |
| Sai mật khẩu nhiều lần | Sau 5 lần sai, IP bị khóa 5 phút. Nhờ admin đặt lại mật khẩu nếu quên. |

## Dành cho quản trị viên

### Địa chỉ

| Dịch vụ | Địa chỉ |
|---|---|
| Game (TCP) | `101.99.7.167:8122` |
| Trang chủ / đăng ký | http://101.99.7.167:8123/ |
| Trang nhân vật | http://101.99.7.167:8123/nhan-vat/ |
| Trang quản trị | http://101.99.7.167:8123/admin/ |

Tài khoản admin mặc định: **admin / admin123**. **Đổi mật khẩu ngay** bằng nút "Đổi mật khẩu" trong trang quản trị.

Trang quản trị cho phép: cộng/trừ xu, lượng, tăng cấp người chơi, đặt lại mật khẩu, đổi tên, sửa giá và chỉ số nhân vật, trang bị, vật phẩm. Thay đổi có hiệu lực ngay, không cần khởi động lại.

### Bật / tắt server

Đăng nhập VPS bằng tài khoản Windows đã cài đặt, mở thư mục `C:\Users\mobiarmy\mobi-army-2-`:

- Bật server: chạy **`Start-Server.cmd`** (tự bật cả database).
- Tắt server: chạy **`Stop-Server.cmd`**.

**Lưu ý:** server **không tự chạy** khi khởi động lại VPS. Sau mỗi lần reboot, phải chạy lại `Start-Server.cmd`.

### Cấu trúc trên VPS

| Thư mục / file | Nội dung |
|---|---|
| `tools\jdk-21...` | Java 21 portable |
| `tools\mariadb-11.4.4-winx64` | MariaDB portable |
| `runtime\database` | Dữ liệu database (port 3307, chỉ nghe 127.0.0.1) |
| `runtime\db-credential.xml` | Mật khẩu root database, mã hóa theo tài khoản Windows (chỉ tài khoản đó đọc được) |
| `logs\` | Log server và database |
| `web\downloads\MobiArmy2-PC.zip` | Gói client cho người chơi tải |

### Sao lưu database

Nên sao lưu định kỳ. Chạy trong PowerShell tại thư mục dự án khi server đang chạy:

```powershell
$pw = (Import-Clixml runtime\db-credential.xml).GetNetworkCredential().Password
& tools\mariadb-11.4.4-winx64\bin\mariadb-dump.exe -uroot "-p$pw" --port=3307 --host=127.0.0.1 army > "backup-$(Get-Date -f yyyyMMdd).sql"
```

Không sửa dữ liệu người chơi trực tiếp trong database khi server đang chạy, vì server ghi đè mỗi 30 giây. Hãy dùng trang quản trị.

### Tường lửa

- Windows Firewall đã mở TCP **8122** và **8123** cho mọi địa chỉ.
- Nếu nhà cung cấp VPS có tường lửa riêng (security group), cũng phải mở TCP 8122 và 8123 ở đó.
- **Không** mở port 3307 (database).

### Build lại sau khi sửa code

`scripts\Build.ps1` bị lỗi trên PowerShell 5.1 của VPS này vì dừng lại ở cảnh báo vô hại của trình biên dịch Java. Build bằng Git Bash:

```bash
cd /c/Users/mobiarmy/mobi-army-2-
export JAVA_HOME="$(pwd)/tools/jdk-21.0.12.1+1" GRADLE_USER_HOME="$(pwd)/tools/gradle-cache"
"$JAVA_HOME/bin/javac" -encoding UTF-8 --release 20 -cp "server/lib/*" -d runtime/server-classes @runtime/server-sources.txt
cmd //c "client\\gradlew.bat -p desktop-build --no-daemon --console=plain installDist"
```

Sau đó chạy `Stop-Server.cmd` rồi `Start-Server.cmd`. Nếu client thay đổi, đóng gói lại zip (thư mục `runtime\pkg\MobiArmy2-PC`, thay thư mục `lib` bằng bản mới trong `desktop-build\build\install\army2-lan-desktop\lib`) và gửi người chơi tải lại.

### Quên mật khẩu admin

Xóa các dòng trong bảng `admin_account` rồi khởi động lại server; tài khoản admin/admin123 sẽ được tạo lại.
