import { useEffect, useState } from 'react';
import { getGroupBalances, computeSettlement, markSettlementPaid, getGroupMembers } from '../api/groups';

export default function SettlementView({ groupId, baseCurrency }) {
    const [balances, setBalances] = useState(null);
    const [members, setMembers] = useState([]);
    const [settlement, setSettlement] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [paying, setPaying] = useState(null);

    // Fetches the latest member balances and regenerates the settlement plan when the group changes.
    async function loadBalances() {
        try {
            setLoading(true);
            setError(null);
            const [b, memberResult] = await Promise.all([
                getGroupBalances(groupId),
                getGroupMembers(groupId)
            ]);
            const memberList = memberResult && Array.isArray(memberResult.members) ? memberResult.members : [];
            setBalances(b || []);
            setMembers(memberList);

            if (!b || b.length === 0 || b.every(item => Number(item.balance) === 0)) {
                setSettlement([]);
                return;
            }

            const plan = await computeSettlement(groupId, b || []);
            setSettlement(plan || []);
        } catch (e) {
            setError('Could not load balances');
        } finally {
            setLoading(false);
        }
    }

    useEffect(() => {
        setSettlement(null);
        loadBalances();
    }, [groupId]);

    // Recomputes the settlement plan using the current balances without reloading unrelated page data.
    async function onCompute() {
        try {
            setLoading(true);
            setError(null);
            const plan = await computeSettlement(groupId, balances || []);
            setSettlement(plan || []);
        } catch (e) {
            setError('Failed to compute settlement');
        } finally {
            setLoading(false);
        }
    }

    // Marks a pending transfer as complete and then refreshes the balances so the UI reflects payment status.
    async function onMarkPaid(fromUserId, toUserId) {
        try {
            setPaying(`${fromUserId}-${toUserId}`);
            setError(null);
            await markSettlementPaid(groupId, fromUserId, toUserId);
            await loadBalances();
        } catch (e) {
            setError('Failed to mark settlement as paid');
        } finally {
            setPaying(null);
        }
    }

    if (loading) return <p>Loading…</p>;
    if (error) return <p className="error">{error}</p>;

    if (!balances) return <p>Loading balances…</p>;

    // Resolve display names once so each settlement row can render a readable payer/recipient label.
    const memberLookup = new Map(members.map(member => [member.userId, member.username]));

    return (
        <div>
            <button onClick={onCompute}>Generate settlement plan</button>
            {settlement && (
                <div>
                    {settlement.length === 0 ? (
                        <p className="empty">Everyone is settled up.</p>
                    ) : (
                        <ul>
                            {settlement.map((s, idx) => {
                                const fromUsername = memberLookup.get(s.fromUserId) || `User ${s.fromUserId}`;
                                const toUsername = memberLookup.get(s.toUserId) || `User ${s.toUserId}`;
                                                            const currentMember = members.find(m => m.currentUser);
                                                            const currentUserId = currentMember ? currentMember.userId : null;
                                                            const canMark = currentUserId !== null && (currentUserId === s.fromUserId || currentUserId === s.toUserId);

                                                            return (
                                                                <li key={idx}>
                                                                    <span>{`${fromUsername} pays ${toUsername}: ${baseCurrency} ${parseFloat(s.amount).toFixed(2)}`}</span>
                                                                    {canMark && (
                                                                        <button
                                                                            type="button"
                                                                            onClick={() => onMarkPaid(s.fromUserId, s.toUserId)}
                                                                            disabled={paying === `${s.fromUserId}-${s.toUserId}`}
                                                                            style={{ marginLeft: '0.75rem' }}
                                                                        >
                                                                            {paying === `${s.fromUserId}-${s.toUserId}` ? 'Marking paid…' : 'Mark as paid'}
                                                                        </button>
                                                                    )}
                                                                </li>
                                                            );
                                                        })}
                        </ul>
                    )}
                </div>
            )}
        </div>
    );
}