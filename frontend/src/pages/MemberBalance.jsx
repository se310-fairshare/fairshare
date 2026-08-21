import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getGroup, getGroupMembers } from '../api/groups';
import './MemberBalance.css';

function formatMoney(currency, value) {
    return `${currency} ${Math.abs(Number(value)).toFixed(2)}`;
}

function MemberBalance() {
    const { id, memberId } = useParams();
    const [group, setGroup] = useState(null);
    const [member, setMember] = useState(null);
    const [expenses, setExpenses] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        async function loadMemberBalance() {
            try {
                const groupResult = await getGroup(id);
                if (groupResult === null) {
                    setError('This group does not exist, or you are not a member of it.');
                    return;
                }

                const memberResult = await getGroupMembers(id);
                if (memberResult.error) {
                    setError(memberResult.error);
                    return;
                }

                const selectedMember = memberResult.members.find(
                    (candidate) => String(candidate.userId) === memberId
                );
                if (!selectedMember) {
                    setError('This member could not be found in the group.');
                    return;
                }

                setGroup(groupResult);
                setMember(selectedMember);
            } catch {
                setError('Could not load the member balance. Please try again.');
            } finally {
                setLoading(false);
            }
        }

        loadMemberBalance();
    }, [id, memberId]);

    if (loading) return <div className="page"><p>Loading balance...</p></div>;

    if (error) {
        return (
            <div className="page">
                <div className="card balance-card">
                    <h1>Member balance unavailable</h1>
                    <p className="error">{error}</p>
                    <Link to={`/groups/${id}/balance`}>Back to balances</Link>
                </div>
            </div>
        );
    }

    const balance = Number(member.netBalance);
    let message;
    if (balance > 0) {
        message = `owed ${formatMoney(group.baseCurrency, balance)}`;
    } else if (balance < 0) {
        message = ` owes ${formatMoney(group.baseCurrency, balance)}`;
    } else {
        message = `${member.username} is settled up`;
    }

    return (
        <div className="page">
            <div className="card balance-card">
                <h1>{member.username}</h1>
                <p className="subtitle">Balance in {group.name}</p>
                <h2>Current Balance</h2>
                <p className="balance">{message}</p>

                <h2>Transaction History</h2>
                {expenses.length === 0 ? (
                        <p className="empty">No expenses yet.</p>
                    ) : (
                        <ul className="expense-list">
                            {expenses.map((expense) => (
                                <li key={expense.id}>
                                    <span className="expense-description">{expense.description}</span>
                                    <span className="expense-meta">
                                        {expense.paidByUsername} paid on {expense.expenseDate}
                                    </span>
                                    <span className="expense-amount">
                                        {money(group.baseCurrency, expense.amount)}
                                    </span>
                                </li>
                            ))}
                        </ul>
                    )}
                <Link to={`/groups/${id}/balance`}>Back to balances</Link>
            </div>
        </div>
    );
}

export default MemberBalance;
