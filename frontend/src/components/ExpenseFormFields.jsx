import { EXPENSE_CATEGORIES } from '../utils/expenseCategories';

// Field groups shared by ExpenseForm and RecurringExpenseForm, which collect the same
// amount/description/payer/participants details for a one-off and a recurring expense.

export function FieldError({ message }) {
    return message ? <span className="error">{message}</span> : null;
}

export function AmountField({ value, onChange, error }) {
    return (
        <div className="form-group">
            <label htmlFor="amount">Amount</label>
            <input
                id="amount"
                type="number"
                inputMode="decimal"
                step="0.01"
                value={value}
                placeholder="0.00"
                onChange={(event) => onChange(event.target.value)}
            />
            <FieldError message={error} />
        </div>
    );
}

// The labelled dropdown behind the currency, category and payer fields.
function SelectField({ id, label, value, onChange, error, children }) {
    return (
        <div className="form-group">
            <label htmlFor={id}>{label}</label>
            <select
                id={id}
                value={value}
                onChange={(event) => onChange(event.target.value)}
            >
                {children}
            </select>
            <FieldError message={error} />
        </div>
    );
}

// #14 AC1, AC3: only supported ISO 4217 codes can be picked. emptyLabel adds a "no choice" option.
export function CurrencyField({ value, currencies, onChange, error, emptyLabel }) {
    return (
        <SelectField id="currency" label="Currency" value={value} onChange={onChange} error={error}>
            {emptyLabel && <option value="">{emptyLabel}</option>}
            {currencies.map((option) => (
                <option key={option.code} value={option.code}>
                    {option.code} — {option.name}
                </option>
            ))}
        </SelectField>
    );
}

export function DescriptionField({ value, onChange, error, placeholder }) {
    return (
        <div className="form-group">
            <label htmlFor="description">Description</label>
            <input
                id="description"
                type="text"
                value={value}
                placeholder={placeholder}
                onChange={(event) => onChange(event.target.value)}
            />
            <FieldError message={error} />
        </div>
    );
}

// #16 AC1: optional, so the spending overview can break spending down by category.
export function CategoryField({ value, onChange, error }) {
    return (
        <SelectField id="category" label="Category" value={value} onChange={onChange} error={error}>
            <option value="">No category</option>
            {EXPENSE_CATEGORIES.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
            ))}
        </SelectField>
    );
}

export function PayerSelect({ value, members, onChange, error }) {
    return (
        <SelectField id="paidByUserId" label="Paid by" value={value} onChange={onChange} error={error}>
            {members.map((member) => (
                <option key={member.userId} value={member.userId}>
                    {member.username}
                </option>
            ))}
        </SelectField>
    );
}

export function ParticipantsChecklist({ participantUserIds, members, onParticipantUserIdsChange, error }) {
    function handleParticipantChange(event) {
        const userId = event.target.value;
        onParticipantUserIdsChange((currentIds) => (
            event.target.checked
                ? [...currentIds, userId]
                : currentIds.filter((id) => id !== userId)
        ));
    }

    return (
        <div className="select-participants">
            <p>Participants</p>
            <ul>
                {members.map((member) => (
                    <li key={member.userId}>
                        <label>
                            <input
                                type="checkbox"
                                value={member.userId}
                                checked={participantUserIds.includes(String(member.userId))}
                                onChange={handleParticipantChange}
                            />
                            {member.username}
                        </label>
                    </li>
                ))}
            </ul>
            <FieldError message={error} />
        </div>
    );
}
