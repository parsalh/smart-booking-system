let map = null;
let marker = null;

const DEFAULT_LAT = 37.9602;
const DEFAULT_LNG = 23.7088;

function initMap() {
    if (!map) {
        map = L.map('modalMap').setView([DEFAULT_LAT, DEFAULT_LNG], 16);

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '© OpenStreetMap'
        }).addTo(map);

        marker = L.marker([DEFAULT_LAT, DEFAULT_LNG]).addTo(map);
    }
}

async function geocodeAddress(address) {
    if (!address || address.trim() === '') return null;
    try {
        const response = await fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(address)}`);
        const data = await response.json();
        if (data && data.length > 0) {
            return {
                lat: parseFloat(data[0].lat),
                lng: parseFloat(data[0].lon)
            };
        }
    } catch (error) {
        console.error("Geocoding failed:", error);
    }
    return null;
}

async function openModal(name, building, address, floor, capacity, imageUrl, amenitiesStr) {
    const buildingName = building && building.trim() !== '' ? building.trim() : 'Main Campus';
    const addressText = address && address.trim() !== '' ? address.trim() : 'El. Venizelou 70, Kallithea';
    const floorName = floor && floor.trim() !== '' ? floor.trim() : 'N/A';

    document.getElementById('modalRoomName').innerText = name || 'Room Details';
    document.getElementById('modalBuildingBadge').innerText = buildingName;
    document.getElementById('modalFloorBadge').innerText = floorName;

    document.getElementById('modalCapacity').innerText = (capacity || '0') + ' Seats';
    document.getElementById('modalFloorSpec').innerText = floorName;
    document.getElementById('modalBuildingSpec').innerText = buildingName;

    const modalImg = document.getElementById('modalImage');
    const modalPlaceholder = document.getElementById('modalImagePlaceholder');

    if (imageUrl && imageUrl.trim() !== '') {
        modalImg.src = imageUrl.trim();
        modalImg.classList.remove('hidden');
        modalPlaceholder.classList.add('hidden');
    } else {
        modalImg.src = '';
        modalImg.classList.add('hidden');
        modalPlaceholder.classList.remove('hidden');
    }

    const amenitiesContainer = document.getElementById('modalAmenitiesList');
    amenitiesContainer.innerHTML = '';

    if (amenitiesStr && amenitiesStr.trim() !== '') {
        const amenities = amenitiesStr.split(',');
        amenities.forEach(amenity => {
            const badge = document.createElement('span');
            badge.className = 'px-3 py-1 rounded-lg text-xs font-semibold bg-gray-100 text-gray-700 border border-gray-200/80 capitalize flex items-center gap-1';
            badge.innerHTML = `<i data-lucide="check" class="w-3.5 h-3.5 text-emerald-600"></i> ${amenity.trim()}`;
            amenitiesContainer.appendChild(badge);
        });
    } else {
        amenitiesContainer.innerHTML = '<span class="text-xs text-gray-400 italic">No special amenities listed</span>';
    }

    document.getElementById('roomModal').classList.remove('hidden');
    lucide.createIcons();

    setTimeout(async () => {
        initMap();
        map.invalidateSize();

        const coords = await geocodeAddress(addressText);
        const lat = coords ? coords.lat : DEFAULT_LAT;
        const lng = coords ? coords.lng : DEFAULT_LNG;

        map.setView([lat, lng], 17);
        marker.setLatLng([lat, lng]);
        marker.bindPopup(`<b>${name}</b><br>${buildingName}<br><span class="text-xs text-gray-500">${addressText}</span>`).openPopup();
    }, 150);
}

document.addEventListener('DOMContentLoaded', () => {
    lucide.createIcons();

    document.querySelectorAll('.room-card').forEach(card => {
        card.addEventListener('click', () => {
            const name = card.dataset.name || '';
            const building = card.dataset.building || '';
            const location = card.dataset.location || '';
            const floor = card.dataset.floor || '';
            const capacity = card.dataset.capacity || '';
            const imageUrl = card.dataset.image || '';
            const amenities = card.dataset.amenities || '';
            const isAvailable = card.dataset.isAvailable === 'true';

            const banner = document.getElementById('modalUnavailableBanner');
            if (banner) {
                if (!isAvailable) {
                    banner.classList.remove('hidden');
                } else {
                    banner.classList.add('hidden');
                }
            }

            openModal(name, building, location, floor, capacity, imageUrl, amenities);
        });
    });

    document.getElementById('roomModal').addEventListener('click', (e) => {
        if (e.target === document.getElementById('roomModal')) {
            closeModal();
        }
    });
});

function closeModal() {
    const modal = document.getElementById('roomModal');
    if (modal) {
        modal.classList.add('hidden');
    }
}