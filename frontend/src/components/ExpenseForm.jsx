function ExpenseForm({
    amount,
    description,
    paidByUserId,
    expenseDate,
    participantUserIds,
    members,
    errors,
    submitting,
    onAmountChange,
    onDescriptionChange,
    onPaidByUserIdChange,
    onExpenseDateChange,
    onParticipantUserIdsChange,
    onSubmit,
    maxExpenseDate,
}) {
    function handleParticipantChange(event) {
        const userId = event.target.value;
        onParticipantUserIdsChange((currentIds) => (
            event.target.checked
                ? [...currentIds, userId]
                : currentIds.filter((id) => id !== userId)
        ));
    }

    return (
        <form onSubmit={onSubmit} noValidate>
            <div className="form-group">
                <label htmlFor="amount">Amount</label>
                <input
                    id="amount"
                    type="number"
                    step="0.01"
                    value={amount}
                    placeholder="0.00"
                    onChange={(event) => onAmountChange(event.target.value)}
                />
                {errors.amount && <span className="error">{errors.amount}</span>}
            </div>

            <div className="form-group">
                <label htmlFor="description">Description</label>
                <input
                    id="description"
                    type="text"
                    value={description}
                    placeholder="What was it for?"
                    onChange={(event) => onDescriptionChange(event.target.value)}
                />
                {errors.description && <span className="error">{errors.description}</span>}
            </div>

            <div className="form-group">
                <label htmlFor="paidByUserId">Paid by</label>
                <select
                    id="paidByUserId"
                    value={paidByUserId}
                    onChange={(event) => onPaidByUserIdChange(event.target.value)}
                >
                    {members.map((member) => (
                        <option key={member.userId} value={member.userId}>
                            {member.username}
                        </option>
                    ))}
                </select>
                {errors.paidByUserId && <span className="error">{errors.paidByUserId}</span>}
            </div>

            <div className="form-group">
                <label htmlFor="expenseDate">Date</label>
                <input
                    id="expenseDate"
                    type="date"
                    value={expenseDate}
                    max={maxExpenseDate}
                    onChange={(event) => onExpenseDateChange(event.target.value)}
                />
                {errors.expenseDate && <span className="error">{errors.expenseDate}</span>}
            </div>

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
                {errors.participantUserIds && <span className="error">{errors.participantUserIds}</span>}
            </div>

            {errors.form && <span className="error">{errors.form}</span>}

            <button type="submit" disabled={submitting}>
                {submitting ? 'Saving...' : 'Save expense'}
            </button>
        </form>
    );
}

export default ExpenseForm;