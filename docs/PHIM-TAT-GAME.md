# Phím tắt trong trận

| Phím | Chức năng |
|---|---|
| ← / → | Giữ để di chuyển |
| ↑ / ↓ | Mỗi lần nhấn nâng/hạ góc bắn 1°; giữ phím không tăng tiếp |
| Enter | Giữ để căn lực, thả để bắn; súng hai lực dùng hai lần giữ/thả theo cơ chế có sẵn |
| Esc | Mở/đóng menu trận; đóng bảng vật phẩm nếu đang mở |
| Space | Kích hoạt kỹ năng đặc biệt khi đủ nộ và chưa dùng vật phẩm trong lượt |
| Q | Mở/đóng bảng chọn vật phẩm trong lượt của mình |
| 1–8 | Dùng trực tiếp ô vật phẩm tương ứng, trái sang phải, hàng trên rồi hàng dưới; bỏ qua ô trống hoặc chưa đủ điều kiện |

Di chuyển/bắn vẫn tuân theo lượt và trạng thái nhân vật. Khi mất focus, phím giữ và lực đang căn được xóa để tránh thao tác ngoài ý muốn.

Kiểm thử ngày 20/10/2026: client LibGDX thật, gửi sự kiện qua InputProcessor, **34 PASS**, exit 0. Gồm mỗi lần nhấn đổi 1°, bỏ qua lặp khi giữ phím, dùng đúng cả 8 ô vật phẩm, bỏ qua ô trống/đã dùng item trong lượt, di chuyển hai hướng, menu Esc/Q, điều kiện kỹ năng, mất focus, căn lực và hai phát bắn liên tiếp. Build và `git diff --check` thành công.

Đã cập nhật thư mục `G:\MobiArmy2-LAN\Player-Client`. Các ZIP bàn giao trước chưa chứa bộ phím mới.
