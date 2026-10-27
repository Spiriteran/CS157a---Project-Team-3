/* Shared account form behavior for login.html and register.html. */
(function () {
    const form = document.getElementById('login-form') || document.getElementById('register-form');
    if (!form) return;
    const message = document.getElementById('msg');
    form.addEventListener('submit', async function (event) {
        event.preventDefault();
        const submit = form.querySelector('button[type="submit"]');
        submit.disabled = true;
        message.className = 'form-message';
        message.textContent = 'Working…';
        const values = Object.fromEntries(new FormData(form).entries());
        values.action = form.id === 'login-form' ? 'login' : 'register';
        try {
            const response = await fetch('api/auth', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(values)
            });
            const data = await response.json();
            message.className = 'form-message ' + (data.ok ? 'success' : 'error');
            message.textContent = data.message || 'Request completed.';
            if (data.ok && values.action === 'login') {
                const next = new URLSearchParams(window.location.search).get('next') || 'home.html';
                setTimeout(() => { window.location.href = next; }, 500);
            }
            if (data.ok && values.action === 'register') {
                setTimeout(() => { window.location.href = 'login.html'; }, 800);
            }
        } catch (error) {
            message.className = 'form-message error';
            message.textContent = 'Could not reach the account service. Please try again.';
        } finally { submit.disabled = false; }
    });
}());
