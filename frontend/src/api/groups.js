import { apiFetch } from './config.js';

export async function readError(response, fallback) {
    try {
        const body = await response.json();
        return body.error || Object.values(body)[0] || fallback;
    } catch {
        return fallback;
    }
}

export function requirePositiveInteger(value, label) {
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
    const response = await apiFetch('/groups', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
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
    const response = await apiFetch('/groups');
    if (!response.ok) throw new Error(`Failed to load groups: ${response.status}`);
    return response.json();
}

export async function getGroup(id) {
    const groupId = requirePositiveInteger(id, 'Group ID');
    const response = await apiFetch(`/groups/${groupId}`);
    if (response.status === 404) return null;
    if (!response.ok) throw new Error(`Failed to load group: ${response.status}`);
    return response.json();
}

export async function getGroupBalances(id) {
    const groupId = requirePositiveInteger(id, 'Group ID');
    const response = await apiFetch(`/groups/${groupId}/balances`);
    if (response.status === 404) return null;
    if (!response.ok) throw new Error(`Failed to load balances: ${response.status}`);
    return response.json();
}

export async function computeSettlement(id, balances) {
    const groupId = requirePositiveInteger(id, 'Group ID');
    const response = await apiFetch(`/groups/${groupId}/settlement`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ balances })
    });
    if (!response.ok) throw new Error(`Failed to compute settlement: ${response.status}`);
    return response.json();
}

export async function getGroupMembers(id) {
    const groupId = requirePositiveInteger(id, 'Group ID');
    const response = await apiFetch(`/groups/${groupId}/members`);
    if (!response.ok) {
        return { error: await readError(response, 'Could not load group members.') };
    }
    return { members: await response.json() };
}

export async function addGroupMember(id, identifier) {
    const groupId = requirePositiveInteger(id, 'Group ID');
    const response = await apiFetch(`/groups/${groupId}/members`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
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
    const response = await apiFetch(`/groups/${groupId}/members/${memberId}`, {
        method: 'DELETE'
    });
    if (!response.ok) {
        return { error: await readError(response, 'Could not remove this member.') };
    }
    return {};
}