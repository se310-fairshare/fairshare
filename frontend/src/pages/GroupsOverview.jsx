import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { getGroups } from '../api/groups';
import './GroupsOverview.css';

function GroupsOverview() {
    const location = useLocation();
    const [groups, setGroups] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        async function load() {
            try {
                setGroups(await getGroups());
            } catch (err) {
                console.error('Failed to load groups', err);
                setError('Could not load your groups. Please try again.');
            } finally {
                setLoading(false);
            }
        }
        load();
    }, []);

    if (loading) {
        return <div className="page"><p>Loading your groups…</p></div>;
    }

    return (
        <div className="page">
            <div className="card">
                <h1>Your Groups</h1>

                {location.state?.notice && (
                    <p className="success" role="status">{location.state.notice}</p>
                )}

                {error && <span className="error">{error}</span>}

                {!error && groups.length === 0 && (
                    <p className="empty">You are not in any groups yet.</p>
                )}

                <ul className="group-list">
                    {groups.map((group) => (
                        <li key={group.id}>
                            <Link to={`/groups/${group.id}`}>
                                <span className="group-name">{group.name}</span>
                                <span className="group-meta">
                  {group.memberCount} {group.memberCount === 1 ? 'member' : 'members'}
                                    {' · created '}
                                    {new Date(group.createdAt).toLocaleDateString()}
                </span>
                            </Link>
                        </li>
                    ))}
                </ul>

                <Link to="/groups/new" className="create-link">Create group</Link>
            </div>
        </div>
    );
}

export default GroupsOverview;
