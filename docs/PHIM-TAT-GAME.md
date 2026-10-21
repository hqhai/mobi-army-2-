# Phím tắt trong trận

| Phím | Chức năng |
|---|---|
| ← / A và → / D | Giữ để di chuyển trái/phải |
| ↑ / W và ↓ / S | Nhấn nhanh nâng/hạ góc bắn 1°; giữ để tăng/giảm liên tục, thả để dừng |
| R | Bật/tắt camera tự do. Khi bật, giữ WASD hoặc các phím mũi tên để xem bản đồ; không di chuyển nhân vật hoặc chỉnh góc bắn |
| Enter | Giữ để căn lực, thả để bắn; súng hai lực dùng hai lần giữ/thả theo cơ chế có sẵn |
| Esc | Mở/đóng menu trận; đóng bảng vật phẩm nếu đang mở |
| Space | Kích hoạt kỹ năng đặc biệt khi đủ nộ và chưa dùng vật phẩm trong lượt |
| Q | Mở/đóng bảng chọn vật phẩm trong lượt của mình |
| 1–8 | Dùng trực tiếp ô vật phẩm tương ứng, trái sang phải, hàng trên rồi hàng dưới; bỏ qua ô trống hoặc chưa đủ điều kiện |

Di chuyển/bắn vẫn tuân theo lượt và trạng thái nhân vật. Khi mất focus, phím giữ và lực đang căn được xóa để tránh thao tác ngoài ý muốn.

Góc đổi ngay 1° khi nhấn, bắt đầu lặp sau 180 ms và lặp mỗi 40 ms (tối đa một bước mỗi lần cập nhật game). Giữ hai hướng đối nhau sẽ tạm dừng; hai phím cùng hướng không làm tăng tốc. WASD chỉ điều khiển nhân vật trong trận, không kích hoạt thao tác khi đang chat hoặc ngoài lượt.

Camera tự do dùng được cả ngoài lượt. Nhấn R lần nữa trả về theo nhân vật đang đến lượt hoặc viên đạn đang bay. Bật camera sẽ hủy lực đang căn; thả phím hoặc mất focus sẽ dừng di chuyển khung nhìn.

Trên desktop, kéo/giữ chuột trên bản đồ không đổi góc bắn hoặc hướng ngắm nữa. Click menu và các nút vẫn hoạt động; thao tác ngắm bằng cảm ứng trên mobile được giữ nguyên.

Bộ kiểm tra bàn phím/camera gồm 90 kiểm tra qua InputProcessor của client LibGDX, cùng kiểm tra xử lý chuột/cảm ứng, nằm trong `verification/KeyboardControlsCheck.java` tại máy phát triển. Bản build cũ và client đang mở cần được cập nhật/khởi động lại để nhận thay đổi.
