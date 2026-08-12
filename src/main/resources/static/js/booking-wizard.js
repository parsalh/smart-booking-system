
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
                    <img src="${user.avatarUrl || ''}" referrerpolicy="no-referrer" onerror="this.src='https://ui-avatars.com/api/?name=${encodeURIComponent(user.fullname)}&background=dbeafe&color=2563eb'" class="w-8 h-8 rounded-full object-cover">
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
            <img src="${p.avatar || ''}" referrerpolicy="no-referrer" onerror="this.src='https://ui-avatars.com/api/?name=${encodeURIComponent(p.name)}&background=dbeafe&color=2563eb'" class="w-10 h-10 rounded-full object-cover">
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

    if (!slots || slots.length === 0) {
        container.innerHTML = `<div class="p-5 text-amber-600 font-bold border-2 border-amber-200 bg-amber-50 rounded-2xl text-center text-sm">No available times found for the selected dates.</div>`;
        return;
    }

    const totalSlots = slots.length;

    container.innerHTML = slots.map((slot, index) => {
        const start = new Date(slot.startTime);
        const end = new Date(slot.endTime);

        const dateString = start.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
        const timeString = `${start.toLocaleTimeString('en-US', {hour:'2-digit', minute:'2-digit'})} - ${end.toLocaleTimeString('en-US', {hour:'2-digit', minute:'2-digit'})}`;

        if (index === 0) {
            const badgeHtml = `
                <div class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[9px] font-black bg-emerald-600 text-white uppercase tracking-wider mb-1 shadow-sm">
                    <i data-lucide="sparkles" class="w-3 h-3"></i> Best Match
                </div>`;

            return `
                <div onclick="selectTimeSlot('${slot.startTime}', '${slot.endTime}')" 
                     class="relative overflow-hidden p-[2px] rounded-2xl mb-3 shadow-md cursor-pointer group transition-all hover:scale-[1.005]">
                    
                    <div class="absolute inset-[-500%] animate-[spin_4s_linear_infinite] bg-[conic-gradient(from_0deg,#059669_0%,#34d399_25%,#e6f4ea_50%,#34d399_75%,#059669_100%)] opacity-90"></div>
                    
                    <div class="relative bg-emerald-50/95 rounded-[14px] p-4 transition-all group-hover:bg-emerald-100/90">
                        <div class="flex justify-between items-center">
                            <div>
                                ${badgeHtml}
                                <h3 class="text-base font-bold text-slate-900">${dateString}</h3>
                                <p class="text-slate-600 text-xs font-medium flex items-center gap-1 mt-0.5">
                                    <i data-lucide="clock" class="w-3.5 h-3.5"></i>
                                    ${timeString}
                                </p>
                            </div>
                            <div class="text-right">
                                <span class="text-[9px] font-bold uppercase tracking-wider text-emerald-800 opacity-80">Score</span>
                                <p class="text-xl font-black text-emerald-800">${slot.score}</p>
                            </div>
                        </div>
                    </div>
                </div>`;
        }

        let colorClass = "";
        let textColor = "";
        const ratio = index / totalSlots;

        if (ratio <= 0.20) {
            colorClass = "border-2 border-lime-500 bg-lime-50/80 hover:border-lime-600";
            textColor = "text-lime-800";
        } else if (ratio <= 0.45) {
            colorClass = "border-2 border-yellow-400 bg-yellow-50/80 hover:border-yellow-500";
            textColor = "text-yellow-800";
        } else if (ratio <= 0.72) {
            colorClass = "border-2 border-orange-400 bg-orange-50/80 hover:border-orange-500";
            textColor = "text-orange-800";
        } else {
            colorClass = "border-2 border-red-400 bg-red-50/80 hover:border-red-500";
            textColor = "text-red-800";
        }

        return `
            <div onclick="selectTimeSlot('${slot.startTime}', '${slot.endTime}')" 
                 class="cursor-pointer rounded-2xl p-4 mb-3 shadow-sm hover:shadow-md transition-all ${colorClass}">
                <div class="flex justify-between items-center">
                    <div>
                        <h3 class="text-base font-bold text-slate-900">${dateString}</h3>
                        <p class="text-slate-600 text-xs font-medium flex items-center gap-1 mt-0.5">
                            <i data-lucide="clock" class="w-3.5 h-3.5"></i>
                            ${timeString}
                        </p>
                    </div>
                    <div class="text-right">
                        <span class="text-[9px] font-bold uppercase tracking-wider ${textColor} opacity-80">Score</span>
                        <p class="text-xl font-black ${textColor}">${slot.score}</p>
                    </div>
                </div>
            </div>`;
    }).join('');

    if (typeof lucide !== 'undefined') {
        lucide.createIcons();
    }
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

    const titleInput = document.getElementById('final-title-input');
    if (titleInput) {
        titleInput.value = "";
    }
}


async function submitFinalBooking() {
    const titleInput = document.getElementById('final-title-input');
    const errorElement = document.getElementById('title-error');
    const meetingTitle = titleInput ? titleInput.value.trim() : "";

    if (errorElement) {
        errorElement.classList.add('hidden');
        errorElement.innerText = "";
    }
    if (titleInput) {
        titleInput.classList.remove('border-red-500');
    }

    if (!meetingTitle) {
        if (errorElement && titleInput) {
            errorElement.innerText = "Please enter a title for your meeting before confirming.";
            errorElement.classList.remove('hidden');
            titleInput.classList.add('border-red-500');
            titleInput.focus();
        }
        return;
    }

    const payload = {
        roomId: state.selectedRoomId,
        title: meetingTitle,
        startTime: state.selectedTimeSlot.start,
        endTime: state.selectedTimeSlot.end,
        participants: state.participants.map(p => p.email)
    };

    const btn = document.getElementById('final-confirm-btn');
    btn.disabled = true;
    btn.innerHTML = `<i data-lucide="loader-2" class="w-5 h-5 animate-spin inline mr-2"></i> Booking & Syncing...`;
    if (typeof lucide !== 'undefined') lucide.createIcons();

    try {
        const res = await fetch('/api/bookings/confirm', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const data = await res.json();

        if (res.status === 409) {
            alert("Conflict Error: " + (data.error || "Room is no longer available at this time."));
            goToStep(3);
            return;
        }

        if (!res.ok) {
            throw new Error(data.error || "Failed to confirm booking.");
        }

        renderSuccessScreen(data);

    } catch (e) {
        alert("Booking Error: " + e.message);
        console.error(e);
    } finally {
        btn.disabled = false;
        btn.innerHTML = `Confirm Booking`;
        if (typeof lucide !== 'undefined') lucide.createIcons();
    }
}

function renderSuccessScreen(bookingData) {
    goToStep(4);

    const container = document.getElementById('step-4-container');
    if (!container) return;

    const start = new Date(state.selectedTimeSlot.start);
    const end = new Date(state.selectedTimeSlot.end);

    const dateStr = start.toLocaleDateString('en-US', { weekday: 'long', month: 'short', day: 'numeric', year: 'numeric' });
    const timeStr = `${start.toLocaleTimeString('en-US', {hour:'2-digit', minute:'2-digit'})} - ${end.toLocaleTimeString('en-US', {hour:'2-digit', minute:'2-digit'})}`;

    const calendarBtn = bookingData.googleEventLink || bookingData.htmlLink
        ? `<a href="${bookingData.googleEventLink || bookingData.htmlLink}" target="_blank" 
              class="inline-flex items-center justify-center gap-2 bg-white border border-slate-300 hover:bg-slate-50 text-slate-700 font-bold py-3 px-6 rounded-xl transition-all shadow-sm">
                <i data-lucide="calendar" class="w-5 h-5 text-blue-600"></i> View in Google Calendar
           </a>`
        : '';

    const participantList = state.participants.map(p => p.name || p.email);
    const participantsHtml = participantList.length > 0 ? participantList.join(', ') : 'You (Organizer)';

    container.innerHTML = `
        <div class="text-center py-8 px-4 max-w-lg mx-auto">
            <div class="w-20 h-20 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mx-auto mb-6 shadow-inner animate-bounce">
                <i data-lucide="check-circle-2" class="w-10 h-10"></i>
            </div>

            <h2 class="text-2xl font-black text-slate-900 mb-2">Meeting Confirmed!</h2>
            <p class="text-slate-500 text-sm mb-8">Calendar invites have been automatically sent to all participants.</p>

            <div class="bg-slate-50 border border-slate-200 rounded-2xl p-6 text-left mb-8 shadow-sm space-y-4">
                <div>
                    <span class="text-xs font-bold uppercase tracking-wider text-slate-400">Title</span>
                    <p class="text-base font-bold text-slate-900">${bookingData.title || document.getElementById('final-title-input').value}</p>
                </div>
                <div class="grid grid-cols-2 gap-4 border-t border-slate-200 pt-3">
                    <div>
                        <span class="text-xs font-bold uppercase tracking-wider text-slate-400">Date & Time</span>
                        <p class="text-xs font-bold text-slate-800 mt-1">${dateStr}</p>
                        <p class="text-xs text-slate-600">${timeStr}</p>
                    </div>
                    <div>
                        <span class="text-xs font-bold uppercase tracking-wider text-slate-400">Room</span>
                        <p class="text-xs font-bold text-slate-800 mt-1">${state.selectedRoomName}</p>
                    </div>
                </div>
                <div class="border-t border-slate-200 pt-3">
                    <span class="text-xs font-bold uppercase tracking-wider text-slate-400">Participants (${state.participants.length + 1})</span>
                    <p class="text-xs text-slate-600 mt-1 truncate">${participantsHtml}, You (Organizer)</p>
                </div>
            </div>

            <div class="flex flex-col sm:flex-row gap-3 justify-center">
                ${calendarBtn}
                <a href="/" class="inline-flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-700 text-white font-bold py-3 px-6 rounded-xl transition-all shadow-md">
                    <i data-lucide="home" class="w-5 h-5"></i> Back to Dashboard
                </a>
            </div>
        </div>
    `;

    if (typeof lucide !== 'undefined') lucide.createIcons();
}