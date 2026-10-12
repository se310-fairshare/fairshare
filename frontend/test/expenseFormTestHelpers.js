// Shared by AddExpense.test.jsx and AddRecurringExpense.test.jsx, which exercise near-identical
// form behaviour (group-access errors, non-positive amounts, a removed payer) on top of their
// own form-specific fields.
import { screen } from '@testing-library/react';
import { expect } from 'vitest';

export const MEMBERS = [
    { userId: 1, username: 'alice', email: 'alice@test.com', netBalance: '0.00', currentUser: true },
    { userId: 2, username: 'bob', email: 'bob@test.com', netBalance: '0.00', currentUser: false },
];

export async function expectGroupAccessErrorShown(getGroupMembers, renderPage) {
    getGroupMembers.mockResolvedValue({ error: 'You must be a group member to manage its members' });
    renderPage();

    expect(await screen.findByText('You must be a group member to manage its members'))
        .toBeInTheDocument();
}

export async function expectRejectsNonPositiveAmount(
    user,
    amountField,
    value,
    saveButtonName,
    mockCreateFn
) {
    await user.clear(amountField);
    await user.type(amountField, value);
    await user.click(screen.getByRole('button', { name: saveButtonName }));

    expect(screen.getByText('Amount must be a positive number')).toBeInTheDocument();
    expect(mockCreateFn).not.toHaveBeenCalled();
}

export async function expectShowsPayerRemovedError(user, mockCreateFn, saveButtonName) {
    mockCreateFn.mockResolvedValue({ errors: { form: 'Payer must be a member of the group' } });
    await user.click(screen.getByRole('button', { name: saveButtonName }));

    expect(await screen.findByText('Payer must be a member of the group')).toBeInTheDocument();
}
