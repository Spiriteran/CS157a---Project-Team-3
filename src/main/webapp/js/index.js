document.addEventListener('DOMContentLoaded', () => {
    // 1. Initialize Map
    initMap();

    // 2. Set Timestamp
    updateTimestamp();

    // 3. Setup Filter Interactions
    setupFilters();
});

let map;

function initMap() {
    // Set view to a generic city center coordinate (e.g., somewhere in San Jose for SJSU CS157A context)
    map = L.map('map', {
        zoomControl: false // Move to bottom right
    }).setView([37.3352, -121.8810], 14);

    L.control.zoom({
        position: 'bottomright'
    }).addTo(map);

    // Dark theme tile layer
    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>',
        subdomains: 'abcd',
        maxZoom: 20
    }).addTo(map);

    // Mock Data for Facilities
    const facilities = [
        { id: 1, name: 'Central Recreation Center', lat: 37.3362, lng: -121.8850, status: 'open', label: '1' },
        { id: 2, name: 'Riverside Courts', lat: 37.3302, lng: -121.8820, status: 'limited', label: '2' },
        { id: 3, name: 'Southside Pool', lat: 37.3252, lng: -121.8860, status: 'open', label: '3' },
        { id: 4, name: 'North Campus Gym', lat: 37.3402, lng: -121.8750, status: 'closed', label: '4' }
    ];

    facilities.forEach(fac => {
        const iconHtml = `
            <div class="custom-marker">
                <div class="marker-pin ${fac.status}"></div>
                <span>${fac.label}</span>
            </div>
        `;

        const icon = L.divIcon({
            className: 'custom-div-icon',
            html: iconHtml,
            iconSize: [30, 42],
            iconAnchor: [15, 42]
        });

        const marker = L.marker([fac.lat, fac.lng], { icon: icon }).addTo(map);

        marker.on('click', () => {
            selectFacility(fac);
        });
    });
}

function selectFacility(facility) {
    // In a real application, this would make an AJAX call to the Servlet backend
    // to fetch live details about the facility spaces and events.
    // e.g., fetch(`/api/facilities/${facility.id}`)
    
    console.log(`Facility ${facility.name} selected. Map centered.`);
    map.flyTo([facility.lat, facility.lng], 15, {
        animate: true,
        duration: 0.5
    });

    // We simulate updating the side panel here.
    // If the panel was hidden, we would show it. Right now it's always visible in the prototype.
    const panelHeader = document.querySelector('.panel-header h2');
    if(panelHeader) {
        panelHeader.textContent = facility.name;
    }
}

function updateTimestamp() {
    const timeEl = document.getElementById('live-timestamp');
    if (timeEl) {
        timeEl.textContent = 'Updated just now';
        // A real app might format new Date()
    }
}

function setupFilters() {
    const chips = document.querySelectorAll('.filter-chip');
    chips.forEach(chip => {
        chip.addEventListener('click', () => {
            chip.classList.toggle('active');
            // Logic to filter map markers and list items would go here
            console.log('Filter changed:', chip.textContent);
        });
    });
}
