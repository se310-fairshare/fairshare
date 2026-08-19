import {API_BASE} from "./config.js";

async function readError(response, fallback) {
    try {
        const body = await response.json();
        return body.error || Object.values(body)[0] || fallback;
    } catch {
        return fallback;
    }
}

function requirePositiveInteger(value, label) {
    const text = String(value);
    if (!/^[1-9]\d*$/.test(text)) {
        throw new TypeError(`${label} must be a positive integer`);
    }

    const number = Number(text);
    if (!Number.isSafeInteger(number)) {
        throw new TypeError(`${label} must be a positive integer`);
    }
    return number;
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
    const groupId = requirePositiveInteger(id, 'Group ID');
    const response = await fetch(`${API_BASE}/groups/${groupId}`, { credentials: 'include' });
    if (response.status === 404) return null;
    if (!response.ok) throw new Error(`Failed to load group: ${response.status}`);
    return response.json();
}

export async function getGroupMembers(id) {
    const groupId = requirePositiveInteger(id, 'Group ID');
    const response = await fetch(`${API_BASE}/groups/${groupId}/members`, {
        credentials: 'include'
    });
    if (!response.ok) {
        return { error: await readError(response, 'Could not load group members.') };
    }
    return { members: await response.json() };
}

export async function addGroupMember(id, identifier) {
    const groupId = requirePositiveInteger(id, 'Group ID');
    const response = await fetch(`${API_BASE}/groups/${groupId}/members`, {
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
    const groupId = requirePositiveInteger(id, 'Group ID');
    const memberId = requirePositiveInteger(userId, 'Member ID');
    const response = await fetch(`${API_BASE}/groups/${groupId}/members/${memberId}`, {
        method: 'DELETE',
        credentials: 'include'
    });
    if (!response.ok) {
        return { error: await readError(response, 'Could not remove this member.') };
    }
    return {};
}
