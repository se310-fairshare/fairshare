import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getGroupMembers } from '../api/groups';
import { createExpense } from '../api/expenses';
import './AddExpense.css';

// Built from local date because toISOString() reports the UTC date and
// would give yesterday for the first hours of a New Zealand day.
function today() {
    const now = new Date();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${now.getFullYear()}-${month}-${day}`;
}

function validate({ amount, description, paidByUserId, expenseDate }) {
    const errors = {};

    if (amount.trim() === '') {
        errors.amount = 'Amount is required';                          // AC2
    } else if (!(Number(amount) > 0)) {
        errors.amount = 'Amount must be a positive number';            // AC3
    }

    if (description.trim() === '') {
        errors.description = 'Description is required';                // AC2
    }

    if (paidByUserId === '') {
        errors.paidByUserId = 'Payer is required';                     // AC2
    }

    if (expenseDate > today()) {
        errors.expenseDate = 'Expense date cannot be in the future';   // AC6
    }

    return errors;
}

function AddExpense() {
    const { id } = useParams();
    const navigate = useNavigate();
    const [members, setMembers] = useState([]);
    const [amount, setAmount] = useState('');
    const [description, setDescription] = useState('');
    const [paidByUserId, setPaidByUserId] = useState('');
    const [expenseDate, setExpenseDate] = useState(today());   // AC6
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [errors, setErrors] = useState({});

    useEffect(() => {
        async function loadMembers() {
            const result = await getGroupMembers(id);
            if (result.error) {
                setErrors({ form: result.error });                     // AC8
            } else {
                setMembers(result.members);                            // AC5
                const self = result.members.find((member) => member.currentUser);
                setPaidByUserId(String((self ?? result.members[0])?.userId ?? ''));
            }
            setLoading(false);
        }

        loadMembers().catch(() => {
            setErrors({ form: 'Could not load group members.' });
            setLoading(false);
        });
    }, [id]);

    async function handleSubmit(event) {
        event.preventDefault();

        const found = validate({ amount, description, paidByUserId, expenseDate });
        if (Object.keys(found).length > 0) {
            setErrors(found);
            return;
        }

        setSubmitting(true);
        setErrors({});

        try {
            const result = await createExpense(id, {
                amount,
                description,
                paidByUserId: Number(paidByUserId),
                expenseDate
            });

            if (result.errors) {
                setErrors(result.errors);
                return;
            }

            navigate(`/groups/${id}`);          // AC7: back to the list the expense now appears in
        } catch {
            setErrors({ form: 'Could not add this expense. Please try again.' });
        } finally {
            setSubmitting(false);
        }
    }

    if (loading) {
        return <div className="page"><p>Loading…</p></div>;
    }

    return (
        <div className="page">
            <div className="card">
                <h1>Add an expense</h1>
                <p className="subtitle">Split equally among everyone in the group</p>

                <form onSubmit={handleSubmit} noValidate>
                    <div className="form-group">
                        <label htmlFor="amount">Amount</label>
                        <input
                            id="amount"
                            type="number"
                            step="0.01"
                            value={amount}
                            placeholder="0.00"
                            onChange={(event) => setAmount(event.target.value)}
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
                            onChange={(event) => setDescription(event.target.value)}
                        />
                        {errors.description && <span className="error">{errors.description}</span>}
                    </div>

                    <div className="form-group">
                        <label htmlFor="paidByUserId">Paid by</label>
                        <select
                            id="paidByUserId"
                            value={paidByUserId}
                            onChange={(event) => setPaidByUserId(event.target.value)}
                        >
                            {/* AC5: only current members of the group */}
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
                            max={today()}                 // AC6: past dates only
                            onChange={(event) => setExpenseDate(event.target.value)}
                        />
                        {errors.expenseDate && <span className="error">{errors.expenseDate}</span>}
                    </div>

                    {errors.form && <span className="error">{errors.form}</span>}

                    <button type="submit" disabled={submitting}>
                        {submitting ? 'Saving…' : 'Save expense'}
                    </button>
                </form>

                <Link to={`/groups/${id}`}>Back to the group</Link>
            </div>
        </div>
    );
}

export default AddExpense;
