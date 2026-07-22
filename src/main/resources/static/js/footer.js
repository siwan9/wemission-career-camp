(function () {
    const adminEntry = document.querySelector('[data-admin-entry]');
    const modal = document.getElementById('adminLoginModal');
    const loginForm = modal ? modal.querySelector('[data-admin-login-form]') : null;
    const loginError = modal ? modal.querySelector('[data-admin-login-error]') : null;

    if (!adminEntry || !modal) {
        return;
    }

    let clickCount = 0;
    let resetTimer = null;
    const sessionExpiredMessage = '관리자 로그인 정보가 없거나 세션이 만료되어 요청을 처리하지 못했습니다. 입력하거나 변경한 내용은 저장되지 않았습니다. 다시 로그인한 뒤 작업을 다시 진행해주세요.';

    function openModal() {
        modal.classList.add('is-open');
        modal.setAttribute('aria-hidden', 'false');
        document.body.classList.add('admin-login-modal-open');

        const firstInput = modal.querySelector('input');

        if (firstInput) {
            firstInput.focus();
        }
    }

    function closeModal() {
        modal.classList.remove('is-open');
        modal.setAttribute('aria-hidden', 'true');
        document.body.classList.remove('admin-login-modal-open');
        clickCount = 0;

        if (loginError) {
            loginError.hidden = true;
        }
    }

    async function handleAdminSessionExpiry(response) {
        if (response.status !== 401) {
            return false;
        }

        const result = await response.clone().json().catch(function () {
            return {};
        });

        window.location.assign(result.redirectUrl || '/home?adminLogin=session-expired');
        return true;
    }

    window.handleAdminSessionExpiry = handleAdminSessionExpiry;

    adminEntry.addEventListener('click', function (event) {
        event.preventDefault();
        event.stopPropagation();

        clickCount += 1;

        window.clearTimeout(resetTimer);

        if (clickCount >= 5) {
            openModal();
            return;
        }

        resetTimer = window.setTimeout(function () {
            clickCount = 0;
        }, 1600);
    });

    modal.querySelectorAll('[data-admin-modal-close]').forEach(function (button) {
        button.addEventListener('click', closeModal);
    });

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape' && modal.classList.contains('is-open')) {
            closeModal();
        }
    });

    if (loginForm) {
        loginForm.addEventListener('submit', async function (event) {
            event.preventDefault();

            const submitButton = loginForm.querySelector('button[type="submit"]');

            if (loginError) {
                loginError.hidden = true;
            }

            if (submitButton) {
                submitButton.disabled = true;
                submitButton.textContent = '로그인 중...';
            }

            try {
                const response = await fetch(loginForm.action, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded'
                    },
                    body: new URLSearchParams(new FormData(loginForm))
                });
                const result = await response.json();

                if (!response.ok || !result.success) {
                    throw new Error(result.message || '로그인에 실패했습니다.');
                }

                window.location.href = result.redirectUrl || '/admin/home';
            } catch (error) {
                if (loginError) {
                    loginError.textContent = error.message;
                    loginError.hidden = false;
                }
            } finally {
                if (submitButton) {
                    submitButton.disabled = false;
                    submitButton.textContent = '로그인';
                }
            }
        });
    }

    const currentUrl = new URL(window.location.href);
    if (currentUrl.searchParams.get('adminLogin') === 'session-expired') {
        if (loginError) {
            loginError.textContent = sessionExpiredMessage;
            loginError.hidden = false;
        }

        openModal();
        currentUrl.searchParams.delete('adminLogin');
        window.history.replaceState({}, '', currentUrl.pathname + currentUrl.search + currentUrl.hash);
    }
})();
