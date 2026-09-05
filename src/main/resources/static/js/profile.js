lucide.createIcons();

const oooForm = document.getElementById('oooForm');
const initialStart = oooForm.dataset.initialStart || null;
const initialEnd = oooForm.dataset.initialEnd || null;

const startInput = document.getElementById('ooo-start');
const endInput = document.getElementById('ooo-end');

if (initialStart) {
    startInput.value = formatDateDMY(initialStart);
    startInput.dataset.iso = initialStart;
}
if (initialEnd) {
    endInput.value = formatDateDMY(initialEnd);
    endInput.dataset.iso = initialEnd;
}

attachDatePicker('ooo-start');
attachDatePicker('ooo-end');

function todayIso() {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function updateBanner() {
    const start = startInput.dataset.iso;
    const end = endInput.dataset.iso;
    const banner = document.getElementById('oooActiveBanner');
    const text = document.getElementById('oooActiveText');

    if (!start || !end) {
        banner.classList.add('hidden');
        return;
    }

    banner.classList.remove('hidden');

    const today = todayIso();
    if (today < start) {
        text.innerText = `Scheduled: out of office from ${formatDateDMY(start)} to ${formatDateDMY(end)}`;
    } else if (today > end) {
        text.innerText = `Out of office ended ${formatDateDMY(end)}`;
    } else {
        text.innerText = `Currently out of office until ${formatDateDMY(end)}`;
    }
}

if (initialStart && initialEnd) {
    updateBanner();
}

async function saveOutOfOffice(event) {
    event.preventDefault();

    const startDate = startInput.dataset.iso || '';
    const endDate = endInput.dataset.iso || '';
    const errorMsg = document.getElementById('oooError');
    const savedMsg = document.getElementById('oooSavedMsg');
    const submitBtn = event.target.querySelector('button[type="submit"]');

    errorMsg.classList.add('hidden');
    savedMsg.classList.add('hidden');

    if (!startDate || !endDate) {
        errorMsg.innerText = 'Please select both start and end dates.';
        errorMsg.classList.remove('hidden');
        return;
    }

    if (submitBtn) submitBtn.disabled = true;

    const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

    fetch('/api/profile/out-of-office', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            [csrfHeader]: csrfToken
        },
        body: JSON.stringify({ startDate: startDate, endDate: endDate })
    })
        .then(async (res) => {
            if (!res.ok) {
                const data = await res.json().catch(() => ({}));
                throw new Error(data.error || 'Failed to save out of office');
            }

            savedMsg.classList.remove('hidden');
            if (typeof lucide !== 'undefined') lucide.createIcons();
            setTimeout(() => savedMsg.classList.add('hidden'), 3000);

            updateBanner();
        })
        .catch((err) => {
            console.error(err);
            errorMsg.innerText = err.message === 'Failed to fetch' ? 'Network error.' : err.message;
            errorMsg.classList.remove('hidden');
        })
        .finally(() => {
            if (submitBtn) submitBtn.disabled = false;
        });
}


function clearOutOfOffice() {
    const modal = document.getElementById('oooConfirmModal');
    modal.classList.remove('hidden');

    setTimeout(() => {
        modal.classList.remove('opacity-0');
        modal.children[0].classList.remove('scale-95');
    }, 10);
}

function cancelOooClear() {
    closeOooModal();
}

function closeOooModal() {
    const modal = document.getElementById('oooConfirmModal');
    modal.classList.add('opacity-0');
    modal.children[0].classList.add('scale-95');

    setTimeout(() => {
        modal.classList.add('hidden');
    }, 300);
}

function executeOooClear() {
    closeOooModal();

    const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

    fetch('/api/profile/out-of-office', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            [csrfHeader]: csrfToken
        },
        body: JSON.stringify({ startDate: '', endDate: '' })
    })
        .then(res => {
            if (!res.ok) throw new Error('Failed to clear out-of-office status');

            document.getElementById('ooo-start').value = '';
            document.getElementById('ooo-end').value = '';
            delete startInput.dataset.iso;
            delete endInput.dataset.iso;
            document.getElementById('oooActiveBanner').classList.add('hidden');

            const savedMsg = document.getElementById('oooSavedMsg');
            if (savedMsg) {
                savedMsg.classList.remove('hidden');
                if (typeof lucide !== 'undefined') lucide.createIcons();
                setTimeout(() => savedMsg.classList.add('hidden'), 3000);
            }
        })
        .catch(err => {
            const errorMsg = document.getElementById('oooError');
            if (errorMsg) {
                errorMsg.innerText = 'Could not remove out of office. Please try again.';
                errorMsg.classList.remove('hidden');
            }
            console.error(err);
        });
}

function saveTitle(event) {
    event.preventDefault();

    const select = document.getElementById('userTitle');
    const savedMsg = document.getElementById('titleSavedMsg');
    const errorMsg = document.getElementById('titleErrorMsg');
    const submitBtn = event.target.querySelector('button[type="submit"]');

    savedMsg.classList.add('hidden');
    errorMsg.classList.add('hidden');
    if (submitBtn) submitBtn.disabled = true;

    const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

    fetch('/api/profile/title', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            [csrfHeader]: csrfToken
        },
        body: JSON.stringify({ title: select.value })
    })
        .then(function (res) {
            if (!res.ok) throw new Error('Request failed');
            savedMsg.classList.remove('hidden');
            if (typeof lucide !== 'undefined') lucide.createIcons();
            setTimeout(function () { savedMsg.classList.add('hidden'); }, 3000);
        })
        .catch(function (err) {
            console.error('Failed to save title:', err);
            errorMsg.innerText = 'Could not save your title. Please try again.';
            errorMsg.classList.remove('hidden');
        })
        .finally(function () {
            if (submitBtn) submitBtn.disabled = false;
        });
}