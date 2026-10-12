import {useEffect, useState} from 'react';
import {Link, useParams} from 'react-router-dom';
import {getGroup, getGroupMembers} from '../api/groups';
import {getExpenses} from '../api/expenses';
import {getRecurringExpenses} from '../api/recurringExpenses';
import SettlementView from './SettlementView';
import {formatExpenseAmount} from '../utils/formatExpenseAmount';
import './GroupPage.css';

const FREQUENCY_LABELS = {
    WEEKLY: 'Weekly',
    FORTNIGHTLY: 'Fortnightly',
    MONTHLY: 'Monthly',
};

// Formats a numeric balance into the UI's currency display and keeps the sign readable.
function money(currency, value) {
    return `${currency} ${Math.abs(Number(value)).toFixed(2)}`;
}

// Converts the signed balance into a human-readable sentence about who owes or is owed.
function balanceLine(member, currency) {
    const balance = Number(member.netBalance);
    if (balance > 0) {
        return `${member.username} owes ${money(currency, balance)}`;
    } else if (balance < 0) {
        return `${member.username} is owed ${money(currency, balance)}`;
    }
    return `${member.username} is settled up`;
}

function GroupPage() {
    const {id} = useParams();
    const [group, setGroup] = useState(null);
    const [expenses, setExpenses] = useState([]);
    const [members, setMembers] = useState([]);
    const [recurringExpenses, setRecurringExpenses] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [recurringExpensesError, setRecurringExpensesError] = useState(null);
    const [notFound, setNotFound] = useState(false);

    // Fetch the group, expenses, and members whenever the route changes so the page stays in sync.
    useEffect(() => {
        async function load() {
            try {
                const result = await getGroup(id);
                if (result === null) {
                    setNotFound(true);        // AC8: not a member, or no such group
                    return;
                }
                setGroup(result);

                const [expenseResult, memberResult, recurringExpenseResult] = await Promise.all([
                    getExpenses(id),
                    getGroupMembers(id),
                    getRecurringExpenses(id),
                ]);

                if (expenseResult.error || memberResult.error) {
                    setError(expenseResult.error || memberResult.error);
                    return;
                }
                setExpenses(expenseResult.expenses);   // AC7
                setMembers(memberResult.members);      // AC1

                // A failure loading recurring expenses shouldn't hide the rest of the group page -
                // it's shown inline in that section instead (bug report from teammate review).
                if (recurringExpenseResult.error) {
                    setRecurringExpensesError(recurringExpenseResult.error);
                } else {
                    setRecurringExpenses(recurringExpenseResult.recurringExpenses);   // #13 AC1
                }
            } catch (err) {
                console.error('Failed to load group', err);
                setError('Could not load this group. Please try again.');
            } finally {
                setLoading(false);
            }
        }

        void load();
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

    // A group is fully settled only when every member's net balance is zero.
    const settled = members.every((member) => Number(member.netBalance) === 0);
    let recurringExpensesContent;
    if (recurringExpensesError) {
        recurringExpensesContent = <span className="error">{recurringExpensesError}</span>;
    } else if (recurringExpenses.length === 0) {
        recurringExpensesContent = <p className="empty">No recurring expenses yet.</p>;
    } else {
        recurringExpensesContent = (
            <ul className="expense-list">
                {recurringExpenses.map((recurringExpense) => (
                    <li key={recurringExpense.id} id={`recurring-expense-${recurringExpense.id}`}>
                        <span className="expense-description">{recurringExpense.description}</span>
                        <span className="expense-meta">
                            {FREQUENCY_LABELS[recurringExpense.frequency]} · paid by {recurringExpense.paidByUsername} · starts {recurringExpense.startDate}
                            {recurringExpense.endDate && ` · ends ${recurringExpense.endDate}`}
                            {' · '}{recurringExpense.active ? 'Active' : 'Ended'}
                        </span>
                        <span className="expense-amount">
                            {money(group.baseCurrency, recurringExpense.amount)}
                        </span>
                    </li>
                ))}
            </ul>
        );
    }

    return (
        <div className="page">
            <div className="card group-card">
                <h1>{group.name}</h1>
                {group.description && <p className="subtitle">{group.description}</p>}

                <Link to={`/groups/${id}/members`}>Manage members</Link>

                <section>
                    <h2>Expenses</h2>
                    <Link className="action" to={`/groups/${id}/expenses/new`}>Add an expense</Link>

                    {/* AC7: every member sees the expense with amount, description, payer and date */}
                    {/* #8 AC7: Expenses can be edited */}
                    {expenses.length === 0 ? (
                        <p className="empty">No expenses yet.</p>
                    ) : (
                        <ul className="expense-list">
                            {expenses.map((expense) => (
                                <li key={expense.id}>
                                    <Link className="action"
                                          to={`/groups/${id}/expenses/${expense.id}/edit`}>Edit</Link>
                                    <span className="expense-description">{expense.description}</span>
                                    <span className="expense-meta">
                                        {expense.paidByUsername} paid on {expense.expenseDate}
                                        {/* #13 AC3: marks a generated entry and links back to its recurring expense */}
                                        {expense.recurringExpenseId && (
                                            <> · <a href={`#recurring-expense-${expense.recurringExpenseId}`}>Recurring</a></>
                                        )}
                                    </span>
                                    <span className="expense-amount">
                                        {formatExpenseAmount(expense, group.baseCurrency)}
                                    </span>
                                </li>
                            ))}
                        </ul>
                    )}
                </section>

                <section>
                    <h2>Recurring expenses</h2>
                    <Link className="action" to={`/groups/${id}/recurring-expenses/new`}>Add recurring expense</Link>

                    {/* #13 AC1, AC4: every recurring expense in the group, marked active or ended */}
                    {recurringExpensesContent}
                </section>

                <section>
                    <h2>Balances</h2>
                    {/* AC1: each member's balance reflects the expenses recorded so far */}
                    {settled ? (
                        <p className="balance">
                            Everyone is settled up. Balance: {money(group.baseCurrency, 0)}
                            <br/>


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
                    <Link to={`/groups/${id}/balance`}>View Detailed</Link>
                </section>

                <section>
                    <h2>Settlement plan</h2>
                    <SettlementView groupId={group.id} baseCurrency={group.baseCurrency}/>
                </section>

                <Link to="/groups">Back to your groups</Link>
            </div>
        </div>
    );
}

export default GroupPage;
