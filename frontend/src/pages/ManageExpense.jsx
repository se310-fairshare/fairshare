import { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { getGroupMembers } from '../api/groups';
import { getExpense, updateExpense } from '../api/expenses';
import ExpenseForm from '../components/ExpenseForm';
import './ManageExpense.css';

function EditExpense() {
    const { id, expenseId } = useParams();
    const navigate = useNavigate();
    const [members, setMembers] = useState([]);
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
                setAmount(String(expenseResult.expense.amount));
                setDescription(expenseResult.expense.description);
                setPaidByUserId(String(expenseResult.expense.paidByUserId));
                setExpenseDate(expenseResult.expense.expenseDate);
                setParticipantUserIds(expenseResult.expense.participantUserIds.map(String));
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

        setSubmitting(true);
        setErrors({});

        try {
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
        } finally {
            setSubmitting(false);
        }
    }

    if (loading) {
        return <p>Loading...</p>;
    }

    return (
        <div className="page">
            <div className="card">
                <h2>Edit Expense</h2>
                <p className="subtitle">Edit the details of this expense</p>
                <ExpenseForm
                    amount={amount}
                    description={description}
                    paidByUserId={paidByUserId}
                    expenseDate={expenseDate}
                    participantUserIds={participantUserIds}
                    members={members}
                    errors={errors}
                    submitting={submitting}
                    onAmountChange={setAmount}
                    onDescriptionChange={setDescription}
                    onPaidByUserIdChange={setPaidByUserId}
                    onExpenseDateChange={setExpenseDate}
                    onParticipantUserIdsChange={setParticipantUserIds}
                    onSubmit={handleSubmit}
                />

                <Link to={`/groups/${id}`}>Back to the group</Link>
            </div>
        </div>
    );
}

export default EditExpense;
