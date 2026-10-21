# Photon VIP cho Magenta

Chạy `Start-Photon-Client.cmd`, đăng nhập, chọn Magenta → Shop trang bị → mục VIP → Photon (50 lượng / 7 ngày, cùng giá/thời hạn và chỉ số VIP Iron Man). Mua rồi mặc trong rương VIP. Tháo VIP sẽ trở lại trang phục thường. Chỉ người đang mặc VIP Photon được đổi hình; client cũ cần mở bản mới để thấy hình mới.

Catalog: class 9, type 1, id 30001, data [21,30001,20,-1,-1]. Dùng súng Iron Man gốc 21. SQL migration idempotent, không ghi đè ID đang dùng; backup trước triển khai tại runtime/backups/before-photon-vip-20261021.sql. Metadata baseline 14. Không cấp miễn phí hoặc thay ví tài khoản thật.

Form duyệt: art/concepts/magenta-photon/photon-ten-review-v6.png. Bản triển khai khớp từng pixel với ảnh này ở 10 mẫu ngắm/đi/hướng; nguồn ảnh vip-poses-v5.png có 6 pose, 4 pose ngắm được lật để tạo 10 mẫu đối chiếu. Frame trúng đạn/nằm/thắng vẫn dùng nhánh native của game, chưa được vẽ riêng. Laser vàng trắng có sét chạy hai bên; POW lớn hơn và nhiều nhánh sét hơn. Thor giữ nguyên.

Kiểm tra server: scripts/tests/PhotonVipCheck.java (11 kiểm tra catalog, mua, từ chối thiếu tiền/sai nhân vật, mặc/tháo, persistence, metadata). Tài khoản test được dọn sau chạy. Kiểm tra render: scripts/tests/PhotonVipRenderCheck.java, đọc verification/photon-catalog.bin và dùng JVM property review.root trỏ repo. Chưa nghiệm thu một trận multiplayer đầy đủ.

Gói hiện tại runtime/photon-vip-gold-v2; server local đã nạp catalog. Skill quy chuẩn: C:/Users/haqua/.codex/skills/mobiarmy-sprite-form/SKILL.md.
