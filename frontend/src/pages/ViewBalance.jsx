import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getGroup, getGroupMembers } from '../api/groups';
import './ViewBalance.css';

function formatMoney(currency, value) {
    return `${currency} ${Math.abs(Number(value)).toFixed(2)}`;
}

function ViewBalance() {
    const { id } = useParams();
    const [group, setGroup] = useState(null);
    const [members, setMembers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        async function loadBalance() {
            try {
                const groupResult = await getGroup(id);
                if (groupResult === null) {
                    setNotFound(true);
                    return;
                }

                const memberResult = await getGroupMembers(id);
                if (memberResult.error) {
                    setError(memberResult.error);
                    return;
                }

                setGroup(groupResult);
                setMembers(memberResult.members);
            } catch {
                setError('Could not load the group balance. Please try again.');
            } finally {
                setLoading(false);
            }
        }

        loadBalance();
    }, [id]);

    if (loading) return <div className="page"><p>Loading balance...</p></div>;


    if (error) {
        return (
            <div className="page">
                <div className="card balance-card">
                    <p className="error">{error}</p>
                    <Link to={`/groups/${id}`}>Back to group</Link>
                </div>
            </div>
        );
    }

    const settled = members.every((member) => Number(member.netBalance) === 0);

    return (
        <div className="page">
            <div className="card balance-card">
                <h1>Balances</h1>
                <p className="subtitle">{group.name}</p>

                {settled ? (
                    <p className="empty">Everyone is settled up.</p>
                ) : (
                    <ul className="detailed-balance-list">
                        {members.map((member) => (
                            <li key={member.userId}>
                                <span className="balance-member">
                                    {member.username}
                                    {member.currentUser && <span className="you-label">You</span>}
                                </span>
                                <span className={Number(member.netBalance) >= 0 ? 'balance-owed' : 'balance-due'}>
                                    {balanceDescription(member, group.baseCurrency)}
                                </span>
                            </li>
                        ))}
                    </ul>
                )}

                <Link to={`/groups/${id}`}>Back to group</Link>
            </div>
        </div>
    );
}

export default ViewBalance;
