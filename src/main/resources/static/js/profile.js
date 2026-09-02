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

function updateBanner() {
    const start = startInput.dataset.iso;
    const end = endInput.dataset.iso;
    const banner = document.getElementById('oooActiveBanner');
    const text = document.getElementById('oooActiveText');

    if (start && end) {
        banner.classList.remove('hidden');
        text.innerText = `Out of office from ${formatDateDMY(start)} to ${formatDateDMY(end)}`;
    } else {
        banner.classList.add('hidden');
    }
}

if (initialStart && initialEnd) {
    updateBanner();
}

async function saveOutOfOffice(e) {
    e.preventDefault();

    const startDate = startInput.dataset.iso || '';
    const endDate = endInput.dataset.iso || '';
    const errorEl = document.getElementById('oooError');
    const savedMsg = document.getElementById('oooSavedMsg');

    errorEl.classList.add('hidden');
    savedMsg.classList.add('hidden');

    if ((startDate && !endDate) || (!startDate && endDate)) {
        errorEl.innerText = 'Please provide both a start and end date, or leave both empty.';
        errorEl.classList.remove('hidden');
        return;
    }

    try {
        const res = await fetch('/api/profile/out-of-office', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ startDate, endDate })
        });

        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            errorEl.innerText = data.error || 'Failed to save. Please try again.';
            errorEl.classList.remove('hidden');
            return;
        }

        updateBanner();
        savedMsg.classList.remove('hidden');
        setTimeout(() => savedMsg.classList.add('hidden'), 3000);
    } catch (err) {
        errorEl.innerText = 'Something went wrong. Please try again.';
        errorEl.classList.remove('hidden');
        console.error(err);
    }
}

async function clearOutOfOffice() {
    const confirmed = confirm('Remove your out-of-office period? SmartBooking will treat you as available again for future meeting suggestions.');
    if (!confirmed) return;

    startInput.value = '';
    delete startInput.dataset.iso;
    endInput.value = '';
    delete endInput.dataset.iso;

    await fetch('/api/profile/out-of-office', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ startDate: '', endDate: '' })
    });

    updateBanner();
}