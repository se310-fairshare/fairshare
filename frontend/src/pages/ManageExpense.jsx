import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { getGroupMembers } from "../api/groups";
import { getExpense, updateExpense } from "../api/expenses";
import { Link } from "react-router-dom";
import './ManageExpense.css';

function Validate({ amount, description, paidByUserId, expenseId }) {
}

function EditExpense() {
    const { id, expenseId } = useParams();
    const navigate = useNavigate();
    const [members, setMembers] = useState([]);
    const [expense, setExpense] = useState(null);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [errors, setErrors] = useState({});
    const [amount, setAmount] = useState('');
    const [description, setDescription] = useState('');
    const [paidByUserId, setPaidByUserId] = useState('');
    const [expenseDate, setExpenseDate] = useState('');
    const [participantUserIds, setParticipantUserIds] = useState([]);  // #8 AC3

    useEffect(() => {
        async function loadData() {
            const [membersResult, expenseResult] = await Promise.all([
                getGroupMembers(id),
                getExpense(id, expenseId)
            ]);

            if (membersResult.error) {
                setErrors({ form: membersResult.error });
            } else {
                setMembers(membersResult.members);
            }
            if (expenseResult.error) {
                setErrors({ form: expenseResult.error });
            } else {
                setExpense(expenseResult.expense);
                setAmount(String(expenseResult.expense.amount));
                setDescription(expenseResult.expense.description);
                setPaidByUserId(String(expenseResult.expense.paidByUserId));
                setExpenseDate(expenseResult.expense.expenseDate);
            }
            setLoading(false);
        }

        loadData().catch(() => {
            setErrors({ form: 'Could not load data.' });
            setLoading(false);
        });
    }, [id, expenseId]);

    async function handleSubmit(event) {
        event.preventDefault();

        if (participantUserIds.length === 0) {
            setErrors({ participantUserIds: 'At least one participant must be selected.' });
            return;
        }

        const result = await updateExpense(id, expenseId, {
            amount,
            description,
            paidByUserId: Number(paidByUserId),
            expenseDate,
            participantUserIds: participantUserIds.map(Number)
        });

        if (result.errors) {
            setErrors(result.errors);
            return;
        }

        navigate(`/groups/${id}`);
    }

    if (loading) {
        return <p>Loading...</p>;
    }

    return (
        <div className="page">
            <div className="card">
                <h2>Edit Expense</h2>
                <p className="subtitle">Edit the details of this expense</p>
                <form onSubmit={handleSubmit} noValidate>
                    <div className="form-group">
                        <label htmlFor="amount">Amount</label>
                        <input
                            id="amount"
                            type="number"
                            step="0.01"
                            value={amount}
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

                    <div className="select-participants"> {/* #8 AC3: split among a subset of the group */}
                        <p>Participants</p>
                        <ul>
                            {members.map((member) => (
                                <li key={member.userId}>
                                    <label>
                                        <input
                                            type="checkbox"
                                            value={member.userId}
                                            checked={participantUserIds.includes(String(member.userId))}
                                            onChange={(event) => {
                                                const userId = event.target.value;
                                                if (event.target.checked) {
                                                    setParticipantUserIds((currentIds) => [...currentIds, userId]);
                                                } else {
                                                    setParticipantUserIds((currentIds) => currentIds.filter((id) => id !== userId));
                                                }
                                            }}
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
                        {submitting ? 'Saving…' : 'Save expense'}
                    </button>
                </form>

                <Link to={`/groups/${id}`}>Back to the group</Link>
            </div>
        </div>
    );
}

export default EditExpense;
