import { it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import AddRecurringExpense from '../src/pages/AddRecurringExpense.jsx';
import { getGroupMembers } from '../src/api/groups';
import { createRecurringExpense } from '../src/api/recurringExpenses';
import { today } from '../src/utils/dates';
import { MEMBERS, expectGroupAccessErrorShown, expectRejectsNonPositiveAmount, expectShowsPayerRemovedError }
    from './expenseFormTestHelpers';

vi.mock('../src/api/groups', () => ({
    getGroupMembers: vi.fn(),
}));

vi.mock('../src/api/recurringExpenses', () => ({
    createRecurringExpense: vi.fn(),
}));

beforeEach(() => {
    vi.clearAllMocks();
    getGroupMembers.mockResolvedValue({ members: MEMBERS });
    createRecurringExpense.mockResolvedValue({ recurringExpense: { id: 9 } });
});

function renderPage() {
    render(
        <MemoryRouter initialEntries={['/groups/1/recurring-expenses/new']}>
            <Routes>
                <Route path="/groups/:id/recurring-expenses/new" element={<AddRecurringExpense />} />
                <Route path="/groups/:id" element={<h1>Flat 3</h1>} />
            </Routes>
        </MemoryRouter>
    );
}

it('AC1: saves the recurring expense and returns to the group', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText('Amount'), '500');
    await user.type(screen.getByLabelText('Description'), 'Rent');
    await user.selectOptions(screen.getByLabelText('Paid by'), '2');
    await user.selectOptions(screen.getByLabelText('Frequency'), 'MONTHLY');
    await user.click(screen.getByRole('checkbox', { name: 'alice' }));
    await user.click(screen.getByRole('checkbox', { name: 'bob' }));
    await user.click(screen.getByRole('button', { name: 'Save recurring expense' }));

    expect(createRecurringExpense).toHaveBeenCalledWith('1', {
        amount: '500',
        description: 'Rent',
        paidByUserId: 2,
        frequency: 'MONTHLY',
        startDate: today(),
        endDate: null,
        participantUserIds: [1, 2],
    });
    expect(await screen.findByText('Flat 3')).toBeInTheDocument();
});

it('AC1: the start date defaults to today', async () => {
    renderPage();

    expect(await screen.findByLabelText('Start date')).toHaveValue(today());
});

it('AC5: shows an inline error on each missing field', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Save recurring expense' }));

    expect(screen.getByText('Amount is required')).toBeInTheDocument();
    expect(screen.getByText('Description is required')).toBeInTheDocument();
    expect(screen.getByText('Frequency is required')).toBeInTheDocument();
    expect(createRecurringExpense).not.toHaveBeenCalled();
});

it.each(['0', '-5'])('AC5: rejects a non-positive amount of %s', async (value) => {
    const user = userEvent.setup();
    renderPage();

    const amount = await screen.findByLabelText('Amount');
    await user.type(screen.getByLabelText('Description'), 'Rent');
    await user.selectOptions(screen.getByLabelText('Frequency'), 'MONTHLY');
    await user.click(screen.getByRole('checkbox', { name: 'alice' }));

    await expectRejectsNonPositiveAmount(
        user,
        amount,
        value,
        'Save recurring expense',
        createRecurringExpense
    );
});

it.each(['10.005', '0.001'])(
    'AC5: rejects an amount with more than two decimal places: %s',
    async (value) => {
        const user = userEvent.setup();
        renderPage();

        const amount = await screen.findByLabelText('Amount');
        await user.type(screen.getByLabelText('Description'), 'Rent');
        await user.selectOptions(screen.getByLabelText('Frequency'), 'MONTHLY');
        await user.click(screen.getByRole('checkbox', { name: 'alice' }));

        await user.clear(amount);
        await user.type(amount, value);
        await user.click(screen.getByRole('button', { name: 'Save recurring expense' }));

        expect(screen.getByText('Amounts should only have up to 2 decimal places.')).toBeInTheDocument();
        expect(createRecurringExpense).not.toHaveBeenCalled();
    }
);

it.each([
    // The number input normalises 10.00 to 10; the backend stores it at two decimal places.
    ['10.00', '10'],
    ['10.01', '10.01'],
])('AC5: accepts a validly formatted amount of %s', async (typed, submitted) => {
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText('Amount'), typed);
    await user.type(screen.getByLabelText('Description'), 'Rent');
    await user.selectOptions(screen.getByLabelText('Frequency'), 'MONTHLY');
    await user.click(screen.getByRole('checkbox', { name: 'alice' }));
    await user.click(screen.getByRole('button', { name: 'Save recurring expense' }));

    expect(createRecurringExpense).toHaveBeenCalledWith('1', expect.objectContaining({ amount: submitted }));
    expect(screen.queryByText('Amounts should only have up to 2 decimal places.')).not.toBeInTheDocument();
});

it('AC5: rejects an end date before the start date', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText('Amount'), '500');
    await user.type(screen.getByLabelText('Description'), 'Rent');
    await user.selectOptions(screen.getByLabelText('Frequency'), 'MONTHLY');
    await user.click(screen.getByRole('checkbox', { name: 'alice' }));
    await user.type(screen.getByLabelText('End date (optional)'), '2020-01-01');
    await user.click(screen.getByRole('button', { name: 'Save recurring expense' }));

    expect(screen.getByText('End date cannot be before the start date')).toBeInTheDocument();
    expect(createRecurringExpense).not.toHaveBeenCalled();
});

it('offers weekly, fortnightly and monthly as frequency options', async () => {
    renderPage();

    const frequency = await screen.findByLabelText('Frequency');

    expect([...frequency.options].map((option) => option.textContent))
        .toEqual(['Select a frequency', 'Weekly', 'Fortnightly', 'Monthly']);
});

it('AC8: shows the error when the group is not readable', async () => {
    await expectGroupAccessErrorShown(getGroupMembers, renderPage);
});

it('AC5: shows the error when the payer is no longer a group member', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText('Amount'), '500');
    await user.type(screen.getByLabelText('Description'), 'Rent');
    await user.selectOptions(screen.getByLabelText('Frequency'), 'MONTHLY');
    await user.click(screen.getByRole('checkbox', { name: 'alice' }));

    await expectShowsPayerRemovedError(user, createRecurringExpense, 'Save recurring expense');
});

it('#84 AC3: the amount field opens the number keypad on a phone', async () => {
    renderPage();

    expect(await screen.findByLabelText('Amount')).toHaveAttribute('inputmode', 'decimal');
});
