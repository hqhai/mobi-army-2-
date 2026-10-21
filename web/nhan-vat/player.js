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
    var state = {profile: null, shop: null, tab: 'points', shopTab: 'equipment', typeFilter: 'all', pending: [0, 0, 0, 0, 0]};

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
        node.style.backgroundImage = 'url(../img/items.png)';
        node.style.backgroundPosition = '0 -' + (id + 2) * 16 + 'px';
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

    function confirmBuy(text, picture) {
        return new Promise(function (resolve) {
            var dialog = $('confirm');
            $('confirm-text').textContent = text;
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
        if (glassChanged) state.shop = null;
        renderHero();
        renderPicker();
        renderPoints();
        renderBag();
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

    function renderBag() {
        var p = state.profile;
        var slots = $('slots');
        slots.replaceChildren();
        SLOTS.forEach(function (label, type) {
            var worn = p.bag.find(function (item) { return item.inUse && !item.vip && item.type === type; });
            slots.append(el('div', {className: 'slot' + (worn ? ' filled' : '')}, [
                el('b', {text: label}),
                worn ? gear(worn, 44) : el('span', {className: 'thumb small'}),
                el('span', {text: worn ? worn.name : 'Chưa mặc'})
            ]));
        });
        var vip = p.bag.find(function (item) { return item.inUse && item.vip; });
        if (p.bag.some(function (item) { return item.vip; })) {
            slots.append(el('div', {className: 'slot' + (vip ? ' filled' : '')}, [
                el('b', {text: 'VIP'}), vip ? gear(vip, 44) : el('span', {className: 'thumb small'}), el('span', {text: vip ? vip.name : 'Chưa mặc'})
            ]));
        }
        $('bag-count').textContent = '(' + p.bagSize + '/' + p.bagLimit + ' món, đang xem đồ của ' + p.character.name + ')';
        var bag = $('bag');
        bag.replaceChildren();
        if (!p.bag.length) bag.append(el('p', {className: 'empty', text: 'Rương chưa có đồ của ' + p.character.name + '. Ghé Cửa hàng để mua nhé!'}));
        p.bag.forEach(function (item) {
            var action;
            if (item.inUse) {
                action = el('button', {type: 'button', className: 'buy secondary', text: 'Tháo ra',
                    onclick: function () { run(function () { return api('POST', 'remove', {dbKey: item.dbKey}); }); }});
            } else if (item.wearable) {
                action = el('button', {type: 'button', className: 'buy', text: 'Mặc vào',
                    onclick: function () { run(function () { return api('POST', 'wear', {dbKey: item.dbKey}); }, cheer); }});
            } else {
                action = el('span', {className: 'note-line', text: item.days <= 0 && !item.permanent ? 'Đã hết hạn' : 'Cần cấp ' + item.level});
            }
            bag.append(el('article', {className: 'card' + (item.inUse ? ' worn' : '')}, [
                el('div', {className: 'card-top'}, [
                    gear(item, 64),
                    el('div', null, [
                        el('p', {className: 'card-name', text: item.name}),
                        el('p', {className: 'card-meta', text: item.typeName + ' · cấp ' + item.level + ' · ' + daysText(item)})
                    ])
                ]),
                statTags(item),
                el('div', {className: 'card-actions'}, [item.inUse ? el('span', {className: 'note-line', text: 'Đang mặc'}) : null, action])
            ]));
        });
        var pocket = $('pocket');
        pocket.replaceChildren();
        p.items.forEach(function (item) {
            pocket.append(el('span', {className: 'chip', title: item.name}, [itemIcon(item.id, 1.5), item.name + ' ×' + item.num]));
        });
        p.specials.forEach(function (item) {
            pocket.append(el('span', {className: 'chip', title: item.detail}, [specialIcon(item.id, 1.5), item.name + ' ×' + item.num]));
        });
        if (!pocket.children.length) pocket.append(el('p', {className: 'empty', text: 'Túi đang trống.'}));
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
        if (state.shopTab === 'equipment') {
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
    }

    api('GET', 'me').then(showApp, showLogin);
})();
