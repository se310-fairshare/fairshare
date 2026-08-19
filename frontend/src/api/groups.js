import {API_BASE} from "./config.js";

async function readError(response, fallback) {
    try {
        const body = await response.json();
        return body.error || Object.values(body)[0] || fallback;
    } catch {
        return fallback;
    }
}

export async function createGroup(name, description) {
    const response = await fetch(`${API_BASE}/groups`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ name, description })
    });

    if (response.status === 400) {
        return { errors: await response.json() };
    }
    if (!response.ok) {
        throw new Error(`Failed to create group: ${response.status}`);
    }
    return { group: await response.json() };
}

export async function getGroups() {
    const response = await fetch(`${API_BASE}/groups`, { credentials: 'include' });
    if (!response.ok) throw new Error(`Failed to load groups: ${response.status}`);
    return response.json();
}

export async function getGroup(id) {
    const response = await fetch(`${API_BASE}/groups/${id}`, { credentials: 'include' });
    if (response.status === 404) return null;
    if (!response.ok) throw new Error(`Failed to load group: ${response.status}`);
    return response.json();
}

export async function getGroupMembers(id) {
    const response = await fetch(`${API_BASE}/groups/${id}/members`, {
        credentials: 'include'
    });
    if (!response.ok) {
        return { error: await readError(response, 'Could not load group members.') };
    }
    return { members: await response.json() };
}

export async function addGroupMember(id, identifier) {
    const response = await fetch(`${API_BASE}/groups/${id}/members`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ identifier })
    });
    if (!response.ok) {
        return { error: await readError(response, 'Could not add this member.') };
    }
    return { member: await response.json() };
}

export async function removeGroupMember(id, userId) {
    const response = await fetch(`${API_BASE}/groups/${id}/members/${userId}`, {
        method: 'DELETE',
        credentials: 'include'
    });
    if (!response.ok) {
        return { error: await readError(response, 'Could not remove this member.') };
    }
    return {};
}
