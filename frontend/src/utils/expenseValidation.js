// The amount/description/payer/participants fields are common to AddExpense and
// AddRecurringExpense; each caller adds its own date-related validation on top.
export function validateSharedExpenseFields({ amount, description, paidByUserId, participantUserIds }) {
    const errors = {};

    if (amount.trim() === '') {
        errors.amount = 'Amount is required';
    } else if (Number.isNaN(Number(amount)) || Number(amount) <= 0) {
        errors.amount = 'Amount must be a positive number';
    }

    if (description.trim() === '') {
        errors.description = 'Description is required';
    }

    if (paidByUserId === '') {
        errors.paidByUserId = 'Payer is required';
    }

    if (!participantUserIds || participantUserIds.length === 0) {
        errors.participantUserIds = 'At least one participant is required';
    }

    return errors;
}
