import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import ViewBalance from '../src/pages/ViewBalance.jsx';
import { getGroup, getGroupMembers } from '../src/api/groups';

vi.mock('../src/api/groups', () => ({
    createGroup: vi.fn(),
    getGroups: vi.fn(),
    getGroup: vi.fn(),
    getGroupMembers: vi.fn(),
    getGroupBalances: vi.fn(),
    computeSettlement: vi.fn(),
}));

const GROUP = {
    id: 1,
    name: 'Flat 3',
    description: null,
    baseCurrency: 'NZD',
    createdAt: '2026-08-16T00:00:00Z',
    memberCount: 3,
};

function renderPage() {
    render(
        <MemoryRouter initialEntries={['/groups/1/balance']}>
            <Routes>
                <Route path="/groups/:id/balance" element={<ViewBalance />} />
            </Routes>
        </MemoryRouter>
    );
}


beforeEach(() => {
    vi.clearAllMocks();
    getGroup.mockResolvedValue(GROUP);
    getGroupMembers.mockResolvedValue({
        members: [
            { userId: 1, username: 'alice', netBalance: '12.50' },
            { userId: 2, username: 'bob', netBalance: '-5.25' },
            { userId: 3, username: 'carol', netBalance: '0.00' },
        ],
    });
});

it('renders each member balance with correct text, class and link', async () => {
    renderPage();

    expect(await screen.findByText('Balances')).toBeInTheDocument();
    expect(screen.getByText('Flat 3')).toBeInTheDocument();

    const positive = screen.getByText('+ NZD 12.50');
    const negative = screen.getByText('- NZD 5.25');
    const neutral = screen.getByText('is settled up');

    expect(positive).toHaveClass('balance-Positive');
    expect(negative).toHaveClass('balance-Negative');
    expect(neutral).toHaveClass('balance-Neutral');

    expect(screen.getByRole('link', { name: 'alice' })).toHaveAttribute('href', '/groups/1/balance/1');
    expect(screen.getByRole('link', { name: 'bob' })).toHaveAttribute('href', '/groups/1/balance/2');
    expect(screen.getByRole('link', { name: 'carol' })).toHaveAttribute('href', '/groups/1/balance/3');
});

it('shows API error from group members request', async () => {
    getGroupMembers.mockResolvedValue({ error: 'Could not load group members.' });

    renderPage();

    expect(await screen.findByText('Could not load group members.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to group' })).toHaveAttribute('href', '/groups/1');
});

it('shows settled state when member net balance is zero', async () => {
    getGroupMembers.mockResolvedValue({
        members: [{ userId: 3, username: 'carol', netBalance: '0.00' }],
    });

    renderPage();

    expect(await screen.findByText('carol')).toBeInTheDocument();
    const neutral = screen.getByText('is settled up');
    expect(neutral).toHaveClass('balance-Neutral');
});

it('shows fallback error when request throws', async () => {
    getGroup.mockRejectedValue(new Error('Network error'));

    renderPage();

    expect(await screen.findByText('Could not load the group balance. Please try again.')).toBeInTheDocument();
});

