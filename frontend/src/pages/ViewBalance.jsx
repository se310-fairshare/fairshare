import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getGroup, getGroupMembers } from '../api/groups';
import './ViewBalance.css';

function money(currency, value) {
    return `${currency} ${Number(value).toFixed(2)}`;
}

function balanceLine(member, currency) {
    const balance = Number(member.netBalance);
    if (balance > 0) {
        return `+ ${money(currency, balance)}`;
    }
    if (balance < 0) {
        return `- ${money(currency, -balance)}`;
    }
    return `is settled up`;
}
function balanceClass(member) {
    const balance = Number(member.netBalance);
    if (balance > 0) {
        return 'balance-Positive';
    }
    if (balance < 0) {
        return 'balance-Negative';
    }
    return 'balance-Neutral';
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

    
    

    return (
        <div className="page">
            <div className="card balance-card">
                <h1>Balances</h1>
                <p className="subtitle">{group.name}</p>
                
                    <ul className="detailed-balance-list">
                        
                        {members.map((member) => (
                            <li key={member.userId} className="balance">
                                    <Link to={`/groups/${id}/balance/${member.userId}`}>{member.username} </Link> 
                                    <div className = {balanceClass(member)}>{balanceLine(member, group.baseCurrency)}</div>
                                </li>
                            
                        ))}
                        
                        
                        
                    </ul>
                

                <Link to={`/groups/${id}`}>Back to group</Link>
            </div>
        </div>
    );
}

export default ViewBalance;
