function updateImagePreview(url) {
    const preview = document.getElementById('imagePreview');
    const placeholder = document.getElementById('imagePlaceholder');

    if (!preview || !placeholder) return;

    if (url && url.trim() !== '') {
        preview.src = url.trim();
        preview.classList.remove('hidden');
        placeholder.classList.add('hidden');
    } else {
        preview.src = '';
        preview.classList.add('hidden');
        placeholder.classList.remove('hidden');
    }
}

function openAddModal() {
    const modalTitle = document.getElementById('modalTitle');
    const form = document.getElementById('roomForm');

    if (modalTitle) modalTitle.innerText = "Create New Room";
    if (form) {
        form.action = "/admin/rooms/add";
        form.reset();
    }

    document.getElementById('roomId').value = "";
    document.getElementById('roomName').value = "";
    document.getElementById('roomBuilding').value = "";
    document.getElementById('roomLocation').value = "";
    document.getElementById('roomFloor').value = "";
    document.getElementById('roomCapacity').value = "";
    document.getElementById('roomImage').value = "";

    document.querySelectorAll('input[name="amenities"]').forEach(cb => cb.checked = false);

    updateImagePreview("");
    document.getElementById('roomModal').classList.remove('hidden');
}

function openEditModal(id, name, building, location, floor, capacity, imageUrl, amenitiesStr) {
    const modalTitle = document.getElementById('modalTitle');
    const form = document.getElementById('roomForm');

    if (modalTitle) modalTitle.innerText = "Edit Room";
    if (form) form.action = "/admin/rooms/update";

    document.getElementById('roomId').value = id || '';
    document.getElementById('roomName').value = name || '';
    document.getElementById('roomBuilding').value = building || '';
    document.getElementById('roomLocation').value = location || '';
    document.getElementById('roomFloor').value = floor || '';
    document.getElementById('roomCapacity').value = capacity || '';
    document.getElementById('roomImage').value = imageUrl || '';

    document.querySelectorAll('input[name="amenities"]').forEach(cb => cb.checked = false);
    if (amenitiesStr) {
        const roomAmenities = amenitiesStr.split(',');
        roomAmenities.forEach(amenity => {
            const checkbox = document.querySelector(`input[name="amenities"][value="${amenity.trim()}"]`);
            if (checkbox) checkbox.checked = true;
        });
    }

    updateImagePreview(imageUrl || '');
    document.getElementById('roomModal').classList.remove('hidden');
}

function closeModal() {
    const modal = document.getElementById('roomModal');
    if (modal) modal.classList.add('hidden');
}

function openDeleteModal(roomId, roomName) {
    window.roomToDeleteId = roomId;
    const nameEl = document.getElementById('deleteRoomName');
    if (nameEl) nameEl.innerText = roomName;

    const deleteModal = document.getElementById('deleteModal');
    if (deleteModal) {
        deleteModal.classList.remove('hidden');
        setTimeout(() => {
            deleteModal.classList.remove('opacity-0');
            deleteModal.querySelector('div').classList.remove('scale-95');
        }, 10);
    }
}

function closeDeleteModal() {
    const deleteModal = document.getElementById('deleteModal');
    if (deleteModal) {
        deleteModal.classList.add('opacity-0');
        deleteModal.querySelector('div').classList.add('scale-95');

        setTimeout(() => {
            deleteModal.classList.add('hidden');
            window.roomToDeleteId = null;
        }, 300);
    }
}

function confirmDelete() {
    if (window.roomToDeleteId) {
        const deleteForm = document.getElementById('delete-form-' + window.roomToDeleteId);
        if (deleteForm) {
            const deleteModal = document.getElementById('deleteModal');
            const btn = deleteModal.querySelector('button.bg-red-600');
            if (btn) {
                btn.innerHTML = `<i data-lucide="loader-2" class="w-4 h-4 animate-spin"></i> Deleting...`;
                lucide.createIcons();
            }
            deleteForm.submit();
        }
    }
}

document.addEventListener('DOMContentLoaded', () => {
    lucide.createIcons();

    const roomImageInput = document.getElementById('roomImage');
    const imagePreview = document.getElementById('imagePreview');
    const imagePlaceholder = document.getElementById('imagePlaceholder');

    if (roomImageInput) {
        ['input', 'change', 'keyup', 'paste'].forEach(eventType => {
            roomImageInput.addEventListener(eventType, () => {
                setTimeout(() => {
                    updateImagePreview(roomImageInput.value);
                }, 10);
            });
        });
    }

    if (imagePreview) {
        imagePreview.onerror = function() {
            imagePreview.classList.add('hidden');
            if (imagePlaceholder) imagePlaceholder.classList.remove('hidden');
        };
    }

    const addBtn = document.getElementById('addRoomBtn');
    if (addBtn) {
        addBtn.addEventListener('click', () => {
            openAddModal();
        });
    }

    document.querySelectorAll('.edit-room-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const id = btn.dataset.id || '';
            const name = btn.dataset.name || '';
            const building = btn.dataset.building || '';
            const location = btn.dataset.location || '';
            const floor = btn.dataset.floor || '';
            const capacity = btn.dataset.capacity || '';
            const imageUrl = btn.dataset.image || '';
            const amenities = btn.dataset.amenities || '';

            openEditModal(id, name, building, location, floor, capacity, imageUrl, amenities);
        });
    });

    document.querySelectorAll('.delete-room-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const id = btn.dataset.id || '';
            const name = btn.dataset.name || '';
            openDeleteModal(id, name);
        });
    });
});