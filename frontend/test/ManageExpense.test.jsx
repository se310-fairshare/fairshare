import { it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import ManageExpense from '../src/pages/ManageExpense.jsx';
import { getGroupMembers } from '../src/api/groups';
import { getExpense, updateExpense } from '../src/api/expenses';

vi.mock('../src/api/groups', () => ({
    getGroupMembers: vi.fn(),
}));

vi.mock('../src/api/expenses', () => ({
    getExpense: vi.fn(),
    updateExpense: vi.fn(),
}));

const MEMBERS = [
    { userId: 1, username: 'alice', email: 'alice@test.com' },
    { userId: 2, username: 'bob', email: 'bob@test.com' },
    { userId: 3, username: 'carol', email: 'carol@test.com' },
];

beforeEach(() => {
    vi.clearAllMocks();
    getGroupMembers.mockResolvedValue({ members: MEMBERS });
    getExpense.mockResolvedValue({ expense: { id: 9, amount: '42.50', description: 'Groceries', paidByUserId: 2, expenseDate: '2024-06-15' } });
    updateExpense.mockResolvedValue({ expense: { id: 9 } });
});

function renderPage() {
    render(
        <MemoryRouter initialEntries={['/groups/1/expenses/9/edit']}>
            <Routes>
                <Route path="/groups/:id/expenses/:expenseId/edit" element={<ManageExpense />} />
                <Route path="/groups/:id" element={<h1>Flat 3</h1>} />
            </Routes>
        </MemoryRouter>
    );
}

it('AC3: split across a subset of members', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.selectOptions(await screen.findByLabelText('Paid by'), '2');
    // Select only Alice and Bob as participants
    await user.click(screen.getByLabelText('alice'));
    await user.click(screen.getByLabelText('bob'));
    await user.click(screen.getByRole('button', { name: 'Save expense' }));
    expect(updateExpense).toHaveBeenCalledWith('1', '9', {
        amount: '42.50',
        description: 'Groceries',
        paidByUserId: 2,
        expenseDate: '2024-06-15',
        participantUserIds: [1, 2], // Only Alice and Bob
    });
});

it('AC6: at least one participant must be selected', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText('Amount'), '42.50');
    await user.type(screen.getByLabelText('Description'), 'Groceries');
    await user.selectOptions(screen.getByLabelText('Paid by'), '2');
    await user.click(screen.getByRole('button', { name: 'Save expense' }));
    expect(await screen.findByText('At least one participant must be selected.')).toBeInTheDocument();
    expect(updateExpense).not.toHaveBeenCalled();
});