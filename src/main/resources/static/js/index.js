lucide.createIcons();

function closeModal() {
    document.getElementById('eventModal').classList.add('hidden');
}

function showToast(message, isError = true) {
    const toast = document.getElementById('global-toast');
    const msgEl = document.getElementById('toast-message');
    const iconEl = document.getElementById('toast-icon');

    if (!toast) { alert(message); return; }

    msgEl.innerText = message;

    if (isError) {
        toast.className = 'fixed top-20 right-5 z-[9999] flex items-center gap-3 px-5 py-3.5 rounded-xl shadow-xl transition-all duration-500 transform translate-x-0 bg-rose-600 text-white';
        iconEl.setAttribute('data-lucide', 'alert-triangle');
    } else {
        toast.className = 'fixed top-20 right-5 z-[9999] flex items-center gap-3 px-5 py-3.5 rounded-xl shadow-xl transition-all duration-500 transform translate-x-0 bg-emerald-600 text-white';
        iconEl.setAttribute('data-lucide', 'check-circle-2');
    }

    if (typeof lucide !== 'undefined') lucide.createIcons();

    setTimeout(() => {
        toast.classList.replace('translate-x-0', 'translate-x-[150%]');
    }, 4000);
}

document.addEventListener('DOMContentLoaded', function() {
    const calendarEl = document.getElementById('calendar');
    const eventInput = document.getElementById('calendarEventsData');

    let eventsData = [];
    if (eventInput && eventInput.value) {
        try {
            const rawEvents = JSON.parse(eventInput.value);
            const emailInput = document.getElementById('currentUserEmail');
            const currentUserEmail = emailInput ? emailInput.value.trim().toLowerCase() : '';

            eventsData = rawEvents.filter(ev => {
                const props = ev.extendedProps || {};
                const parts = props.participants || {};

                for (const email of Object.keys(parts)) {
                    if (email.trim().toLowerCase() === currentUserEmail) {
                        return parts[email] !== 'DECLINED';
                    }
                }
                return true;
            });

        } catch (e) {
            console.error("JSON Error:", e);
        }
    }

    const calendar = new FullCalendar.Calendar(calendarEl, {
        initialView: 'dayGridMonth',
        initialDate: new Date(),
        headerToolbar: {
            left: 'prev,next today',
            center: 'title',
            right: 'dayGridMonth,timeGridWeek'
        },
        events: eventsData,
        firstDay: 1,
        height: 750,
        expandRows: true,
        dayMaxEvents: 2,
        fixedWeekCount: false,
        slotDuration: '01:00:00',
        slotMinTime: '08:00:00',
        slotMaxTime: '22:00:00',
        allDaySlot: false,
        eventDisplay: 'block',
        eventTimeFormat: { hour: 'numeric', minute: '2-digit', hour12: false },

        datesSet: function(info) {
            if (info.view.type === 'dayGridMonth') {
                setTimeout(() => {
                    const todayCell = document.querySelector('.fc-day-today');
                    if (todayCell) {
                        todayCell.scrollIntoView({
                            behavior: 'smooth',
                            block: 'center'
                        });
                    }
                }, 100);
            }
        },

        eventClassNames: function(arg) {
            const now = new Date();
            const eventDate = arg.event.end || arg.event.start;
            let classes = [];

            if (eventDate < now) {
                classes.push('opacity-50', 'hover:opacity-100', 'transition-opacity');
            }

            const emailInput = document.getElementById('currentUserEmail');
            const currentUserEmail = emailInput ? emailInput.value.trim().toLowerCase() : '';
            const props = arg.event.extendedProps || {};
            const parts = props.participants || {};

            let myStatus = props.myRsvpStatus;
            if (!myStatus) {
                for (const email of Object.keys(parts)) {
                    if (email.trim().toLowerCase() === currentUserEmail) {
                        myStatus = parts[email];
                        break;
                    }
                }
            }

            if (myStatus === 'PENDING') {
                classes.push('fc-event-pending');
            }

            return classes;
        },

        eventDidMount: function(info) {

        },

        eventClick: function(info) {
            const props = info.event.extendedProps;

            document.getElementById('modalTitle').innerText = props['fullTitle'] || info.event.title || 'Untitled Event';

            const startTime = info.event.start ? info.event.start.toLocaleTimeString([], {
                hour: '2-digit',
                minute: '2-digit'
            }) : '';
            const endTime = info.event.end ? info.event.end.toLocaleTimeString([], {
                hour: '2-digit',
                minute: '2-digit'
            }) : '';
            document.getElementById('modalTime').innerText = endTime ? `${startTime} - ${endTime}` : startTime;

            const rawRoomName = props['locationName'];
            const rawLocation = props['roomLocation'] || props['fullLocation'];

            let displayLocation = 'No location specified';

            if (rawRoomName && rawRoomName !== 'No location specified' && rawLocation && rawLocation !== 'No location specified') {
                displayLocation = `${rawRoomName} - ${rawLocation}`;
            } else if (rawRoomName && rawRoomName !== 'No location specified') {
                displayLocation = rawRoomName;
            } else if (rawLocation && rawLocation !== 'No location specified') {
                displayLocation = rawLocation;
            }

            document.getElementById('modalLocation').innerText = displayLocation;

            document.getElementById('modalLocation').innerText = displayLocation;

            const descriptionText = props.description || 'No details available.';
            const isSmartBooking = props.bookingId || descriptionText.includes('Automatically scheduled via SmartBooking App');

            const mapSection = document.getElementById('eventMapSection');
            const descSection = document.getElementById('eventDescSection');

            if (isSmartBooking) {
                mapSection.classList.remove('hidden');
                descSection.classList.add('hidden');
                document.getElementById('modalDescription').innerText = descriptionText;
            } else {
                mapSection.classList.add('hidden');
                descSection.classList.remove('hidden');
                document.getElementById('modalDescriptionFull').innerText = descriptionText;
            }

            document.getElementById('eventModal').classList.remove('hidden');
            lucide.createIcons();
            document.getElementById('modalParticipantsContainer').classList.remove('hidden');

            if (isSmartBooking && props.bookingId) {
                renderLiveParticipants(props.bookingId, props.participants, 'modalParticipantsList');
            } else {
                document.getElementById('modalParticipantsContainer').classList.add('hidden');
            }

            if (isSmartBooking) {
                setTimeout(async () => {
                    if (typeof initEventMap === 'function') {
                        initEventMap();
                        if (eventMap) {
                            eventMap.invalidateSize();

                            const coords = await geocodeAddress(roomLocation);
                            const lat = coords ? coords.lat : 37.9575;
                            const lng = coords ? coords.lng : 23.7025;

                            eventMap.setView([lat, lng], 17);
                            eventMarker.setLatLng([lat, lng]);
                            eventMarker.bindPopup(`<b>${roomName}</b><br><span class="text-xs text-gray-500">${roomLocation}</span>`).openPopup();

                            setTimeout(() => eventMap.invalidateSize(), 100);
                        }
                    }
                }, 300);
            }
        }
    });

    function renderParticipants(bookingId, fallbackParticipants) {
        const participantsList = document.getElementById('modalParticipantsList');
        const participantsContainer = document.getElementById('modalParticipantsContainer');

        const draw = (participantsRaw) => {
            participantsList.innerHTML = '';

            let participantsEntries = [];
            if (Array.isArray(participantsRaw)) {
                participantsEntries = participantsRaw.map(a => {
                    const email = typeof a === 'string' ? a : (a.email || a.displayName);
                    return [email, 'UNKNOWN'];
                }).filter(entry => entry[0]);
            } else {
                participantsEntries = Object.entries(participantsRaw || {});
            }

            if (participantsEntries.length > 0) {
                participantsContainer.classList.remove('hidden');
                participantsEntries.forEach(([email, status]) => {
                    let icon = 'help-circle';
                    let colorClass = 'bg-slate-50 text-slate-600 border-slate-200';

                    if (status === 'ACCEPTED') {
                        icon = 'check-circle-2';
                        colorClass = 'bg-emerald-50 text-emerald-700 border-emerald-200';
                    } else if (status === 'DECLINED') {
                        icon = 'x-circle';
                        colorClass = 'bg-red-50 text-red-700 border-red-200';
                    } else if (status === 'TENTATIVE') {
                        icon = 'help-circle';
                        colorClass = 'bg-purple-50 text-purple-700 border-purple-200';
                    } else if (status === 'PENDING') {
                        icon = 'clock';
                        colorClass = 'bg-amber-50 text-amber-700 border-amber-200';
                    }

                    const span = document.createElement('div');
                    span.className = `flex items-center gap-2 px-3 py-2 ${colorClass} rounded-xl text-xs font-bold border`;
                    span.innerHTML = `<i data-lucide="${icon}" class="w-4 h-4"></i> ${email}`;
                    participantsList.appendChild(span);
                });
                lucide.createIcons();
            } else {
                participantsContainer.classList.add('hidden');
            }
        };

        if (!bookingId) {
            draw(fallbackParticipants || {});
            return;
        }

        fetch(`/api/bookings/${bookingId}/participants`)
            .then(res => res.ok ? res.json() : Promise.reject())
            .then(freshParticipants => draw(freshParticipants))
            .catch(() => draw(fallbackParticipants || {}));
    }

    calendar.render();
    window.smartCalendar = calendar;

    setTimeout(() => {
        const todayCell = document.querySelector('.fc-day-today');
        if (todayCell) {
            todayCell.scrollIntoView({
                behavior: 'smooth',
                block: 'center'
            });
        }
    }, 500);

    document.getElementById('eventModal').addEventListener('click', function(e) {
        if (e.target === this) closeModal();
    });
    document.getElementById('allBookingsModal').addEventListener('click', function(e) {
        if (e.target === this) closeAllBookingsModal();
    });
});

let eventMap = null;
let eventMarker = null;

function initEventMap() {
    if (!eventMap) {
        eventMap = L.map('eventMap').setView([37.9575, 23.7025], 17);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '© OpenStreetMap contributors'
        }).addTo(eventMap);

        eventMarker = L.marker([37.9575, 23.7025]).addTo(eventMap);
    }
}

async function geocodeAddress(address) {
    try {
        const response = await fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(address)}`);
        const data = await response.json();
        if (data && data.length > 0) {
            return { lat: parseFloat(data[0].lat), lng: parseFloat(data[0].lon) };
        }
    } catch (e) {
        console.error("Geocoding error:", e);
    }
    return null;
}

function openAllBookingsModal() {
    const listContainer = document.getElementById('allBookingsList');
    if (!listContainer) return;

    listContainer.innerHTML = `
            <div class="text-center py-12">
                <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600 mx-auto mb-4"></div>
                <p class="text-slate-500 font-medium">Loading your smart bookings...</p>
            </div>
        `;

    const modal = document.getElementById('allBookingsModal');
    if (modal) modal.classList.remove('hidden');

    fetch('/api/bookings/my-smartbookings')
        .then(res => {
            if (!res.ok) throw new Error('Network response error');
            return res.json();
        })
        .then(bookings => {

            const activeBookings = bookings.filter(booking => {
                const parts = booking.participants || {};
                for (const email of Object.keys(parts)) {
                    if (email.trim().toLowerCase() === currentUserEmail) {
                        return parts[email] !== 'DECLINED';
                    }
                }
                return true;
            });

            listContainer.innerHTML = '';

            if (!activeBookings || activeBookings.length === 0) {
                listContainer.innerHTML = `
                    <div class="text-center py-12">
                        <div class="w-16 h-16 bg-white border border-slate-200 text-slate-300 rounded-full flex items-center justify-center mx-auto mb-4 shadow-sm">
                            <i data-lucide="inbox" class="w-8 h-8"></i>
                        </div>
                        <h4 class="text-slate-700 font-bold text-lg mb-1">No SmartBookings found</h4>
                        <p class="text-sm text-slate-500">Meetings you schedule through the app will appear here.</p>
                    </div>`;
                if (typeof lucide !== 'undefined') lucide.createIcons();
                return;
            }

            activeBookings.forEach(booking => {
                const startDate = new Date(booking.startTime);
                const endDate = booking.endTime ? new Date(booking.endTime) : null;

                const timeStr = endDate
                    ? `${startDate.toLocaleTimeString('en-US', {hour:'2-digit', minute:'2-digit'})} - ${endDate.toLocaleTimeString('en-US', {hour:'2-digit', minute:'2-digit'})}`
                    : startDate.toLocaleTimeString('en-US', {hour:'2-digit', minute:'2-digit'});

                const roomName = booking.room && booking.room.name ? booking.room.name : 'Unknown Room';
                const title = booking.title || roomName || 'SmartBooking Meeting';
                const containerId = `participants-list-${booking.id}`;

                const card = document.createElement('div');
                card.className = "flex items-start gap-5 p-5 bg-white border border-slate-200 rounded-2xl shadow-sm hover:shadow-md transition-shadow group";
                card.innerHTML = `
                    <div class="w-16 h-16 bg-emerald-50 border border-emerald-100 text-emerald-600 rounded-2xl flex flex-col items-center justify-center shrink-0 group-hover:bg-emerald-600 group-hover:text-white transition-colors">
                        <span class="text-xs font-black uppercase tracking-wider">${startDate.toLocaleString('en-US', { month: 'short'})}</span>
                        <span class="text-2xl font-black leading-none mt-0.5">${startDate.getDate()}</span>
                    </div>
                    <div class="flex-1 min-w-0">
                        <h4 class="font-bold text-slate-900 text-lg truncate">${title}</h4>
                        <div class="flex flex-wrap items-center gap-x-5 gap-y-2 mt-1.5">
                            <p class="text-sm text-slate-500 font-semibold flex items-center gap-1.5"><i data-lucide="clock" class="w-4 h-4 text-slate-400"></i> ${timeStr}</p>
                            <p class="text-sm text-slate-500 font-semibold flex items-center gap-1.5"><i data-lucide="map-pin" class="w-4 h-4 text-blue-500"></i> ${roomName}</p>
                        </div>
                        <div class="mt-4 pt-3 border-t border-slate-100">
                            <p class="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-2">Participants & RSVP</p>
                           
                            <div id="${containerId}" class="flex flex-wrap gap-2"></div>
                        </div>
                    </div>`;

                listContainer.appendChild(card);

                renderLiveParticipants(booking.id, booking.participants, containerId);
            });

            if (typeof lucide !== 'undefined') lucide.createIcons();
        })
        .catch(err => {
            console.error("Failed to load smart bookings:", err);
            listContainer.innerHTML = '<div class="text-center py-8 text-red-500">Failed to load bookings.</div>';
        });
}

function closeAllBookingsModal() {
    const modal = document.getElementById('allBookingsModal');
    if (modal) {
        modal.classList.add('hidden');
    }
}

function updateEventStatistics() {
    const eventsInput = document.getElementById('calendarEventsData');
    if (!eventsInput || !eventsInput.value) return;

    try {
        const eventsData = JSON.parse(eventsInput.value);

        const now = new Date();
        const dayOfWeek = now.getDay();
        const diffToMonday = now.getDate() - dayOfWeek + (dayOfWeek === 0 ? -6 : 1);

        const startOfWeek = new Date(now.setDate(diffToMonday));
        startOfWeek.setHours(0, 0, 0, 0);

        const endOfWeek = new Date(startOfWeek);
        endOfWeek.setDate(startOfWeek.getDate() + 6);
        endOfWeek.setHours(23, 59, 59, 999);

        let stats = {
            smart: 0,
            lecture: 0,
            lab: 0,
            meeting: 0,
            office: 0,
            other: 0
        };
        let totalThisWeek = 0;

        eventsData.forEach(ev => {
            const eventDate = new Date(ev.start);

            if (eventDate >= startOfWeek && eventDate <= endOfWeek) {
                totalThisWeek++;

                const props = ev.extendedProps || {};
                const type = props.type || '';
                const description = props.description || '';

                if (type === 'SMART_BOOKING' || description.includes('Automatically scheduled via SmartBooking App')) {
                    stats.smart++;
                } else if (type === 'LECTURE') {
                    stats.lecture++;
                } else if (type === 'LAB') {
                    stats.lab++;
                } else if (type === 'MEETING') {
                    stats.meeting++;
                } else if (type === 'OFFICE_HOURS') {
                    stats.office++;
                } else {
                    stats.other++;
                }
            }
        });

        const animateBar = (id, count) => {
            const countEl = document.getElementById(`stat-count-${id}`);
            const barEl = document.getElementById(`stat-bar-${id}`);

            if (countEl && barEl) {
                countEl.innerText = count;
                const percentage = totalThisWeek > 0 ? (count / totalThisWeek) * 100 : 0;

                setTimeout(() => {
                    barEl.style.width = `${percentage}%`;
                }, 100);
            }
        };

        animateBar('smart', stats.smart);
        animateBar('lecture', stats.lecture);
        animateBar('lab', stats.lab);
        animateBar('meeting', stats.meeting);
        animateBar('office', stats.office);
        animateBar('other', stats.other);

    } catch (e) {
        console.error("Failed to parse events for statistics:", e);
    }
}

document.addEventListener('DOMContentLoaded', function() {
    updateEventStatistics();
    fetchPendingInvites();
});

function openPendingInvitesModal() {
    const modal = document.getElementById('pendingInvitesModal');
    const listContainer = document.getElementById('pendingInvitesList');

    if (modal) modal.classList.remove('hidden');

    if (listContainer) {
        listContainer.innerHTML = `
                <div class="text-center py-12">
                    <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-amber-500 mx-auto mb-4"></div>
                    <p class="text-slate-500 font-medium">Checking with Google Calendar...</p>
                </div>
            `;
    }

    fetchPendingInvites();
}

function closePendingInvitesModal() {
    document.getElementById('pendingInvitesModal').classList.add('hidden');
}

function fetchPendingInvites() {
    fetch('/api/bookings/pending-invites')
        .then(response => {
            if (!response.ok) throw new Error('Failed to fetch pending invites');
            return response.json();
        })
        .then(data => {
            const pendingInvites = data.map(invite => ({
                bookingId: invite.bookingId,
                title: invite.title || 'SmartBooking Meeting',
                roomName: invite.roomName || 'Unknown Room',
                organizerEmail: invite.organizerEmail || 'Unknown Organizer',
                startTime: invite.startTime,
                endTime: invite.endTime
            }));

            renderPendingInvites(pendingInvites);

            const badge = document.getElementById('inviteBadge');
            const countBadge = document.getElementById('inviteCountBadge');

            if (pendingInvites.length > 0) {
                if (badge) badge.classList.remove('hidden');
                if (countBadge) {
                    countBadge.classList.remove('hidden');
                    countBadge.innerText = pendingInvites.length;
                }
            } else {
                if (badge) badge.classList.add('hidden');
                if (countBadge) countBadge.classList.add('hidden');
            }
        })
        .catch(error => {
            console.error('Error fetching invites:', error);
            const listContainer = document.getElementById('pendingInvitesList');
            if (listContainer) {
                listContainer.innerHTML = '<div class="text-center py-8 text-red-500">Failed to load invites.</div>';
            }
        });
}

function renderPendingInvites(invites) {
    const listContainer = document.getElementById('pendingInvitesList');
    if (!listContainer) return;
    listContainer.innerHTML = '';

    if (!invites || invites.length === 0) {
        listContainer.innerHTML = `
                <div class="text-center py-12">
                    <div class="w-16 h-16 bg-white border border-slate-200 text-slate-300 rounded-full flex items-center justify-center mx-auto mb-4 shadow-sm">
                        <i data-lucide="mail-check" class="w-8 h-8"></i>
                    </div>
                    <h4 class="text-slate-700 font-bold text-lg mb-1">No pending invites</h4>
                    <p class="text-sm text-slate-500">You're all caught up! New meeting invites will appear here.</p>
                </div>
            `;
        if (typeof lucide !== 'undefined') lucide.createIcons();
        return;
    }

    invites.forEach(invite => {
        const startDate = new Date(invite.startTime);
        const endDate = invite.endTime ? new Date(invite.endTime) : new Date(startDate.getTime() + 3600000);

        const dateStr = startDate.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
        const timeStr = `${startDate.toLocaleTimeString('en-US', {hour:'2-digit', minute:'2-digit'})} - ${endDate.toLocaleTimeString('en-US', {hour:'2-digit', minute:'2-digit'})}`;

        const displayTitle = (invite.title && invite.title !== 'SmartBooking Meeting')
            ? invite.title
            : (invite.roomName ? invite.roomName + ' Meeting' : 'SmartBooking Meeting');

        const card = document.createElement('div');
        card.id = `invite-card-${invite.bookingId}`;
        card.className = "bg-white p-5 border border-slate-200 rounded-2xl shadow-sm hover:shadow-md transition-shadow";

        card.innerHTML = `
                <div class="mb-4">
                    <h4 class="font-bold text-slate-900 text-lg truncate" title="Invitation: ${displayTitle}">Invitation: ${displayTitle}</h4>
                    <p class="text-sm text-slate-500 font-medium flex items-center gap-1.5 mt-1.5">
                        <i data-lucide="user" class="w-4 h-4 text-slate-400"></i>
                        Organizer: <span class="text-slate-700 font-bold">${invite.organizerEmail}</span>
                    </p>
                </div>
                <div class="space-y-2 mb-5 bg-slate-50 p-3 rounded-xl border border-slate-100">
                    <p class="text-sm text-slate-700 flex items-center gap-2 font-medium">
                        <i data-lucide="calendar" class="w-4 h-4 text-blue-500"></i> ${dateStr}
                    </p>
                    <p class="text-sm text-slate-700 flex items-center gap-2 font-medium">
                        <i data-lucide="clock" class="w-4 h-4 text-orange-500"></i> ${timeStr}
                    </p>
                    <p class="text-sm text-slate-700 flex items-center gap-2 font-medium">
                        <i data-lucide="map-pin" class="w-4 h-4 text-emerald-500"></i> ${invite.roomName}
                    </p>
                </div>
                <div class="flex gap-3">
                    <button onclick="handleRsvp(${invite.bookingId}, 'ACCEPTED')" class="flex-1 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white font-bold rounded-xl flex items-center justify-center gap-2 transition-colors shadow-sm">
                        <i data-lucide="check" class="w-4 h-4"></i> Accept
                    </button>
                    <button onclick="handleRsvp(${invite.bookingId}, 'DECLINED')" class="flex-1 py-2.5 bg-red-50 hover:bg-red-100 text-red-600 border border-red-100 font-bold rounded-xl flex items-center justify-center gap-2 transition-colors">
                        <i data-lucide="x" class="w-4 h-4"></i> Decline
                    </button>
                </div>
            `;
        listContainer.appendChild(card);
    });

    if (typeof lucide !== 'undefined') lucide.createIcons();
}

function handleRsvp(bookingId, status) {
    const card = document.getElementById(`invite-card-${bookingId}`);
    if(card) {
        card.style.opacity = '0.5';
        card.style.pointerEvents = 'none';
    }

    fetch(`/api/bookings/${bookingId}/rsvp`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ status: status })
    })
        .then(response => {
            if(response.ok) {
                if(card) {
                    card.style.opacity = '1';
                    card.innerHTML = `
                        <div class="text-center py-8 flex flex-col items-center gap-3">
                            <div class="w-12 h-12 bg-emerald-100 rounded-full flex items-center justify-center">
                                <i data-lucide="check-circle-2" class="w-6 h-6 text-emerald-600"></i>
                            </div>
                            <p class="text-slate-700 font-bold">Response sent!</p>
                        </div>
                    `;
                    if (typeof lucide !== 'undefined') lucide.createIcons();

                    setTimeout(() => {
                        card.remove();

                        updatePendingBadges();

                        if (window.smartCalendar) {
                            const events = window.smartCalendar.getEvents();
                            events.forEach(ev => {
                                if (ev.extendedProps && ev.extendedProps.bookingId === bookingId) {
                                    if (status === 'DECLINED') {
                                        ev.remove();
                                    } else {
                                        let updatedParticipants = { ...ev.extendedProps.participants };
                                        const emailInput = document.getElementById('currentUserEmail');
                                        const currentUserEmail = emailInput ? emailInput.value.trim().toLowerCase() : '';
                                        if (currentUserEmail) {
                                            updatedParticipants[currentUserEmail] = status;
                                            ev.setExtendedProp('participants', updatedParticipants);
                                        }
                                    }
                                }
                            });
                        }

                        const remainingCards = document.querySelectorAll('[id^="invite-card-"]').length;
                        if (remainingCards === 0) {
                            closePendingInvitesModal();
                        }
                    }, 2000);
                }
            } else {
                alert("Failed to send RSVP. Please try again.");
                if(card) {
                    card.style.opacity = '1';
                    card.style.pointerEvents = 'auto';
                }
            }
        })
        .catch(error => console.error('Error handling RSVP:', error));
}

function updatePendingBadges() {
    const remainingCards = document.querySelectorAll('[id^="invite-card-"]').length;
    const badge = document.getElementById('inviteBadge');
    const countBadge = document.getElementById('inviteCountBadge');

    if (remainingCards > 0) {
        if (badge) badge.classList.remove('hidden');
        if (countBadge) {
            countBadge.classList.remove('hidden');
            countBadge.innerText = remainingCards;
        }
    } else {
        if (badge) badge.classList.add('hidden');
        if (countBadge) countBadge.classList.add('hidden');
    }
}

function renderLiveParticipants(bookingId, fallbackParticipants, containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;

    const emailInput = document.getElementById('currentUserEmail');
    const currentUserEmail = emailInput ? emailInput.value.trim().toLowerCase() : '';

    const draw = (participantsRaw) => {
        container.innerHTML = '';

        let participantsEntries = [];
        if (Array.isArray(participantsRaw)) {
            participantsEntries = participantsRaw.map(a => [typeof a === 'string' ? a : (a.email || a.displayName), 'UNKNOWN']).filter(entry => entry[0]);
        } else {
            participantsEntries = Object.entries(participantsRaw || {});
        }

        if (participantsEntries.length > 0) {
            participantsEntries.forEach(([email, status]) => {
                let icon = 'help-circle';
                let colorClass = 'bg-slate-50 text-slate-600 border-slate-200';
                let iconColor = 'text-slate-400';

                if (status === 'ACCEPTED') {
                    icon = 'check-circle-2';
                    colorClass = 'bg-emerald-50 text-emerald-700 border-emerald-200';
                    iconColor = 'text-emerald-500';
                } else if (status === 'DECLINED') {
                    icon = 'x-circle';
                    colorClass = 'bg-red-50 text-red-700 border-red-200';
                    iconColor = 'text-red-500';
                } else if (status === 'TENTATIVE') {
                    icon = 'help-circle';
                    colorClass = 'bg-purple-50 text-purple-700 border-purple-200';
                    iconColor = 'text-purple-500';
                } else if (status === 'PENDING') {
                    icon = 'clock';
                    colorClass = 'bg-amber-50 text-amber-700 border-amber-200';
                    iconColor = 'text-amber-500';
                }

                const isUser = (currentUserEmail && email.trim().toLowerCase() === currentUserEmail);
                const displayLabel = isUser ? `${email} (You)` : email;

                const span = document.createElement('div');
                span.className = `flex items-center gap-2 px-3 py-2 ${colorClass} rounded-xl text-[10px] font-bold border truncate max-w-[200px]`;
                span.innerHTML = `<i data-lucide="${icon}" class="w-4 h-4 ${iconColor}"></i> ${displayLabel}`;
                container.appendChild(span);
            });
            if (typeof lucide !== 'undefined') lucide.createIcons();
        } else {
            container.innerHTML = '<p class="text-xs text-slate-400 italic">No participants found.</p>';
        }
    };

    if (!bookingId) {
        draw(fallbackParticipants || {});
        return;
    }

    container.innerHTML = `<div class="flex items-center gap-2 px-3 py-2 bg-slate-50 text-slate-500 border border-slate-200 rounded-xl text-xs font-bold w-fit"><i data-lucide="loader-2" class="w-4 h-4 animate-spin"></i> Fetching Status...</div>`;
    if (typeof lucide !== 'undefined') lucide.createIcons();

    fetch(`/api/bookings/${bookingId}/participants`)
        .then(res => res.ok ? res.json() : Promise.reject())
        .then(freshParticipants => {

            if (freshParticipants) {
                for (const [email, status] of Object.entries(freshParticipants)) {
                    if (email.trim().toLowerCase() === currentUserEmail && String(status).trim().toUpperCase() === 'DECLINED') {

                        const cardElement = container.closest('.group');
                        if (cardElement) {
                            cardElement.remove();
                        }

                        if (window.smartCalendar) {
                            const events = window.smartCalendar.getEvents();
                            events.forEach(ev => {
                                if (ev.extendedProps && ev.extendedProps.bookingId === bookingId) {
                                    ev.remove();
                                }
                            });
                        }

                        const eventModal = document.getElementById('eventModal');
                        if (eventModal && !eventModal.classList.contains('hidden')) {
                            closeModal();
                        }

                        return;
                    }
                }
            }
            draw(freshParticipants);
        })
        .catch(() => draw(fallbackParticipants || {}));
}