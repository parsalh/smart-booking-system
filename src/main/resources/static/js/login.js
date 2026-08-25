lucide.createIcons();

// Fake stats haha
new Chart(document.getElementById('matchChart'), {
    type: 'bar',
    data: {
        labels: ['Jul', 'Aug', 'Sep', 'Oct', 'Nov'],
        datasets: [{
            data: [88, 91, 93, 94, 95],
            backgroundColor: '#10b981',
            borderRadius: 4
        }]
    },
    options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
            y: { display: false, min: 80 },
            x: { ticks: { color: 'rgba(255,255,255,0.7)', font: { size: 10 } }, grid: { display: false } }
        }
    }
});

new Chart(document.getElementById('efficiencyChart'), {
    type: 'line',
    data: {
        labels: ['W1', 'W2', 'W3', 'W4'],
        datasets: [{
            data: [45, 38, 32, 30],
            borderColor: '#fbbf24',
            borderWidth: 3,
            pointBackgroundColor: '#fbbf24',
            tension: 0.4,
            fill: false
        }]
    },
    options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
            y: { display: false },
            x: { ticks: { color: 'rgba(255,255,255,0.7)', font: { size: 10 } }, grid: { display: false } }
        }
    }
});

new Chart(document.getElementById('syncChart'), {
    type: 'doughnut',
    data: {
        labels: ['Synced', 'Failed'],
        datasets: [{
            data: [100, 0],
            backgroundColor: ['#10b981', '#ef4444'],
            borderWidth: 0
        }]
    },
    options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        cutout: '75%'
    }
});

