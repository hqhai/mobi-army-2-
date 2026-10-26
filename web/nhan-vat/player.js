(function () {
    'use strict';

    var number = new Intl.NumberFormat('vi-VN');
    // Character sheets: [file, frame width, frame height, portrait frame, cheer frame].
    var PORTRAITS = {
        0: ['yellowP', 24, 24, 8, 9], 1: ['cuteGirl', 24, 24, 8, 9], 2: ['greenP', 24, 24, 8, 9],
        3: ['conKhiP', 30, 32, 0, 0], 4: ['rocketer', 24, 24, 8, 9], 5: ['robot', 24, 24, 8, 9],
        6: ['ga', 29, 24, 8, 9], 7: ['tazz', 32, 32, 8, 9], 8: ['apache', 24, 24, 8, 9], 9: ['magenta', 32, 32, 0, 0]
    };
    var STATS = [
        {name: 'Máu', hint: 'Mỗi điểm +10 máu', short: 'Máu'},
        {name: 'Sức mạnh', hint: '3 điểm thêm 1% sát thương', short: 'SM'},
        {name: 'Phòng thủ', hint: 'Mỗi điểm +10 phòng thủ', short: 'PT'},
        {name: 'May mắn', hint: 'Mỗi điểm +10 may mắn', short: 'MM'},
        {name: 'Đồng đội', hint: 'Mỗi điểm +10 đồng đội', short: 'ĐĐ'}
    ];
    var SLOTS = ['Súng', 'Nón', 'Áo', 'Kính', 'Balo'];
    var state = {profile: null, shop: null, tab: 'points', shopTab: 'chests', typeFilter: 'all', pending: [0, 0, 0, 0, 0],
        sellMode: false, sellPick: new Set()};

    function $(id) {
        return document.getElementById(id);
    }

    function el(tag, props, children) {
        var node = document.createElement(tag);
        Object.entries(props || {}).forEach(function (entry) {
            var key = entry[0];
            var value = entry[1];
            if (value == null || value === false) return;
            if (key === 'text') node.textContent = value;
            else if (key === 'className') node.className = value;
            else if (key.indexOf('on') === 0) node.addEventListener(key.slice(2), value);
            else node.setAttribute(key, value === true ? '' : value);
        });
        (children || []).forEach(function (child) {
            if (child != null) node.append(child instanceof Node ? child : String(child));
        });
        return node;
    }

    async function api(method, path, body) {
        var options = {method: method, headers: {}, credentials: 'same-origin'};
        if (method !== 'GET') {
            options.headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(body || {});
        }
        var response;
        try {
            response = await fetch('/api/player/' + path, options);
        } catch (error) {
            throw new Error('Không kết nối được tới máy chủ.');
        }
        var data = {};
        try {
            data = await response.json();
        } catch (ignored) {
            // empty body
        }
        if (response.status === 401 && path !== 'login') showLogin();
        if (!response.ok) throw new Error(data.error || 'Lỗi ' + response.status + '.');
        return data;
    }

    // --- sprites -----------------------------------------------------------------------------

    function portrait(glassId, scale, cheer) {
        var spec = PORTRAITS[glassId] || PORTRAITS[0];
        var node = el('span', {className: 'sprite'});
        node.style.width = spec[1] + 'px';
        node.style.height = spec[2] + 'px';
        node.style.backgroundImage = 'url(../img/chars/' + spec[0] + '.png)';
        node.style.backgroundPosition = '0 -' + (cheer ? spec[4] : spec[3]) * spec[2] + 'px';
        node.style.zoom = scale;
        return node;
    }

    function gear(item, box) {
        var holder = el('span', {className: 'thumb' + (box < 60 ? ' small' : '')});
        if (item.icon) {
            // Own picture (Thor costumes), scaled to fit the box.
            var size = Math.max(16, Math.min(box - 6, 48));
            holder.append(el('img', {className: 'own-icon', src: item.icon, alt: '', style: 'max-height:' + size + 'px;max-width:' + size + 'px'}));
            return holder;
        }
        if (item.sprite) {
            var x = item.sprite[0], y = item.sprite[1], w = item.sprite[2], h = item.sprite[3];
            var node = el('span', {className: 'sprite'});
            node.style.width = w + 'px';
            node.style.height = h + 'px';
            node.style.backgroundImage = 'url(/game-res/bigImage' + item.glassId + '.png)';
            node.style.backgroundPosition = '-' + x + 'px -' + y + 'px';
            node.style.zoom = Math.max(1, Math.min(3, Math.floor((box - 8) / Math.max(w, h))));
            holder.append(node);
        }
        return holder;
    }

    function itemIcon(id, scale) {
        var node = el('span', {className: 'sprite'});
        node.style.width = '16px';
        node.style.height = '16px';
        // LAN items 36-38 are not in the game's strip; each has its own icon.
        var own = id >= 36 && id <= 38;
        node.style.backgroundImage = own ? 'url(../img/item' + id + '.png)' : 'url(../img/items.png)';
        node.style.backgroundPosition = own ? '0 0' : '0 -' + (id + 2) * 16 + 'px';
        node.style.zoom = scale;
        return node;
    }

    function specialIcon(id, scale) {
        var node = el('img', {className: 'sprite', src: '/game-res/icon/item/' + id + '.png', alt: ''});
        node.style.zoom = scale;
        return node;
    }

    function framed(icon, small) {
        return el('span', {className: 'thumb' + (small ? ' small' : '')}, [icon]);
    }

    // --- feedback ----------------------------------------------------------------------------

    var toastTimer;

    function toast(text, isError) {
        var box = $('toast');
        box.textContent = text;
        box.className = 'toast' + (isError ? ' error' : '');
        box.hidden = false;
        clearTimeout(toastTimer);
        toastTimer = setTimeout(function () { box.hidden = true; }, isError ? 5000 : 3200);
    }

    var cheerTimer;

    function cheer() {
        var stage = $('portrait');
        clearTimeout(cheerTimer);
        stage.replaceChildren(portrait(state.profile.selectedGlass, 4, true));
        stage.classList.add('cheer');
        cheerTimer = setTimeout(function () {
            stage.classList.remove('cheer');
            stage.replaceChildren(portrait(state.profile.selectedGlass, 4, false));
        }, 1300);
    }

    function confirmBuy(text, picture, okLabel) {
        return new Promise(function (resolve) {
            var dialog = $('confirm');
            $('confirm-text').textContent = text;
            $('confirm-ok').firstElementChild.textContent = okLabel || 'Mua';
            $('confirm-pic').replaceChildren(picture);
            dialog.returnValue = '';
            dialog.addEventListener('close', function onClose() {
                dialog.removeEventListener('close', onClose);
                resolve(dialog.returnValue === 'ok');
            });
            dialog.showModal();
            $('confirm-ok').focus();
        });
    }

    async function run(action, successful) {
        try {
            var result = await action();
            if (result && result.character) setProfile(result);
            if (result && result.message) toast(result.message);
            if (successful) successful(result);
            return result;
        } catch (error) {
            toast(error.message, true);
            return null;
        }
    }

    // --- login -------------------------------------------------------------------------------

    function showLogin() {
        $('app').hidden = true;
        $('login-view').hidden = false;
        $('login-password').value = '';
        $('login-username').focus();
    }

    function showApp(profile) {
        $('login-view').hidden = true;
        $('app').hidden = false;
        setProfile(profile);
        selectTab(state.tab);
    }

    $('login-form').addEventListener('submit', async function (event) {
        event.preventDefault();
        var errorBox = $('login-error');
        errorBox.hidden = true;
        $('login-submit').disabled = true;
        try {
            showApp(await api('POST', 'login', {username: $('login-username').value.trim(), password: $('login-password').value}));
        } catch (error) {
            errorBox.textContent = error.message;
            errorBox.hidden = false;
        } finally {
            $('login-submit').disabled = false;
        }
    });

    $('logout').addEventListener('click', async function () {
        try {
            await api('POST', 'logout');
        } catch (ignored) {
            // already signed out
        }
        state.shop = null;
        showLogin();
    });

    // --- profile -----------------------------------------------------------------------------

    function setProfile(profile) {
        var glassChanged = !state.profile || state.profile.selectedGlass !== profile.selectedGlass;
        state.profile = profile;
        state.pending = [0, 0, 0, 0, 0];
        if (glassChanged) {
            state.shop = null;
            state.sellMode = false;
        }
        renderHero();
        renderPicker();
        renderPoints();
        renderBag();
        renderGems();
        renderUpgrade();
        renderStats();
        if (state.tab === 'shop') {
            if (state.shop) renderShop();
            else loadShop();
        }
    }

    function renderHero() {
        var p = state.profile;
        var c = p.character;
        clearTimeout(cheerTimer); // a cheer still running from an earlier action would draw the old character back
        $('portrait').classList.remove('cheer');
        $('portrait').replaceChildren(portrait(p.selectedGlass, 4, false));
        $('player-name').textContent = p.name;
        $('char-name').textContent = c.name;
        $('char-level').textContent = 'Cấp ' + c.level;
        $('exp-fill').style.width = c.expPercent + '%';
        $('exp-text').textContent = c.expPercent + '%';
        $('wallet-xu').textContent = number.format(p.xu);
        $('wallet-luong').textContent = number.format(p.luong);
        var line = $('player-state');
        line.className = 'state' + (p.busy ? ' warn' : '');
        line.textContent = p.busy ? 'Bạn đang ở trong phòng hoặc trận đấu trong game: rời phòng rồi mới mua hay đổi đồ được.'
            : p.online ? 'Bạn đang online trong game, thay đổi ở đây sẽ hiện ngay trong game.' : '';
    }

    function renderPicker() {
        var picker = $('picker');
        picker.replaceChildren();
        state.profile.characters.forEach(function (c) {
            var current = c.id === state.profile.selectedGlass;
            picker.append(el('button', {
                type: 'button',
                className: 'pick' + (current ? ' current' : ''),
                disabled: !c.open,
                'aria-pressed': current ? 'true' : 'false',
                title: c.open ? c.name + ' - cấp ' + c.level : c.name + ' - chưa mở (mua trong game)',
                onclick: function () {
                    if (current) return;
                    run(function () { return api('POST', 'select', {glassId: c.id}); });
                }
            }, [
                el('span', {className: 'face'}, [portrait(c.id, 2, false)]),
                el('b', {text: c.name}),
                el('span', {text: c.open ? 'Cấp ' + c.level : 'Chưa mở'})
            ]));
        });
    }

    // --- stat points -------------------------------------------------------------------------

    function renderPoints() {
        var c = state.profile.character;
        var used = state.pending.reduce(function (a, b) { return a + b; }, 0);
        var left = c.points - used;
        $('points-left').textContent = number.format(left);
        var list = $('stats');
        list.replaceChildren();
        STATS.forEach(function (stat, i) {
            var add = function (amount) {
                state.pending[i] = Math.max(0, state.pending[i] + amount);
                renderPoints();
            };
            list.append(el('div', {className: 'stat'}, [
                el('div', {className: 'stat-name'}, [stat.name, el('small', {text: stat.hint + ' · đã cộng ' + c.ability[i]})]),
                el('div', {className: 'stat-value'}, [
                    number.format(c.stats[i]),
                    state.pending[i] ? el('span', {className: 'pending', text: ' +' + state.pending[i]}) : null
                ]),
                el('div', {className: 'stepper'}, [
                    el('button', {type: 'button', className: 'round', text: '−', 'aria-label': 'Bớt 1 điểm ' + stat.name,
                        disabled: state.pending[i] === 0, onclick: function () { add(-1); }}),
                    el('button', {type: 'button', className: 'round', text: '+', 'aria-label': 'Cộng 1 điểm ' + stat.name,
                        disabled: left < 1, onclick: function () { add(1); }}),
                    el('button', {type: 'button', className: 'round wide', text: '+5', 'aria-label': 'Cộng 5 điểm ' + stat.name,
                        disabled: left < 5, onclick: function () { add(5); }})
                ])
            ]));
        });
        $('points-save').disabled = used === 0;
        $('points-reset').hidden = used === 0;
    }

    $('points-reset').addEventListener('click', function () {
        state.pending = [0, 0, 0, 0, 0];
        renderPoints();
    });

    $('points-refund').addEventListener('click', async function () {
        var c = state.profile.character;
        if (!await confirmBuy('Đặt lại tiềm năng của ' + c.name + '? Toàn bộ điểm đã cộng được trả lại để bạn cộng lại từ đầu.',
            el('span'), 'Đặt lại')) return;
        state.pending = [0, 0, 0, 0, 0];
        run(function () { return api('POST', 'points/reset'); });
    });

    $('points-save').addEventListener('click', function () {
        var add = state.pending.slice();
        run(function () { return api('POST', 'points', {add: add}); }, cheer);
    });

    // --- bag ---------------------------------------------------------------------------------

    function statTags(item) {
        var tags = [];
        STATS.forEach(function (stat, i) {
            if (item.ability[i]) tags.push(el('span', {className: 'tag-stat', text: (item.ability[i] > 0 ? '+' : '') + item.ability[i] + ' ' + stat.short}));
            if (item.percent[i]) tags.push(el('span', {className: 'tag-stat pct', text: (item.percent[i] > 0 ? '+' : '') + item.percent[i] + '% ' + stat.short}));
        });
        return el('div', {className: 'tags'}, tags);
    }

    function daysText(item) {
        if (item.permanent) return 'vĩnh viễn';
        if (item.days <= 0) return 'hết hạn';
        return 'còn ' + item.days + ' ngày';
    }

    // Mirrors SetPassive on the server: set = last word of the gear's name, every piece +14.
    var SET_NAMES = ['1', '2', '3', '4', 'bạc', 'vàng'];
    var SET_LABELS = ['bộ 1', 'bộ 2', 'bộ 3', 'bộ 4', 'bộ bạc', 'bộ vàng'];
    var SET_STRENGTH = [50, 60, 70, 80, 90, 100];

    /**
     * Set passives per character id, mirroring mobiarmy.war.SetPassive. detail(s) words the effect at strength s
     * (percent of the full gold-set passive).
     */
    function scaledText(full, s) { return Math.round(full * s / 100); }
    var PASSIVES = {
        0: {name: 'Súng Thần Công', detail: function (s) {
            return 'Khi Gunner bắn, không ai gặp may mắn, kể cả Gunner. Đòn POW bắn ra một viên thần công khổng lồ: sát thương +' + scaledText(50, s)
                + '%, hố rộng gấp ' + (1 + scaledText(100, s) / 100).toLocaleString('vi-VN') + ' lần. Thanh nộ tích nhanh hơn ' + scaledText(50, s)
                + '%. Phản chấn: mỗi đối thủ bị viên thần công hạ gục, Gunner mất máu bằng ' + scaledText(20, s) + '% lượng máu đối thủ đó vừa mất. '
                + 'Hất văng: viên thần công trúng đối thủ nào thì hất đối thủ đó văng ra xa ' + scaledText(40, s) + ' px, ngược phía tâm nổ (gặp vách thì dừng, rơi khỏi bản đồ là chết).';
        }},
        1: {name: 'Mũi tên thứ ba', detail: function (s) {
            return 'Cứ mỗi phát trúng đối thủ thứ 3 gây sát thương chuẩn: bỏ qua toàn bộ phòng thủ, không bị may mắn cứu, và chắc chắn chí mạng x'
                + (1 + scaledText(50, s) / 100).toLocaleString('vi-VN') + '. Bắn trượt không tính; trúng đồng đội không tính. '
                + 'Dịch chuyển chỉ phải chờ 2 lượt giữa hai lần dùng (nhân vật khác chờ 3).';
        }},
        2: {name: 'Tê liệt', detail: function (s) {
            return 'Đạn điện to hơn ' + scaledText(15, s) + '%: nổ rộng hơn và đục hố to hơn chừng ấy. Đối thủ trúng đạn điện trong một phát bắn thì ở lượt kế tiếp của mình: '
                + 'trúng cả 3 viên thì mất lượt (chữ "Choáng" trên đầu); trúng 2 viên thì không đi được và không đổi được góc bắn; '
                + 'trúng 1 viên thì đi ít hơn ' + scaledText(30, s) + '% và không ngắm thấp hơn 60 độ, kể cả bằng lệnh "g" (chữ "Tê liệt" trên đầu).';
        }},
        3: {name: 'Bàn thạch', detail: function (s) {
            return 'Mỗi vòng đứng yên một chỗ King Kong to thêm ' + scaledText(25, s) + '%, tối đa ' + (100 + scaledText(200, s))
                + '% sau 8 vòng: dễ bị bắn trúng hơn nhưng càng to càng lì, tới cỡ tối đa thì nhận ít hơn ' + scaledText(20, s)
                + '% sát thương và thêm ' + scaledText(20, s) + '% máu tối đa (tăng dần theo từng vòng). Đứng yên từ 2 vòng, đất dưới chân không bị phá. '
                + 'Càng to thì chuối thường càng nhỏ và đục hố càng nhỏ, tới cỡ tối đa còn ' + (100 - scaledText(50, s)) + '% (sát thương như thường). POW to theo King Kong nhưng chỉ mạnh thêm tối đa ' + scaledText(30, s)
                + '% khi đạt cỡ tối đa, và không đục đất. Đi bộ, dịch chuyển hay bị hất văng đều mất hết và King Kong về cỡ thường (máu tối đa cũng về như cũ).';
        }},
        4: {name: 'WorldWar', detail: function (s) {
            return 'POW không bắn tên lửa nữa mà bắn pháo hiệu gọi máy bay: pháo hiệu không bị gió ảnh hưởng và được ngắm mọi góc. '
                + 'Pháo hiệu rơi ở đâu, máy bay thả 3 quả bom hạt nhân cách nhau 30 quanh chỗ đó, mỗi quả nổ thành nấm lửa như bom tên lửa '
                + 'của T-Rex với sát thương POW (gấp đôi sức tấn công của Rocketer) và đào hố lớn; bom không làm Rocketer mất máu. '
                + 'Phóng xạ lan ra từ chỗ nổ: đối thủ mất tới ' + scaledText(20, s) + '% sức tấn công của Rocketer, càng xa càng nhẹ; '
                + 'Rocketer chỉ bị khi đứng quá gần. Thanh nộ tích nhanh hơn ' + scaledText(40, s) + '%. Các phát bắn thường như cũ.';
        }},
        5: {name: 'Người Gieo Laze', detail: function (s) {
            return 'Lựu đạn của Granos đục hố rộng gấp ' + (1 + scaledText(80, s) / 100).toLocaleString('vi-VN') + ' lần. '
                + 'POW không bắn 5 quả lựu đạn nữa mà bắn một hạt giống: không bị gió ảnh hưởng, ngắm được mọi góc (như pháo hiệu của Rocketer), '
                + 'rơi xuống không đục đất, không gây sát thương. Chỗ hạt giống rơi, 5 quả lựu đạn từ trên trời rơi xuống nằm thành một hàng ngang, '
                + 'cách nhau 36 px. Sau 2 vòng, mỗi quả bị một tia laze từ trên trời bắn xuống (tia laze của UFO), gây ' + scaledText(1000, s)
                + ' sát thương chuẩn (bỏ qua phòng thủ và may mắn) cho mỗi đối thủ trong vòng 24 px, không trúng phe mình. '
                + 'Đứng giữa hai quả thì trúng cả hai tia. Đối thủ có 2 vòng để tránh khỏi hàng lựu đạn.';
        }},
        6: {name: 'Trứng Phục sinh', detail: function (s) {
            return 'Đối thủ trúng cả gà lẫn trứng trong một phát bắn, hoặc trúng quả trứng gà thứ 5 của Chicky trong trận, sẽ bị đẻ một quả trứng Phục sinh '
                + 'dưới chân, mang màu skin của gà (mỗi lúc Chicky chỉ có một quả trứng Phục sinh chờ nở). Sau 2 vòng trứng nổ thành cột nấm khổng lồ (như bom tên lửa của T-Rex) mà không làm hỏng địa hình: '
                + 'Chicky và cả đội được hồi ' + scaledText(10, s) + '% sát thương mà gà và trứng đã gây ra trong lượt đẻ trứng, dù đứng ở đâu; '
                + 'đối thủ trong tầm trứng mất ' + scaledText(25, s) + '% lượng đó (sát thương chuẩn: bỏ qua phòng thủ và may mắn).';
        }},
        7: {name: 'Khế Ước Rừng Xanh', detail: function (s) {
            return 'Boomerang không bị gió ảnh hưởng, nổ rộng gấp rưỡi và gây thêm 20% khi trúng lúc bay về. '
                + 'POW "Dây leo kéo": boomerang POW trúng đối thủ nào thì Tarzan hú lên, quăng dây kéo đối thủ đó lại gần mình ' + scaledText(60, s)
                + ' px (không sát hơn 20 px); bay qua hố và gờ thấp, gặp vách thì dừng; rơi khỏi bản đồ là chết (tính cho Tarzan). '
                + 'Đi được 120 bước mỗi lượt (thường 60). Vẫn giảm ' + scaledText(8, s) + '% mọi sát thương nhận vào. '
                + 'Leo trèo: chân luôn bám vào địa hình, đi được lên vách và treo ngược dưới mỏm đá (phím phải giữ địa hình bên tay phải, phím trái bên tay trái); '
                + 'khi đang bám vách hay treo ngược thì ngắm được mọi hướng, boomerang bay ra từ giữa thân. Chỉ rơi khi chỗ đang bám bị phá.';
        }},
        8: {name: 'Thợ săn ẩn nấp', detail: function (s) {
            return 'Cứ mỗi 4 búa trúng đối thủ (búa thường hay búa POW đều tính, tiếng nổ lúc búa tách không tính), Apache tàng hình như khi dùng Tàng hình '
                + 'và đi xa gấp đôi (mỗi bước 2 ô) cho tới khi bị mất máu: đối thủ không nhìn thấy Apache. Bị mất máu thì hiện hình, đi như thường '
                + 'và đếm lại 4 búa từ đầu. Khi bắn POW, mỗi búa trúng đối thủ còn hồi cho Apache ' + scaledText(10, s) + '% máu tối đa.';
        }},
        9: {name: 'Người Ngoài Hành Tinh', detail: function (s) {
            return 'Khi tia laser POW của Magenta trúng đối thủ, một UFO phe Magenta được gọi tới lơ lửng trên đầu đối thủ đó (mỗi lúc chỉ một chiếc; '
                + 'bị bắn hạ thì lần POW sau gọi chiếc mới). Tới lượt của mình, UFO bắn laser thẳng xuống chỗ nó đang lơ lửng, '
                + 'lượt sau bay tới trên đầu đối thủ gần nhất rồi lại bắn, cứ thế cho tới khi bị bắn hạ hoặc Magenta gục. '
                + 'UFO có ' + scaledText(15, s) + '% máu tối đa của Magenta, laser gây ' + scaledText(50, s) + '% sức tấn công của Magenta và chỉ trúng phe đối thủ. '
                + 'Đối thủ nào bắn trúng UFO được hồi 20% lượng máu vừa bắn mất của UFO.';
        }}    };

    /** Set tier 1..6 of a full +14 set among the worn gear, or 0, with what is still missing. */
    function setStatus(worn) {
        var pieces = worn.filter(function (item) { return !item.vip && item.type >= 0 && item.type <= 4; });
        var sets = pieces.map(function (item) { return SET_NAMES.indexOf(item.name.split(' ').pop()); });
        var set = sets.length ? sets[0] : -1;
        var sameSet = set >= 0 && sets.every(function (s) { return s === set; });
        var maxed = pieces.filter(function (item) { return (item.plus || 0) >= 14; }).length;
        var need = [];
        if (pieces.length < 5) need.push('mặc đủ 5 món (đang ' + pieces.length + ')');
        if (pieces.length && !sameSet) need.push('cùng một bộ');
        if (maxed < 5) need.push('cả 5 món +14 (đang ' + maxed + '/5)');
        return {tier: need.length ? 0 : set + 1, need: need};
    }

    // Links with other characters, by character id: only the ones this character is part of.
    var PACT = {name: 'Khế Ước Rừng Xanh', with: 'Tarzan ↔ King Kong',
        need: 'Tarzan mặc đủ bộ +14 (King Kong không cần bộ).',
        text: 'Ở hai phe: hai bên chỉ gây cho nhau một nửa sát thương. Cùng phe: cả hai gây thêm 20% và nhận ít hơn 10% sát thương khi cả hai còn sống (theo sức mạnh bộ của Tarzan).'};
    var OUTSIDERS = {name: 'Kẻ Ngoại Lai', with: 'Chicky, Apache, Magenta',
        need: 'Cùng phe có ít nhất 2 nhân vật khác nhau trong nhóm; không cần bộ +14.',
        text: 'Mỗi phát POW của họ có 25% gây sát thương chuẩn (bỏ qua phòng thủ, may mắn và nội tại giảm sát thương).'};
    var PRIVATES = {name: 'Liên Minh Binh Bét', with: 'Gunner, Miss 6, Electrician',
        need: 'Cùng phe có ít nhất 2 nhân vật khác nhau trong nhóm; không cần bộ +14.',
        text: 'Cả đội nhận ít hơn 1% sát thương mỗi vòng (tối đa 30%); ai dưới 50% máu nhận ít hơn thêm 30%.'};
    var SCIENTISTS = {name: 'Nhà Khoa Học', with: 'Rocketer, Granos',
        need: 'Cùng phe có cả Rocketer và Granos; không cần bộ +14.',
        text: 'Bắn thêm 1 viên đạn, kể cả POW (thêm 1 bom hạt nhân, thêm 1 quả lựu đạn laze). Nhà khoa học gục đầu tiên triệu hồi 1 T-rex phe mình '
            + 'với máu bằng 100% máu tối đa và chỉ số của người đó, đánh như boss T-rex; người thứ hai gục thì không triệu hồi nữa.'};
    var LINKS = {0: [PRIVATES], 1: [PRIVATES], 2: [PRIVATES], 3: [PACT], 4: [SCIENTISTS], 5: [SCIENTISTS],
        6: [OUTSIDERS], 7: [PACT], 8: [OUTSIDERS], 9: [OUTSIDERS]};

    /** Chỉ số tab, below the passive: the links of this character with others, if any. */
    function renderLinks() {
        var box = $('link-box');
        var links = LINKS[state.profile.character.id] || [];
        box.hidden = !links.length;
        box.replaceChildren(el('h3', {text: 'Liên kết với nhân vật khác'}));
        links.forEach(function (link) {
            box.append(el('div', {className: 'link-item'}, [
                el('p', {className: 'link-title'}, [el('b', {className: 'passive-name', text: link.name}), el('span', {className: 'link-with', text: ' · ' + link.with})]),
                el('p', {className: 'passive-detail', text: link.text}),
                el('p', {className: 'hint', text: 'Điều kiện: ' + link.need + ' Không có ở phòng Sát thương gốc' + (link === PACT ? '.' : ' và phòng đánh tự do.')})
            ]));
        });
    }

    /** Chỉ số tab: this character's set passive, at the strength of the set worn now (or the full one). */
    function renderPassive() {
        var c = state.profile.character;
        var passive = PASSIVES[c.id];
        var box = $('passive-box');
        if (!passive) { box.hidden = true; renderLinks(); return; }
        var status = setStatus(state.profile.bag.filter(function (item) { return item.inUse; }));
        var strength = status.tier ? SET_STRENGTH[status.tier - 1] : 100;
        var rows = SET_LABELS.map(function (label, i) {
            return el('tr', {className: status.tier === i + 1 ? 'current' : ''}, [
                el('td', {text: 'Full ' + label + ' +14'}),
                el('td', {text: SET_STRENGTH[i] + '%'}),
                el('td', {text: passive.detail(SET_STRENGTH[i])})
            ]);
        });
        box.replaceChildren(
            el('h3', null, [el('span', {text: 'Nội tại bộ +14: '}), el('b', {className: 'passive-name', text: passive.name})]),
            el('p', {className: 'passive-state' + (status.tier ? ' on' : ''), text: status.tier
                ? 'Đang kích hoạt với full ' + SET_LABELS[status.tier - 1] + ' +14 (' + strength + '% sức mạnh).'
                : 'Chưa kích hoạt: cần ' + status.need.join(', ') + '. Mô tả dưới đây là mức của full bộ vàng +14.'}),
            el('p', {className: 'passive-detail', text: passive.detail(strength)}),
            el('p', {className: 'hint', text: 'Kích hoạt khi mặc đủ súng, nón, áo, kính, balo cùng một bộ (1, 2, 3, 4, bạc hoặc vàng) và cả 5 món đều +14. '
                + 'Trang phục (skin) mặc thêm không ảnh hưởng. Bộ càng cao nội tại càng mạnh. Không có tác dụng ở phòng luật Sát thương gốc.'}),
            el('p', {className: 'hint', text: 'Bộ +15/+16: khi nâng một món lên +15 bạn chọn hệ cho nó. Cả 5 món +15 trở lên cùng một hệ thì ngoài nội tại +14, '
                + 'mỗi phát bắn trúng đối thủ có 10% (+16: 15%) gây hiệu ứng lên lượt kế của họ: '
                + ELEMENTS.map(function (k) { return k.name + ': ' + k.text; }).join('; ') + '.'}),
            el('details', {className: 'passive-sets'}, [
                el('summary', {text: 'Sức mạnh theo từng bộ'}),
                el('div', {className: 'table-scroll'}, [el('table', {className: 'upgrade-table'}, [
                    el('thead', null, [el('tr', null, ['Bộ', 'Sức mạnh', 'Hiệu ứng'].map(function (h) { return el('th', {text: h}); }))]),
                    el('tbody', null, rows)
                ])])
            ])
        );
        box.hidden = false;
        renderLinks();
    }

    /** Sum of what the worn gear adds, and how close it is to a full +14 set (the set passive). */
    function renderWornSummary(worn) {
        var box = $('worn-summary');
        var ability = [0, 0, 0, 0, 0], percent = [0, 0, 0, 0, 0];
        worn.forEach(function (item) {
            for (var i = 0; i < 5; i++) { ability[i] += item.ability[i] || 0; percent[i] += item.percent[i] || 0; }
        });
        var total = statTags({ability: ability, percent: percent});
        var status = setStatus(worn);
        var passive = PASSIVES[state.profile.character.id];
        var title = passive ? '"' + passive.name + '"' : '';
        var line = status.tier
            ? el('p', {className: 'set-line on', text: 'Nội tại ' + title + ' đã kích hoạt: full ' + SET_LABELS[status.tier - 1] + ' +14 (' + SET_STRENGTH[status.tier - 1] + '% sức mạnh). Chi tiết ở tab Chỉ số.'})
            : el('p', {className: 'set-line', text: 'Nội tại ' + title + ' bộ +14: cần ' + status.need.join(', ') + '. Chi tiết ở tab Chỉ số.'});
        box.replaceChildren(el('b', {text: 'Đồ đang mặc cộng tổng cộng'}), total, line);
        box.hidden = !worn.length;
    }

    function renderBag() {
        var p = state.profile;
        var slots = $('slots');
        slots.replaceChildren();
        var wornGear = [];
        function wornSlot(label, worn) {
            if (worn) wornGear.push(worn);
            var plus = worn && worn.plus ? worn.plus : 0;
            return el('div', {className: 'slot' + (worn ? ' filled' : '') + (plus ? ' upgrade-card tier-' + tier(plus) : '')}, [
                el('b', {text: label}),
                worn ? aura(worn, 44) : el('span', {className: 'thumb small'}),
                el('span', {className: 'slot-name', text: worn ? worn.name + (plus ? ' +' + plus : '') : 'Chưa mặc'}),
                worn ? elementBadge(worn) : null,
                worn ? statTags(worn) : null
            ]);
        }
        SLOTS.forEach(function (label, type) {
            slots.append(wornSlot(label, p.bag.find(function (item) { return item.inUse && !item.vip && item.type === type; })));
        });
        var vip = p.bag.find(function (item) { return item.inUse && item.vip; });
        if (p.bag.some(function (item) { return item.vip; })) slots.append(wornSlot('VIP', vip));
        renderWornSummary(wornGear);
        $('bag-count').textContent = '(' + p.bagSize + '/' + p.bagLimit + ' món, đang xem đồ của ' + p.character.name + ')';
        var keys = p.bag.map(function (item) { return item.dbKey; });
        state.sellPick = new Set(Array.from(state.sellPick).filter(function (key) { return keys.indexOf(key) !== -1; }));
        renderBagTools();
        var bag = $('bag');
        bag.replaceChildren();
        if (!p.bag.length) bag.append(el('p', {className: 'empty', text: 'Rương chưa có đồ của ' + p.character.name + '. Ghé Cửa hàng để mua nhé!'}));
        p.bag.forEach(function (item) {
            var action;
            if (state.sellMode) {
                action = canSell(item) ? el('span', {className: 'note-line', text: 'Bán được ' + priceLabel(item.sellPrice, 'xu')}) : sellNote(item);
            } else if (item.inUse) {
                action = el('button', {type: 'button', className: 'buy secondary', text: 'Tháo ra',
                    onclick: function () { run(function () { return api('POST', 'remove', {dbKey: item.dbKey}); }); }});
            } else if (item.wearable) {
                action = el('button', {type: 'button', className: 'buy', text: 'Mặc vào',
                    onclick: function () { run(function () { return api('POST', 'wear', {dbKey: item.dbKey}); }, cheer); }});
            } else {
                action = el('span', {className: 'note-line', text: item.days <= 0 && !item.permanent ? 'Đã hết hạn' : 'Cần cấp ' + item.level});
            }
            var sell = null;
            if (!state.sellMode && !item.inUse) {
                sell = canSell(item) ? el('button', {type: 'button', className: 'buy secondary', text: 'Bán · ' + priceLabel(item.sellPrice, 'xu'),
                    onclick: function () { sellGear([item]); }}) : sellNote(item);
            }
            var picked = state.sellPick.has(item.dbKey);
            var pickable = state.sellMode && canSell(item);
            var card = el('article', {
                className: 'card' + (item.inUse ? ' worn' : '') + (item.plus ? ' upgrade-card tier-' + tier(item.plus) : '')
                    + (state.sellMode ? (pickable ? ' pickable' : ' unpickable') : '') + (picked ? ' picked' : ''),
                role: pickable ? 'checkbox' : null, 'aria-checked': pickable ? String(picked) : null, tabindex: pickable ? '0' : null
            }, [
                pickable ? el('span', {className: 'pick-box', 'aria-hidden': 'true'}) : null,
                el('div', {className: 'card-top'}, [
                    aura(item, 64),
                    el('div', null, [
                        el('p', {className: 'card-name', text: item.name + (item.plus ? ' +' + item.plus : '')}),
                        el('p', {className: 'card-meta', text: item.typeName + ' · cấp ' + item.level + ' · ' + daysText(item)}),
                        elementBadge(item)
                    ])
                ]),
                statTags(item),
                el('div', {className: 'card-actions'}, [item.inUse ? el('span', {className: 'note-line', text: 'Đang mặc'}) : null, action, sell])
            ]);
            if (pickable) {
                var toggle = function () {
                    if (state.sellPick.has(item.dbKey)) state.sellPick.delete(item.dbKey);
                    else state.sellPick.add(item.dbKey);
                    renderBag();
                    var again = document.querySelector('#bag [data-key="' + item.dbKey + '"]');
                    if (again) again.focus();
                };
                card.setAttribute('data-key', item.dbKey);
                card.addEventListener('click', toggle);
                card.addEventListener('keydown', function (event) {
                    if (event.key === ' ' || event.key === 'Enter') {
                        event.preventDefault();
                        toggle();
                    }
                });
            }
            bag.append(card);
        });
        var pocket = $('pocket');
        pocket.replaceChildren();
        p.items.forEach(function (item) {
            pocket.append(pocketCard(item, 'item', function () { return framed(itemIcon(item.id, 2), true); }));
        });
        p.specials.forEach(function (item) {
            pocket.append(pocketCard(item, 'special', function () { return framed(specialIcon(item.id, 2), true); }));
        });
        if (!pocket.children.length) pocket.append(el('p', {className: 'empty', text: 'Túi đang trống.'}));
    }

    // --- selling (the game's prices: half the xu price, gear for the days it has left) ------------

    function canSell(item) {
        return !item.inUse && !item.gems.length && item.sellPrice > 0;
    }

    function sellNote(item) {
        if (item.inUse) return null;
        return el('span', {className: 'note-line', text: item.gems.length ? 'Còn ngọc: tháo ngọc mới bán được' : 'Không bán được'});
    }

    function renderBagTools() {
        var tools = $('bag-tools');
        var sellable = state.profile.bag.filter(canSell);
        if (!state.sellMode) {
            tools.className = 'bag-tools';
            tools.replaceChildren(sellable.length > 1 ? el('button', {type: 'button', className: 'text-btn', text: 'Chọn nhiều món để bán',
                onclick: function () {
                    state.sellMode = true;
                    state.sellPick = new Set();
                    renderBag();
                }}) : null);
            return;
        }
        var picked = sellable.filter(function (item) { return state.sellPick.has(item.dbKey); });
        var total = picked.reduce(function (sum, item) { return sum + item.sellPrice; }, 0);
        tools.className = 'bag-tools selling';
        tools.replaceChildren(
            el('span', {className: 'sell-summary', text: picked.length ? 'Đã chọn ' + picked.length + ' món · ' + priceLabel(total, 'xu') : 'Bấm vào các món muốn bán'}),
            el('button', {type: 'button', className: 'text-btn', text: picked.length === sellable.length ? 'Bỏ chọn hết' : 'Chọn hết',
                onclick: function () {
                    state.sellPick = new Set(picked.length === sellable.length ? [] : sellable.map(function (item) { return item.dbKey; }));
                    renderBag();
                }}),
            el('button', {type: 'button', className: 'buy', text: 'Bán', disabled: !picked.length, onclick: function () { sellGear(picked); }}),
            el('button', {type: 'button', className: 'text-btn', text: 'Thôi', onclick: endSelling})
        );
    }

    function endSelling() {
        state.sellMode = false;
        state.sellPick = new Set();
        renderBag();
    }

    async function sellGear(items) {
        var total = items.reduce(function (sum, item) { return sum + item.sellPrice; }, 0);
        var upgraded = items.filter(function (item) { return item.plus > 0; });
        var notes = [];
        if (upgraded.length) {
            notes.push(items.length === 1 ? 'Món này đang +' + upgraded[0].plus + ', bán là mất luôn cấp đã nâng.'
                : 'Có ' + upgraded.length + ' món đã nâng cấp, bán là mất luôn cấp đã nâng.');
        }
        if (items.some(function (item) { return item.permanent; })) notes.push('Skin Thor mua lại tốn 1.000 lượng.');
        var what = items.length === 1 ? items[0].name + (items[0].plus ? ' +' + items[0].plus : '') : items.length + ' món';
        var picture = items.length === 1 ? aura(items[0], 64)
            : el('span', {className: 'sell-stack'}, items.slice(0, 3).map(function (item) { return gear(item, 44); }));
        if (!await confirmBuy(('Bán ' + what + ' được ' + priceLabel(total, 'xu') + '? ' + notes.join(' ')).trim(), picture, 'Bán')) return;
        await run(function () { return api('POST', 'sell/equipment', {dbKeys: items.map(function (item) { return item.dbKey; })}); }, endSelling);
    }

    /** A battle item or special item in the pocket, with how many to sell. */
    function pocketCard(item, kind, icon) {
        var actions;
        if (item.sell > 0) {
            var input = amountInput(item.num);
            actions = [
                el('label', {className: 'amount'}, ['Bán', input]),
                el('button', {type: 'button', className: 'buy secondary', text: priceLabel(item.sell, 'xu') + ' mỗi cái',
                    onclick: async function () {
                        var amount = readAmount(input, item.num);
                        if (!await confirmBuy('Bán ' + amount + ' ' + item.name + ' được ' + priceLabel(item.sell * amount, 'xu') + '?', icon(), 'Bán')) return;
                        run(function () { return api('POST', 'sell/' + kind, {id: item.id, amount: amount}); });
                    }})
            ];
        } else {
            actions = [el('span', {className: 'note-line', text: 'Không bán được'})];
        }
        return el('article', {className: 'card pocket-card', title: item.detail || item.name}, [
            el('div', {className: 'card-top'}, [
                icon(),
                el('div', null, [
                    el('p', {className: 'card-name', text: item.name}),
                    el('p', {className: 'card-meta', text: 'Đang có ' + item.num})
                ])
            ]),
            el('div', {className: 'card-actions'}, actions)
        ]);
    }

    // --- final stats ---------------------------------------------------------------------------

    function pct(value) {
        return (Math.round(value * 10) / 10).toLocaleString('vi-VN') + '%';
    }

    /** Same formulas as the server (Glass.createAbility and the battle code in Player). */
    function renderStats() {
        var c = state.profile.character;
        var s = c.stats, pts = c.ability, flat = c.gearFlat, per = c.gearPercent;
        $('stats-worn').textContent = '(đang mặc ' + c.wornCount + ' món)';
        var damagePct = c.baseAttack ? s[1] * 100 / c.baseAttack : 100;
        var reduce = (1 - Math.exp(-s[2] / 6000)) * 100;
        var luck = (1 - Math.exp(-s[3] / 6000)) * 100;
        var cards = [
            {name: 'Máu', value: number.format(s[0]), note: 'Lượng máu khi vào trận'},
            {name: 'Sát thương', value: number.format(s[1]), note: pct(damagePct) + ' so với gốc ' + number.format(c.baseAttack)},
            {name: 'Phòng thủ', value: number.format(s[2]), note: 'Giảm ' + pct(reduce) + ' sát thương nhận vào'},
            {name: 'May mắn', value: number.format(s[3]), note: pct(luck) + ' mỗi lượt: x2 sát thương, +3.000 thủ, không chết'},
            {name: 'Đồng đội', value: number.format(s[4]), note: 'Góp ' + number.format(Math.floor(s[4] / 200)) + ' vào quỹ đồng đội của đội'}
        ];
        renderRules(state.profile.rules, c, s, reduce, luck);
        renderPassive();
        var box = $('final-stats');
        box.replaceChildren();
        cards.forEach(function (card, i) {
            box.append(el('div', {className: 'final-stat stat-' + i}, [
                el('span', {className: 'final-name', text: card.name}),
                el('b', {className: 'final-value', text: card.value}),
                el('small', {text: card.note})
            ]));
        });

        var hpPercent = Math.floor((1000 + pts[0]) * per[0] / 100);
        var rows = [
            ['Máu', '1.000', '+' + number.format(pts[0] * 10), '+' + number.format(flat[0] * 10), per[0] ? '+' + number.format(hpPercent) + ' (' + per[0] + '%)' : '–', number.format(s[0])],
            ['Sức mạnh', number.format(c.baseAttack), '+' + pct(pts[1] / 3), '+' + pct(flat[1] / 3), per[1] ? '+' + per[1] + '%' : '–', number.format(s[1])],
            ['Phòng thủ', '0', '+' + number.format(pts[2] * 10), '+' + number.format(flat[2] * 10), per[2] ? '+' + per[2] + '%' : '–', number.format(s[2])],
            ['May mắn', '0', '+' + number.format(pts[3] * 10), '+' + number.format(flat[3] * 10), per[3] ? '+' + per[3] + '%' : '–', number.format(s[3])],
            ['Đồng đội', '0', '+' + number.format(pts[4] * 10), '+' + number.format(flat[4] * 10), per[4] ? '+' + per[4] + '%' : '–', number.format(s[4])]
        ];
        var table = $('stats-table');
        table.replaceChildren(
            el('thead', null, [el('tr', null, ['Chỉ số', 'Gốc', 'Điểm cộng', 'Trang bị (gồm ngọc, nâng cấp)', 'Trang bị %', 'Tổng']
                .map(function (h) { return el('th', {text: h}); }))]),
            el('tbody', null, rows.map(function (row) {
                return el('tr', null, row.map(function (cell, i) { return el(i === 5 ? 'th' : 'td', {text: cell}); }));
            }))
        );
    }

    /** The three room rules in plain words, with this character's numbers (server: mobiarmy.war.RoomRules, SkillBalance). */
    function renderRules(rules, c, s, reduce, luck) {
        var box = $('skill-rules');
        box.hidden = !rules;
        if (!rules) return;
        var names = rules.modes.map(function (mode) { return mode.name; });
        var base = state.profile.baseStats || [1000, c.baseAttack, 0, 0, 0];
        var bonus = Math.max(0, s[1] - c.baseAttack);
        box.replaceChildren(
            el('h3', {text: 'Chủ phòng chọn luật cho trận đấu (trong phòng: Menu → Luật trận đấu)'}),
            el('ul', null, [
                el('li', null, [el('b', {text: names[0]}), ': tính đủ như các ô ở trên, build đồ thoải mái.']),
                el('li', null, [el('b', {text: names[1]}), ': sát thương gốc ' + number.format(c.baseAttack) + ' luôn có, phần cộng thêm +' + number.format(bonus)
                    + ' chỉ ăn đủ khi đạn nổ trúng thân địch (nổ lệch giảm dần, còn dưới ' + rules.bonusZero + '% sát thương thì mất hẳn). May mắn '
                    + pct(Math.min(luck, rules.maxLuck)) + ' (tối đa ' + rules.maxLuck + '%), chỉ x2 khi trúng thân. Phòng thủ giảm ' + pct(Math.min(reduce, rules.maxReduction))
                    + ' (tối đa ' + rules.maxReduction + '%), trúng thân xuyên ' + rules.pierce + '%. Trúng thân gây thêm ' + rules.tank + '% phần máu trên '
                    + number.format(rules.baseHp) + ' của địch. Skin Thor chỉ là trang phục.']),
                el('li', null, [el('b', {text: names[2]}), ': ai cũng như nhân vật mới cấp 1, bạn vào trận với ' + number.format(base[0]) + ' máu, sát thương '
                    + number.format(base[1]) + ', phòng thủ ' + number.format(base[2]) + ', may mắn ' + number.format(base[3]) + '. Không tính điểm, đồ, nâng cấp, ngọc.'])
            ]),
            el('p', {className: 'rules-default', text: 'Phòng mới bắt đầu với luật ' + names[rules['default']] + '.'})
        );
    }

    // --- upgrade (+1 to +16; +15 picks the set's element) ---------------------------------------

    // Mirrors the server tables in LanPlayerGame (index = target level; rate in tenths of a percent).
    var UPGRADE = {
        rate: [0, 1000, 900, 800, 700, 600, 500, 400, 320, 250, 180, 120, 80, 2, 1, 1, 1],
        pity: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 500, 1000, 1500, 2000],
        xu: [0, 5000, 9000, 16000, 29000, 52000, 94000, 170000, 306000, 551000, 992000, 1785000, 3213000, 5784000, 10411000, 18740000, 33730000],
        luong: [0, 0, 0, 0, 0, 0, 0, 0, 20, 40, 80, 160, 320, 640, 1280, 2560, 5120],
        stones: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16],
        bonus: [0, 1, 2, 3, 4, 6, 8, 10, 12, 15, 18, 22, 26, 31, 37, 43, 50]
    };
    var STONE_ID = 80;
    // Mirrors LanPlayerGame.Insurance: keeps the level on a failed try up to upTo.
    var INSURANCE = [
        {id: 88, name: 'Bảo hiểm sơ cấp', upTo: 9},
        {id: 89, name: 'Bảo hiểm trung cấp', upTo: 12},
        {id: 90, name: 'Bảo hiểm cao cấp', upTo: 16}
    ];

    // Mirrors SetElement: the element a piece takes at +15; a full set of one element strikes with it.
    var ELEMENTS = [
        {id: 1, name: 'Lôi', text: 'tê liệt, đi chậm 30% và không ngắm thấp hơn 60°'},
        {id: 2, name: 'Hỏa', text: 'bỏng, nhận thêm 15% sát thương (+16: 25%)'},
        {id: 3, name: 'Băng', text: 'đóng băng, không di chuyển được'},
        {id: 4, name: 'Độc', text: 'mất 3% máu tối đa mỗi lượt trong 2 lượt (+16: 5%)'}
    ];

    var ELEMENT_ICONS = {1: '⚡', 2: '🔥', 3: '❄', 4: '☠'};

    /** A coloured "hệ Hỏa" tag for a piece +15 or more with an element, else null. */
    function elementBadge(item) {
        var kind = ELEMENTS.find(function (k) { return k.id === item.element; });
        if (!kind || (item.plus || 0) < 15) return null;
        return el('span', {className: 'element-badge element-' + kind.id, title: 'Hệ ' + kind.name + ': ' + kind.text,
            text: ELEMENT_ICONS[kind.id] + ' Hệ ' + kind.name});
    }

    /** " · hệ Lôi" for a piece +15 or more with an element, else nothing. */
    function elementLabel(item) {
        var kind = ELEMENTS.find(function (k) { return k.id === item.element; });
        return kind && (item.plus || 0) >= 15 ? ' · hệ ' + kind.name : '';
    }

    function tier(plus) {
        return plus >= 15 ? 7 : plus >= 14 ? 6 : plus >= 13 ? 5 : plus >= 11 ? 4 : plus >= 8 ? 3 : plus >= 5 ? 2 : plus >= 1 ? 1 : 0;
    }

    /** Gear picture inside a frame whose colour and animation follow the upgrade level. */
    function aura(item, box) {
        var plus = item.plus || 0;
        var wrap = el('span', {className: 'aura tier-' + tier(plus)}, [gear(item, box)]);
        if (plus > 0) wrap.append(el('span', {className: 'plus-badge tier-' + tier(plus), text: '+' + plus}));
        if (tier(plus) >= 3) {
            for (var i = 0; i < (tier(plus) >= 4 ? 6 + (tier(plus) - 4) * 2 : 4); i++) {
                var spark = el('span', {className: 'spark'});
                spark.style.setProperty('--i', i);
                wrap.append(spark);
            }
        }
        return wrap;
    }

    function specialCount(id) {
        var owned = state.profile.specials.find(function (item) { return item.id === id; });
        return owned ? owned.num : 0;
    }

    function stoneCount() {
        return specialCount(STONE_ID);
    }

    function ratePercent(tenths) {
        return chanceFormat.format(tenths / 10) + '%';
    }

    /**
     * Focus on the item: everything is sucked into it in a swirl of its rarity colour for 1 second, or 2 when the
     * server says a +12 or higher try succeeded (it holds the next try as long). Returns {until, reveal, stop}:
     * reveal bursts out in the new level's colour on success or grey on failure, then clears the screen.
     */
    function swirl(item) {
        var ms = 1000;
        var target = item.plus + 1;
        var burst = el('div', {className: 'focus-burst'});
        var label = el('p', {className: 'focus-text', text: '+' + item.plus + ' → +' + target});
        // Motes spiral in from all around (sucked into the item), then fly out again with the result.
        var motes = [];
        for (var i = 0; i < 24; i++) {
            var mote = el('span', {className: 'focus-mote'});
            mote.style.setProperty('--a', (i * 15) + 'deg');
            mote.style.setProperty('--d', (-(i % 6) * 0.12) + 's');
            mote.style.setProperty('--r', (120 + (i % 4) * 18) + 'px');
            motes.push(mote);
        }
        var stage = el('div', {className: 'focus-stage tier-' + tier(item.plus || 1)}, [
            el('div', {className: 'focus-swirl'}),
            el('div', {className: 'focus-motes'}, motes),
            el('div', {className: 'focus-wave'}),
            burst,
            el('div', {className: 'focus-item'}, [aura(item, 80)]),
            label
        ]);
        var layer = el('div', {className: 'focus-layer', role: 'status', 'aria-live': 'polite'}, [stage]);
        layer.style.setProperty('--spin-ms', ms + 'ms');
        document.body.append(layer);
        var started = Date.now();
        function stop() { layer.remove(); }
        return {
            // Resolves when the swirl has run for the given time; a longer one charges up harder for the extra time.
            until: function (total) {
                if (total > ms) setTimeout(function () { stage.classList.add('focus-charged'); }, ms);
                return new Promise(function (resolve) { setTimeout(resolve, Math.max(0, started + total - Date.now())); });
            },
            stop: stop,
            reveal: function (outcome) {
                stage.classList.add(outcome.success ? 'focus-win' : 'focus-lose');
                if (outcome.success) {
                    stage.className = stage.className.replace(/tier-\d/, 'tier-' + tier(outcome.to));
                    label.textContent = 'Thành công +' + outcome.to;
                    cheer();
                } else {
                    label.textContent = outcome.to < outcome.from ? 'Thất bại · tụt về +' + outcome.to : 'Thất bại · giữ +' + outcome.to;
                }
                return new Promise(function (resolve) { setTimeout(function () { stop(); resolve(); }, 1100); });
            }
        };
    }

    function renderUpgrade() {
        var stones = stoneCount();
        $('stone-count').textContent = '(Đá cường hóa: ' + stones + ')';
        var list = $('upgrade-list');
        list.replaceChildren();
        var items = state.profile.bag.filter(function (item) { return item.upgradeable; });
        if (!items.length) list.append(el('p', {className: 'empty', text: 'Nhân vật này chưa có trang bị nào có chỉ số để nâng cấp.'}));
        items.forEach(function (item) {
            var next = item.next;
            var body;
            if (!next) {
                body = el('p', {className: 'note-line max-line', text: 'Đã đạt cấp tối đa +16'});
            } else {
                var short = [];
                if (stones < next.stones) short.push('thiếu ' + (next.stones - stones) + ' đá');
                if (state.profile.xu < next.xu) short.push('thiếu xu');
                if (state.profile.luong < next.luong) short.push('thiếu lượng');
                // Insurance only matters where a failed try would drop a level.
                var picker = null;
                var usable = next.dropOnFail ? INSURANCE.filter(function (kind) {
                    return kind.upTo >= item.plus + 1 && specialCount(kind.id) > 0;
                }) : [];
                if (usable.length) {
                    picker = el('select', {className: 'insurance-pick', 'aria-label': 'Bảo hiểm'},
                        [el('option', {value: '0', text: 'Không dùng bảo hiểm'})].concat(usable.map(function (kind) {
                            return el('option', {value: String(kind.id), text: kind.name + ' (còn ' + specialCount(kind.id) + ')'});
                        })));
                } else if (next.dropOnFail) {
                    var fits = INSURANCE.filter(function (kind) { return kind.upTo >= item.plus + 1; })[0];
                    if (fits) picker = el('span', {className: 'note-line', text: 'Mua ' + fits.name + ' (Cửa hàng → Đồ đặc biệt) để thất bại không bị tụt cấp.'});
                }
                // +15 gives the piece the element of the set (SetElement on the server): the player picks it here.
                var elementPick = next.pickElement ? el('select', {className: 'insurance-pick', 'aria-label': 'Hệ'},
                    ELEMENTS.map(function (kind) {
                        return el('option', {value: String(kind.id), text: 'Hệ ' + kind.name + ': ' + kind.text, selected: kind.id === (item.element || 1)});
                    })) : null;
                body = el('div', null, [
                    el('div', {className: 'upgrade-cost'}, [
                        el('span', {className: 'rate tier-' + tier(item.plus + 1), text: ratePercent(next.rate)}),
                        next.pity ? el('span', {className: 'note-line', text: 'Chắc chắn thành công ở lần thứ ' + next.pity + ' · đã đập ' + next.fails + '/' + next.pity}) : null,
                        el('span', {text: 'Lên +' + (item.plus + 1) + ' · mỗi chỉ số +' + next.bonus}),
                        el('span', {text: priceLabel(next.xu, 'xu') + (next.luong ? ' · ' + priceLabel(next.luong, 'luong') : '') + ' · ' + next.stones + ' đá'}),
                        next.dropOnFail ? el('span', {className: 'warn-text', text: 'Thất bại tụt về +' + (item.plus - 1)}) : null
                    ]),
                    picker,
                    elementPick,
                    el('div', {className: 'card-actions'}, [
                        short.length ? el('span', {className: 'note-line', text: short.join(', ')}) : null,
                        el('button', {
                            type: 'button', className: 'buy upgrade-btn tier-' + tier(item.plus + 1), text: 'Nâng cấp', disabled: short.length > 0,
                            onclick: async function () {
                                var insurance = picker && picker.tagName === 'SELECT' ? Number(picker.value) : 0;
                                var kind = INSURANCE.find(function (k) { return k.id === insurance; });
                                var element = elementPick ? Number(elementPick.value) : 0;
                                var elementKind = ELEMENTS.find(function (k) { return k.id === element; });
                                var text = 'Nâng ' + item.name + ' lên +' + (item.plus + 1) + (elementKind ? ' hệ ' + elementKind.name : '') + '? Tỉ lệ ' + ratePercent(next.rate) + ', tốn '
                                    + priceLabel(next.xu, 'xu') + (next.luong ? ', ' + priceLabel(next.luong, 'luong') : '') + ' và ' + next.stones + ' đá'
                                    + (kind ? ', 1 ' + kind.name + '. Thất bại vẫn giữ cấp.' : '.' + (next.dropOnFail ? ' Thất bại sẽ tụt 1 cấp.' : ''));
                                if (!await confirmBuy(text, aura(item, 64), 'Nâng cấp')) return;
                                // The server answers at once but the result shows when the swirl ends.
                                var spin = swirl(item);
                                var result = null;
                                try {
                                    result = await api('POST', 'upgrade', {dbKey: item.dbKey, insurance: insurance, element: element});
                                } catch (error) {
                                    spin.stop();
                                    toast(error.message, true);
                                    return;
                                }
                                await spin.until(result.upgrade ? result.upgrade.spinMs : 0);
                                if (result.upgrade) await spin.reveal(result.upgrade); else spin.stop();
                                if (result.character) setProfile(result);
                                if (result.message) toast(result.message);
                            }
                        })
                    ])
                ]);
            }
            list.append(el('article', {className: 'card upgrade-card tier-' + tier(item.plus), 'data-key': item.dbKey}, [
                el('div', {className: 'card-top'}, [
                    aura(item, 64),
                    el('div', null, [
                        el('p', {className: 'card-name', text: item.name + (item.plus ? ' +' + item.plus : '')}),
                        el('p', {className: 'card-meta', text: item.typeName + (item.inUse ? ' · đang mặc' : '') + elementLabel(item)})
                    ])
                ]),
                statTags(item),
                body
            ]));
        });
        renderUpgradeTable();
    }

    function renderUpgradeTable() {
        var table = $('upgrade-table');
        if (table.rows.length) return;
        table.append(el('thead', null, [el('tr', null, ['Cấp', 'Tỉ lệ', 'Xu', 'Lượng', 'Đá', 'Cộng mỗi chỉ số', 'Thất bại']
            .map(function (h) { return el('th', {text: h}); }))]));
        var tbody = el('tbody');
        for (var n = 1; n <= UPGRADE.rate.length - 1; n++) {
            tbody.append(el('tr', null, [
                el('td', null, [el('span', {className: 'plus-badge tier-' + tier(n), text: '+' + n})]),
                el('td', {text: ratePercent(UPGRADE.rate[n]) + (UPGRADE.pity[n] ? ' (chắc chắn ở lần ' + UPGRADE.pity[n] + ')' : '')}),
                el('td', {text: number.format(UPGRADE.xu[n])}),
                el('td', {text: UPGRADE.luong[n] ? number.format(UPGRADE.luong[n]) : '–'}),
                el('td', {text: UPGRADE.stones[n]}),
                el('td', {text: '+' + UPGRADE.bonus[n] + ' (tổng)'}),
                el('td', {text: n >= 8 ? 'tụt 1 cấp (trừ khi có bảo hiểm)' : 'giữ cấp'})
            ]));
        }
        table.append(tbody);
    }

    // --- gems (khảm ngọc) ----------------------------------------------------------------------

    function ownedGems() {
        return state.profile.specials.filter(function (item) { return item.id < 50; })
            .sort(function (a, b) { return a.id - b.id; });
    }

    function renderGems() {
        var gems = ownedGems();
        var list = $('gem-list');
        list.replaceChildren();
        if (!gems.length) list.append(el('p', {className: 'empty', text: 'Bạn chưa có ngọc. Mua ngọc ở Cửa hàng → Đồ đặc biệt.'}));
        gems.forEach(function (gem) {
            var level = gem.id % 10 + 1;
            var maxTries = Math.floor(gem.num / 5);
            var top = level === 10;
            var input = el('input', {type: 'number', min: 5, step: 5, max: Math.max(5, maxTries * 5), value: 5, inputmode: 'numeric', 'aria-label': 'Số ngọc đem ghép'});
            input.disabled = top || maxTries < 1;
            var rate = 100 - level * 10;
            list.append(el('article', {className: 'card'}, [
                el('div', {className: 'card-top'}, [
                    framed(specialIcon(gem.id, 3), false),
                    el('div', null, [
                        el('p', {className: 'card-name', text: gem.name}),
                        el('p', {className: 'card-meta', text: 'Đang có ' + gem.num + (top ? ' · cấp cao nhất' : ' · tỉ lệ lên cấp ' + rate + '%')})
                    ])
                ]),
                gem.detail ? el('p', {className: 'card-detail', text: gem.detail}) : null,
                top ? null : maxTries < 1 ? el('span', {className: 'note-line', text: 'Cần ít nhất 5 viên để ghép'}) : el('label', {className: 'amount'}, ['Số viên', input]),
                top || maxTries < 1 ? null : el('div', {className: 'card-actions'}, [el('button', {
                    type: 'button', className: 'buy', text: 'Ghép',
                    onclick: async function () {
                        var amount = Math.floor(Number(input.value) / 5) * 5;
                        amount = Math.max(5, Math.min(amount, maxTries * 5));
                        input.value = amount;
                        if (!await confirmBuy('Ghép ' + amount + ' ' + gem.name + ' (' + amount / 5 + ' lần, mỗi lần ' + rate + '%)?',
                            framed(specialIcon(gem.id, 3), false), 'Ghép')) return;
                        run(function () { return api('POST', 'gem/combine', {gemId: gem.id, amount: amount}); });
                    }
                })])
            ]));
        });

        var sockets = $('socket-list');
        sockets.replaceChildren();
        var bag = state.profile.bag.filter(function (item) { return item.holes > 0; });
        if (!bag.length) sockets.append(el('p', {className: 'empty', text: 'Nhân vật này chưa có trang bị khảm được.'}));
        bag.forEach(function (item) {
            var holes = [];
            for (var i = 0; i < item.holes; i++) {
                var gemId = item.gems[i];
                holes.push(el('span', {className: 'thumb small', title: gemId != null ? gemName(gemId) : 'Lỗ trống'},
                    [gemId != null ? specialIcon(gemId, 2) : null]));
            }
            var free = item.holes - item.gems.length;
            var select = el('select', {'aria-label': 'Chọn ngọc để gắn'}, gems.map(function (gem) {
                return el('option', {value: gem.id, text: gem.name + ' (' + gem.num + ')'});
            }));
            var actions = [];
            if (free > 0 && gems.length) {
                actions.push(select, el('button', {type: 'button', className: 'buy', text: 'Gắn ngọc',
                    onclick: async function () {
                        var gemId = Number(select.value);
                        if (!await confirmBuy('Gắn ' + gemName(gemId) + ' vào ' + item.name + '?', gear(item, 64), 'Gắn')) return;
                        run(function () { return api('POST', 'gem/socket', {dbKey: item.dbKey, gemId: gemId}); });
                    }}));
            } else if (free > 0) {
                actions.push(el('span', {className: 'note-line', text: 'Chưa có ngọc để gắn'}));
            } else {
                actions.push(el('span', {className: 'note-line', text: 'Đã đủ 3 lỗ'}));
            }
            if (item.gems.length) {
                actions.push(item.inUse
                    ? el('span', {className: 'note-line', text: 'Tháo đồ ra mới lấy ngọc được'})
                    : el('button', {type: 'button', className: 'buy secondary', text: 'Tháo ngọc (' + priceLabel(item.unsocketPrice, 'xu') + ')',
                        onclick: async function () {
                            if (!canAfford(item.unsocketPrice, 'xu')) {
                                toast('Không đủ xu: cần ' + priceLabel(item.unsocketPrice, 'xu') + '.', true);
                                return;
                            }
                            if (!await confirmBuy('Tháo hết ngọc khỏi ' + item.name + ' với phí ' + priceLabel(item.unsocketPrice, 'xu') + '?', gear(item, 64), 'Tháo')) return;
                            run(function () { return api('POST', 'gem/unsocket', {dbKey: item.dbKey}); });
                        }}));
            }
            sockets.append(el('article', {className: 'card' + (item.inUse ? ' worn' : '')}, [
                el('div', {className: 'card-top'}, [
                    gear(item, 64),
                    el('div', null, [
                        el('p', {className: 'card-name', text: item.name}),
                        el('p', {className: 'card-meta', text: item.typeName + (item.inUse ? ' · đang mặc' : '') + elementLabel(item)})
                    ])
                ]),
                el('div', {className: 'tags'}, holes),
                statTags(item),
                el('div', {className: 'card-actions'}, actions)
            ]));
        });
    }

    function gemName(id) {
        var owned = state.profile.specials.find(function (item) { return item.id === id; });
        if (owned) return owned.name;
        var colours = ['Lục bảo ngọc', 'Hồng ngọc', 'Lam ngọc', 'Hoàng ngọc', 'Thạch anh tím'];
        return colours[Math.floor(id / 10)] + ' cấp ' + (id % 10 + 1);
    }

    // --- shop --------------------------------------------------------------------------------

    async function loadShop() {
        try {
            state.shop = await api('GET', 'shop');
            renderShop();
        } catch (error) {
            toast(error.message, true);
        }
    }

    document.querySelectorAll('[data-shop]').forEach(function (pill) {
        pill.addEventListener('click', function () {
            state.shopTab = pill.dataset.shop;
            document.querySelectorAll('[data-shop]').forEach(function (other) {
                other.classList.toggle('active', other === pill);
            });
            renderShop();
        });
    });

    function canAfford(price, currency) {
        return price >= 0 && price <= (currency === 'xu' ? state.profile.xu : state.profile.luong);
    }

    function priceLabel(price, currency) {
        return number.format(price) + (currency === 'xu' ? ' xu' : ' lượng');
    }

    function buyButtons(row, amountOf, buy, describe, picture) {
        var buttons = [];
        ['xu', 'luong'].forEach(function (currency) {
            var unit = row[currency];
            if (unit < 0) return;
            buttons.push(el('button', {
                type: 'button',
                className: 'buy',
                text: priceLabel(unit, currency),
                title: 'Mua bằng ' + (currency === 'xu' ? 'xu' : 'lượng'),
                onclick: async function () {
                    var amount = amountOf();
                    var total = unit * amount;
                    if (!canAfford(total, currency)) {
                        toast('Không đủ ' + (currency === 'xu' ? 'xu' : 'lượng') + ': cần ' + priceLabel(total, currency) + '.', true);
                        return;
                    }
                    if (!await confirmBuy('Mua ' + describe(amount) + ' với giá ' + priceLabel(total, currency) + '?', picture())) return;
                    await run(function () { return buy(currency, amount); }, function () {
                        cheer();
                        loadShop();
                    });
                }
            }));
        });
        if (!buttons.length) buttons.push(el('span', {className: 'note-line', text: 'Không bán'}));
        return buttons;
    }

    function amountInput(max) {
        var input = el('input', {type: 'number', min: 1, max: Math.max(1, max), step: 1, value: 1, inputmode: 'numeric', 'aria-label': 'Số lượng'});
        input.disabled = max < 1;
        return input;
    }

    function readAmount(input, max) {
        var value = Math.floor(Number(input.value));
        if (!Number.isFinite(value) || value < 1) value = 1;
        value = Math.min(value, Math.max(1, max));
        input.value = value;
        return value;
    }

    function renderTypeFilter() {
        var filter = $('type-filter');
        filter.replaceChildren();
        filter.hidden = state.shopTab !== 'equipment';
        if (filter.hidden || !state.shop) return;
        var types = [{key: 'all', label: 'Tất cả'}];
        SLOTS.forEach(function (label, type) {
            if (state.shop.equipment.some(function (row) { return !row.vip && row.type === type; })) types.push({key: String(type), label: label});
        });
        if (state.shop.equipment.some(function (row) { return row.vip; })) types.push({key: 'vip', label: 'VIP'});
        types.forEach(function (type) {
            filter.append(el('button', {
                type: 'button', className: 'pill' + (state.typeFilter === type.key ? ' active' : ''), text: type.label,
                onclick: function () {
                    state.typeFilter = type.key;
                    renderShop();
                }
            }));
        });
    }

    function renderShop() {
        renderTypeFilter();
        var list = $('shop-list');
        list.replaceChildren();
        if (!state.shop) {
            list.append(el('p', {className: 'empty', text: 'Đang mở cửa hàng...'}));
            return;
        }
        if (state.shopTab === 'chests') {
            renderChests(list);
        } else if (state.shopTab === 'equipment') {
            var rows = state.shop.equipment.filter(function (row) {
                return state.typeFilter === 'all' || (state.typeFilter === 'vip' ? row.vip : !row.vip && String(row.type) === state.typeFilter);
            });
            if (!rows.length) list.append(el('p', {className: 'empty', text: 'Không có món nào trong mục này.'}));
            rows.forEach(function (row) {
                var tooLow = state.profile.character.level < row.level;
                list.append(el('article', {className: 'card'}, [
                    el('div', {className: 'card-top'}, [
                        gear(row, 64),
                        el('div', null, [
                            el('p', {className: 'card-name', text: row.name}),
                            el('p', {className: 'card-meta', text: row.typeName + ' · cấp ' + row.level + ' · '
                                + (row.permanent ? 'vĩnh viễn' : row.days + ' ngày') + (row.owned ? ' · đã có ' + row.owned : '')})
                        ])
                    ]),
                    statTags(row),
                    tooLow ? el('span', {className: 'note-line', text: 'Mua được, nhưng cần cấp ' + row.level + ' mới mặc được'}) : null,
                    el('div', {className: 'card-actions'}, buyButtons(row, function () { return 1; }, function (currency) {
                        return api('POST', 'buy/equipment', {equipId: row.equipId, currency: currency});
                    }, function () { return row.name; }, function () { return gear(row, 64); }))
                ]));
            });
        } else if (state.shopTab === 'items') {
            state.shop.items.forEach(function (row) {
                var room = 99 - row.owned;
                var input = amountInput(room);
                list.append(el('article', {className: 'card'}, [
                    el('div', {className: 'card-top'}, [
                        framed(itemIcon(row.id, 3), false),
                        el('div', null, [
                            el('p', {className: 'card-name', text: row.name}),
                            el('p', {className: 'card-meta', text: 'Đang có ' + row.owned + '/99'})
                        ])
                    ]),
                    room > 0 ? el('label', {className: 'amount'}, ['Số lượng', input]) : el('span', {className: 'note-line', text: 'Đã đầy 99'}),
                    room > 0 ? el('div', {className: 'card-actions'}, buyButtons(row, function () { return readAmount(input, room); }, function (currency, amount) {
                        return api('POST', 'buy/item', {id: row.id, amount: amount, currency: currency});
                    }, function (amount) { return amount + ' ' + row.name; }, function () { return framed(itemIcon(row.id, 3), false); })) : null
                ]));
            });
        } else {
            state.shop.specials.forEach(function (row) {
                var input = amountInput(99);
                list.append(el('article', {className: 'card'}, [
                    el('div', {className: 'card-top'}, [
                        framed(specialIcon(row.id, 3), false),
                        el('div', null, [
                            el('p', {className: 'card-name', text: row.name}),
                            el('p', {className: 'card-meta', text: (row.days ? row.days + ' ngày' : 'không hạn') + (row.owned ? ' · đang có ' + row.owned : '')})
                        ])
                    ]),
                    row.detail ? el('p', {className: 'card-detail', text: row.detail}) : null,
                    row.pickAmount ? el('label', {className: 'amount'}, ['Số lượng', input]) : null,
                    el('div', {className: 'card-actions'}, buyButtons(row, function () { return row.pickAmount ? readAmount(input, 99) : 1; }, function (currency, amount) {
                        return api('POST', 'buy/special', {id: row.id, amount: amount, currency: currency});
                    }, function (amount) { return amount + ' ' + row.name; }, function () { return framed(specialIcon(row.id, 3), false); }))
                ]));
            });
        }
    }

    // --- lucky chests -----------------------------------------------------------------------

    // 20x15 pixel chest: o outline, W/w/V wood, T/t/U metal, K keyhole. Rows 0-6 are the lid.
    var CHEST_ROWS = [
        '...oooooooooooooo...',
        '.oowtTwwwwwwwwtTwoo.',
        'owwwtTwwwwwwwwtTwwwo',
        'oWWWtTWWWWWWWWtTWWWo',
        'oWWWtTWWWWWWWWtTWWWo',
        'oVVVtTVVtTTTVVtTVVVo',
        'oUUUUUUUtTTTUUUUUUUo',
        'owWWtTWWtKKTWWtTWWVo',
        'owWWtTWWtKKTWWtTWWVo',
        'owWWtTWWUUUUWWtTWWVo',
        'owWWtTWWWWWWWWtTWWVo',
        'owWWtTWWWWWWWWtTWWVo',
        'oVVVtTVVVVVVVVtTVVVo',
        'otTTTTTTTTTTTTTTTTUo',
        '.oooooooooooooooooo.'
    ];
    var CHEST_COLOURS = {
        bronze: {o: '#2b1608', W: '#8a5a2b', w: '#a8743d', V: '#6b4220', T: '#cd7f32', t: '#f0a860', U: '#8c5220', K: '#2b1608'},
        silver: {o: '#1e2530', W: '#4b5a70', w: '#61728a', V: '#39465a', T: '#c9d3df', t: '#ffffff', U: '#8a97a8', K: '#1e2530'},
        gold: {o: '#3a1406', W: '#a0302a', w: '#c4473d', V: '#7a221d', T: '#f5c542', t: '#fff3a0', U: '#c08a1a', K: '#3a1406'},
        platinum: {o: '#1b2a36', W: '#5d7d93', w: '#7fa3ba', V: '#3e5a6e', T: '#e8fbff', t: '#ffffff', U: '#9fd8ec', K: '#1b2a36'},
        diamond: {o: '#14324a', W: '#9fe3ff', w: '#d4f6ff', V: '#5fb8e0', T: '#ffffff', t: '#fff0fb', U: '#b9ecff', K: '#14324a'}
    };
    var SVG_NS = 'http://www.w3.org/2000/svg';
    var chanceFormat = new Intl.NumberFormat('vi-VN', {maximumFractionDigits: 2});
    var reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

    function chestArt(kind, scale) {
        var colours = CHEST_COLOURS[kind] || CHEST_COLOURS.bronze;
        var svg = document.createElementNS(SVG_NS, 'svg');
        svg.setAttribute('viewBox', '0 0 20 15');
        svg.setAttribute('width', 20 * scale);
        svg.setAttribute('height', 15 * scale);
        svg.setAttribute('shape-rendering', 'crispEdges');
        svg.setAttribute('class', 'chest-art');
        svg.setAttribute('aria-hidden', 'true');
        var body = document.createElementNS(SVG_NS, 'g');
        var lid = document.createElementNS(SVG_NS, 'g');
        lid.setAttribute('class', 'chest-lid');
        CHEST_ROWS.forEach(function (row, y) {
            for (var x = 0; x < row.length;) {
                var end = x;
                while (end < row.length && row[end] === row[x]) end++;
                if (row[x] !== '.') {
                    var rect = document.createElementNS(SVG_NS, 'rect');
                    rect.setAttribute('x', x);
                    rect.setAttribute('y', y);
                    rect.setAttribute('width', end - x);
                    rect.setAttribute('height', 1);
                    rect.setAttribute('fill', colours[row[x]]);
                    (y <= 6 ? lid : body).append(rect);
                }
                x = end;
            }
        });
        svg.append(body, lid);
        return svg;
    }

    function chanceText(chance) {
        return chanceFormat.format(chance) + '%';
    }

    function renderChests(list) {
        if (state.profile.rules) {
            list.append(el('p', {className: 'hint chest-hint', text: 'Đồ xịn, +10 phát huy hết ở phòng luật Tự do; phòng Cân bằng chỉ khi bắn trúng thân; phòng Sát thương gốc không tính đồ. Xem tab Chỉ số.'}));
        }
        state.shop.chests.forEach(function (chest) {
            var odds = el('details', {className: 'odds'}, [
                el('summary', {text: 'Tỉ lệ rơi (' + chest.prizes.length + ' món)'}),
                el('ul', null, chest.prizes.map(function (prize) {
                    return el('li', null, [
                        gear(prize, 34),
                        el('span', {text: prize.name + (chest.plus ? ' +' + chest.plus : '')}),
                        el('b', {text: chanceText(prize.chance)})
                    ]);
                }))
            ]);
            var buttons = [];
            ['xu', 'luong'].forEach(function (currency) {
                var price = chest[currency];
                if (price < 0) return;
                buttons.push(el('button', {
                    type: 'button', className: 'buy', text: 'Mở · ' + priceLabel(price, currency), disabled: !chest.prizes.length,
                    onclick: async function () {
                        if (!canAfford(price, currency)) {
                            toast('Không đủ ' + (currency === 'xu' ? 'xu' : 'lượng') + ': cần ' + priceLabel(price, currency) + '.', true);
                            return;
                        }
                        if (!await confirmBuy('Mở ' + chest.name + ' với giá ' + priceLabel(price, currency) + '?', chestArt(chest.key, 4), 'Mở rương')) return;
                        openChest(chest, currency);
                    }
                }));
            });
            if (!buttons.length) buttons.push(el('span', {className: 'note-line', text: 'Không bán'}));
            list.append(el('article', {className: 'card chest-card chest-' + chest.key}, [
                el('div', {className: 'chest-stage'}, [chestArt(chest.key, 5)]),
                el('p', {className: 'card-name', text: chest.name}),
                el('p', {className: 'card-detail', text: chest.detail}),
                chest.prizes.length ? odds : el('span', {className: 'note-line', text: 'Chưa có món nào cho ' + state.profile.character.name}),
                el('div', {className: 'card-actions'}, buttons)
            ]));
        });
    }

    var chestBusy = false;

    async function openChest(chest, currency) {
        if (chestBusy) return;
        chestBusy = true;
        var show = showChest(chest);
        try {
            var result = await api('POST', 'chest/open', {chest: chest.key, currency: currency});
            setProfile(result);
            show.prize(result.prize, currency);
            loadShop(); // the odds can change, for example once Skin Thor is owned
        } catch (error) {
            show.failed(error.message);
        } finally {
            chestBusy = false;
        }
    }

    /** Shakes the chest in the dialog until the prize is known, then opens it and shows the prize. */
    function showChest(chest) {
        var dialog = $('chest-dialog');
        var scene = $('chest-scene');
        scene.className = 'chest-scene chest-' + chest.key + ' rolling';
        scene.replaceChildren(el('span', {className: 'chest-rays'}), chestArt(chest.key, 8));
        $('chest-title').textContent = 'Đang mở ' + chest.name + '...';
        $('chest-prize').replaceChildren();
        $('chest-actions').replaceChildren();
        if (!dialog.open) dialog.showModal();
        var started = Date.now();

        function after(work) {
            var wait = reducedMotion.matches ? 0 : 900 - (Date.now() - started);
            setTimeout(work, Math.max(0, wait));
        }

        function closeButton() {
            return el('button', {type: 'button', className: 'text-btn', text: 'Đóng', onclick: function () { dialog.close(); }});
        }

        return {
            prize: function (prize, currency) {
                after(function () {
                    scene.classList.remove('rolling');
                    scene.classList.add('open');
                    $('chest-title').textContent = prize.extended ? 'Cộng thêm ngày!' : prize.chance < 3 ? 'Cực hiếm!' : prize.chance < 10 ? 'Hiếm!' : 'Bạn nhận được';
                    $('chest-prize').replaceChildren(el('article', {className: 'card prize-card' + (prize.plus ? ' upgrade-card tier-' + tier(prize.plus) : '')}, [
                        el('div', {className: 'card-top'}, [
                            aura(prize, 64),
                            el('div', null, [
                                el('p', {className: 'card-name', text: prize.name + (prize.plus ? ' +' + prize.plus : '')}),
                                el('p', {className: 'card-meta', text: prize.typeName + ' · cấp ' + prize.level + ' · ' + daysText(prize)})
                            ])
                        ]),
                        statTags(prize),
                        el('p', {className: 'note-line', text: prize.extended ? 'Bạn đã có món này nên được cộng thêm ngày dùng.'
                            : 'Tỉ lệ rơi ' + chanceText(prize.chance) + (prize.wearable ? '' : ' · cần cấp ' + prize.level + ' mới mặc được')})
                    ]));
                    var actions = [];
                    if (!prize.extended && prize.wearable && !prize.inUse) {
                        actions.push(el('button', {type: 'button', className: 'game-btn small-btn', onclick: function () {
                            dialog.close();
                            run(function () { return api('POST', 'wear', {dbKey: prize.dbKey}); }, cheer);
                        }}, [el('span', {className: 'big', text: 'Mặc ngay'})]));
                    }
                    var price = chest[currency];
                    if (canAfford(price, currency)) {
                        actions.push(el('button', {type: 'button', className: 'buy', text: 'Mở tiếp · ' + priceLabel(price, currency),
                            onclick: function () { openChest(chest, currency); }}));
                    }
                    actions.push(closeButton());
                    $('chest-actions').replaceChildren.apply($('chest-actions'), actions);
                    if (!reducedMotion.matches && prize.chance < 10) cheer();
                });
            },
            failed: function (message) {
                after(function () {
                    scene.classList.remove('rolling');
                    $('chest-title').textContent = 'Chưa mở được';
                    $('chest-prize').replaceChildren(el('p', {className: 'form-error', text: message}));
                    $('chest-actions').replaceChildren(closeButton());
                });
            }
        };
    }

    // --- tabs and start ----------------------------------------------------------------------

    document.querySelectorAll('.tab-btn').forEach(function (button) {
        button.addEventListener('click', function () { selectTab(button.dataset.tab); });
    });

    function selectTab(tab) {
        state.tab = tab;
        document.querySelectorAll('.tab-btn').forEach(function (button) {
            var active = button.dataset.tab === tab;
            button.classList.toggle('active', active);
            button.setAttribute('aria-selected', active ? 'true' : 'false');
        });
        document.querySelectorAll('.tab-panel').forEach(function (panel) {
            panel.hidden = panel.id !== 'tab-' + tab;
        });
        if (tab === 'shop') {
            renderShop();
            loadShop();
        }
        if (tab === 'record') loadRecord();
    }

    // --- Thành tích: totals, leaderboard, head-to-head, match history ---------------------------

    var RESULT_TEXT = {'1': 'Thắng', '0': 'Hoà', '-1': 'Thua'};

    function fmt(n) { return Number(n || 0).toLocaleString('vi-VN'); }

    function table(id, head, rows) {
        var t = document.getElementById(id);
        t.replaceChildren(el('tr', {}, head.map(function (h) { return el('th', {text: h}); })));
        if (!rows.length) t.append(el('tr', {}, [el('td', {colspan: head.length, text: 'Chưa có trận nào.'})]));
        rows.forEach(function (r) { t.append(r); });
    }

    function cells(values, className) {
        return el('tr', {className: className}, values.map(function (v) { return el('td', {text: String(v)}); }));
    }

    async function loadRecord() {
        var data;
        try {
            data = await api('GET', 'stats');
        } catch (error) {
            document.getElementById('record-total').replaceChildren(el('p', {className: 'hint', text: error.message}));
            return;
        }
        var t = data.total;
        var rate = t.matches ? Math.round(t.wins * 100 / t.matches) + '%' : '-';
        document.getElementById('record-total').replaceChildren.apply(document.getElementById('record-total'), [
            ['Điểm', fmt(t.points)], ['Trận', fmt(t.matches)], ['Thắng / Hoà / Thua', t.wins + ' / ' + t.draws + ' / ' + t.losses],
            ['Tỉ lệ thắng', rate], ['Sát thương gây ra', fmt(t.dealt)], ['Sát thương hứng chịu', fmt(t.taken)],
            ['Máu đã hồi', fmt(t.healed)], ['Hạ gục', fmt(t.kills)]
        ].map(function (p, i) { return el('div', {className: 'final-stat stat-' + (i % 4)}, [el('span', {className: 'final-name', text: p[0]}), el('b', {className: 'final-value', text: p[1]})]); }));
        table('record-ranking', ['#', 'Người chơi', 'Điểm', 'Trận', 'Thắng', 'Sát thương', 'Hạ gục'],
            data.ranking.map(function (r, i) {
                return cells([i + 1, r.name, fmt(r.points), r.matches, r.wins, fmt(r.dealt), r.kills], r.user_id === data.me ? 'me-row' : null);
            }));
        table('record-rivals', ['Đối thủ', 'Trận', 'Thắng', 'Hoà', 'Thua'],
            data.rivals.map(function (r) { return cells([r.name, r.matches, r.wins, r.draws, r.losses]); }));
        var box = document.getElementById('record-matches');
        box.replaceChildren();
        if (!data.matches.length) box.append(el('p', {className: 'hint', text: 'Chưa có trận nào.'}));
        data.matches.forEach(function (m) {
            var head = el('summary', {}, [el('b', {className: 'res res' + m.result, text: RESULT_TEXT[m.result]}),
                ' ' + m.at + ' · ' + m.glass + ' · gây ' + fmt(m.dealt) + ' · chịu ' + fmt(m.taken) + ' · hồi ' + fmt(m.healed) + ' · hạ ' + m.kills]);
            var rows = el('table', {className: 'upgrade-table'}, [el('tr', {}, ['Đội', 'Người chơi', 'Nhân vật', 'Kết quả', 'Gây ra', 'Hứng chịu', 'Hồi máu', 'Hạ gục']
                .map(function (h) { return el('th', {text: h}); }))]);
            m.fighters.forEach(function (f) {
                rows.append(cells([f.team, f.name, f.glass, RESULT_TEXT[f.result], fmt(f.dealt), fmt(f.taken), fmt(f.healed), f.kills]));
            });
            box.append(el('details', {className: 'match-row'}, [head, el('div', {className: 'table-scroll'}, [rows])]));
        });
    }

    api('GET', 'me').then(showApp, showLogin);
})();
