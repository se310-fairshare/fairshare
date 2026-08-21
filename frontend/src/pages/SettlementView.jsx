import { useEffect, useState } from 'react';
import { getGroupBalances, computeSettlement } from '../api/groups';

export default function SettlementView({ groupId, baseCurrency }) {
    const [balances, setBalances] = useState(null);
    const [settlement, setSettlement] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        async function loadBalances() {
            try {
                setLoading(true);
                const b = await getGroupBalances(groupId);
                setBalances(b);
            } catch (e) {
                setError('Could not load balances');
            } finally {
                setLoading(false);
            }
        }
        loadBalances();
    }, [groupId]);

    async function onCompute() {
        try {
            setLoading(true);
            setError(null);
            const plan = await computeSettlement(groupId, balances || []);
            setSettlement(plan);
        } catch (e) {
            setError('Failed to compute settlement');
        } finally {
            setLoading(false);
        }
    }

    if (loading) return <p>Loading…</p>;
    if (error) return <p className="error">{error}</p>;

    if (!balances) return <p>Loading balances…</p>;

    // If all balances zero -> already settled
    const allZero = balances.every(b => Number(b.balance) === 0);
    if (allZero) return <p className="empty">Everyone is settled up.</p>;

    return (
        <div>
            <button onClick={onCompute}>Generate settlement plan</button>
            {settlement && (
                <div>
                    {settlement.length === 0 ? (
                        <p className="empty">Everyone is settled up.</p>
                    ) : (
                        <ul>
                            {settlement.map((s, idx) => (
                                <li key={idx}>{`User ${s.fromUserId} pays User ${s.toUserId}: ${baseCurrency} ${parseFloat(s.amount).toFixed(2)}`}</li>
                            ))}
                        </ul>
                    )}
                </div>
            )}
        </div>
    );
}