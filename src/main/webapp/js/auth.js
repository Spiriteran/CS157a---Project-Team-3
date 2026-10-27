/**
 * auth.js — client-side authentication and session management.
 * Injects session state into the site header and provides auth API helpers.
 */

const Auth = {
    _currentUser: null,

    async getMe() {
        if (this._currentUser) return this._currentUser;
        try {
            const res = await fetch('api/auth/me');
            const data = await res.json();
            if (data && data.authenticated && data.user) {
                this._currentUser = data.user;
                return this._currentUser;
            }
        } catch (e) {
            console.warn('Auth check failed:', e);
        }
        this._currentUser = null;
        return null;
    },

    async login(email, password) {
        const res = await fetch('api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });
        const data = await res.json();
        if (data.success && data.user) {
            this._currentUser = data.user;
        }
        return data;
    },

    async register(email, password, phone, role) {
        const res = await fetch('api/auth/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password, phone, role })
        });
        const data = await res.json();
        if (data.success && data.user) {
            this._currentUser = data.user;
        }
        return data;
    },

    async logout() {
        try {
            await fetch('api/auth/logout', { method: 'POST' });
        } finally {
            this._currentUser = null;
            window.location.href = 'index.html';
        }
    },

    async updateHeader() {
        const user = await this.getMe();
        const authHost = document.querySelector('.nav-auth');
        const navLinks = document.querySelector('.nav-links');

        if (user) {
            // Update nav-links to include Favorites
            if (navLinks && !document.getElementById('nav-favorites-link')) {
                const favLink = document.createElement('a');
                favLink.id = 'nav-favorites-link';
                favLink.href = 'favorites.html';
                favLink.textContent = 'Favorites';
                if (window.location.pathname.endsWith('favorites.html')) {
                    favLink.classList.add('active');
                }
                navLinks.appendChild(favLink);
            }

            // Update authHost with user menu
            if (authHost) {
                const roleLabel = (user.roleName === 'GENERAL_USER') ? 'General' :
                                  (user.roleName === 'RESERVATION_USER') ? 'Reservation' : 'Admin';
                authHost.innerHTML = `
                    <div class="user-chip">
                        <span class="user-chip-name">&#128100; ${this.esc(user.email)}</span>
                        <span class="badge slate">${this.esc(roleLabel)}</span>
                    </div>
                    <a class="btn btn-ghost" href="profile.html">Profile</a>
                    <button class="btn btn-ghost" onclick="Auth.logout()">Sign out</button>
                `;
            }
        } else {
            // Guest mode
            if (authHost) {
                authHost.innerHTML = `
                    <a class="btn btn-ghost" href="login.html">Sign in</a>
                    <a class="btn btn-primary" href="register.html">Create account</a>
                `;
            }
        }
    },

    esc(s) {
        if (!s) return '';
        return String(s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }
};

window.addEventListener('DOMContentLoaded', () => {
    Auth.updateHeader();
});
