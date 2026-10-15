/* ==========================================================================
   fr-api.js — shared client for the five functional-requirement endpoints.
   All live DB access in the frontend goes through here.
   Backend: GET /api/fr?fr=1..5  ->  { columns, rows, rowCount, title, sql, error? }
   ========================================================================== */

/**
 * Fetch one functional requirement.
 * @param {number} fr      1..5
 * @param {object} params  optional query params (city, space_type, space_id)
 * @returns {Promise<object>} parsed JSON payload
 */
async function fetchFr(fr, params = {}) {
    const qs = new URLSearchParams({ fr: String(fr), ...params });
    const res = await fetch('api/fr?' + qs.toString());
    // The servlet returns JSON (with an "error" field) even on 4xx/5xx.
    let data;
    try {
        data = await res.json();
    } catch (e) {
        return { error: 'Server returned a non-JSON response (status ' + res.status + ').' };
    }
    return data;
}

/** Escape untrusted DB values before inserting into HTML. */
function esc(s) {
    if (s === null || s === undefined) return '';
    return String(s)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

/** Render a {columns, rows} payload as a styled data table into `el`. */
function renderTable(el, data) {
    if (data.error) { el.innerHTML = '<p class="err-state">' + esc(data.error) + '</p>'; return; }
    let html = '<p class="result-meta">' + data.rowCount + ' row(s)</p>';
    if (!data.rows || data.rows.length === 0) {
        html += '<p class="empty-state">No matching rows.</p>';
    } else {
        html += '<div class="table-wrap"><table class="data"><thead><tr>';
        data.columns.forEach(c => html += '<th>' + esc(c) + '</th>');
        html += '</tr></thead><tbody>';
        data.rows.forEach(row => {
            html += '<tr>';
            data.columns.forEach(c => html += '<td>' + esc(row[c]) + '</td>');
            html += '</tr>';
        });
        html += '</tbody></table></div>';
    }
    html += sqlDetails(data.sql);
    el.innerHTML = html;
}

/**
 * Render rows as cards using a template function.
 * @param {HTMLElement} el
 * @param {object} data        the FR payload
 * @param {(row:object)=>string} cardTemplate returns inner HTML for one card
 * @param {object} [opts]      { showSql: boolean }
 */
function renderCards(el, data, cardTemplate, opts = {}) {
    if (data.error) { el.innerHTML = '<p class="err-state">' + esc(data.error) + '</p>'; return; }
    let html = '<p class="result-meta">' + data.rowCount + ' result(s)</p>';
    if (!data.rows || data.rows.length === 0) {
        html += '<p class="empty-state">No matching results.</p>';
    } else {
        html += '<div class="card-grid">';
        data.rows.forEach(row => html += cardTemplate(row));
        html += '</div>';
    }
    if (opts.showSql !== false) html += sqlDetails(data.sql);
    el.innerHTML = html;
}

function sqlDetails(sql) {
    if (!sql) return '';
    return '<details class="sql"><summary>Show SQL</summary><pre>' + esc(sql) + '</pre></details>';
}

/** "2026-10-01T08:00" / "2026-10-01 08:00:00" -> "Oct 1, 8:00 AM". */
function formatDateTime(raw) {
    if (!raw) return '';
    const d = new Date(String(raw).replace(' ', 'T'));
    if (isNaN(d.getTime())) return esc(raw);
    return d.toLocaleString('en-US',
        { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });
}

/** Pick a status-pill CSS class for an availability/status value. */
function statusClass(status) {
    const s = String(status || '').toUpperCase();
    if (s === 'AVAILABLE' || s === 'OPEN_PLAY') return 'green';
    if (s === 'OCCUPIED' || s === 'RESERVED') return 'red';
    if (s === 'CLOSED') return 'slate';
    return 'amber';
}
