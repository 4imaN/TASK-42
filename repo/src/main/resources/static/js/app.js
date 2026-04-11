/* ============================================================
   ReClaim Portal — app.js
   Utilities: JWT, fetch wrapper, autocomplete, star rating,
   image upload, canvas signature, slot loader, form validation,
   logout handler.
   ============================================================ */

'use strict';

/* ── Token management (in-memory only) ───────────────────────
   The access token is kept only in memory within the current
   page load. The HttpOnly accessToken cookie (set by the server
   on login/refresh) authenticates page navigations and API calls
   when the in-memory token is absent. JS must never read the
   cookie directly — it is HttpOnly.
   ─────────────────────────────────────────────────────────── */
const TokenStore = (() => {
    let _token = null;

    function get() { return _token; }

    function set(token) { _token = token; }

    function clear() {
        _token = null;
        document.cookie = 'accessToken=; path=/; max-age=0; SameSite=Strict';
    }

    return { get, set, clear };
})();

/* ── Fetch wrapper ───────────────────────────────────────────
   Automatically attaches Authorization header when token exists.
   Handles 401 by redirecting to /login.
   ─────────────────────────────────────────────────────────── */
async function apiFetch(url, options = {}) {
    const token = TokenStore.get();
    const headers = Object.assign({}, options.headers || {});

    if (token) {
        headers['Authorization'] = 'Bearer ' + token;
    }

    if (!(options.body instanceof FormData) && !headers['Content-Type']) {
        if (options.body && typeof options.body === 'string') {
            headers['Content-Type'] = 'application/json';
        }
    }

    // Include CSRF token from XSRF-TOKEN cookie (set by Spring CookieCsrfTokenRepository)
    const xsrfMatch = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
    if (xsrfMatch) {
        headers['X-XSRF-TOKEN'] = decodeURIComponent(xsrfMatch[1]);
    }

    const response = await fetch(url, Object.assign({}, options, { headers, credentials: 'same-origin' }));

    if (response.status === 401) {
        TokenStore.clear();
        window.location.href = '/login';
        throw new Error('Unauthorized — redirecting to login');
    }

    return response;
}

/* ── Logout handler ─────────────────────────────────────────── */
function initLogout() {
    document.querySelectorAll('[data-action="logout"]').forEach(el => {
        el.addEventListener('click', async function (e) {
            e.preventDefault();
            try {
                await apiFetch('/api/auth/logout', { method: 'POST' });
            } catch (_) { /* ignore network errors */ }
            TokenStore.clear();
            window.location.href = '/login';
        });
    });
}

/* ── Debounce ────────────────────────────────────────────────── */
function debounce(fn, ms) {
    let timer;
    return function (...args) {
        clearTimeout(timer);
        timer = setTimeout(() => fn.apply(this, args), ms);
    };
}

/* ── Autocomplete ────────────────────────────────────────────── */
function initAutocomplete(inputEl, listEl, endpoint) {
    if (!inputEl || !listEl) return;

    let activeIndex = -1;

    const fetchSuggestions = debounce(async function (q) {
        if (!q || q.length < 2) {
            closeList();
            return;
        }
        try {
            const res = await apiFetch(`${endpoint}?q=${encodeURIComponent(q)}&limit=8`);
            if (!res.ok) { closeList(); return; }
            const suggestions = await res.json(); // expected: string[]
            renderList(suggestions, q);
        } catch (_) { closeList(); }
    }, 280);

    inputEl.addEventListener('input', function () {
        activeIndex = -1;
        fetchSuggestions(this.value.trim());
    });

    inputEl.addEventListener('keydown', function (e) {
        const items = listEl.querySelectorAll('.autocomplete-item');
        if (!items.length) return;

        if (e.key === 'ArrowDown') {
            e.preventDefault();
            activeIndex = Math.min(activeIndex + 1, items.length - 1);
            highlightItem(items);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            activeIndex = Math.max(activeIndex - 1, 0);
            highlightItem(items);
        } else if (e.key === 'Enter' && activeIndex >= 0) {
            e.preventDefault();
            items[activeIndex].click();
        } else if (e.key === 'Escape') {
            closeList();
        }
    });

    document.addEventListener('click', function (e) {
        if (!inputEl.contains(e.target) && !listEl.contains(e.target)) {
            closeList();
        }
    });

    function highlightItem(items) {
        items.forEach((it, i) => it.classList.toggle('highlighted', i === activeIndex));
    }

    function escHtml(str) {
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function renderList(suggestions, q) {
        listEl.innerHTML = '';
        if (!suggestions.length) { closeList(); return; }
        suggestions.forEach(s => {
            const div = document.createElement('div');
            div.className = 'autocomplete-item';
            // Bold the matching part — escape suggestion text first to prevent XSS
            const escaped = escHtml(s);
            const esc = q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
            div.innerHTML = escaped.replace(new RegExp(`(${esc})`, 'i'), '<strong>$1</strong>');
            div.addEventListener('click', function () {
                inputEl.value = s;
                closeList();
                inputEl.dispatchEvent(new Event('autocomplete-select', { bubbles: true }));
            });
            listEl.appendChild(div);
        });
        listEl.classList.add('open');
    }

    function closeList() {
        listEl.innerHTML = '';
        listEl.classList.remove('open');
        activeIndex = -1;
    }
}

/* ── Star rating click handler ───────────────────────────────── */
function initStarRating(containerEl) {
    if (!containerEl) return;
    // Works with the CSS-only reverse flex approach:
    // <div class="star-rating">
    //   <input type="radio" id="s5" name="rating" value="5"><label for="s5"></label>
    //   ... (5 down to 1)
    // </div>
    // No JS needed for CSS-only version, but this adds accessible keyboard support.
    const inputs = containerEl.querySelectorAll('input[type="radio"]');
    inputs.forEach(input => {
        input.addEventListener('change', function () {
            containerEl.dataset.value = this.value;
        });
    });
}

/* ── Image upload validation ─────────────────────────────────── */
function initImageUpload(uploadZoneEl, previewsEl, hiddenInputEl) {
    if (!uploadZoneEl) return;

    const MAX_FILES = 5;
    const MAX_SIZE  = 3 * 1024 * 1024; // 3 MB
    const ALLOWED   = ['image/jpeg', 'image/png'];

    let files = [];

    const fileInput = uploadZoneEl.querySelector('input[type="file"]');
    if (!fileInput) return;

    fileInput.addEventListener('change', handleFiles);

    uploadZoneEl.addEventListener('dragover', e => {
        e.preventDefault();
        uploadZoneEl.classList.add('drag-over');
    });
    uploadZoneEl.addEventListener('dragleave', () => uploadZoneEl.classList.remove('drag-over'));
    uploadZoneEl.addEventListener('drop', e => {
        e.preventDefault();
        uploadZoneEl.classList.remove('drag-over');
        handleFiles({ target: { files: e.dataTransfer.files } });
    });

    function handleFiles(e) {
        const incoming = Array.from(e.target.files || []);
        const errors = [];

        incoming.forEach(f => {
            if (files.length >= MAX_FILES) {
                errors.push(`Maximum ${MAX_FILES} images allowed.`);
                return;
            }
            if (!ALLOWED.includes(f.type)) {
                errors.push(`${f.name}: only JPG and PNG are accepted.`);
                return;
            }
            if (f.size > MAX_SIZE) {
                errors.push(`${f.name}: exceeds 3 MB limit.`);
                return;
            }
            files.push(f);
        });

        if (errors.length) {
            showUploadError(errors.join(' '));
        }

        renderPreviews();
        syncHiddenInput();
        fileInput.value = '';
    }

    function renderPreviews() {
        if (!previewsEl) return;
        previewsEl.innerHTML = '';
        files.forEach((f, i) => {
            const wrap = document.createElement('div');
            wrap.className = 'upload-preview-item';

            const img = document.createElement('img');
            img.src = URL.createObjectURL(f);
            img.alt = f.name;
            wrap.appendChild(img);

            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'upload-preview-remove';
            btn.innerHTML = '&times;';
            btn.setAttribute('aria-label', 'Remove image');
            btn.addEventListener('click', () => {
                files.splice(i, 1);
                renderPreviews();
                syncHiddenInput();
            });
            wrap.appendChild(btn);
            previewsEl.appendChild(wrap);
        });
    }

    function syncHiddenInput() {
        if (!hiddenInputEl) return;
        const dt = new DataTransfer();
        files.forEach(f => dt.items.add(f));
        hiddenInputEl.files = dt.files;
    }

    function showUploadError(msg) {
        const errEl = uploadZoneEl.closest('.form-group')?.querySelector('.form-error');
        if (errEl) {
            errEl.textContent = msg;
            errEl.classList.add('visible');
            setTimeout(() => errEl.classList.remove('visible'), 4000);
        } else {
            alert(msg);
        }
    }

    // Expose for external use
    uploadZoneEl._getFiles = () => files;
}

/* ── Canvas signature pad ────────────────────────────────────── */
function initSignaturePad(canvasEl, clearBtnEl, placeholder) {
    if (!canvasEl) return null;

    const ctx = canvasEl.getContext('2d');
    let drawing = false;
    let hasStrokes = false;

    // Match canvas logical size to its CSS display size
    function resize() {
        const rect = canvasEl.getBoundingClientRect();
        // Preserve existing drawing
        const imgData = ctx.getImageData(0, 0, canvasEl.width, canvasEl.height);
        canvasEl.width  = rect.width  || 600;
        canvasEl.height = rect.height || 240;
        ctx.putImageData(imgData, 0, 0);
        ctx.strokeStyle = '#111';
        ctx.lineWidth   = 2;
        ctx.lineCap     = 'round';
        ctx.lineJoin    = 'round';
    }

    resize();

    function getPos(e) {
        const rect = canvasEl.getBoundingClientRect();
        const src  = e.touches ? e.touches[0] : e;
        return {
            x: (src.clientX - rect.left) * (canvasEl.width  / rect.width),
            y: (src.clientY - rect.top)  * (canvasEl.height / rect.height)
        };
    }

    function startDraw(e) {
        e.preventDefault();
        drawing = true;
        const p = getPos(e);
        ctx.beginPath();
        ctx.moveTo(p.x, p.y);
    }

    function draw(e) {
        if (!drawing) return;
        e.preventDefault();
        const p = getPos(e);
        ctx.lineTo(p.x, p.y);
        ctx.stroke();
        hasStrokes = true;
        if (placeholder) placeholder.classList.add('hidden');
    }

    function endDraw() { drawing = false; }

    // Mouse events
    canvasEl.addEventListener('mousedown',  startDraw);
    canvasEl.addEventListener('mousemove',  draw);
    canvasEl.addEventListener('mouseup',    endDraw);
    canvasEl.addEventListener('mouseleave', endDraw);

    // Touch events
    canvasEl.addEventListener('touchstart', startDraw, { passive: false });
    canvasEl.addEventListener('touchmove',  draw,      { passive: false });
    canvasEl.addEventListener('touchend',   endDraw);

    // Clear button
    if (clearBtnEl) {
        clearBtnEl.addEventListener('click', function () {
            ctx.clearRect(0, 0, canvasEl.width, canvasEl.height);
            hasStrokes = false;
            if (placeholder) placeholder.classList.remove('hidden');
        });
    }

    window.addEventListener('resize', debounce(resize, 200));

    return {
        isEmpty:  () => !hasStrokes,
        toPNG:    () => canvasEl.toDataURL('image/png'),
        toBlob:   (cb) => canvasEl.toBlob(cb, 'image/png'),
        clear:    () => clearBtnEl && clearBtnEl.click()
    };
}

/* ── Appointment slot loader ─────────────────────────────────── */
function initSlotLoader(dateInputEl, typeGroupEl, gridEl, hiddenSlotEl) {
    if (!dateInputEl || !gridEl) return;

    async function loadSlots() {
        const date = dateInputEl.value;
        if (!date) return;

        const type = typeGroupEl
            ? (typeGroupEl.querySelector('.toggle-option.active')?.dataset.value || 'OFFLINE')
            : 'OFFLINE';

        gridEl.innerHTML = '<div class="text-muted mono" style="font-size:.7rem;padding:.5rem;">Loading slots…</div>';

        try {
            const res = await apiFetch(
                `/api/appointments/available?date=${encodeURIComponent(date)}&type=${encodeURIComponent(type)}`
            );
            if (!res.ok) {
                let msg = 'Failed to load slots.';
                try {
                    const err = await res.json();
                    // Surface the server's validation message (e.g. 2-hour minimum, 14-day max)
                    msg = err.message || msg;
                } catch (_) {}
                gridEl.innerHTML = '<div class="text-error mono" style="font-size:.7rem;padding:.5rem;">' +
                    msg.replace(/</g, '&lt;') + '</div>';
                return;
            }
            const slots = await res.json();
            renderSlots(slots);
        } catch (_) {
            gridEl.innerHTML = '<div class="text-error mono" style="font-size:.7rem;padding:.5rem;">Network error loading slots. Please try again.</div>';
        }
    }

    function renderSlots(slots) {
        gridEl.innerHTML = '';
        if (!slots.length) {
            gridEl.innerHTML = '<div class="text-muted mono" style="font-size:.7rem;padding:.5rem;">No slots available.</div>';
            return;
        }
        slots.forEach(s => {
            const div = document.createElement('div');
            div.className = 'slot-item' + (s.available === false ? ' slot-taken' : '');
            div.textContent = formatSlotTime(s.startTime) + ' – ' + formatSlotTime(s.endTime);
            div.dataset.slotId = s.id;
            if (s.available !== false) {
                div.addEventListener('click', function () {
                    gridEl.querySelectorAll('.slot-item').forEach(el => el.classList.remove('slot-selected'));
                    this.classList.add('slot-selected');
                    if (hiddenSlotEl) hiddenSlotEl.value = s.id;
                });
            }
            gridEl.appendChild(div);
        });
    }

    function formatSlotTime(t) {
        if (!t) return '';
        // t may be "HH:mm:ss" or ISO
        const parts = t.split(':');
        if (parts.length >= 2) {
            let h = parseInt(parts[0], 10);
            const m = parts[1].padStart(2, '0');
            const ampm = h >= 12 ? 'PM' : 'AM';
            h = h % 12 || 12;
            return `${h}:${m} ${ampm}`;
        }
        return t;
    }

    dateInputEl.addEventListener('change', loadSlots);

    if (typeGroupEl) {
        typeGroupEl.querySelectorAll('.toggle-option').forEach(btn => {
            btn.addEventListener('click', function () {
                typeGroupEl.querySelectorAll('.toggle-option').forEach(b => b.classList.remove('active'));
                this.classList.add('active');
                loadSlots();
            });
        });
    }
}

/* ── Click tracking ─────────────────────────────────────────── */
function initClickTracking(containerEl) {
    if (!containerEl) return;
    containerEl.addEventListener('click', function (e) {
        const card = e.target.closest('[data-item-id]');
        if (!card) return;
        const itemId = card.dataset.itemId;
        // Include search session context if available (set on the results grid by server)
        const searchLogId = containerEl.dataset.searchLogId;
        const payload = { itemId: parseInt(itemId) };
        if (searchLogId) {
            payload.searchLogId = parseInt(searchLogId);
        }
        apiFetch('/api/catalog/click', {
            method: 'POST',
            body: JSON.stringify(payload)
        }).catch(() => {});
    });
}

/* ── Form validation helper ─────────────────────────────────── */
function validateForm(formEl) {
    let valid = true;
    formEl.querySelectorAll('[required]').forEach(field => {
        const val = field.value.trim();
        const errEl = field.closest('.form-group')?.querySelector('.form-error');
        if (!val) {
            valid = false;
            if (errEl) {
                errEl.textContent = 'This field is required.';
                errEl.classList.add('visible');
            } else {
                field.style.borderColor = 'var(--error)';
            }
        } else {
            if (errEl) errEl.classList.remove('visible');
            field.style.borderColor = '';
        }
    });
    return valid;
}

/* ── Alert helper ───────────────────────────────────────────── */
function showAlert(containerEl, message, type = 'error') {
    if (!containerEl) return;
    containerEl.className = `alert alert-${type}`;
    containerEl.textContent = message;
    containerEl.classList.remove('hidden');
    containerEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function hideAlert(containerEl) {
    if (!containerEl) return;
    containerEl.classList.add('hidden');
}

/* ── Password complexity hints ──────────────────────────────── */
function initPasswordHints(inputEl, hintsContainer) {
    if (!inputEl || !hintsContainer) return;

    const rules = [
        { id: 'len',    label: 'At least 12 characters',       test: v => v.length >= 12 },
        { id: 'upper',  label: 'One uppercase letter',          test: v => /[A-Z]/.test(v) },
        { id: 'lower',  label: 'One lowercase letter',          test: v => /[a-z]/.test(v) },
        { id: 'digit',  label: 'One number',                    test: v => /\d/.test(v) },
        { id: 'special', label: 'One special character',        test: v => /[^A-Za-z0-9]/.test(v) }
    ];

    // Build hint items
    hintsContainer.innerHTML = '';
    rules.forEach(r => {
        const div = document.createElement('div');
        div.className = 'pw-hint';
        div.id = 'hint-' + r.id;
        div.innerHTML = `<span class="pw-hint-icon">✗</span><span>${r.label}</span>`;
        hintsContainer.appendChild(div);
    });

    inputEl.addEventListener('input', function () {
        const val = this.value;
        rules.forEach(r => {
            const el = hintsContainer.querySelector('#hint-' + r.id);
            if (!el) return;
            const ok = r.test(val);
            el.classList.toggle('valid', ok);
            el.querySelector('.pw-hint-icon').textContent = ok ? '✓' : '✗';
        });
    });
}

/* ── Char counter ───────────────────────────────────────────── */
function initCharCounter(textareaEl, counterEl, max) {
    if (!textareaEl || !counterEl) return;

    function update() {
        const len = textareaEl.value.length;
        const remaining = max - len;
        counterEl.textContent = `${len} / ${max}`;
        counterEl.classList.toggle('near-limit', remaining <= max * 0.1 && remaining > 0);
        counterEl.classList.toggle('at-limit',   remaining <= 0);
    }

    textareaEl.addEventListener('input', update);
    update();
}

/* ── Toggle password visibility ─────────────────────────────── */
function initTogglePassword(toggleBtn, inputEl) {
    if (!toggleBtn || !inputEl) return;
    toggleBtn.addEventListener('click', function () {
        if (inputEl.type === 'password') {
            inputEl.type = 'text';
            this.setAttribute('aria-label', 'Hide password');
        } else {
            inputEl.type = 'password';
            this.setAttribute('aria-label', 'Show password');
        }
    });
}

/* ── DOM-ready initialisation ───────────────────────────────── */
document.addEventListener('DOMContentLoaded', function () {
    initLogout();

    // Generic per-page inits based on data attributes
    const searchInput   = document.getElementById('searchKeyword');
    const acList        = document.getElementById('autocompleteList');
    if (searchInput && acList) {
        initAutocomplete(searchInput, acList, '/api/search/autocomplete');
    }

    const starContainer = document.querySelector('.star-rating');
    if (starContainer) initStarRating(starContainer);

    const uploadZone    = document.getElementById('uploadZone');
    const uploadPreviews = document.getElementById('uploadPreviews');
    const uploadInput   = document.getElementById('imagesInput');
    if (uploadZone)  initImageUpload(uploadZone, uploadPreviews, uploadInput);

    const sigCanvas     = document.getElementById('signatureCanvas');
    const sigClear      = document.getElementById('signatureClear');
    const sigPlaceholder = document.getElementById('signaturePlaceholder');
    if (sigCanvas) {
        window._signaturePad = initSignaturePad(sigCanvas, sigClear, sigPlaceholder);
    }

    const dateInput     = document.getElementById('appointmentDate');
    const typeGroup     = document.getElementById('appointmentTypeGroup');
    const slotGrid      = document.getElementById('slotGrid');
    const hiddenSlot    = document.getElementById('selectedSlotId');
    if (dateInput && slotGrid) {
        initSlotLoader(dateInput, typeGroup, slotGrid, hiddenSlot);
    }

    const resultsGrid   = document.getElementById('resultsGrid');
    if (resultsGrid)    initClickTracking(resultsGrid);

    const reviewTextarea  = document.getElementById('reviewContent');
    const reviewCounter   = document.getElementById('reviewCounter');
    if (reviewTextarea)   initCharCounter(reviewTextarea, reviewCounter, 1000);

    const newPasswordInput = document.getElementById('newPassword');
    const pwHints          = document.getElementById('pwHints');
    if (newPasswordInput && pwHints) {
        initPasswordHints(newPasswordInput, pwHints);
    }
});
