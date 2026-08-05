
const state = {
    step: 1,
    participants: [],
    preferences: {
        durationMinutes: 60,
        startDate: '',
        endDate: '',
        minCapacity: 4,
        requiredAmenities: []
    },
    selectedTimeSlot: null,
    selectedRoomId: null,
    selectedRoomName: ''
};

const dbContacts = [
    { id: '1', name: 'Sarah Chen', email: 'sarah.chen@hua.gr', avatar: 'https://i.pravatar.cc/150?img=1' },
    { id: '2', name: 'Mike Johnson', email: 'mike.j@hua.gr', avatar: 'https://i.pravatar.cc/150?img=3' },
    { id: '3', name: 'Emily Davis', email: 'emily.d@hua.gr', avatar: 'https://i.pravatar.cc/150?img=5' },
    { id: '4', name: 'James Wilson', email: 'james.w@hua.gr', avatar: 'https://i.pravatar.cc/150?img=7' },
    { id: '5', name: 'Lisa Anderson', email: 'lisa.a@hua.gr', avatar: 'https://i.pravatar.cc/150?img=9' },
    { id: '6', name: 'Dr. Professor', email: 'prof@hua.gr', avatar: 'https://i.pravatar.cc/150?img=11' }
];

const amenitiesList = ['Video Conference', 'Whiteboard', 'TV Display', 'Phone', 'Projector'];

document.addEventListener('DOMContentLoaded', () => {
    const today = new Date().toISOString().split('T')[0];
    const dateInput = document.getElementById('pref-date');

    if (dateInput) {
        dateInput.value = today;
        state.preferences.startDate = today;
        state.preferences.endDate = today;
    }

    const searchInput = document.getElementById('participant-search');
    if (searchInput) {
        searchInput.addEventListener('input', (e) => {
            handleSearch(e.target.value.toLowerCase());
        });
    } else {
        console.error("Could not find element with id 'participant-search'");
    }

    renderAmenities();
    renderSelected();
    lucide.createIcons();
});

function goToStep(stepNumber) {
    document.querySelectorAll('.wizard-step-container').forEach(el => el.classList.add('hidden'));
    document.getElementById(`step-${stepNumber}-container`).classList.remove('hidden');
    state.step = stepNumber;
    updateProgressBar(stepNumber);
}

function updateProgressBar(activeStep) {
    for(let i=1; i<=4; i++) {
        const circle = document.getElementById(`step-circle-${i}`);
        if(!circle) continue;
        if(i < activeStep) {
            circle.className = "w-10 h-10 rounded-full bg-emerald-500 text-white flex items-center justify-center font-bold shadow-md";
            circle.innerHTML = `<i data-lucide="check" class="w-5 h-5"></i>`;
        } else if (i === activeStep) {
            circle.className = "w-10 h-10 rounded-full bg-blue-600 text-white flex items-center justify-center font-bold shadow-lg scale-110";
            circle.innerHTML = i;
        } else {
            circle.className = "w-10 h-10 rounded-full bg-white border-2 border-slate-300 text-slate-400 flex items-center justify-center font-bold";
            circle.innerHTML = i;
        }
    }
    lucide.createIcons();
}

let currentSearchId = 0;

async function handleSearch(query) {
    const container = document.getElementById('search-results-container');
    const searchTerm = query.trim().replace(/,$/, '').toLowerCase();

    if (searchTerm.length < 2) {
        container.classList.add('hidden');
        return;
    }

    const searchId = ++currentSearchId;

    try {
        const response = await fetch(`/api/users/search?q=${encodeURIComponent(searchTerm)}`);
        const users = await response.json();

        if (searchId !== currentSearchId) return;

        if (users.length === 0) {
            container.innerHTML = `<div class="p-4 text-sm text-slate-500 italic">No real users found in DB for "${searchTerm}"</div>`;
        } else {
            const filtered = users.filter(u => !state.participants.find(p => p.email === u.email));

            container.innerHTML = filtered.map(user => `
                <div onclick="addParticipantFromDB('${user.id}', '${user.fullname}', '${user.email}', '${user.avatarUrl || ''}')"
                     class="flex items-center gap-3 p-3 hover:bg-blue-50 cursor-pointer border-b border-slate-100 last:border-0 transition-all group">
                    <img src="${user.avatarUrl || ''}" onerror="this.src='https://ui-avatars.com/api/?name=${encodeURIComponent(user.fullname)}&background=dbeafe&color=2563eb'" class="w-8 h-8 rounded-full object-cover">
                    <div class="flex-1">
                        <div class="text-sm font-bold text-slate-900 group-hover:text-blue-600">${user.fullname}</div>
                        <div class="text-[10px] text-slate-500">${user.email}</div>
                    </div>
                    <i data-lucide="plus-circle" class="w-4 h-4 text-slate-300 group-hover:text-blue-500"></i>
                </div>
            `).join('');
            lucide.createIcons();
        }
        container.classList.remove('hidden');
    } catch (error) {
        console.error("Database search failed:", error);
    }
}

function addParticipantFromDB(id, name, email, avatar) {
    state.participants.push({ id, name, email, avatar, required: true });
    document.getElementById('participant-search').value = '';
    document.getElementById('search-results-container').classList.add('hidden');
    renderSelected();
}

function addParticipant(id) {
    const person = dbContacts.find(c => c.id === id);
    if (person) {
        state.participants.push({ ...person, required: true });
        document.getElementById('participant-search').value = '';
        document.getElementById('search-results-container').classList.add('hidden');
        renderSelected();
    }
}

function removeParticipant(id) {
    state.participants = state.participants.filter(p => p.id !== id);
    renderSelected();
}

function toggleRequired(id) {
    const p = state.participants.find(p => p.id === id);
    if (p) p.required = !p.required;
}

function renderAmenities() {
    const container = document.getElementById('amenities-container');
    container.innerHTML = amenitiesList.map(a => `
        <label class="flex items-center gap-3 cursor-pointer">
            <input type="checkbox" onchange="toggleAmenity('${a}')" class="w-4 h-4 text-blue-600 rounded focus:ring-blue-500">
            <span class="text-sm font-medium text-slate-700">${a}</span>
        </label>
    `).join('');
}

function toggleAmenity(amenity) {
    const idx = state.preferences.requiredAmenities.indexOf(amenity);
    if (idx === -1) state.preferences.requiredAmenities.push(amenity);
    else state.preferences.requiredAmenities.splice(idx, 1);
}

function renderSelected() {
    const list = document.getElementById('selected-list');
    const btn = document.getElementById('find-times-btn');
    document.getElementById('selected-count-label').innerText = `Selected Participants (${state.participants.length})`;
    btn.disabled = state.participants.length === 0;

    if (state.participants.length === 0) {
        list.innerHTML = `<div class="text-center py-8 text-slate-400 text-sm italic border-2 border-dashed border-slate-200 rounded-xl">Search to add participants</div>`;
        return;
    }

    list.innerHTML = state.participants.map(p => `
        <div class="flex items-center gap-3 p-3 bg-white border border-slate-200 rounded-xl shadow-sm">
            <img src="${p.avatar || ''}" onerror="this.src='https://ui-avatars.com/api/?name=${encodeURIComponent(p.name)}&background=dbeafe&color=2563eb'" class="w-10 h-10 rounded-full object-cover">
            <div class="flex-1 overflow-hidden">
                <div class="text-sm font-bold text-slate-900 truncate">${p.name}</div>
                <div class="text-xs text-slate-500 truncate">${p.email}</div>
            </div>
            <label class="flex items-center gap-2 cursor-pointer bg-slate-50 px-2 py-1 rounded-lg border">
                <input type="checkbox" ${p.required ? 'checked' : ''} onchange="toggleRequired('${p.id}')" class="w-4 h-4 text-blue-600">
                <span class="text-xs font-bold text-slate-600">Required</span>
            </label>
            <button onclick="removeParticipant('${p.id}')" class="p-2 hover:text-red-500"><i data-lucide="x" class="w-4 h-4"></i></button>
        </div>
    `).join('');
    lucide.createIcons();
}

async function handleFindBestTimes() {
    const req = state.participants.filter(p => p.required).map(p => p.email);
    const opt = state.participants.filter(p => !p.required).map(p => p.email);

    state.preferences.durationMinutes = parseInt(document.getElementById('pref-duration').value);

    state.preferences.startDate = document.getElementById('pref-date-start').value;
    state.preferences.endDate = document.getElementById('pref-date-end').value;

    if (!state.preferences.startDate || !state.preferences.endDate) {
        alert("Please select both 'From' and 'To' dates.");
        return;
    }
    if (state.preferences.endDate < state.preferences.startDate) {
        alert("The 'To Date' cannot be earlier than the 'From Date'.");
        return;
    }

    const payload = {
        requiredParticipants: req,
        optionalParticipants: opt,
        durationMinutes: state.preferences.durationMinutes,
        dateRangeStart: state.preferences.startDate,
        dateRangeEnd: state.preferences.endDate
    };

    const btn = document.getElementById('find-times-btn');
    btn.innerHTML = `<i data-lucide="loader-2" class="w-4 h-4 animate-spin"></i> Analyzing...`;

    try {
        const response = await fetch('/api/bookings/suggest-times', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const data = await response.json();

        if (response.status === 404 && data.status === 'requires_invite') {
            document.getElementById('invite-email-display').innerText = data.missingEmail;
            document.getElementById('inviteModal').classList.remove('hidden');
            return;
        }

        if (!Array.isArray(data)) {
            console.error("Backend did not return an array. Received:", data);
            throw new Error(data.message || data.error || "Unexpected response format from server");
        }

        document.getElementById('summary-synced-count').innerText = state.participants.length + 1;
        document.getElementById('summary-options-count').innerText = data.length;
        document.getElementById('summary-duration').innerText = state.preferences.durationMinutes + 'm';

        renderTrafficLightTimeSlots(data);
        goToStep(2);

    } catch (error) {
        alert("Search Error: " + error.message);
        console.error(error);
    } finally {
        btn.innerHTML = `Find Best Times <i data-lucide="arrow-right" class="w-4 h-4"></i>`;
        lucide.createIcons();
    }
}

function renderTrafficLightTimeSlots(slots) {
    const container = document.getElementById('recommended-times-container');
    container.innerHTML = slots.map((slot, index) => {
        const start = new Date(slot.startTime);
        const end = new Date(slot.endTime);
        const colorClass = index === 0 ? "border-emerald-500" : "border-yellow-400";
        return `
            <div onclick="selectTimeSlot('${slot.startTime}', '${slot.endTime}')" class="cursor-pointer bg-white border-2 ${colorClass} rounded-2xl p-5 mb-4 hover:shadow-lg transition-all">
                <div class="flex justify-between items-center">
                    <div>
                        <h3 class="text-lg font-bold">${start.toLocaleDateString()}</h3>
                        <p>${start.toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'})} - ${end.toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'})}</p>
                    </div>
                    <div class="text-right">
                        <span class="text-xs font-bold uppercase">Score</span>
                        <p class="text-xl font-black">${slot.score}</p>
                    </div>
                </div>
            </div>`;
    }).join('');
}

function selectTimeSlot(start, end) {
    state.selectedTimeSlot = { start, end };
    fetchAvailableRooms();
}

async function fetchAvailableRooms() {
    const payload = {
        startTime: state.selectedTimeSlot.start, endTime: state.selectedTimeSlot.end,
        minCapacity: state.preferences.minCapacity, requiredAmenities: state.preferences.requiredAmenities
    };
    try {
        const res = await fetch('/api/bookings/suggest-rooms', {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
        });
        const rooms = await res.json();
        renderRoomCards(rooms);
        const dateObj = new Date(state.selectedTimeSlot.start);
        document.getElementById('step3-time-text').innerText = dateObj.toLocaleString();
        goToStep(3);
    } catch (e) { alert("Error: " + e.message); }
}

function renderRoomCards(rooms) {
    const container = document.getElementById('recommended-rooms-container');
    if (rooms.length === 0) {
        container.innerHTML = `<div class="p-6 text-red-500 font-bold border-2 border-red-200 rounded-2xl">No rooms available</div>`;
        document.getElementById('btn-to-confirm').classList.add('hidden');
        return;
    }
    container.innerHTML = rooms.map(room => {
        const missingWarning = room.missingAmenities && room.missingAmenities.length > 0
            ? `<div class="mt-2 flex items-center gap-2 text-amber-600 text-xs font-bold bg-amber-50 px-3 py-1.5 rounded-lg border border-amber-200">
                 <i data-lucide="alert-triangle" class="w-3.5 h-3.5"></i>
                 Missing: ${room.missingAmenities.join(', ')}
               </div>`
            : `<div class="mt-2 flex items-center gap-2 text-emerald-600 text-xs font-bold">
                 <i data-lucide="check-circle" class="w-3.5 h-3.5"></i>
                 All requirements met
               </div>`;

        return `
        <div onclick="selectRoom(${room.id}, '${room.name}')" class="cursor-pointer bg-white border-2 border-slate-200 hover:border-blue-500 rounded-2xl p-5 mb-4">
            <h3 class="text-lg font-bold">${room.name}</h3>
            <p class="text-sm">Capacity: ${room.capacity} | Floor: ${room.floor}</p>
            ${missingWarning}
        </div>`;
    }).join('');
    lucide.createIcons();
}

function selectRoom(id, name) {
    state.selectedRoomId = id;
    state.selectedRoomName = name;
    document.getElementById('btn-to-confirm').classList.remove('hidden');
}

function renderFinalReview() {
    const start = new Date(state.selectedTimeSlot.start);
    document.getElementById('review-datetime').innerText = start.toLocaleString();
    document.getElementById('review-room').innerText = state.selectedRoomName;
    document.getElementById('final-title-input').value = `Meeting with ${state.participants.length} participants`;
}

async function submitFinalBooking() {
    const payload = {
        roomId: state.selectedRoomId, title: document.getElementById('final-title-input').value,
        startTime: state.selectedTimeSlot.start, endTime: state.selectedTimeSlot.end,
        participants: state.participants.map(p => p.email)
    };
    const btn = document.getElementById('final-confirm-btn');
    btn.innerHTML = `<i data-lucide="loader-2" class="w-5 h-5 animate-spin"></i> Booking...`;
    try {
        const res = await fetch('/api/bookings/confirm', {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (res.status === 409) { alert("Conflict: " + data.error); goToStep(3); return; }
        alert("Booking Confirmed!");
        window.location.href = "/";
    } catch (e) { alert("Error: " + e.message); }
    finally { btn.innerHTML = `Confirm Booking`; lucide.createIcons(); }
}