import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import GroupsOverview from '../src/pages/GroupsOverview.jsx';
import { getGroups } from '../src/api/groups';

vi.mock('../src/api/groups', () => ({
    createGroup: vi.fn(),
    getGroups: vi.fn(),
    getGroup: vi.fn(),
}));

function renderPage(initialEntries = ['/groups']) {
    render(
        <MemoryRouter initialEntries={initialEntries}>
            <GroupsOverview />
        </MemoryRouter>
    );
}

describe('GroupsOverview', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('AC5: lists each group by name and links to its page', async () => {
        getGroups.mockResolvedValue([
            { id: 1, name: 'Flat 3', baseCurrency: 'NZD', createdAt: '2026-08-16T00:00:00Z', memberCount: 3 },
            { id: 2, name: 'Ski trip', baseCurrency: 'NZD', createdAt: '2026-08-15T00:00:00Z', memberCount: 5 },
        ]);

        renderPage();

        expect(await screen.findByText('Flat 3')).toBeInTheDocument();
        expect(screen.getByText('Ski trip')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: /Flat 3/ })).toHaveAttribute('href', '/groups/1');
        expect(screen.getByRole('link', { name: /Ski trip/ })).toHaveAttribute('href', '/groups/2');
    });

    it('AC6: shows enough detail to tell two groups with the same name apart', async () => {
        getGroups.mockResolvedValue([
            { id: 1, name: 'Trip', baseCurrency: 'NZD', createdAt: '2026-08-16T00:00:00Z', memberCount: 2 },
            { id: 2, name: 'Trip', baseCurrency: 'NZD', createdAt: '2026-01-02T00:00:00Z', memberCount: 6 },
        ]);

        renderPage();

        expect(await screen.findAllByText('Trip')).toHaveLength(2);

        const links = screen.getAllByRole('link', { name: /Trip/ });
        expect(links[0]).toHaveAttribute('href', '/groups/1');
        expect(links[1]).toHaveAttribute('href', '/groups/2');
        expect(links[0].textContent).not.toEqual(links[1].textContent);
    });

    it('shows an empty state when the user has no groups', async () => {
        getGroups.mockResolvedValue([]);

        renderPage();

        expect(await screen.findByText(/not in any groups yet/i)).toBeInTheDocument();
    });

    it('shows confirmation after the user leaves a group', async () => {
        getGroups.mockResolvedValue([]);

        renderPage([{
            pathname: '/groups',
            state: { notice: 'You left the group.' },
        }]);

        expect(await screen.findByRole('status')).toHaveTextContent(
            'You left the group.');
    });
});
