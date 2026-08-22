import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import MemberBalance from '../src/pages/MemberBalance.jsx';
import { getGroup, getGroupMembers } from '../src/api/groups';
import { getExpenses } from '../src/api/expenses';

vi.mock('../src/api/groups', () => ({
    createGroup: vi.fn(),
    getGroups: vi.fn(),
    getGroup: vi.fn(),
    getGroupMembers: vi.fn(),
    getGroupBalances: vi.fn(),
    computeSettlement: vi.fn(),
}));

vi.mock('../src/api/expenses', () => ({
    getExpenses: vi.fn(),
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
        <MemoryRouter initialEntries={['/groups/1/balance/2']}>
            <Routes>
                <Route path="/groups/:id/balance/:memberId" element={<MemberBalance />} />
            </Routes>
        </MemoryRouter>
    );
}


beforeEach(() => {
    vi.clearAllMocks();
    getGroup.mockResolvedValue(GROUP);
    getGroupMembers.mockResolvedValue({
        members: [
            { userId: 1, username: 'alice', netBalance: '-10.50' },
            { userId: 2, username: 'bob', netBalance: '10.50' },
        ],
    });
    getExpenses.mockResolvedValue({
        expenses: [
            {
                id: 1,
                groupId: 1,
                paidByUserId: 2,
                paidByUsername: 'bob',
                amount: '30.00',
                description: 'Groceries',
                expenseDate: '2026-08-19',
            },
            {
                id: 2,
                groupId: 1,
                paidByUserId: 1,
                paidByUsername: 'alice',
                amount: '15.00',
                description: 'Taxi',
                expenseDate: '2026-08-20',
            },
        ],
    });
});

it('renders selected member balance and only their transactions', async () => {
    renderPage();

    expect(await screen.findByText('bob')).toBeInTheDocument();
    const balance = screen.getByText('+ NZD 10.50');
    expect(balance).toHaveClass('balance-Positive');

    expect(screen.getByText('Groceries')).toBeInTheDocument();
    expect(screen.getByText('bob paid on 2026-08-19')).toBeInTheDocument();
    expect(screen.getByText('NZD 30.00')).toBeInTheDocument();

    expect(screen.queryByText('Taxi')).not.toBeInTheDocument();
});

it('shows member-not-found message when route member is absent', async () => {
    getGroupMembers.mockResolvedValue({
        members: [{ userId: 1, username: 'alice', netBalance: '0.00' }],
    });

    renderPage();

    expect(await screen.findByText('This member could not be found in the group.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to balances' })).toHaveAttribute('href', '/groups/1/balance');
});

it('shows API error from members or expenses endpoint', async () => {
    getExpenses.mockResolvedValue({ error: 'Could not load expenses.' });

    renderPage();

    expect(await screen.findByText('Could not load expenses.')).toBeInTheDocument();
});

