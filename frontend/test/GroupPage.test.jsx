import { it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import GroupPage from '../src/pages/GroupPage.jsx';
import { getGroup, getGroupBalances, getGroupMembers } from '../src/api/groups';
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
    memberCount: 2,
};

beforeEach(() => {
    vi.clearAllMocks();
    getGroup.mockResolvedValue(GROUP);
    getExpenses.mockResolvedValue({ expenses: [] });
    getGroupBalances.mockResolvedValue([]);
    getGroupMembers.mockResolvedValue({
        members: [
            { userId: 1, username: 'alice', email: 'alice@test.com', netBalance: '0.00', currentUser: true },
        ],
    });
});

function renderPage() {
    render(
        <MemoryRouter initialEntries={['/groups/1']}>
            <Routes>
                <Route path="/groups/:id" element={<GroupPage />} />
            </Routes>
        </MemoryRouter>
    );
}

it('AC2: a new group lists no expenses and shows zero balances', async () => {
    renderPage();

    expect(await screen.findByText('Flat 3')).toBeInTheDocument();
    expect(screen.getByText('No expenses yet.')).toBeInTheDocument();
    expect(screen.getByText(/NZD 0\.00/)).toBeInTheDocument();
});

it('AC7: lists each expense with amount, description, payer and date', async () => {
    getExpenses.mockResolvedValue({
        expenses: [
            {
                id: 2, groupId: 1, paidByUserId: 2, paidByUsername: 'bob',
                amount: '20.00', description: 'Pizza', expenseDate: '2026-08-18',
                createdAt: '2026-08-18T00:00:00Z',
            },
            {
                id: 1, groupId: 1, paidByUserId: 1, paidByUsername: 'alice',
                amount: '10.00', description: 'Taxi', expenseDate: '2026-08-16',
                createdAt: '2026-08-16T00:00:00Z',
            },
        ],
    });

    renderPage();

    expect(await screen.findByText('Pizza')).toBeInTheDocument();
    expect(screen.getByText('bob paid on 2026-08-18')).toBeInTheDocument();
    expect(screen.getByText('NZD 20.00')).toBeInTheDocument();
    expect(screen.getByText('Taxi')).toBeInTheDocument();
    expect(screen.getByText('alice paid on 2026-08-16')).toBeInTheDocument();
    expect(screen.getByText('NZD 10.00')).toBeInTheDocument();
});

it('AC1: shows what each member is owed or owes', async () => {
    getGroupMembers.mockResolvedValue({
        members: [
            { userId: 1, username: 'alice', email: 'alice@test.com', netBalance: '-21.25', currentUser: true },
            { userId: 2, username: 'bob', email: 'bob@test.com', netBalance: '21.25', currentUser: false },
        ],
    });

    renderPage();

    expect(await screen.findByText('alice owes NZD 21.25')).toBeInTheDocument();
    expect(screen.getByText('bob is owed NZD 21.25')).toBeInTheDocument();
});

it('AC8: shows a not-found message when the user is not a member', async () => {
    getGroup.mockResolvedValue(null);

    renderPage();

    expect(await screen.findByText('Group not found')).toBeInTheDocument();
    expect(screen.queryByText('No expenses yet.')).not.toBeInTheDocument();
});
