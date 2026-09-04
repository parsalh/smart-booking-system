lucide.createIcons();

let pendingRoleChange = null;

function handleRoleChange(selectEl) {
    const userId = selectEl.dataset.userId;
    const newRole = selectEl.value;
    const previousRole = selectEl.dataset.previousRole || selectEl.querySelector('option[selected]')?.value;

    pendingRoleChange = { selectEl, userId, newRole, previousRole };

    document.getElementById('confirmNewRoleName').innerText = newRole;

    const modal = document.getElementById('roleConfirmModal');
    modal.classList.remove('hidden');

    setTimeout(() => {
        modal.classList.remove('opacity-0');
        modal.children[0].classList.remove('scale-95');
    }, 10);
}

function cancelRoleChange() {
    if (pendingRoleChange) {
        pendingRoleChange.selectEl.value = pendingRoleChange.previousRole;
        pendingRoleChange = null;
    }
    closeRoleModal();
}

function closeRoleModal() {
    const modal = document.getElementById('roleConfirmModal');
    modal.classList.add('opacity-0');
    modal.children[0].classList.add('scale-95');

    setTimeout(() => {
        modal.classList.add('hidden');
    }, 300);
}

function executeRoleChange() {
    if (!pendingRoleChange) return;

    const { selectEl, userId, newRole, previousRole } = pendingRoleChange;
    closeRoleModal();

    selectEl.disabled = true;

    const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

    fetch(`/admin/users/${userId}/role`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            [csrfHeader]: csrfToken
        },
        body: JSON.stringify({ role: newRole })
    })
        .then(async (res) => {
            if (!res.ok) {
                const data = await res.json().catch(() => ({}));
                throw new Error(data.error || 'Failed to update role');
            }
            return res.json();
        })
        .then((data) => {
            const badge = document.getElementById(`role-badge-${userId}`);
            if (badge) {
                badge.innerText = data.role;
                badge.className = 'text-[10px] font-bold px-2.5 py-1 rounded-lg border uppercase tracking-wider ' +
                    (data.role === 'ADMIN' ? 'bg-purple-50 text-purple-700 border-purple-200'
                        : data.role === 'PROFESSOR' ? 'bg-blue-50 text-blue-700 border-blue-200'
                            : 'bg-slate-100 text-slate-600 border-slate-200');
            }
            selectEl.dataset.previousRole = newRole;
        })
        .catch((err) => {
            alert(err.message || 'Failed to update role. Please try again.');
            if (previousRole) selectEl.value = previousRole;
            console.error(err);
        })
        .finally(() => {
            selectEl.disabled = false;
            pendingRoleChange = null;
        });
}