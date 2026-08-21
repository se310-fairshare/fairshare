import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getGroup, getGroupMembers } from '../api/groups';
import { getExpenses } from '../api/expenses';
import SettlementView from './SettlementView';
import './GroupPage.css';

function money(currency, value) {
    return `${currency} ${Number(value).toFixed(2)}`;
}

function balanceLine(member, currency) {
    const balance = Number(member.netBalance);
    if (balance > 0) {
        return `${member.username} is owed ${money(currency, balance)}`;
    }
    if (balance < 0) {
        return `${member.username} owes ${money(currency, -balance)}`;
    }
    return `${member.username} is settled up`;
}

function GroupPage() {
    const { id } = useParams();
    const [group, setGroup] = useState(null);
    const [expenses, setExpenses] = useState([]);
    const [members, setMembers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [notFound, setNotFound] = useState(false);

    useEffect(() => {
        async function load() {
            try {
                const result = await getGroup(id);
                if (result === null) {
                    setNotFound(true);        // AC8: not a member, or no such group
                    return;
                }
                setGroup(result);

                const [expenseResult, memberResult] = await Promise.all([
                    getExpenses(id),
                    getGroupMembers(id),
                ]);

                if (expenseResult.error || memberResult.error) {
                    setError(expenseResult.error || memberResult.error);
                    return;
                }
                setExpenses(expenseResult.expenses);   // AC7
                setMembers(memberResult.members);      // AC1
            } catch (err) {
                console.error('Failed to load group', err);
                setError('Could not load this group. Please try again.');
            } finally {
                setLoading(false);
            }
        }
        load();
    }, [id]);

    if (loading) {
        return <div className="page"><p>Loading…</p></div>;
    }

    if (notFound) {
        return (
            <div className="page">
                <div className="card">
                    <h1>Group not found</h1>
                    <p className="empty">This group does not exist, or you are not a member of it.</p>
                    <Link to="/groups">Back to your groups</Link>
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="page">
                <div className="card">
                    <span className="error">{error}</span>
                    <Link to="/groups">Back to your groups</Link>
                </div>
            </div>
        );
    }

    const settled = members.every((member) => Number(member.netBalance) === 0);

    return (
        <div className="page">
            <div className="card group-card">
                <h1>{group.name}</h1>
                {group.description && <p className="subtitle">{group.description}</p>}

                <section>
                    <h2>Expenses</h2>
                    <Link className="action" to={`/groups/${id}/expenses/new`}>Add an expense</Link>

                    {/* AC7: every member sees the expense with amount, description, payer and date */}
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
                </section>

                <section>
                    <h2>Balances</h2>
                    {/* AC1: each member's balance reflects the expenses recorded so far */}
                    {settled ? (
                        <p className="balance">
                            Everyone is settled up. Balance: {money(group.baseCurrency, 0)}
                        </p>
                    ) : (
                        <ul className="balance-list">
                            {members.map((member) => (
                                <li key={member.userId} className="balance">
                                    {balanceLine(member, group.baseCurrency)}
                                </li>
                            ))}
                        </ul>
                    )}
                </section>

                <section>
                    <h2>Settlement plan</h2>
                    <SettlementView groupId={group.id} baseCurrency={group.baseCurrency} />
                </section>

                <Link to="/groups">Back to your groups</Link>
            </div>
        </div>
    );
}

export default GroupPage;
