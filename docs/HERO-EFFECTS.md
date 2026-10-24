# Magenta photon và Thor POW

> Cập nhật: bản Photon đã tích hợp, laser thường đỏ và POW xanh lam. Xem MAGENTA-PHOTON.md và dùng Start-Photon-Client.cmd; phần Captain Marvel đang chờ dưới đây là lịch sử bản trước.

## Bản hiện tại

- Chạy `Start-Hero-Effects.cmd` để mở client đóng gói riêng trong `runtime/hero-effects-client-v2`. Server local hiện có vẫn dùng cổng 8122. Không cần cập nhật database hoặc khởi động lại server.
- Magenta (gun 10, bullet 49): laser vàng cam, lõi trắng, lóe sáng ở đầu tia. Giữ điểm phản xạ và hai đoạn laser hiện có. POW tăng bề rộng, kích thước lóe sáng và thêm các vệt photon chạy dọc tia.
- Thor: tia điện trên búa POW và búa cắt từ ảnh concept **đã bỏ**. Skin Thor ném Mjölnir là kiểu đạn trong dải đạn gốc của Apache, POW dùng hiệu ứng gốc (xem APACHE-THOR.md, mục búa; đạn các trang phục: `art/costume-bullets`).
- Búa POW lớn gấp đôi (40×40 thay cho 20×20), đủ tám hướng xoay, bảy nhánh sét chuyển động quanh từng búa và đuôi điện dài hơn. Kích thước hiển thị không thay vùng sát thương.
- Trạng thái POW lấy từ item 100 đã được server thông báo, chụp lại trong BM lúc nhận phát bắn và sao sang từng Bullet. Không lấy từ `isSuper` hoặc chỉ số nộ đầy. Bắn thường tiếp theo xóa trạng thái.
- Mức đồ họa thấp dùng tia mảnh và vòng búa đơn giản. Không đổi sát thương, đường đạn, hitbox, camera hoặc giá/chỉ số skin.
- Màu sắc dùng bảng màu cố định, số vệt giới hạn; không tạo danh sách particle tăng vô hạn.

## Skin Captain Marvel đang chờ

Thiết kế: chibi tóc vàng ngắn, giáp xanh navy–đỏ, ngôi sao vàng trên ngực, hai tay tụ/phóng photon. Cần 10 ô sprite gồm đứng, ngắm, tụ lực, bắn, hai bước chân riêng, trúng đạn, nằm, chiến thắng và Binary POW.

Đã thử công cụ imagegen tích hợp với nền trong suốt, nhưng không có ảnh được tạo (output moderation error, request `156d9b8d-2f14-477d-9420-6a413fa39e35`). Người dùng chọn hoàn thiện hiệu ứng trước, không dùng CLI/API. Vì vậy bản này vẫn là hình Magenta hiện tại, chưa có skin Captain Marvel hay món hàng mới trong shop. Laser photon tạm áp dụng cho Magenta; khi có skin thật sẽ giới hạn theo trang bị.

## Kiểm chứng

`scripts/tests/HeroEffectsCheck.java`: kiểm tra chọn đúng Thor/Magenta, loại shooter sai, chụp POW, giữ POW trong thời gian đạn con trễ, xóa ở phát thường, không nhầm đầy nộ với kích hoạt POW. Render thật bằng LibGDX tại `art/concepts/hero-effects-preview.png`: hàng trên laser thường, giữa POW, dưới Thor và ba búa.

Ảnh là màn render kiểm tra, không phải bằng chứng đã chơi một trận multiplayer đầy đủ.
