import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiFetch } from '../src/api/config.js';

describe('apiFetch', () => {
    let assign;
    let fetchMock;

    beforeEach(() => {
        assign = vi.fn();
        fetchMock = vi.fn();
        vi.stubGlobal('location', { assign });
        vi.stubGlobal('fetch', fetchMock);
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('sends the user to login when the request is unauthenticated', async () => {
        fetchMock.mockResolvedValue({ status: 401, ok: false });

        await expect(apiFetch('/groups')).rejects.toThrow('Not authenticated');
        expect(assign).toHaveBeenCalledWith('/login');
    });

    it('does not redirect when the user is logged in but lacks permission', async () => {
        const forbidden = { status: 403, ok: false };
        fetchMock.mockResolvedValue(forbidden);

        await expect(apiFetch('/groups/1/members')).resolves.toBe(forbidden);
        expect(assign).not.toHaveBeenCalled();
    });

    it('always sends the session cookie', async () => {
        fetchMock.mockResolvedValue({ status: 200, ok: true });

        await apiFetch('/groups');

        expect(fetchMock).toHaveBeenCalledWith(
            expect.stringContaining('/groups'),
            expect.objectContaining({ credentials: 'include' })
        );
    });
});