const form = document.getElementById('registrationForm');
const phoneNumberInput = document.getElementById('phoneNumber');
const passwordInput = document.getElementById('password');
const confirmAdditionalInput = document.getElementById('confirmAdditionalInput');
const confirmAdditionalButton = document.getElementById('confirmAdditionalButton');
const passwordToggleButton = document.getElementById('passwordToggleButton');
const duplicateApplicationModal = document.getElementById('duplicateApplicationModal');
const submitButton = document.getElementById('submitButton');
const churchCombobox = document.querySelector('[data-church-combobox]');
const participantTypeSelect = document.getElementById('participantTypeId');
const churchSearchInput = document.getElementById('churchSearchInput');
const churchIdInput = document.getElementById('churchId');
const churchOptions = document.getElementById('churchOptions');
const churchOptionButtons = churchOptions
    ? Array.from(churchOptions.querySelectorAll('.church-option'))
    : [];
const churchEmpty = churchOptions
    ? churchOptions.querySelector('.church-empty')
    : null;
const storageKey = 'careerCampRegistrationForm';
const isEditMode = form && form.dataset.editMode === 'true';

const navigationEntry = performance.getEntriesByType('navigation')[0];
const isFreshRegisterEntry =
    window.location.pathname === '/register'
    && (!navigationEntry || navigationEntry.type === 'navigate');

if (isFreshRegisterEntry) {
    sessionStorage.removeItem(storageKey);
}

function getSavedFormState() {
    if (isEditMode) {
        return null;
    }

    const savedState = sessionStorage.getItem(storageKey);

    if (!savedState) {
        return null;
    }

    return JSON.parse(savedState);
}

function saveFormState() {
    if (!form || isEditMode) {
        return;
    }

    const formData = new FormData(form);
    sessionStorage.setItem(
        storageKey,
        JSON.stringify({
            name: formData.get('name') || '',
            participantTypeId: formData.get('participantTypeId') || '',
            churchId: formData.get('churchId') || '',
            phoneNumber: formData.get('phoneNumber') || '',
            password: formData.get('password') || ''
        })
    );
}

function restoreFormState() {
    const values = getSavedFormState();

    if (!values) {
        return;
    }

    Object.entries(values).forEach(function ([name, value]) {
        const field = form.querySelector(`[name="${name}"]`);

        if (field && !field.value) {
            field.value = value;
        }
    });

    syncChurchSearchInput();
    syncParticipantTypePlaceholder();
}

function syncParticipantTypePlaceholder() {
    if (!participantTypeSelect) {
        return;
    }

    if (!participantTypeSelect.value) {
        participantTypeSelect.selectedIndex = 0;
    }
}

function openChurchOptions() {
    if (!churchOptions || !churchSearchInput) {
        return;
    }

    churchOptions.hidden = false;
    churchSearchInput.setAttribute('aria-expanded', 'true');
}

function closeChurchOptions() {
    if (!churchOptions || !churchSearchInput) {
        return;
    }

    churchOptions.hidden = true;
    churchSearchInput.setAttribute('aria-expanded', 'false');
}

function filterChurchOptions() {
    if (!churchSearchInput || !churchOptions) {
        return;
    }

    const keyword = churchSearchInput.value.trim().toLowerCase();
    let visibleCount = 0;

    churchOptionButtons.forEach(function (option) {
        const name = option.dataset.churchName || '';
        const visible = !keyword || name.toLowerCase().includes(keyword);

        option.hidden = !visible;

        if (visible) {
            visibleCount += 1;
        }
    });

    if (churchEmpty) {
        churchEmpty.hidden = visibleCount > 0;
    }
}

function selectChurch(option) {
    if (!churchSearchInput || !churchIdInput) {
        return;
    }

    churchSearchInput.value = option.dataset.churchName || '';
    churchIdInput.value = option.dataset.churchId || '';
    closeChurchOptions();
    saveFormState();
}

function syncChurchSearchInput() {
    if (!churchSearchInput || !churchIdInput || !churchIdInput.value) {
        return;
    }

    const selectedOption = churchOptionButtons.find(function (option) {
        return option.dataset.churchId === churchIdInput.value;
    });

    if (selectedOption) {
        churchSearchInput.value = selectedOption.dataset.churchName || '';
    }
}

if (submitButton) {
    submitButton.dataset.defaultHtml = submitButton.innerHTML;
}

window.addEventListener('pageshow', function () {
    if (isEditMode && passwordInput && !passwordInput.value && passwordInput.dataset.existingPassword) {
        passwordInput.value = passwordInput.dataset.existingPassword;
    }

    restoreFormState();
    syncChurchSearchInput();
    syncParticipantTypePlaceholder();

    if (confirmAdditionalInput) {
        confirmAdditionalInput.value = 'false';
    }

    if (!submitButton) {
        return;
    }

    submitButton.disabled = false;
    submitButton.innerHTML = submitButton.dataset.defaultHtml;
});

if (
    duplicateApplicationModal
    && duplicateApplicationModal.dataset.show === 'true'
    && window.bootstrap
) {
    new bootstrap.Modal(duplicateApplicationModal).show();
}

if (phoneNumberInput) {
    phoneNumberInput.addEventListener('input', function () {
        this.value = this.value.replace(/\D/g, '').slice(0, 11);
        saveFormState();
    });
}

if (passwordInput) {
    passwordInput.addEventListener('input', function () {
        this.value = this.value.replace(/\D/g, '').slice(0, 6);
        saveFormState();
    });
}

form
    .querySelectorAll('input, select')
    .forEach(function (field) {
        field.addEventListener('change', saveFormState);
    });

if (churchSearchInput && churchIdInput && churchOptions) {
    syncChurchSearchInput();

    churchSearchInput.addEventListener('focus', function () {
        filterChurchOptions();
        openChurchOptions();
    });

    churchSearchInput.addEventListener('input', function () {
        churchIdInput.value = '';
        filterChurchOptions();
        openChurchOptions();
        saveFormState();
    });

    churchOptionButtons.forEach(function (option) {
        option.addEventListener('click', function () {
            selectChurch(option);
        });
    });

    document.addEventListener('click', function (event) {
        if (churchCombobox && !churchCombobox.contains(event.target)) {
            closeChurchOptions();
        }
    });

    churchSearchInput.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
            closeChurchOptions();
        }
    });
}

syncParticipantTypePlaceholder();

if (passwordToggleButton && passwordInput) {
    passwordToggleButton.addEventListener('click', function () {
        const shouldShow = passwordInput.type === 'password';

        passwordInput.type = shouldShow ? 'text' : 'password';
        this.classList.toggle('is-visible', shouldShow);
        this.setAttribute('aria-label', shouldShow ? 'PIN 숨기기' : 'PIN 보기');
        this.setAttribute('aria-pressed', String(shouldShow));
    });
}

if (confirmAdditionalButton) {
    confirmAdditionalButton.addEventListener('click', function () {
        const values = getSavedFormState();

        if (passwordInput && !passwordInput.value && values && values.password) {
            passwordInput.value = values.password;
        }

        confirmAdditionalInput.value = 'true';
        saveFormState();
        form.requestSubmit();
    });
}

form.addEventListener('submit', function (e) {
    restoreFormState();

    if (phoneNumberInput && !/^\d{10,11}$/.test(phoneNumberInput.value)) {
        e.preventDefault();

        alert('전화번호는 10~11자리 숫자로 입력해주세요.');

        phoneNumberInput.focus();

        return;
    }

    if (passwordInput && !/^\d{6}$/.test(passwordInput.value)) {
        e.preventDefault();

        alert('조회용 비밀번호는 숫자 6자리로 입력해주세요.');

        passwordInput.focus();

        return;
    }

    if (churchSearchInput && churchIdInput && !churchIdInput.value) {
        e.preventDefault();

        alert('교회명을 검색한 뒤 목록에서 선택해주세요.');

        churchSearchInput.focus();
        filterChurchOptions();
        openChurchOptions();

        return;
    }

    saveFormState();

    const values = Array.from(
        document.querySelectorAll('.lecture-select')
    ).map(select => select.value);

    if (new Set(values).size !== values.length) {

        e.preventDefault();

        alert('동일한 특강은 중복 선택할 수 없습니다.');

        return;
    }

    const button =
        e.submitter || submitButton;

    if (!button) {
        return;
    }

    if (button.id === 'submitButton') {
        button.disabled = true;
        button.innerHTML =
            '<span class="spinner-border spinner-border-sm me-2"></span>신청 중...';
    }

});

window.addEventListener('pagehide', saveFormState);
