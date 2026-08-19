import { afterEach, expect, it, vi } from 'vitest';
import { removeGroupMember } from '../src/api/groups';

afterEach(() => {
    vi.unstubAllGlobals();
});

it('constructs the member URL from validated numeric identifiers', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal('fetch', fetchMock);

    await removeGroupMember('5', 2);

    expect(fetchMock).toHaveBeenCalledWith(
        'http://localhost:8080/groups/5/members/2',
        { method: 'DELETE', credentials: 'include' });
});

it.each([
    ['../admin', 2, 'Group ID must be a positive integer'],
    [5, '../admin', 'Member ID must be a positive integer'],
    [5, Number.MAX_SAFE_INTEGER + 1, 'Member ID must be a positive integer'],
])('rejects unsafe route identifiers before sending a request',
    async (groupId, memberId, message) => {
        const fetchMock = vi.fn();
        vi.stubGlobal('fetch', fetchMock);

        await expect(removeGroupMember(groupId, memberId)).rejects.toThrow(message);
        expect(fetchMock).not.toHaveBeenCalled();
    });
