# MobiArmy 2 LAN — Gameplay hiện tại

Tài liệu tổng hợp từ code ở commit `9ad6a0e` (25/10/2026). Mọi con số lấy từ `server/src/mobiarmy/war/SetPassive.java`, `SkillBalance.java`, `MapData.java`, `Player.java` và database của server (bảng `glass`, `item`).

> **Bản client build 8 (sau `3bf0001`, chưa deploy) thay đổi những điều dưới đây; các mục bên dưới phần này vẫn mô tả bản cũ.**
>
> - **King Kong**: to thêm 25%/vòng đứng yên, tối đa 300% sau 8 vòng; càng to càng lì, ở cỡ tối đa nhận ít hơn 20% sát thương và thêm 20% máu tối đa (tăng dần theo vòng, đi là mất). POW to theo người nhưng chỉ mạnh thêm tối đa 30% (trước là gấp 3).
> - **Electrician**: đạn to 1,15 lần bản gốc (bản `3bf0001` là 1,3).
> - **Tarzan**: leo trèo, chân luôn bám địa hình, đi lên vách và treo ngược dưới mỏm đá; phím phải giữ địa hình bên tay phải, phím trái bên tay trái. Khi bám vách hay treo ngược thì ngắm được mọi hướng, boomerang bay ra từ giữa thân. Chỉ rơi khi chỗ bám bị phá. Người chơi không chặn đường đi (cả trước đây cũng vậy).
> - **Rocketer**: mỗi quả bom WorldWar có 50% nằm lại trên đất (nhấp nháy đỏ) và nổ sau 1 vòng; vẫn không làm Rocketer mất máu.
> - **Miss 6**: Dịch chuyển chờ 2 lượt (nhân vật khác 3).
> - **Sát thương chuẩn** (trứng Chicky, laze Granos, phát thứ 3 của Miss 6): bỏ qua phòng thủ, may mắn (không nhân đôi, không cứu), và cả các nội tại giảm sát thương (Tarzan −8%, King Kong).
> - **HP** hồi 35% máu tối đa (trước 350). **HP đồng đội** hồi cho cả đội 40% máu tối đa của người dùng (trước 300).
> - **Nâng cấp +15, +16**: tỉ lệ 0,1%, chắc chắn ở lần thứ 1500 và 2000; 18,74 triệu và 33,73 triệu xu; 2560 và 5120 lượng; 15 và 16 đá; cộng 43 và 50 mỗi chỉ số. Bảo hiểm cao cấp dùng được tới +16. Lên +15 thì chọn hệ cho món đó.
> - **4 hệ bộ +15/+16**: cả 5 món +15 trở lên cùng một hệ thì mỗi phát bắn trúng đối thủ có 10% (+16: 15%) gây hiệu ứng lên lượt kế của họ.
>   - Lôi: đi chậm 30%, không ngắm thấp hơn 60°.
>   - Hỏa: nhận thêm 15% (+16: 25%) sát thương cho tới hết lượt kế.
>   - Băng: không di chuyển được.
>   - Độc: mất 3% (+16: 5%) máu tối đa ở đầu 2 lượt kế.
> - **Hộp quà bay**: từ vòng 10, mỗi vòng có 1 hộp quà lơ lửng giữa các người chơi, không quá số người còn trên sàn. Bắn trúng thì được 1 vật phẩm ngẫu nhiên miễn phí vào ô trống; nếu không còn ô trống thì được +20% máu.
> - **Ghi nhớ vật phẩm**: client nhớ các ô vật phẩm đã mang ở lần trước (theo tài khoản) và tự xếp lại khi vào phòng.
> - **Rương kim cương** (đồ đặc biệt 85, 100 triệu xu hoặc 10.000 lượng): 1 vũ khí/trang bị +16, hệ theo hệ các món +15 đang có nhiều nhất (chưa có thì ngẫu nhiên). Trên web đồ +15/+16 có màu kim cương.
> - **Gunner**: viên thần công POW hất văng đối thủ trúng đạn 40 px ra xa tâm nổ.
> - **King Kong**: chuối thường nhỏ dần khi King Kong to lên, ở cỡ tối đa còn 50% (hố cũng vậy, sát thương giữ nguyên). Web có thêm mô tả Khế Ước Rừng Xanh.
> - **Nội tại đội** (cùng phe có ít nhất 2 nhân vật khác nhau trong nhóm, không cần bộ +14, không có ở phòng tự do/Sát thương gốc): Kẻ Ngoại Lai (Chicky, Apache, Magenta), POW có 25% là sát thương chuẩn. Liên Minh Binh Bét (Gunner, Miss 6, Electrician), cả đội nhận ít hơn 1% sát thương mỗi vòng (tối đa 30%), dưới 50% máu thêm 30%. Nhà Khoa Học (Rocketer, Granos), thêm 1 viên đạn kể cả POW; người đầu tiên gục triệu hồi T-rex phe mình (máu tối đa, công, thủ, may mắn của người đó; đánh như boss T-rex); người thứ hai gục thì không.

---

## 1. Tổng quan trận đấu

- **Theo lượt**: mỗi lượt người chơi được di chuyển (60 bước, trừ khi có nội tại/vật phẩm), dùng 1 vật phẩm hoặc POW, rồi bắn 1 phát. Thời gian tối đa mỗi lượt do phòng quy định; hết giờ thì mất lượt.
- **Thắng**: hạ hết người phe đối phương (rơi khỏi bản đồ cũng tính là chết). Hai bên cùng chết hết là hòa.
- **Cược**: thắng +W xu, thua −W, hòa 0, rời trận giữa chừng −W; phòng boss W = 100.
- **Chế độ**: đấu đội, solo (một người đấu cả đội được), đánh boss (một mình cũng được), luyện tập riêng (chọn map bằng chat `map N`, POW và vật phẩm dùng miễn phí, nội tại bộ +14 có tác dụng).

### Gió
- Chỉ còn **gió ngang**, trong khoảng −70..70.
- Cả một vòng (tới khi một người được lượt lần thứ hai) dùng **chung một gió**; sang vòng mới gió chỉ trôi tối đa ±20.
- Vật phẩm Ngưng gió: lượt dùng không có gió.

### Nộ (POW)
- Nộ tăng +10 mỗi lượt và tăng thêm khi gây/nhận sát thương. Đầy 100 thì dùng được POW (phím Tab; Space giữ để căn lực và bắn, Enter mở chat) — phát bắn đặc biệt của từng nhân vật, mặc định gây gấp đôi sát thương.
- Một số nội tại làm nộ tích nhanh hơn (Gunner +50%, Rocketer +40%).

### Đường ngắm (client bản LAN 6)
- Chấm tròn to ở đầu súng, nhỏ dần, vẽ **hết đường bay khi không có gió** tới khi chạm đất hoặc chạm người đang nhìn thấy. Có gió thì người chơi tự bù.
- Không dừng ở người tàng hình (để không lộ vị trí).
- Vật phẩm **Chỉ kẻ đầu** và **Tarzan có nội tại** được đường ngắm tính cả gió.
- Chicky, Apache (đạn tách) và Magenta (tia laser) có nhánh vẽ riêng.

### Trạng thái
| Trạng thái | Nguồn | Tác dụng |
|---|---|---|
| Độc | Cơn mưa độc | Hồi máu chỉ còn một nửa trong 3 lượt của mình |
| Choáng (Tê liệt) | Electrician trúng đủ 3 tia | Mất lượt kế tiếp |
| Tàng hình | Vật phẩm, nội tại Apache | Đối thủ không thấy, đường ngắm không dừng ở người đó |
| Đóng băng, mù, tơ nhện… | Vật phẩm gốc của game | Như game gốc |

Chữ "Độc"/"Choáng" hiện trên đầu nhân vật.

---

## 2. Luật trận (chủ phòng chọn)

Chủ phòng vào **Menu → Luật trận đấu**. Luật mặc định của phòng mới đặt ở trang admin.

| Luật | Cách tính |
|---|---|
| **Tự do** | Như game gốc: điểm, đồ, nâng cấp, ngọc, Skin Thor, nội tại bộ +14 tính đủ. |
| **Cân bằng** | Chỉ số vẫn tính nhưng **chỉ phát huy khi bắn chính xác** (xem dưới). Skin Thor chỉ là trang phục. |
| **Sát thương gốc** | Ai cũng như nhân vật cấp 1 không đồ: 1.000 máu, sát thương gốc, phòng thủ/may mắn/đồng đội 100. **Tắt nội tại.** |

### Luật Cân bằng chi tiết
- Mỗi phát chia hai phần: **sát thương gốc** của nhân vật và **phần cộng thêm** (điểm, đồ, nâng cấp, ngọc, đồng đội).
- Sát thương gốc theo nổ lan như cũ. Phần cộng thêm còn đủ khi độ chính xác ≥ 80%, giảm dần, mất hết khi ≤ 40%.
- **Trúng thân** (tâm vụ nổ trong thân mục tiêu): x2 khi may mắn, xuyên 50% phòng thủ, cộng 8% phần máu trên 1.000 của mục tiêu.
- May mắn tối đa 30% mỗi lượt, không cứu khỏi chết trước đòn trúng thân; phòng thủ giảm tối đa 50% sát thương.

---

## 3. Nhân vật

| # | Nhân vật | Sát thương gốc | Đạn thường |
|---|---|---|---|
| 0 | Gunner | 375 | Đạn đơn |
| 1 | Miss 6 | 310 | Đạn đơn |
| 2 | Electrician | 300 | 3 tia điện |
| 3 | King Kong | 420 | Chuối |
| 4 | Rocketer | 321 | Tên lửa |
| 5 | Granos | 310 | Đạn đào đất |
| 6 | Chicky | 470 | Gà (55% sức công) + trứng (30%), thả trứng theo lực 2 |
| 7 | Tarzan | 340 | Boomerang (bay đi rồi bay về) |
| 8 | Apache | 431 | Búa tách 3 theo lực 2, mỗi búa ¼ sức công |
| 9 | Magenta | 410 | Tia laser phản chiếu |

Chỉ số trong trận (luật Tự do):
- **Máu** = 1000 + 10 × (điểm máu + đồ) + % từ đồ
- **Sức công** = sát thương gốc × (100 + (điểm công + đồ)/3 + % đồ) / 100
- **Phòng thủ, may mắn, đồng đội** = 10 × (điểm + đồ) + % từ đồ
- Mỗi cấp được 3 điểm tiềm năng; cấp tối đa 254 (759 điểm). Đặt lại tiềm năng miễn phí trên trang Nhân vật.

---

## 4. Nội tại bộ +14

**Điều kiện**: mặc đủ **5 món** (súng, nón, áo, kính, balo) **cùng một bộ**, **cùng +14**, đúng nhân vật. Trang phục VIP mặc ngoài không ảnh hưởng.

**Sức mạnh nội tại theo bộ**:

| Bộ | 1 | 2 | 3 | 4 | Bạc | Vàng |
|---|---|---|---|---|---|---|
| Sức mạnh | 50% | 60% | 70% | 80% | 90% | **100%** |

Các con số dưới đây là ở bộ vàng (100%). Bộ thấp hơn nhân theo bảng trên. Nội tại **tắt** ở luật Sát thương gốc.

### Gunner — "Súng Thần Công"
- Khi Gunner bắn, **không ai được may mắn**, kể cả Gunner.
- **POW** bắn **một viên thần công khổng lồ**: sát thương **150%**, hố to **gấp đôi**.
- Nộ tích nhanh hơn **50%**.
- **Phản chấn**: mỗi đối thủ bị thần công hạ gục, Gunner nhận lại **20%** sát thương viên đạn đã gây cho người đó.

### Miss 6
- Cứ **phát trúng thứ 3** thì bỏ qua toàn bộ phòng thủ và chí mạng **x1,5**.

### Electrician — "Tê liệt"
- Đối thủ trúng **cả 3 tia** trong một phát sẽ bị **choáng, mất lượt kế tiếp**.

### King Kong — "Bàn thạch"
- Mỗi vòng **đứng yên**: giảm **10%** sát thương nhận, tối đa **20%**.
- Đứng yên đủ **2 vòng**: đất dưới chân **không bị phá** cho tới khi di chuyển.
- Khi đang đứng trên nền đá: chuối nhẹ hơn **10%**, và mỗi vòng đứng yên tiếp theo hố chuối nhỏ lại **10% → 15% → 30%** (giữ 30% từ đó).
- Di chuyển, dịch chuyển hay bị hất văng đều mất hết tích lũy.

### Rocketer — "WorldWar"
- **POW** không bắn tên lửa mà bắn **pháo hiệu không chịu gió**, ngắm được mọi góc.
- Chỗ pháo hiệu rơi: máy bay thả **3 bom hạt nhân** cách nhau 30, mỗi quả nổ bán kính 70 với sát thương POW (**gấp đôi sức công**). Bom **không bao giờ** làm Rocketer mất máu.
- **Phóng xạ** lan từ vụ nổ, tối đa **10%** sức công POW, nhạt dần, hết hẳn ở khoảng cách 450. Chỉ trúng Rocketer nếu đứng trong bán kính 90.
- Nộ tích nhanh hơn **40%**. Phát thường không đổi.

### Granos
- Đạn của Granos đào hố **to gấp đôi** (trước đây gấp 3; giảm vì một nửa số trận thắng là đối thủ rơi khỏi map).

### Chicky — "Trứng Phục sinh"
- **Đẻ trứng** dưới chân đối thủ khi: trúng **cả gà lẫn trứng** trong một phát, **hoặc** quả trứng gà **thứ 5** trúng đối thủ trong trận.
- Mỗi lúc chỉ **một quả** chờ nở. Trứng mang màu skin của gà.
- Quả trứng ghi nhớ sát thương gà + trứng đã gây trong lượt đẻ. **2 vòng sau** trứng nở thành cột nấm khổng lồ, **không đào đất**:
  - Chicky và **cả đội** (đứng ở đâu cũng được) hồi **10%** lượng sát thương đó (số xanh).
  - Đối thủ trong bán kính 50 mất **25%** lượng đó, là **sát thương chuẩn**.

### Tarzan — "Khế Ước Rừng Xanh"
- Giảm **8%** sát thương nhận.
- Boomerang **không chịu gió** (nên đường ngắm luôn đúng), nổ rộng **gấp rưỡi**, trúng lúc bay về mạnh hơn **20%**.
- Đi **120 bước** mỗi lượt (thường 60).
- **Khế ước với King Kong**:
  - Khác phe: Tarzan và King Kong gây cho nhau **một nửa** sát thương (bất kể bộ đồ).
  - Cùng phe: cả hai **+20%** sát thương gây ra và **−10%** sát thương nhận khi cả hai còn sống.

### Apache — "Thợ săn ẩn nấp"
- Cứ **4 búa** (thường hoặc POW) trúng đối thủ thì Apache **tàng hình** với đối thủ và **đi xa gấp đôi**, cho tới khi mất máu; sau đó đếm lại.
- **POW**: mỗi búa trúng đối thủ hồi **10% máu tối đa**.

### Magenta — "Người Ngoài Hành Tinh"
- POW laser trúng đối thủ thì gọi một **UFO phe Magenta** lơ lửng trên đầu đối thủ đó (mỗi lúc một chiếc).
- Tới lượt, UFO bắn laser thẳng xuống; lượt sau bay tới trên đầu đối thủ gần nhất rồi bắn tiếp, cho tới khi bị bắn hạ hoặc Magenta gục.
- UFO có **15% máu tối đa** của Magenta, laser bằng **50% sức công**, chỉ trúng phe đối thủ. Sát thương tính cho Magenta.
- Đối thủ bắn trúng UFO được hồi **5%** lượng máu bắn mất của nó.

---

## 5. Vật phẩm trong trận

| ID | Vật phẩm | Giá (xu) | Ghi chú |
|---|---|---|---|
| 0 | HP | miễn phí | |
| 1 | Teleport | miễn phí | |
| 2 | Bắn x2 | 200 | |
| 3 | Di chuyển x2 | 70 | |
| 4 | Tàng hình | 100 | |
| 5 | Ngưng gió | 100 | Lượt dùng không có gió |
| 6 | Bom phá đất | 200 | |
| 7 | Lựu đạn | 200 | |
| 8 | Bom hủy diệt | 500 | |
| 9 | Tơ nhện | 200 | |
| 10 | HP đồng đội | 700 | |
| 11 | Đạn trái phá | 500 | |
| 16 | Đạn lazer | 500 | |
| 17 | Vòi rồng | 200 | |
| 18 | Chuột gắn bom | 400 | |
| 19 | Tên lửa | 300 | |
| 20 | Đạn xuyên đất | 300 | |
| 21 | Sao băng | 400 | |
| 22 | Mưa đạn | 500 | |
| 23 | Khoang đất | 150 | |
| 24 | Tự sát | 600 | |
| 25 | Bom mù | 600 | |
| 26 | Khoang đất 2 | 500 | |
| 27 | UFO | 500 | Bay tới trên đầu đối thủ gần nhất, bắn laser bằng sức công người dùng, chỉ trúng đối thủ, 3 phát rồi biến mất (đã sửa lỗi 0 sát thương) |
| 28 | Đóng băng | 300 | |
| 29 | Khói độc | 700 | |
| 30 | Tơ nhện 2 | 600 | |
| 31 | Bom hẹn giờ | 400 | |
| 32 | HP 50% | 500 | |
| 33 | HP 100% | 1000 | |
| 34 | Vô hình | 500 | |
| 35 | Ma cà rồng | 800 | |
| **36** | **Chỉ kẻ đầu** | 600 | **Mới.** 2 phát có đường ngắm tính cả gió |
| **37** | **Bê tông cốt thép** | 500 | **Mới.** Đất dưới chân không bị phá trong 2 lượt của mình |
| **38** | **Cơn mưa độc** | 700 | **Mới.** Phát bắn thành mưa độc (rơi như Sao băng); ai trúng chỉ hồi một nửa máu trong 3 lượt |
| 12–15 | Túi đựng item 1–4 | 100–400 | Mở thêm ô mang vật phẩm |

Mỗi loại tối đa 99 cái.

---

## 6. Trang bị, nâng cấp, rương

### Nâng cấp +1 … +14 (trên web)
- +13 tỉ lệ **1/500**, +14 tỉ lệ **1/1000**, chắc chắn thành công ở lần thứ 500/1000 (bảo đảm).
- **Bảo hiểm** sơ/trung/cao cấp giữ cấp khi thất bại.
- Bộ vàng cần nhân vật cấp 50 để mặc.

### Rương may mắn (web → Cửa hàng)
| Rương | Giá | Quà |
|---|---|---|
| Đồng | 15.000 xu / 15 lượng | 1 vũ khí hoặc trang bị ngẫu nhiên |
| Bạc | 30 lượng | 1 trang phục VIP (Apache ~1% ra Skin Thor) |
| Vàng | 3.000.000 xu / 300 lượng | 1 trang bị **+10** |
| Bạch kim | — | Trang bị **+14** |

### Skin Thor (Apache)
- 1.000 lượng, vĩnh viễn, mặc ô giáp. Chỉ cộng chỉ số ghi trong danh mục (+5% mọi chỉ số như Iron Man); ở luật Cân bằng và Sát thương gốc chỉ là trang phục.
- Đạn theo trang phục: Thor (Mjölnir bạc ánh xanh), Batman, xác ướp, bí ngô có đạn vẽ riêng.

---

## 7. Meta

### Cân bằng sau bản 25/10/2026
Theo commit `9ad6a0e`, bộ giả lập mới (`scripts/sim/DuelSim.java`) đo trên luật Tự do, cấp 254, hai bên đủ bộ vàng +14, 4 mức tay nghề từ ngắm hoàn hảo tới ngắm ẩu, 2.250 trận mỗi mức:
- **Mọi tướng trung bình 44–54%** tỉ lệ thắng.
- **Ngắm chuẩn** có lợi cho **Apache** (dễ đủ 4 búa trúng để tàng hình).
- **Ngắm ẩu** có lợi cho **Granos** (hố to làm đối thủ rơi) và **King Kong** (đứng yên giảm sát thương).

> Mình chưa tự chạy lại giả lập trên bản này; các con số trên lấy từ commit.

### Trước bản cân bằng (để so sánh)
Giả lập 20.000 trận 1v1 trên code ngày 24/10 (cùng điều kiện, bot ngắm có sai số):

| Tướng | Tỉ lệ thắng | Nguyên nhân chính | Đã sửa trong bản mới |
|---|---|---|---|
| Granos | 84.7% | Hố gấp 3 đẩy đối thủ rơi map | Hố gấp 2 |
| Chicky | 74.4% | Gà + trứng 100% sức công 470, trứng hồi/gây nhiều | Gà 55%, trứng 30%, Phục sinh 10%/25%, mỗi lúc 1 quả |
| Miss 6 | 62.1% | Chí mạng xuyên giáp mỗi phát thứ 3 | Không đổi |
| Gunner | 49.4% | | Nộ +50% |
| Electrician | 45.3% | | Không đổi |
| Magenta | 44.2% | | Nội tại mới (UFO) |
| Apache | 37.8% | Búa ¼ sức công, tàng hình khó kích hoạt | Nội tại mới (4 búa → tàng hình) |
| Rocketer | 37.0% | Bom WorldWar chỉ 1x sức công | Bom x2, không tự sát, nộ +40% |
| Tarzan | 36.0% | | Giảm nhẹ nội tại |
| King Kong | 28.7% | Chuối trên đá −30% | Chỉ còn −10% |

### Gợi ý lối chơi theo meta
- **Gunner**: tích nộ nhanh, giữ POW thần công làm đòn kết liễu, nhưng cẩn thận phản chấn 20% khi máu thấp. Nội tại tắt may mắn khắc chế build may mắn của đối thủ.
- **Miss 6**: đếm phát trúng; phát thứ 3 nên nhắm mục tiêu nhiều giáp.
- **Electrician**: bắn gần để cả 3 tia cùng trúng → khóa lượt đối thủ; rất mạnh trong đấu đội.
- **King Kong**: chọn chỗ đứng tốt rồi **không di chuyển**; từ vòng 2 nền thành đá và giảm 20% sát thương. Ghép với Tarzan cùng phe.
- **Rocketer**: POW pháo hiệu không chịu gió nên chính xác nhất game; đứng xa vụ nổ > 90 để tránh phóng xạ.
- **Granos**: bắn dưới chân đối thủ ở mép bản đồ để làm rơi.
- **Chicky**: chỉnh lực 2 để trứng rơi đúng đầu đối thủ (trúng cả gà lẫn trứng mới đẻ trứng Phục sinh); trứng hồi máu cả đội.
- **Tarzan**: đường ngắm chuẩn tuyệt đối (không chịu gió), 120 bước giúp né; ghép với King Kong.
- **Apache**: cần ngắm chuẩn để 4 búa trúng → tàng hình và chạy x2; POW để hồi máu.
- **Magenta**: POW sớm để UFO có thời gian bắn; đối thủ nên ưu tiên bắn hạ UFO (họ được hồi 5%).

---

## 8. Tính năng ngoài trận

### Trang web (cổng 8123)
- **Đăng ký tài khoản** (`/`): tên 3–20 chữ/số, mật khẩu 6–30 chữ/số, dùng được ngay.
- **Trang Nhân vật** (`/nhan-vat/`): chọn nhân vật, cộng/đặt lại điểm tiềm năng, rương đồ (tối đa 120 món), mặc/tháo, mua, bán, nâng cấp +14, khảm ngọc, rương may mắn, mô tả nội tại, tab **Thành tích** (lịch sử 30 trận, sát thương gây/nhận, máu hồi, số mạng hạ, đối đầu, điểm = 3·thắng + hòa, bảng xếp hạng top 50). Thay đổi đồng bộ ngay vào game.
- **Trang Admin** (`/admin/`): cộng xu/lượng, tăng cấp, đặt lại mật khẩu, đổi tên, sửa giá/chỉ số nhân vật và đồ, luật trận mặc định, xem tỉ lệ thắng (tab Cân bằng). **Super-admin** tặng bộ +14 (kèm sát thương ẩn tùy chọn) và vật phẩm.

### Client
- PC, Mac (launcher tự cập nhật khi mở game), Android.
- Khóa phiên bản: hiện bắt buộc **client LAN 6**.
- Phím: ←/→ hoặc A/D di chuyển, ↑/↓ hoặc W/S chỉnh góc, giữ/thả Enter căn lực/bắn, Space POW, Q túi vật phẩm, 1–8 dùng ô, R camera tự do, C/Ctrl chat, Esc menu.
- Lệnh chat: `lm 25` (lực max), `lm 25 12` (lực max + lực 2 cho Chicky/Apache), `g 45` / `gs 20` (đặt góc), `map N` (luyện tập).
