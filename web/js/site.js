(function () {
    'use strict';

    // Same rules as LanAccounts.java: the in-game fields only take letters and digits.
    var NAME_RULE = /^[A-Za-z0-9]{3,20}$/;
    var PASSWORD_RULE = /^[A-Za-z0-9]{6,30}$/;

    var form = document.getElementById('register-form');
    var username = document.getElementById('username');
    var password = document.getElementById('password');
    var confirm = document.getElementById('confirm');
    var showPassword = document.getElementById('show-password');
    var errorBox = document.getElementById('form-error');
    var submit = document.getElementById('submit-btn');
    var success = document.getElementById('success');

    function fill(selector, text) {
        document.querySelectorAll(selector).forEach(function (element) {
            element.textContent = text;
        });
    }

    // The game server runs on the same machine as this page, so the address typed into the browser works in game too.
    var host = location.hostname || '127.0.0.1';
    fill('[data-host]', host);
    if (/^(localhost|127\.|\[?::1\]?$)/.test(host)) {
        document.getElementById('local-note').hidden = false;
    }

    fetch('api/info').then(function (response) {
        return response.ok ? response.json() : null;
    }).then(function (info) {
        if (info && info.gamePort) fill('[data-game-port]', String(info.gamePort));
    }).catch(function () {});

    fetch('downloads/MobiArmy2-PC.zip', {method: 'HEAD'}).then(function (response) {
        if (!response.ok) return;
        document.getElementById('downloads').hidden = false;
        var bytes = Number(response.headers.get('Content-Length'));
        if (bytes > 0) {
            document.getElementById('download-size').textContent =
                'Bản cho Windows · ' + (bytes / 1048576).toFixed(1).replace('.', ',') + ' MB';
        }
    }).catch(function () {});

    function problemWith(name, pass, again) {
        if (!name) return [username, 'Bạn chưa nhập tên đăng nhập.'];
        if (!NAME_RULE.test(name)) return [username, 'Tên đăng nhập gồm 3-20 chữ cái không dấu hoặc chữ số (không dấu cách, không ký tự đặc biệt).'];
        if (!pass) return [password, 'Bạn chưa nhập mật khẩu.'];
        if (!PASSWORD_RULE.test(pass)) return [password, 'Mật khẩu gồm 6-30 chữ cái không dấu hoặc chữ số.'];
        if (pass !== again) return [confirm, 'Hai lần nhập mật khẩu chưa khớp nhau.'];
        return null;
    }

    function showError(message, field) {
        errorBox.textContent = message;
        errorBox.hidden = false;
        if (field) {
            field.setAttribute('aria-invalid', 'true');
            field.focus();
        }
    }

    function clearError() {
        errorBox.hidden = true;
        errorBox.textContent = '';
        [username, password, confirm].forEach(function (field) {
            field.removeAttribute('aria-invalid');
        });
    }

    [username, password, confirm].forEach(function (field) {
        field.addEventListener('input', function () {
            if (field.getAttribute('aria-invalid')) clearError();
        });
    });

    showPassword.addEventListener('change', function () {
        var type = showPassword.checked ? 'text' : 'password';
        password.type = type;
        confirm.type = type;
    });

    form.addEventListener('submit', function (event) {
        event.preventDefault();
        clearError();
        var name = username.value.trim();
        var problem = problemWith(name, password.value, confirm.value);
        if (problem) {
            showError(problem[1], problem[0]);
            return;
        }
        submit.disabled = true;
        var body = new URLSearchParams({username: name, password: password.value});
        fetch('api/register', {method: 'POST', body: body}).then(function (response) {
            return response.json().catch(function () {
                return {};
            }).then(function (data) {
                if (response.status === 201) {
                    form.hidden = true;
                    form.reset();
                    password.type = confirm.type = 'password';
                    document.getElementById('success-name').textContent = data.username || name.toLowerCase();
                    success.hidden = false;
                    document.getElementById('success-title').focus();
                } else {
                    var field = response.status === 409 ? username : null;
                    showError(data.error || 'Không tạo được tài khoản (mã lỗi ' + response.status + ').', field);
                }
            });
        }).catch(function () {
            showError('Không kết nối được tới máy chủ. Kiểm tra mạng LAN rồi thử lại nhé.');
        }).then(function () {
            submit.disabled = false;
        });
    });

    document.getElementById('again').addEventListener('click', function () {
        success.hidden = true;
        form.hidden = false;
        username.focus();
    });
})();
