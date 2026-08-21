import { API_BASE, apiFetch } from './config';

export async function getCurrentUser() {
    // Deliberately not using apiFetch: a 403 here means "not logged in", which is a
    // normal answer, not a reason to redirect. Landing depends on this returning null.
    const response = await fetch(`${API_BASE}/users/me`, {
        credentials: 'include'
    });
    if (response.status === 401) {
        return null;                       // not logged in
    }
    if (!response.ok) {
        throw new Error(`Failed to load profile: ${response.status}`);
    }
    const result = await response.json();
    return result.user;
}

export async function updateCurrentUser(payload) {
    const response = await apiFetch('/users/me', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    if (!response.ok) {
        throw new Error(await response.text() || 'Profile update failed');
    }
    const result = await response.json();
    return result.user;
}

export async function logout() {
    await apiFetch('/users/logout', {method: 'POST'});
}