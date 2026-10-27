# Hệ thống Lõi trong trận (bản thiết kế)

> **Trạng thái:** mới là thiết kế, chưa làm. Lên ý tưởng ngày 26/10/2026; hiện ưu tiên cân bằng game, sẽ làm khi
> được nhắc lại.
> **Ý tưởng:** như ARAM Hỗn Loạn: trong trận mỗi người chọn 1 trong 3 "lõi" nâng cấp, mỗi trận một khác.
> **Ràng buộc:** hệ thống liên kết nhân vật (nội tại đội `SetTeam`: Kẻ Ngoại Lai, Liên Minh Binh Bét, Nhà Khoa Học,
> và Khế Ước Rừng Xanh của Tarzan) **giữ nguyên, không thêm liên kết mới**, không lõi nào đụng tới nó.

## 28 lõi, 4 bậc

Các con số là bản đầu, chỉnh lại bằng mô phỏng trước khi đưa vào game.

### 💎 Kim cương: siêu mạnh, đổi hẳn cách chơi

| Lõi | Hiệu ứng |
|---|---|
| **Song Kích** | Mỗi lượt bắn 2 phát liền nhau, mỗi phát 65% sát thương |
| **Thiên Nhãn** | Mọi phát (cả POW) đều có đường ngắm chấm tính cả gió, như vật phẩm 36 Chỉ kẻ đầu nhưng vĩnh viễn |
| **Bất Tử Điểu** | Lần đầu bị hạ (hết máu hoặc rơi khỏi bản đồ) hồi sinh ở chỗ đứng an toàn gần nhất với 40% máu; một lần mỗi trận |
| **Ma Cà Rồng** | Hồi 35% sát thương mình gây ra |
| **Lưỡi Hái Tử Thần** | Đối thủ dưới 25% máu mà trúng đạn của mình thì gục ngay |
| **Hố Đen** | Mỗi vụ nổ hút đối thủ trong 120 px về phía tâm nổ 40 px; hố to gấp đôi |
| **Mưa Sao Băng** | Đầu mỗi lượt của mình, 2 thiên thạch rơi trúng đối thủ ngẫu nhiên, mỗi viên 40% sức tấn công |

### 🥇 Vàng: mạnh rõ rệt

| Lõi | Hiệu ứng |
|---|---|
| **Sức Mạnh Khổng Lồ** | +25% sát thương |
| **Giáp Titan** | Nhận ít hơn 20% sát thương |
| **Nộ Cuồng** | Thanh nộ đầy nhanh gấp đôi |
| **Xuyên Giáp** | Bỏ qua 50% phòng thủ đối thủ |
| **Phản Đòn** | Trả lại 25% sát thương nhận vào cho người bắn |
| **Kho Báu** | Cứ 2 vòng nhận 1 vật phẩm ngẫu nhiên vào ô trống |
| **Thần Tốc** | Gấp đôi số bước đi mỗi lượt |

### 🥈 Bạc: theo lối chơi

| Lõi | Hiệu ứng |
|---|---|
| **Gió Thuận** | Gió chỉ tác động 50% lên đạn của mình |
| **Xạ Thủ Tầm Xa** | +20% sát thương khi trúng xa hơn 400 px |
| **Đấu Sĩ Cận Chiến** | +25% sát thương khi trúng trong 150 px |
| **Khiên Năng Lượng** | Phát trúng đầu tiên mỗi vòng giảm 40% sát thương |
| **Tái Sinh** | Đầu mỗi vòng hồi 4% máu tối đa |
| **Thợ Mỏ** | Hố to hơn 50%, dễ đánh sập chỗ đứng đối thủ |
| **Bàn Tay May Mắn** | +15% tỉ lệ ra đòn may mắn |

### 🥉 Đồng: nhẹ, hỗ trợ

| Lõi | Hiệu ứng |
|---|---|
| **Bước Nhanh** | +25% bước đi |
| **Da Dày** | +10% máu tối đa |
| **Đạn Nặng** | +8% sát thương |
| **Khởi Động Nộ** | Nhận ngay 50% thanh nộ khi chọn |
| **Tay Vững** | Thanh lực hiện vạch lực của phát bắn trước để căn lại |
| **Chống Choáng** | Miễn 1 lần choáng, tê liệt (Electrician, hệ Lôi) hoặc Cóng (hệ Băng) |
| **Săn Quà** | Hộp quà (từ vòng 10) cho thêm 1 vật phẩm, kèm hồi 10% máu |

## Cách nhận lõi trong trận

- **3 lần chọn mỗi trận.** Mỗi lần đưa 3 thẻ cùng một bậc, chọn 1:

  | Lần chọn | Vòng | Tỉ lệ bậc |
  |---|---|---|
  | 1 | 2 | Đồng 60%, Bạc 40% |
  | 2 | 5 | Bạc 60%, Vàng 40% |
  | 3 | 8 | Vàng 70%, Kim cương 30% |

  Từ vòng 10 đã có hộp quà bay, nên không đặt lần chọn nào sau đó.
- **Lật kèo:** phe đang ít máu hơn (tổng máu) được cộng 15% tỉ lệ lên bậc.
- **Đổi thẻ:** mỗi người đổi cả 3 thẻ được một lần mỗi trận.
- **Giới hạn:** mỗi người tối đa 1 lõi Kim cương, không nhận trùng lõi.
- **Thời gian chọn:** 20 giây, chọn được cả lúc đang chờ lượt đối thủ; hết giờ thì chọn ngẫu nhiên, trận không bị kéo dài.
- **Hiển thị:** biểu tượng lõi đã chọn cạnh thanh máu góc trái, rê chuột vào xem mô tả; màu theo bậc (đồng, bạc, vàng,
  kim cương như đồ +15/+16 trên web).
- **Luật trận:** bật ở Tự do và Cân bằng, tắt ở Sát thương gốc (như nội tại bộ). Chủ phòng có thêm tùy chọn bật/tắt lõi.

## Cân bằng

- Chỉnh bằng mô phỏng tay đôi và 2v2 (`scripts/sim`, `Run-DuelSim.ps1`) trước khi bật: nhất là Lưỡi Hái Tử Thần,
  Song Kích, Ma Cà Rồng, ba lõi dễ quá mạnh khi cộng với nội tại bộ +14.
- Lõi cộng sát thương cộng dồn kiểu **cộng** (không nhân) với nội tại bộ và hệ +15, để không bùng nổ.
- Thử ổn định bằng bot chơi tự động (hai client thật, ẩn) để chắc không kẹt lượt, không văng game.

## Ghi chú làm (chỗ trong code đã có để dựa vào)

Server quyết định và tính mọi hiệu ứng; client chỉ hiện thẻ, gửi lựa chọn và vẽ biểu tượng.

| Lõi | Dựa vào |
|---|---|
| Song Kích | số lần bắn `Player.nshoot` (client đã đọc `nShoot` 2, `BM.shotPaths` chia đường đạn theo lần bắn) |
| Thiên Nhãn | đường ngắm của vật phẩm 36 (`CPlayer.guideShown`, `headGuideShots`), cần cờ từ server trong tin 114 |
| Bất Tử Điểu | chỗ người chơi gục (`Player.updateHP`, rơi khỏi bản đồ), đặt lại chỗ an toàn (`chuanHoaXY`), báo vị trí bằng tin 53 |
| Ma Cà Rồng, Tái Sinh | hồi máu như UFO của Magenta, `Player.updateHP`; hồi đầu vòng ở `Player` lượt mới |
| Lưỡi Hái Tử Thần | đường tính sát thương `SetPassive` (như sát thương chuẩn `trueDamage`) |
| Hố Đen | cú hất của Gunner (`SetPassive.kick`, `kickSpot`; cú kéo của Tarzan đã bỏ ngày 26/10/2026), cỡ hố `SetPassive.holeScale` |
| Mưa Sao Băng | đạn thiên thạch vật phẩm 23 (`ItemMeteorBullet`), bắn thêm khi sang lượt (`MapData`) |
| Sức Mạnh, Giáp, Xuyên Giáp, Phản Đòn, Đạn Nặng, Khiên, Xạ Thủ, Đấu Sĩ | đường tính sát thương `Player.attBullet` / `updateAllAffect` / `SetPassive` |
| Nộ Cuồng, Khởi Động Nộ | chỗ tích thanh nộ của `Player` |
| Kho Báu, Săn Quà | phát vật phẩm vào ô trống như hộp quà `MapData.openGift` (tin 119, -128) |
| Thần Tốc, Bước Nhanh | số bước `Player.theluc` (`SetPassive.steps`), client đọc bước đi qua tin 114 |
| Gió Thuận | hệ số gió của từng người bắn trong `Gun` (`windX`, `windY`) |
| Thợ Mỏ | `SetPassive.holeScale` |
| Bàn Tay May Mắn | lần tung đòn may mắn của `Player` |
| Da Dày | cộng `hpMax` và máu lúc chọn |
| Chống Choáng | đếm miễn trong `SetPassive.shock` và đóng băng của `SetElement` |
| Tay Vững | chỉ ở client (vạch lực trên thanh lực), server báo cờ |

Giao thức cần thêm: một tin server mời chọn (3 mã lõi, bậc, còn đổi thẻ không, thời hạn), một tin client trả lời
(mã lõi hoặc đổi thẻ), và danh sách lõi của từng người cuối tin 114 để mọi client vẽ biểu tượng. Tăng số build client
(`GameMidlet.LAN_BUILD`, `ClientVersion.LATEST`); client cũ vẫn có hiệu ứng ở server, chỉ không thấy thẻ (hết giờ chọn
ngẫu nhiên).
