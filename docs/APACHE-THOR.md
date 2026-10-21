# Skin Thor cho Apache

## Cách mua và dùng

1. Chọn nhân vật Apache.
2. Vào Cửa hàng → Shop trang bị. **Skin Thor** nằm đầu danh sách, giá **1.000 lượng**, không mua bằng xu, dùng vĩnh viễn.
3. Sau khi mua, vào Trang bị/rương, chọn Skin Thor ở ô giáp, đóng màn hình và xác nhận lưu.
4. Chỉ khi trang bị, nhận **+10.000 HP, +1.000 sức mạnh, +10.000 may mắn**. Bonus là số cộng cố định vào chỉ số cuối, không phải điểm tiềm năng hoặc phần trăm. Skin thay món giáp đang mặc; chỉ số của món giáp cũ không còn được cộng.
5. Muốn tháo: vào Trang bị → Menu → **Tháo Skin Thor**, rồi lưu. Skin vẫn nằm trong rương để mặc lại.

Các client đã cập nhật sẽ nhìn thấy Thor của người đang mặc skin. Không còn cờ JVM biến toàn bộ Apache thành Thor. Launcher `scripts/Start-Thor-Client.cmd` vẫn dùng được như một client có profile riêng; nó không cấp skin hoặc tiền.

## Chuyển động và búa

- Bản cũ ánh xạ cả frame 4 và 5 vào ảnh đứng nên hai chân cứng đờ. Bản mới dùng trực tiếp hai dáng chân dang/thu của giáp Iron Man (Apache, trang bị 23, frame 4/5), đổi màu thép khi render và nhún thân cùng nhịp. Chuyển động được kích bởi hai frame đi thật của CPlayer, không chạy khi đứng yên.
- Apache vẫn là nhân vật ID 8. Đạn 17 là búa mẹ; đến frame tương ứng lực thứ hai, server tách đúng ba đạn 18 với hướng cách nhau 15°. Không thay đường đạn, lực bắn hoặc hitbox.
- Chỉ búa của người bắn đang trang bị Thor được đổi hình. Apache thường và người chơi khác giữ nguyên hình gốc.
- Animation đi ghép chân Iron Man với thân Thor, chưa phải bộ sprite Thor vẽ mới đầy đủ. Tư thế trúng đạn/chết/chiến thắng vẫn là bản thử; chưa có vệt điện dài như hình concept. Lần yêu cầu tạo hai sprite bước chân bằng imagegen bị công cụ từ chối, không có ảnh mới được sinh.

## Tích hợp

- Catalog: `glassID=8`, `type=2` (giáp), `id=30000`, `xu=-1`, `luong=1000`.
- `server/.../ThorSkin.ensureCatalog()` thêm một bản ghi `equip` và một bản ghi `shop_equipment` trong transaction, chạy lặp không thêm trùng; từ chối chiếm ID đang thuộc món khác. Bản ghi equip thật cần thiết vì `user_equip.equipID` có foreign key.
- Sở hữu/trạng thái mặc được lưu theo luồng `user_equip` hiện có. Không thêm cột hoặc sửa ví tài khoản người dùng để thử nghiệm. Không cho mua trùng, mua thiếu lượng, mua bằng xu hoặc trang bị trên nhân vật khác.
- Bonus cộng sau công thức `Glass.createAbility`, tránh tràn byte của `inv_ability`. Client bổ sung cùng bonus vào bảng chỉ số và mô tả shop/rương. Chỉ cộng một lần dù có bản ghi trùng.
- Metadata trang bị tăng baseline version 12 → 13, giữ cơ chế revision của admin, để client tải lại catalog. Không sửa atlas Apache gốc/cache nhân vật.
- Trang phục VIP và Thor không dùng đồng thời. Trang bị Thor tháo VIP; muốn mặc VIP trở lại cần tháo Thor trước.
- Skin không có ô ngọc; bot không tự chọn skin từ shop.

## Kiểm chứng

Chạy `scripts/tests/Run-Thor-Checks.ps1` khi database local ở cổng 3307 đang hoạt động. Script build, dùng một tài khoản kiểm tra tạm, kiểm tra command handlers và SQL rồi xóa riêng tài khoản đó. Catalog Thor được giữ lại.

- Server: giá/currency/index/thiếu lượng/mua trùng, sở hữu nhưng chưa mặc, sai nhân vật, mặc/tháo, chỉ số trận đấu, save/load wallet và trang bị, metadata cache.
- Client LibGDX: giải mã metadata thật, di chuyển Apache và hai frame chân, hai hướng nhìn, tám hướng búa xoay, chỉ đổi đạn của người mặc skin.
- UI: giải mã packet shop và mua thật, chọn Thor đầu danh sách, thêm vào rương, bảng chỉ số, thao tác tháo skin. Screenshot là màn UI kiểm tra có dữ liệu từ handler thật, không phải ảnh trận multiplayer.
- Hồi quy: 90 kiểm tra bàn phím/camera/vị trí và 24 tình huống Apache tách ba.
- Chưa nghiệm thu một trận bốn người dùng skin từ đầu tới cuối.

Ảnh: `art/concepts/apache-thor/shop-preview.png`, `runtime-shop-walk-preview.png`.
Nguồn hình hiện có: `client/desktop/assets/skins/apache-thor/concept.png` (concept tạo bằng imagegen ở lượt trước); chân lấy từ bản sao atlas gốc `server/res/bigImage8.png` tại `client/desktop/assets/skins/apache-thor/apache-equipment-source.png`; shader chỉ đổi màu chân và được phục hồi ngay sau khi vẽ.
