function formatDateDMY(isoDate) {
    if (!isoDate) return '';
    const [year, month, day] = isoDate.split('-');
    return `${day}-${month}-${year}`;
}

function attachDatePicker(inputId) {
    const input = document.getElementById(inputId);
    if (!input) return;

    let panel = null;
    let viewDate = new Date();

    function isoOf(date) {
        return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
    }

    function closePanel() {
        if (panel) {
            panel.remove();
            panel = null;
            document.removeEventListener('click', outsideClickHandler, true);
            window.removeEventListener('scroll', closePanel, true);
            window.removeEventListener('resize', closePanel);
        }
    }

    function outsideClickHandler(e) {
        if (panel && !panel.contains(e.target) && e.target !== input) {
            closePanel();
        }
    }

    function positionPanel() {
        const rect = input.getBoundingClientRect();
        panel.style.position = 'fixed';
        panel.style.top = `${rect.bottom + 8}px`;
        panel.style.left = `${rect.left}px`;
    }

    function renderPanel() {
        const year = viewDate.getFullYear();
        const month = viewDate.getMonth();
        const monthNames = ['January','February','March','April','May','June','July','August','September','October','November','December'];

        const firstOfMonth = new Date(year, month, 1);
        const startWeekday = (firstOfMonth.getDay() + 6) % 7;
        const daysInMonth = new Date(year, month + 1, 0).getDate();

        let cells = '';
        for (let i = 0; i < startWeekday; i++) {
            cells += `<div></div>`;
        }
        for (let day = 1; day <= daysInMonth; day++) {
            const cellDate = new Date(year, month, day);
            const iso = isoOf(cellDate);
            const isSelected = input.dataset.iso === iso;
            cells += `<button type="button" data-iso="${iso}"
                class="w-8 h-8 rounded-lg text-xs font-bold hover:bg-blue-50 transition-colors ${isSelected ? 'bg-blue-600 text-white hover:bg-blue-600' : 'text-slate-700'}">${day}</button>`;
        }

        panel.innerHTML = `
            <div class="flex items-center justify-between mb-2 px-1">
                <button type="button" id="${inputId}-prev" class="p-1 hover:bg-slate-100 rounded-lg"><i data-lucide="chevron-left" class="w-4 h-4"></i></button>
                <span class="text-xs font-bold text-slate-700">${monthNames[month]} ${year}</span>
                <button type="button" id="${inputId}-next" class="p-1 hover:bg-slate-100 rounded-lg"><i data-lucide="chevron-right" class="w-4 h-4"></i></button>
            </div>
            <div class="grid grid-cols-7 gap-1 px-1">
                ${['M','T','W','T','F','S','S'].map(d => `<div class="w-8 text-center text-[9px] font-bold text-slate-400">${d}</div>`).join('')}
                ${cells}
            </div>
        `;
        lucide.createIcons();
        positionPanel();

        panel.querySelector(`#${inputId}-prev`).onclick = () => {
            viewDate = new Date(year, month - 1, 1);
            renderPanel();
        };
        panel.querySelector(`#${inputId}-next`).onclick = () => {
            viewDate = new Date(year, month + 1, 1);
            renderPanel();
        };
        panel.querySelectorAll('button[data-iso]').forEach(btn => {
            btn.onclick = () => {
                const iso = btn.dataset.iso;
                input.value = formatDateDMY(iso);
                input.dataset.iso = iso;
                closePanel();
            };
        });
    }

    input.addEventListener('click', () => {
        if (panel) {
            closePanel();
            return;
        }
        viewDate = input.dataset.iso ? new Date(input.dataset.iso) : new Date();
        panel = document.createElement('div');
        panel.className = 'z-[9999] p-3 bg-white border border-slate-200 rounded-xl shadow-xl';
        document.body.appendChild(panel);
        renderPanel();
        setTimeout(() => {
            document.addEventListener('click', outsideClickHandler, true);
            window.addEventListener('scroll', closePanel, true);
            window.addEventListener('resize', closePanel);
        }, 0);
    });
}