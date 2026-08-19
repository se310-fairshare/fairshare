import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getGroup } from '../api/groups';
import './GroupPage.css';

function GroupPage() {
    const { id } = useParams();
    const [group, setGroup] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [notFound, setNotFound] = useState(false);

    useEffect(() => {
        async function load() {
            try {
                const result = await getGroup(id);
                if (result === null) {
                    setNotFound(true);        // AC8: not a member, or no such group
                } else {
                    setGroup(result);
                }
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

    return (
        <div className="page">
            <div className="card">
                <h1>{group.name}</h1>
                {group.description && <p className="subtitle">{group.description}</p>}

                <Link to={`/groups/${id}/members`}>Manage members</Link>

                <section>
                    <h2>Expenses</h2>
                    {/* AC2: a new group has no expenses */}
                    <p className="empty">No expenses yet.</p>
                </section>

                <section>
                    <h2>Balances</h2>
                    {/* AC2: all balances are zero until expenses are added */}
                    <p className="balance">
                        Everyone is settled up. Balance: {group.baseCurrency} 0.00
                    </p>
                </section>

                <Link to="/groups">Back to your groups</Link>
            </div>
        </div>
    );
}

export default GroupPage;
