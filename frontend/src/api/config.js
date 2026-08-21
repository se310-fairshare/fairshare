export const API_BASE = 'http://localhost:8080';

/**
 * Wraps fetch so the session cookie is always sent, and an unauthenticated response
 * sends the user to the login page rather than surfacing as a generic error.
 */
export async function apiFetch(path, options = {}){
    const response =await fetch(`${API_BASE}${path}`, { credentials: 'include', ...options });

    if (response.status === 401) {
        window.location.assign('/login');
        throw new Error('Not authenticated');
    }

    return response;
}