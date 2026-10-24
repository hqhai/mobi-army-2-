(function () {
    'use strict';

    var STATS = 'Máu/SM/PT/MM/ĐĐ';
    var number = new Intl.NumberFormat('vi-VN');
    var state = {tab: 'players', role: 'admin', players: [], selectedId: null, levels: [], sets: [], items: [], data: {}};

    /** Gifting gear and items is for super-admins only (the server refuses everyone else as well). */
    function canGift() {
        return state.role === 'super';
    }

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

    function money(value) {
        return value === -1 ? el('span', {className: 'not-sold', text: 'không bán'}) : number.format(value);
    }

    async function api(method, path, body) {
        var options = {method: method, headers: {}, credentials: 'same-origin'};
        if (method !== 'GET') {
            options.headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(body || {});
        }
        var response;
        try {
            response = await fetch('/api/admin/' + path, options);
        } catch (error) {
            throw new Error('Không kết nối được tới server.');
        }
        var data = {};
        try {
            data = await response.json();
        } catch (ignored) {
            // empty or non-JSON body
        }
        if (response.status === 401 && path !== 'login') showLogin();
        if (!response.ok) throw new Error(data.error || 'Lỗi ' + response.status + '.');
        return data;
    }

    // --- login and account -------------------------------------------------------------------

    function showLogin() {
        $('app').hidden = true;
        $('login-view').hidden = false;
        $('login-password').value = '';
        $('login-username').focus();
    }

    function showApp(me) {
        $('login-view').hidden = true;
        $('app').hidden = false;
        state.role = me.role || 'admin';
        $('who').textContent = me.username + (canGift() ? ' · super-admin' : '');
        $('default-warning').hidden = !me.defaultPassword;
        selectTab(state.tab);
    }

    $('login-form').addEventListener('submit', async function (event) {
        event.preventDefault();
        var errorBox = $('login-error');
        var submit = $('login-submit');
        errorBox.hidden = true;
        submit.disabled = true;
        try {
            showApp(await api('POST', 'login', {username: $('login-username').value, password: $('login-password').value}));
        } catch (error) {
            errorBox.textContent = error.message;
            errorBox.hidden = false;
        } finally {
            submit.disabled = false;
        }
    });

    $('logout').addEventListener('click', async function () {
        try {
            await api('POST', 'logout');
        } catch (ignored) {
            // already logged out
        }
        showLogin();
    });

    function openPassword() {
        $('password-form').reset();
        $('password-error').hidden = true;
        $('password-dialog').showModal();
        $('password-current').focus();
    }

    $('open-password').addEventListener('click', openPassword);
    $('warning-password').addEventListener('click', openPassword);
    $('password-cancel').addEventListener('click', function () {
        $('password-dialog').close();
    });

    $('password-form').addEventListener('submit', async function (event) {
        event.preventDefault();
        var errorBox = $('password-error');
        errorBox.hidden = true;
        var next = $('password-next').value;
        try {
            if (next !== $('password-confirm').value) throw new Error('Hai lần nhập mật khẩu mới chưa khớp.');
            var me = await api('POST', 'password', {current: $('password-current').value, next: next});
            $('default-warning').hidden = !me.defaultPassword;
            $('password-dialog').close();
        } catch (error) {
            errorBox.textContent = error.message;
            errorBox.hidden = false;
        }
    });

    // --- tabs --------------------------------------------------------------------------------

    var loaders = {
        players: loadPlayers,
        characters: function () { return loadCatalog('characters'); },
        equipment: function () { return loadCatalog('equipment'); },
        items: function () { return loadCatalog('items'); },
        specials: function () { return loadCatalog('specials'); },
        rules: loadRules,
        balance: loadBalance
    };

    document.querySelectorAll('.sidebar button').forEach(function (button) {
        button.addEventListener('click', function () {
            selectTab(button.dataset.tab);
        });
    });

    function selectTab(tab) {
        state.tab = tab;
        document.querySelectorAll('.sidebar button').forEach(function (button) {
            var active = button.dataset.tab === tab;
            button.classList.toggle('active', active);
            if (active) button.setAttribute('aria-current', 'page');
            else button.removeAttribute('aria-current');
        });
        document.querySelectorAll('.tab').forEach(function (section) {
            section.hidden = section.id !== 'tab-' + tab;
        });
        tabMessage(null);
        loaders[tab]().catch(function (error) {
            tabMessage(error.message);
        });
    }

    function tabMessage(text) {
        document.querySelectorAll('.tab-error').forEach(function (node) {
            node.remove();
        });
        if (!text) return;
        var toolbar = $('tab-' + state.tab).querySelector('.toolbar');
        toolbar.after(el('p', {className: 'message error tab-error', role: 'alert', text: text}));
    }

    // --- players -----------------------------------------------------------------------------

    async function loadPlayers() {
        var gifts = canGift();
        var results = await Promise.all([api('GET', 'players'), state.levels.length ? state.levels : api('GET', 'levels'),
            !gifts ? [] : state.sets.length ? state.sets : api('GET', 'sets'),
            !gifts ? [] : state.items.length ? state.items : api('GET', 'items')]);
        state.players = results[0];
        state.levels = results[1];
        state.sets = results[2];
        state.items = results[3];
        renderPlayers();
        renderPanel();
    }

    async function refreshPlayersQuietly() {
        try {
            state.players = await api('GET', 'players');
            renderPlayers();
            updatePanelStats();
        } catch (ignored) {
            // the next refresh or a manual reload shows the problem
        }
    }

    setInterval(function () {
        if (state.tab === 'players' && !$('app').hidden && document.visibilityState === 'visible') refreshPlayersQuietly();
    }, 15000);

    $('players-search').addEventListener('input', renderPlayers);
    $('players-refresh').addEventListener('click', function () {
        tabMessage(null);
        loadPlayers().catch(function (error) {
            tabMessage(error.message);
        });
    });

    function selectedPlayer() {
        return state.players.find(function (player) {
            return player.id === state.selectedId;
        });
    }

    function renderPlayers() {
        var query = $('players-search').value.trim().toLowerCase();
        var body = $('players-body');
        var online = state.players.filter(function (player) { return player.online; }).length;
        $('players-summary').textContent = state.players.length + ' tài khoản · ' + online + ' đang online';
        body.replaceChildren();
        state.players.filter(function (player) {
            return player.username.toLowerCase().indexOf(query) !== -1;
        }).forEach(function (player) {
            var glass = player.glasses[player.selectedGlass] || player.glasses[0];
            var select = function () {
                state.selectedId = player.id;
                renderPlayers();
                renderPanel();
            };
            body.append(el('tr', {
                className: player.id === state.selectedId ? 'selected' : null,
                tabindex: 0,
                onclick: select,
                onkeydown: function (event) {
                    if (event.key === 'Enter') select();
                }
            }, [
                el('td', null, [
                    el('span', {className: 'dot' + (player.online ? ' online' : ''), title: player.online ? 'Đang online' : 'Offline'}),
                    player.username,
                    player.loaded ? null : el('span', {className: 'tag', text: 'chưa vào game'})
                ]),
                el('td', {className: 'num', text: number.format(player.xu)}),
                el('td', {className: 'num', text: number.format(player.luong)}),
                el('td', {className: 'num', text: number.format(player.cup)}),
                el('td', {text: glass.name + ' · cấp ' + glass.level})
            ]));
        });
        if (!body.children.length) {
            body.append(el('tr', null, [el('td', {colspan: 5, className: 'muted', text: 'Không có người chơi nào.'})]));
        }
    }

    function updatePlayer(result) {
        var index = state.players.findIndex(function (player) { return player.id === result.id; });
        if (index !== -1) state.players[index] = result;
        renderPlayers();
    }

    function statBox(label, key, value) {
        return el('div', {className: 'stat-box'}, [el('span', {className: 'muted small', text: label}), el('b', {'data-stat': key, text: number.format(value)})]);
    }

    function statusText(player) {
        return player.online ? 'Đang online' : player.loaded ? 'Offline' : 'Chưa vào game';
    }

    function updatePanelStats() {
        var player = selectedPlayer();
        if (!player) return;
        document.querySelectorAll('#player-panel [data-stat]').forEach(function (node) {
            node.textContent = number.format(player[node.dataset.stat]);
        });
        var status = document.querySelector('#player-panel [data-status]');
        if (status) status.textContent = statusText(player) + ' · thay đổi có hiệu lực ngay';
    }

    function renderPanel(message) {
        var panel = $('player-panel');
        var player = selectedPlayer();
        panel.replaceChildren();
        if (!player) {
            panel.append(el('p', {className: 'muted', text: canGift()
                ? 'Chọn một người chơi trong bảng để cộng xu, lượng, tăng cấp hoặc tặng đồ, vật phẩm.'
                : 'Chọn một người chơi trong bảng để cộng xu, lượng hoặc tăng cấp.'}));
            return;
        }
        [
            el('h2', {text: player.username}),
            el('p', {className: 'muted small', 'data-status': true, text: statusText(player) + ' · thay đổi có hiệu lực ngay'}),
            el('div', {className: 'stats'}, [statBox('Xu', 'xu', player.xu), statBox('Lượng', 'luong', player.luong), statBox('Cúp', 'cup', player.cup)]),
            message ? el('p', {className: 'message ' + message.kind, role: message.kind === 'error' ? 'alert' : 'status', text: message.text}) : null,
            moneyForm(player, 'xu', 'Cộng xu'),
            moneyForm(player, 'luong', 'Cộng lượng'),
            levelForm(player),
            canGift() ? setForm(player) : null,
            canGift() ? itemForm(player) : null,
            passwordForm(player),
            renameForm(player),
            el('ul', {className: 'glasses', 'aria-label': 'Nhân vật của người chơi'}, player.glasses.map(function (glass) {
                return el('li', null, [el('span', {text: glass.name}), el('span', {className: glass.open ? null : 'muted', text: glass.open ? 'cấp ' + glass.level : 'chưa mở'})]);
            }))
        ].forEach(function (node) {
            if (node) panel.append(node);
        });
    }

    function moneyForm(player, currency, label) {
        var id = 'amount-' + currency;
        var input = el('input', {id: id, type: 'number', step: 1, inputmode: 'numeric', placeholder: 'VD: 10000'});
        var button = el('button', {type: 'submit', className: 'btn primary', text: 'Cộng'});
        var form = el('form', {novalidate: true}, [
            el('label', {for: id, text: label}),
            el('div', {className: 'row'}, [input, button]),
            el('p', {className: 'hint', text: 'Nhập số âm để trừ (không xuống dưới 0).'})
        ]);
        form.addEventListener('submit', async function (event) {
            event.preventDefault();
            var amount = Number(input.value);
            if (input.value.trim() === '' || !Number.isInteger(amount) || amount === 0) {
                renderPanel({kind: 'error', text: 'Hãy nhập một số nguyên khác 0.'});
                return;
            }
            button.disabled = true;
            try {
                var result = await api('POST', 'players/' + player.id + '/' + currency, {amount: amount});
                updatePlayer(result);
                var unit = currency === 'xu' ? 'xu' : 'lượng';
                var change = result.after - result.before;
                renderPanel({kind: 'ok', text: (change >= 0 ? 'Đã cộng ' : 'Đã trừ ') + number.format(Math.abs(change)) + ' ' + unit + ': '
                    + number.format(result.before) + ' → ' + number.format(result.after) + '.'});
            } catch (error) {
                renderPanel({kind: 'error', text: error.message});
            }
        });
        return form;
    }

    function levelForm(player) {
        var open = player.glasses.filter(function (glass) { return glass.open; });
        var glassSelect = el('select', {id: 'level-glass', 'aria-label': 'Nhân vật cần tăng cấp'});
        open.forEach(function (glass) {
            glassSelect.append(el('option', {value: glass.id, text: glass.name + ' (cấp ' + glass.level + ')'}));
        });
        if (open.some(function (glass) { return glass.id === player.selectedGlass; })) glassSelect.value = String(player.selectedGlass);
        var levelSelect = el('select', {id: 'level-target', 'aria-label': 'Cấp mới'});
        var button = el('button', {type: 'submit', className: 'btn primary', text: 'Lên cấp'});
        var hint = el('p', {className: 'hint'});
        function fillLevels() {
            var glass = player.glasses.find(function (entry) { return entry.id === Number(glassSelect.value); });
            levelSelect.replaceChildren();
            state.levels.filter(function (level) { return glass && level > glass.level; }).forEach(function (level) {
                levelSelect.append(el('option', {value: level, text: 'Cấp ' + level}));
            });
            button.disabled = !levelSelect.options.length;
            hint.textContent = !open.length ? 'Người chơi chưa mở nhân vật nào.'
                : !levelSelect.options.length ? 'Nhân vật này đã ở cấp cao nhất.'
                : 'Được cộng 3 điểm tiềm năng mỗi cấp, như khi lên cấp trong game.';
        }
        glassSelect.addEventListener('change', fillLevels);
        fillLevels();
        var form = el('form', {novalidate: true}, [
            el('label', {for: 'level-glass', text: 'Tăng cấp'}),
            el('div', {className: 'row'}, [glassSelect, levelSelect, button]),
            hint
        ]);
        form.addEventListener('submit', async function (event) {
            event.preventDefault();
            if (button.disabled) return;
            button.disabled = true;
            var glassId = Number(glassSelect.value);
            try {
                var result = await api('POST', 'players/' + player.id + '/level', {glassId: glassId, level: Number(levelSelect.value)});
                updatePlayer(result);
                var glass = result.glasses.find(function (entry) { return entry.id === glassId; });
                renderPanel({kind: 'ok', text: glass.name + ': cấp ' + result.before + ' → ' + result.after
                    + ' (điểm tiềm năng hiện có: ' + result.points + ').'});
            } catch (error) {
                renderPanel({kind: 'error', text: error.message});
            }
        });
        return form;
    }

    function setForm(player) {
        var open = player.glasses.filter(function (glass) { return glass.open; });
        var glassSelect = el('select', {id: 'set-glass', 'aria-label': 'Nhân vật nhận bộ đồ'});
        open.forEach(function (glass) {
            glassSelect.append(el('option', {value: glass.id, text: glass.name + ' (cấp ' + glass.level + ')'}));
        });
        if (open.some(function (glass) { return glass.id === player.selectedGlass; })) glassSelect.value = String(player.selectedGlass);
        var setSelect = el('select', {id: 'set-tier', 'aria-label': 'Bộ đồ'});
        state.sets.forEach(function (set) {
            setSelect.append(el('option', {value: set.tier, text: set.name + ' · cấp ' + set.level + ' · nội tại ' + set.strength + '%'}));
        });
        if (state.sets.length) setSelect.value = String(state.sets[state.sets.length - 1].tier); // the gold set, the usual gift
        var wear = el('input', {id: 'set-wear', type: 'checkbox', checked: true});
        var hidden = el('input', {id: 'set-hidden', className: 'cell', type: 'number', min: 0, max: 100, step: 1, inputmode: 'numeric', value: 0});
        var button = el('button', {type: 'submit', className: 'btn primary', text: 'Tặng', disabled: !open.length || !state.sets.length});
        var form = el('form', {novalidate: true}, [
            el('label', {for: 'set-glass', text: 'Tặng bộ +14'}),
            el('div', {className: 'row'}, [glassSelect, setSelect]),
            el('label', {className: 'check', for: 'set-wear'}, [wear, 'Mặc luôn nếu đủ cấp']),
            el('div', {className: 'row'}, [el('label', {for: 'set-hidden', text: 'Sát thương ẩn (%)'}), hidden, button]),
            el('p', {className: 'hint', text: !open.length ? 'Người chơi chưa mở nhân vật nào.'
                : 'Súng, nón, áo, kính, balo cùng bộ, đã cường hóa +14: mặc đủ 5 món thì có nội tại. Hạn dùng như đồ mua ở cửa hàng. '
                + 'Sát thương ẩn chỉ tính khi mặc đủ 5 món này và người chơi không thấy; để 0 nếu không cần.'})
        ]);
        form.addEventListener('submit', async function (event) {
            event.preventDefault();
            if (button.disabled) return;
            var percent = hidden.value.trim() === '' ? 0 : Number(hidden.value);
            if (!Number.isInteger(percent) || percent < 0) {
                renderPanel({kind: 'error', text: 'Sát thương ẩn là số nguyên, để 0 nếu không cần.'});
                return;
            }
            button.disabled = true;
            try {
                var result = await api('POST', 'players/' + player.id + '/set', {
                    glassId: Number(glassSelect.value), tier: Number(setSelect.value), wear: wear.checked ? 1 : 0, hiddenAtt: percent
                });
                updatePlayer(result);
                renderPanel({kind: 'ok', text: result.message});
            } catch (error) {
                renderPanel({kind: 'error', text: error.message});
            }
        });
        return form;
    }

    function itemForm(player) {
        var select = el('select', {id: 'item-id', 'aria-label': 'Vật phẩm'});
        state.items.forEach(function (item) {
            select.append(el('option', {value: item.id, text: item.name}));
        });
        var amount = el('input', {id: 'item-amount', className: 'cell', type: 'number', min: 1, max: 99, step: 1, inputmode: 'numeric', value: 99,
            'aria-label': 'Số lượng'});
        var give = el('button', {type: 'submit', className: 'btn primary', text: 'Tặng', disabled: !state.items.length});
        var full = el('button', {type: 'button', className: 'btn', text: 'Đầy đủ vật phẩm', disabled: !state.items.length});
        var form = el('form', {novalidate: true}, [
            el('label', {for: 'item-id', text: 'Tặng vật phẩm'}),
            el('div', {className: 'row'}, [select, amount, give]),
            el('div', {className: 'row'}, [full]),
            el('p', {className: 'hint', text: 'Mỗi vật phẩm giữ tối đa 99, như khi mua ở cửa hàng; "Đầy đủ vật phẩm" nâng mọi vật phẩm lên 99. '
                + 'Không tặng được khi người chơi đang trong trận.'})
        ]);
        async function send(itemId, count, button) {
            button.disabled = true;
            try {
                var result = await api('POST', 'players/' + player.id + '/items', {itemId: itemId, amount: count});
                updatePlayer(result);
                renderPanel({kind: 'ok', text: result.message});
            } catch (error) {
                renderPanel({kind: 'error', text: error.message});
            }
        }
        form.addEventListener('submit', function (event) {
            event.preventDefault();
            if (give.disabled) return;
            var count = Number(amount.value);
            if (!Number.isInteger(count) || count < 1 || count > 99) {
                renderPanel({kind: 'error', text: 'Số lượng từ 1 đến 99.'});
                return;
            }
            send(Number(select.value), count, give);
        });
        full.addEventListener('click', function () {
            if (!full.disabled) send(-1, 99, full);
        });
        return form;
    }

    // Same rules as registration: the in-game login fields only take letters and digits.
    var NAME_RULE = /^[A-Za-z0-9]{3,20}$/;
    var PASSWORD_RULE = /^[A-Za-z0-9]{6,30}$/;

    function randomPassword() {
        var alphabet = 'abcdefghjkmnpqrstuvwxyz23456789'; // no look-alikes such as l/1 or o/0
        var bytes = new Uint8Array(8);
        crypto.getRandomValues(bytes);
        return Array.from(bytes, function (byte) { return alphabet[byte % alphabet.length]; }).join('');
    }

    function passwordForm(player) {
        var input = el('input', {id: 'reset-password', type: 'text', maxlength: 30, autocomplete: 'off', spellcheck: 'false', placeholder: 'Mật khẩu mới'});
        var random = el('button', {type: 'button', className: 'btn', text: 'Ngẫu nhiên', onclick: function () {
            input.value = randomPassword();
            input.focus();
        }});
        var button = el('button', {type: 'submit', className: 'btn primary', text: 'Đặt lại'});
        var form = el('form', {novalidate: true}, [
            el('label', {for: 'reset-password', text: 'Đặt lại mật khẩu'}),
            el('div', {className: 'row'}, [input, random, button]),
            el('p', {className: 'hint', text: '6-30 chữ cái không dấu hoặc chữ số. Người chơi bị đăng xuất khỏi trang Nhân vật trên web.'})
        ]);
        form.addEventListener('submit', async function (event) {
            event.preventDefault();
            var password = input.value.trim();
            if (!PASSWORD_RULE.test(password)) {
                renderPanel({kind: 'error', text: 'Mật khẩu gồm 6-30 chữ cái không dấu hoặc chữ số.'});
                return;
            }
            button.disabled = true;
            try {
                var result = await api('POST', 'players/' + player.id + '/password', {password: password});
                updatePlayer(result);
                renderPanel({kind: 'ok', text: 'Mật khẩu mới của ' + result.username + ': ' + password + '. Hãy báo cho người chơi.'});
            } catch (error) {
                renderPanel({kind: 'error', text: error.message});
            }
        });
        return form;
    }

    function renameForm(player) {
        var input = el('input', {id: 'rename', type: 'text', maxlength: 20, autocomplete: 'off', spellcheck: 'false', value: player.username});
        var button = el('button', {type: 'submit', className: 'btn primary', text: 'Đổi tên'});
        var form = el('form', {novalidate: true}, [
            el('label', {for: 'rename', text: 'Đổi tên'}),
            el('div', {className: 'row'}, [input, button]),
            el('p', {className: 'hint', text: 'Đổi cả tên đăng nhập lẫn tên trong game (lưu chữ thường). Người chơi đăng nhập bằng tên mới.'})
        ]);
        form.addEventListener('submit', async function (event) {
            event.preventDefault();
            var name = input.value.trim();
            if (!NAME_RULE.test(name)) {
                renderPanel({kind: 'error', text: 'Tên gồm 3-20 chữ cái không dấu hoặc chữ số.'});
                return;
            }
            button.disabled = true;
            try {
                var result = await api('POST', 'players/' + player.id + '/name', {username: name});
                updatePlayer(result);
                renderPanel({kind: 'ok', text: 'Đã đổi tên ' + result.previous + ' → ' + result.username + '.'
                    + (result.online ? ' Người chơi đang online sẽ thấy tên mới sau khi đăng nhập lại.' : '')});
            } catch (error) {
                renderPanel({kind: 'error', text: error.message});
            }
        });
        return form;
    }

    // --- catalogs ----------------------------------------------------------------------------

    var catalogs = {
        characters: {
            columns: [
                {label: 'Nhân vật', render: function (r) { return el('b', {text: r.name}); }},
                {label: 'Giá mở (xu)', num: true, key: 'xu', render: function (r) { return number.format(r.xu); }},
                {label: 'Giá mở (lượng)', num: true, key: 'luong', render: function (r) { return number.format(r.luong); }},
                {label: 'Sát thương', num: true, key: 'att', render: function (r) { return number.format(r.att); }},
                {label: 'Ma sát', num: true, render: function (r) { return r.friction; }},
                {label: 'Góc', num: true, render: function (r) { return r.angle; }},
                {label: 'Tầm', num: true, render: function (r) { return r.distance; }},
                {label: 'Đạn', num: true, render: function (r) { return r.bullet; }}
            ]
        },
        equipment: {
            filter: equipmentFilter,
            columns: [
                {label: 'Nhân vật', render: function (r) { return r.glassName; }},
                {label: 'Loại', render: function (r) { return r.typeName; }},
                {label: 'Tên', render: function (r) { return r.name; }},
                {label: 'Cấp', num: true, render: function (r) { return r.level; }},
                {label: 'Hạn', num: true, render: function (r) { return r.days + ' ngày'; }},
                {label: 'Giá xu', num: true, key: 'xu', render: function (r) { return money(r.xu); }},
                {label: 'Giá lượng', num: true, key: 'luong', render: function (r) { return money(r.luong); }},
                {label: 'Chỉ số (' + STATS + ')', key: 'ability', stats: true, render: function (r) { return r.ability.join(' / '); }},
                {label: '% (' + STATS + ')', key: 'percent', stats: true, render: function (r) { return r.percent.join(' / '); }}
            ]
        },
        items: {
            columns: [
                {label: 'Vật phẩm', render: function (r) { return r.name; }},
                {label: 'Giá xu', num: true, key: 'xu', render: function (r) { return money(r.xu); }},
                {label: 'Giá lượng', num: true, key: 'luong', render: function (r) { return money(r.luong); }},
                {label: 'Mang theo', num: true, render: function (r) { return r.carryable; }}
            ]
        },
        specials: {
            columns: [
                {label: 'Tên', render: function (r) { return r.name; }},
                {label: 'Mô tả', render: function (r) { return r.detail; }},
                {label: 'Giá xu', num: true, key: 'xu', render: function (r) { return money(r.xu); }},
                {label: 'Giá lượng', num: true, key: 'luong', render: function (r) { return money(r.luong); }},
                {label: 'Hạn', num: true, render: function (r) { return r.days ? r.days + ' ngày' : 'không hạn'; }}
            ]
        }
    };

    async function loadCatalog(name) {
        state.data[name] = await api('GET', name);
        if (name === 'equipment') setupEquipmentFilters(state.data.equipment);
        renderCatalog(name);
    }

    function renderCatalog(name) {
        var spec = catalogs[name];
        var rows = (state.data[name] || []).filter(spec.filter || function () { return true; });
        var head = el('thead', null, [el('tr', null, spec.columns.map(function (column) {
            return el('th', {className: column.num ? 'num' : null, text: column.label});
        }).concat([el('th', {className: 'actions', text: ''})]))]);
        var body = el('tbody', null, rows.map(function (row) { return viewRow(name, row); }));
        if (!rows.length) body.append(el('tr', null, [el('td', {colspan: spec.columns.length + 1, className: 'muted', text: 'Không có dữ liệu phù hợp.'})]));
        $(name + '-table').replaceChildren(head, body);
        if (name === 'equipment') $('equipment-count').textContent = rows.length + ' món';
    }

    function viewRow(name, row, saved) {
        var tr = el('tr', {className: saved ? 'saved' : null}, catalogs[name].columns.map(function (column) {
            return el('td', {className: column.num ? 'num' : null}, [column.render(row)]);
        }));
        tr.append(el('td', {className: 'actions'}, [el('button', {
            type: 'button', className: 'link', text: 'Sửa',
            onclick: function () { tr.replaceWith(editRow(name, row)); }
        })]));
        return tr;
    }

    function editRow(name, row) {
        var inputs = {};
        var tr = el('tr', {className: 'editing'});
        catalogs[name].columns.forEach(function (column) {
            var td = el('td', {className: column.num ? 'num' : null});
            if (column.stats) {
                inputs[column.key] = row[column.key].map(function (value, index) {
                    return el('input', {type: 'number', className: 'stat', value: value, step: 1, min: -128, max: 127,
                        'aria-label': column.label + ' ' + STATS.split('/')[index]});
                });
                td.append.apply(td, inputs[column.key]);
            } else if (column.key) {
                inputs[column.key] = el('input', {type: 'number', className: 'cell', value: row[column.key], step: 1, 'aria-label': column.label});
                td.append(inputs[column.key]);
            } else {
                td.append(column.render(row));
            }
            tr.append(td);
        });
        var error = el('div', {className: 'row-error', role: 'alert'});
        var save = el('button', {type: 'button', className: 'btn primary small', text: 'Lưu'});
        var cancel = el('button', {type: 'button', className: 'btn small', text: 'Hủy', onclick: function () { tr.replaceWith(viewRow(name, row)); }});
        save.addEventListener('click', async function () {
            var payload = {};
            try {
                Object.keys(inputs).forEach(function (key) {
                    payload[key] = Array.isArray(inputs[key]) ? inputs[key].map(readNumber) : readNumber(inputs[key]);
                });
            } catch (problem) {
                error.textContent = problem.message;
                return;
            }
            save.disabled = true;
            try {
                var updated = await api('PUT', name + '/' + row.id, payload);
                var list = state.data[name];
                list[list.findIndex(function (entry) { return entry.id === row.id; })] = updated;
                tr.replaceWith(viewRow(name, updated, true));
            } catch (problem) {
                error.textContent = problem.message;
                save.disabled = false;
            }
        });
        tr.append(el('td', {className: 'actions'}, [save, ' ', cancel, error]));
        tr.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                save.click();
            } else if (event.key === 'Escape') {
                cancel.click();
            }
        });
        setTimeout(function () {
            var first = tr.querySelector('input');
            if (first) first.focus();
        });
        return tr;
    }

    function readNumber(input) {
        var value = Number(input.value);
        if (input.value.trim() === '' || !Number.isInteger(value)) {
            throw new Error('Ô "' + input.getAttribute('aria-label') + '" phải là số nguyên.');
        }
        return value;
    }

    function setupEquipmentFilters(rows) {
        var glassSelect = $('equipment-glass');
        var typeSelect = $('equipment-type');
        if (glassSelect.options.length) return;
        glassSelect.append(el('option', {value: 'all', text: 'Tất cả nhân vật'}));
        typeSelect.append(el('option', {value: 'all', text: 'Tất cả loại'}));
        var glasses = new Map();
        var types = new Map();
        rows.forEach(function (row) {
            glasses.set(row.glassId, row.glassName);
            types.set(row.type, row.typeName);
        });
        glasses.forEach(function (label, id) { glassSelect.append(el('option', {value: id, text: label})); });
        types.forEach(function (label, id) { typeSelect.append(el('option', {value: id, text: label})); });
        if (glasses.size) glassSelect.value = String(glasses.keys().next().value);
    }

    function equipmentFilter(row) {
        var glass = $('equipment-glass').value;
        var type = $('equipment-type').value;
        var query = $('equipment-search').value.trim().toLowerCase();
        return (glass === 'all' || row.glassId === Number(glass))
            && (type === 'all' || row.type === Number(type))
            && (!query || row.name.toLowerCase().indexOf(query) !== -1);
    }

    ['equipment-glass', 'equipment-type'].forEach(function (id) {
        $(id).addEventListener('change', function () { renderCatalog('equipment'); });
    });
    $('equipment-search').addEventListener('input', function () { renderCatalog('equipment'); });

    // --- balance: win rates per character ----------------------------------------------------

    async function loadBalance() {
        var rows = await api('GET', 'balance');
        var table = $('balance-table');
        table.replaceChildren(el('thead', null, [el('tr', null, ['Nhân vật', 'Nội tại +14', 'Số trận', 'Thắng', 'Hòa', 'Thua', 'Tỉ lệ thắng']
            .map(function (h, i) { return el('th', {text: h, className: i >= 2 ? 'num' : ''}); }))]));
        var body = el('tbody');
        rows.filter(function (row) { return row.matches > 0 || !row.passive; }).forEach(function (row) {
            var rate = row.matches ? Math.round((row.wins + row.draws / 2) * 1000 / row.matches) / 10 + '%' : '–';
            body.append(el('tr', null, [
                el('td', {text: row.name}), el('td', {text: row.passive ? 'Có' : 'Không'}),
                el('td', {className: 'num', text: number.format(row.matches)}), el('td', {className: 'num', text: number.format(row.wins)}),
                el('td', {className: 'num', text: number.format(row.draws)}), el('td', {className: 'num', text: number.format(row.losses)}),
                el('td', {className: 'num', text: rate})
            ]));
        });
        table.append(body);
    }

    // --- match rules ---------------------------------------------------------------------------

    async function loadRules() {
        renderRules((await api('GET', 'settings')).rules);
    }

    function renderRules(rules) {
        var choice = $('rules-choice');
        choice.replaceChildren(choice.querySelector('legend'));
        rules.modes.forEach(function (mode) {
            var input = el('input', {type: 'radio', name: 'default-rules', value: mode.id, checked: mode.id === rules['default']});
            input.addEventListener('change', function () { saveDefaultRules(mode); });
            choice.append(el('label', {className: 'rule-option'}, [input, el('span', null, [el('b', {text: mode.name}), ': ' + mode.detail])]));
        });
        $('rules-list').replaceChildren.apply($('rules-list'), [
            'Đạn nổ trúng thân địch: ăn đủ sát thương cộng thêm (từ điểm, đồ, nâng cấp, ngọc, đồng đội).',
            'Nổ lệch: phần cộng thêm giảm dần, còn dưới ' + rules.bonusZero + '% sát thương thì chỉ còn sát thương gốc của nhân vật (đủ từ ' + rules.bonusFull + '%).',
            'Chí mạng x2 của may mắn chỉ nổ khi trúng thân; tỉ lệ may mắn tối đa ' + rules.maxLuck + '% mỗi lượt.',
            'Trúng thân xuyên ' + rules.pierce + '% phòng thủ và gây thêm ' + rules.tank + '% phần máu trên ' + money(rules.baseHp) + ' của mục tiêu.',
            'Phòng thủ giảm tối đa ' + rules.maxReduction + '% sát thương; may mắn không cứu khỏi chết trước đòn trúng thân. Skin Thor chỉ là trang phục.'
        ].map(function (text) { return el('li', {text: text}); }));
    }

    async function saveDefaultRules(mode) {
        var message = $('rules-message');
        var inputs = document.querySelectorAll('#rules-choice input');
        inputs.forEach(function (input) { input.disabled = true; });
        try {
            renderRules((await api('PUT', 'settings', {defaultRules: mode.id})).rules);
            message.className = 'message ok';
            message.textContent = 'Phòng mới sẽ bắt đầu với luật ' + mode.name + '.';
        } catch (error) {
            message.className = 'message error';
            message.textContent = error.message;
            loadRules().catch(function () { /* the error above is enough */ });
        } finally {
            message.hidden = false;
            document.querySelectorAll('#rules-choice input').forEach(function (input) { input.disabled = false; });
        }
    }

    // --- start -------------------------------------------------------------------------------

    api('GET', 'me').then(showApp, showLogin);
})();
