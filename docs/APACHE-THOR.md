# Skin Thor cho Apache

## Cách mua và dùng

1. Chọn nhân vật Apache.
2. Vào Cửa hàng → Shop trang bị. **Skin Thor** nằm đầu danh sách, giá **1.000 lượng**, không mua bằng xu, dùng vĩnh viễn.
3. Sau khi mua, vào Trang bị/rương, chọn Skin Thor ở ô giáp, đóng màn hình và xác nhận lưu.
4. Khi trang bị, skin cộng đúng chỉ số ghi trong danh mục đồ (bản cài mới: +5% mọi chỉ số như VIP Iron Man). Bonus cố định +10.000 HP, +1.000 sức mạnh, +10.000 may mắn của bản đầu đã bỏ từ bản VPS. Ở phòng luật Cân bằng hay Sát thương gốc (chủ phòng chọn) skin chỉ là trang phục. Skin thay món giáp đang mặc; chỉ số của món giáp cũ không còn được cộng.
5. Muốn tháo: vào Trang bị → Menu → **Tháo Skin Thor**, rồi lưu. Skin vẫn nằm trong rương để mặc lại.

Các client đã cập nhật sẽ nhìn thấy Thor của người đang mặc skin. Không còn cờ JVM biến toàn bộ Apache thành Thor. Launcher `scripts/Start-Thor-Client.cmd` vẫn dùng được như một client có profile riêng; nó không cấp skin hoặc tiền.

## Chuyển động và búa

- Skin Thor và các trang phục Thor của mọi nhân vật được vẽ bằng **mảnh đồ kiểu của game**, không còn ghép ảnh concept: mũ là nón bạc của chính nhân vật đó, sáng hơn, thêm cánh trắng phía sau và lọn tóc vàng; vũ khí bạc; giáp bạc tối lại thành xám than còn khuy bạc; áo choàng của trang phục Batman đổi sang đỏ (Granos không có áo choàng). Mỗi mảnh giữ đúng 6 khung đồ và độ lệch của mảnh gốc nên đứng, ngắm, bắn, đi đều khớp như mọi trang phục; khung trúng đạn/chết/thắng như trang phục gốc của game.
- Apache vẫn là nhân vật ID 8. Đạn 17 là búa mẹ; đến frame tương ứng lực thứ hai, server tách đúng ba đạn 18 với hướng cách nhau 15°. Không thay đường đạn, lực bắn hoặc hitbox.
- Búa của người mặc Skin Thor là **Mjölnir**: kiểu đạn Thor trong dải đạn gốc của Apache (`server/res/bullet8.png`, đầu thép cán da), xoay tám hướng như búa gốc; POW dùng hiệu ứng POW gốc của game. Apache thường và người chơi khác giữ nguyên hình gốc. Bản trước dùng búa cắt từ ảnh concept và tia sét vẽ bằng nét; cả hai đã bỏ.
- Cách làm: `art/thor-native/make_thor_costume.py` tạo các mảnh từ đồ có sẵn, ghép vào cuối atlas `server/res/bigImage*.png` (client đọc tối đa 65.535 byte mỗi atlas) và ghi toạ độ vào `server/res/thor-costume.json`. Server cài lúc khởi động (`ThorCostume`): mảnh ẩn 30110 mũ, 30111 vũ khí, 30112 giáp, 30113 áo choàng cho từng nhân vật; trang phục Thor 30100 + nhân vật trỏ vào các mảnh đó; Skin Thor 30000 lấy hình giáp, client vẽ thêm mũ, vũ khí, áo choàng cho Apache (`ThorSkin`). Icon trong game: `client/desktop/assets/skins/thor-native/icons.png`; icon web: `web/img/thor/`. Ảnh xem trước: `art/thor-native/preview.png`.
- Đổi hình bộ Thor: sửa và chạy lại script (đọc danh mục `equip` xuất từ DB), rồi tăng số phiên bản ở `ControlHandler`: `dataInfo` (dữ liệu đồ) và `captionInfo` (khi số này đổi, client tải lại toàn bộ atlas). Quên tăng thì client cũ vẫn dùng atlas lưu sẵn và vẽ sai mảnh mới.

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
Nguồn hình: các mảnh đồ Thor tạo bằng `art/thor-native/make_thor_costume.py`, kiểu đạn bằng `art/costume-bullets/make_costume_bullets.py`, đều từ hình gốc của game. Ảnh concept tạo bằng imagegen và các hiệu ứng đạn theo ảnh (`skins/thor-classes`, `skins/apache-thor`) đã bỏ khỏi client.
