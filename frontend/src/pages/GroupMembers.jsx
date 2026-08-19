import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
    addGroupMember,
    getGroupMembers,
    removeGroupMember,
} from '../api/groups';
import './GroupMembers.css';

function GroupMembers() {
    const { id } = useParams();
    const navigate = useNavigate();
    const [members, setMembers] = useState([]);
    const [identifier, setIdentifier] = useState('');
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        async function loadMembers() {
            const result = await getGroupMembers(id);
            if (result.error) {
                setError(result.error);
            } else {
                setMembers(result.members);
            }
            setLoading(false);
        }

        loadMembers().catch(() => {
            setError('Could not load group members.');
            setLoading(false);
        });
    }, [id]);

    async function handleAdd(event) {
        event.preventDefault();
        setSubmitting(true);
        setError(null);

        try {
            const result = await addGroupMember(id, identifier);
            if (result.error) {
                setError(result.error);
                return;
            }
            setMembers((current) => [...current, result.member]
                .sort((first, second) => first.username.localeCompare(second.username)));
            setIdentifier('');
        } catch {
            setError('Could not add this member.');
        } finally {
            setSubmitting(false);
        }
    }

    async function handleRemove(member) {
        setError(null);
        try {
            const result = await removeGroupMember(id, member.userId);
            if (result.error) {
                setError(result.error);
                return;
            }
            if (member.currentUser) {
                navigate('/groups');
                return;
            }
            setMembers((current) => current.filter(
                (candidate) => candidate.userId !== member.userId));
        } catch {
            setError('Could not remove this member.');
        }
    }

    if (loading) {
        return <div className="page"><p>Loading members...</p></div>;
    }

    return (
        <div className="page">
            <div className="card members-card">
                <h1>Manage Members</h1>
                <p className="subtitle">Add a registered user by email or username</p>

                <form onSubmit={handleAdd}>
                    <div className="form-group">
                        <label htmlFor="member-identifier">Email or username</label>
                        <input
                            id="member-identifier"
                            value={identifier}
                            onChange={(event) => setIdentifier(event.target.value)}
                            placeholder="name@example.com"
                            required
                        />
                    </div>
                    <button type="submit" disabled={submitting}>
                        {submitting ? 'Adding...' : 'Add Member'}
                    </button>
                </form>

                {error && <p className="error" role="alert">{error}</p>}

                <ul className="member-list">
                    {members.map((member) => (
                        <li key={member.userId}>
                            <div>
                                <strong>{member.username}</strong>
                                {member.currentUser && <span className="you-label">You</span>}
                                <span>{member.email}</span>
                                <span>Net balance: {Number(member.netBalance).toFixed(2)}</span>
                            </div>
                            <button
                                type="button"
                                className="remove-button"
                                onClick={() => handleRemove(member)}
                            >
                                Remove
                            </button>
                        </li>
                    ))}
                </ul>

                <Link to={`/groups/${id}`}>Back to group</Link>
            </div>
        </div>
    );
}

export default GroupMembers;
