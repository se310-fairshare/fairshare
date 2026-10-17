import { useState } from 'react';
import { extractReceipt } from '../api/receipts.js';
import './ReceiptScanner.css';

const MAX_FILE_SIZE = 7 * 1024 * 1024;
const ACCEPTED_TYPES = new Set(['image/jpeg', 'image/png']);
const FILE_REQUIREMENTS = 'Choose a JPG or PNG image no larger than 7 MB.';

function asMoney(value) {
    const number = Number(value);
    return Number.isFinite(number) ? number.toFixed(2) : '';
}

function receiptDescription(items) {
    const description = items
        .map((item) => item.description.trim())
        .filter(Boolean)
        .join(', ') || 'Receipt purchase';
    return description.length <= 255 ? description : `${description.slice(0, 252)}...`;
}

function ReceiptScanner({ groupId, onApply }) {
    const [expanded, setExpanded] = useState(false);
    const [processing, setProcessing] = useState(false);
    const [error, setError] = useState('');
    const [receipt, setReceipt] = useState(null);

    async function processFile(file) {
        setError('');
        setReceipt(null);

        if (!file || !ACCEPTED_TYPES.has(file.type) || file.size > MAX_FILE_SIZE) {
            setError(FILE_REQUIREMENTS);
            return;
        }

        setProcessing(true);
        try {
            const result = await extractReceipt(groupId, file);
            if (result.error) {
                setError(result.error);
                return;
            }

            setReceipt({
                items: result.receipt.items.map((item, index) => ({
                    id: index,
                    description: item.description,
                    price: asMoney(item.price),
                })),
                total: asMoney(result.receipt.total),
            });
        } catch {
            setError("We couldn't read this receipt. Enter the expense manually or try again.");
        } finally {
            setProcessing(false);
        }
    }

    function handleFile(event) {
        const file = event.target.files?.[0];
        event.target.value = '';
        void processFile(file);
    }

    function updateItem(index, field, value) {
        setReceipt((current) => ({
            ...current,
            items: current.items.map((item, itemIndex) => (
                itemIndex === index ? { ...item, [field]: value } : item
            )),
        }));
    }

    function applyReceipt() {
        const total = Number(receipt.total);
        const invalidItem = receipt.items.some((item) => (
            !item.description.trim()
            || !Number.isFinite(Number(item.price))
            || Number(item.price) < 0
        ));

        if (invalidItem) {
            setError('Every item needs a description and a valid price.');
            return;
        }
        if (!Number.isFinite(total) || total <= 0) {
            setError('Receipt total must be a positive number.');
            return;
        }

        onApply({
            total: total.toFixed(2),
            description: receiptDescription(receipt.items),
        });
        setExpanded(false);
        setError('');
    }

    function continueManually() {
        setExpanded(false);
        setReceipt(null);
        setError('');
    }

    if (!expanded) {
        return (
            <section className="receipt-scanner" aria-label="Receipt scanner">
                <button
                    type="button"
                    className="receipt-scan-trigger"
                    onClick={() => setExpanded(true)}
                >
                    <span aria-hidden="true" className="receipt-camera-icon">▣</span>
                    <span>
                        <strong>Scan or upload receipt</strong>
                        <small>Automatically fill items and prices from a photo</small>
                    </span>
                </button>
            </section>
        );
    }

    return (
        <section className="receipt-scanner receipt-panel" aria-label="Receipt scanner">
            <div className="receipt-panel-heading">
                <div>
                    <strong>Scan a receipt</strong>
                    <p>Upload a JPG or PNG, then review the extracted details.</p>
                </div>
                {!processing && (
                    <button type="button" className="text-button" onClick={continueManually}>
                        Close
                    </button>
                )}
            </div>

            {!receipt && !processing && (
                <>
                    <div className="receipt-upload-actions">
                        <label className="receipt-file-button" htmlFor="receipt-camera">
                            Take photo
                        </label>
                        <input
                            id="receipt-camera"
                            className="receipt-file-input"
                            type="file"
                            accept="image/jpeg,image/png"
                            capture="environment"
                            onChange={handleFile}
                        />
                        <label className="receipt-file-button secondary" htmlFor="receipt-file">
                            Choose image
                        </label>
                        <input
                            id="receipt-file"
                            className="receipt-file-input"
                            type="file"
                            accept="image/jpeg,image/png"
                            onChange={handleFile}
                        />
                    </div>
                    <small className="receipt-file-help">JPG or PNG · maximum 7 MB</small>
                </>
            )}

            {processing && (
                <div className="receipt-progress" role="status" aria-live="polite">
                    <span className="receipt-spinner" aria-hidden="true" />
                    <span>
                        <strong>Reading receipt…</strong>
                        <small>Detecting items and prices</small>
                    </span>
                </div>
            )}

            {receipt && !processing && (
                <div className="receipt-results">
                    <div className="receipt-list-heading">
                        <strong>Extracted items</strong>
                        <span>Edit anything that was read incorrectly.</span>
                    </div>
                    <div className="receipt-items">
                        {receipt.items.map((item, index) => (
                            <div className="receipt-item" key={item.id}>
                                <label className="sr-only" htmlFor={`receipt-item-${index}`}>
                                    Item {index + 1} description
                                </label>
                                <input
                                    id={`receipt-item-${index}`}
                                    type="text"
                                    value={item.description}
                                    onChange={(event) => updateItem(index, 'description', event.target.value)}
                                />
                                <label className="sr-only" htmlFor={`receipt-price-${index}`}>
                                    Item {index + 1} price
                                </label>
                                <div className="receipt-price-input">
                                    <span aria-hidden="true">$</span>
                                    <input
                                        id={`receipt-price-${index}`}
                                        type="number"
                                        min="0"
                                        step="0.01"
                                        inputMode="decimal"
                                        value={item.price}
                                        onChange={(event) => updateItem(index, 'price', event.target.value)}
                                    />
                                </div>
                            </div>
                        ))}
                    </div>

                    <div className="receipt-total-row">
                        <label htmlFor="receipt-total">Receipt total</label>
                        <div className="receipt-price-input receipt-total-input">
                            <span aria-hidden="true">$</span>
                            <input
                                id="receipt-total"
                                type="number"
                                min="0.01"
                                step="0.01"
                                inputMode="decimal"
                                value={receipt.total}
                                onChange={(event) => setReceipt((current) => ({
                                    ...current,
                                    total: event.target.value,
                                }))}
                            />
                        </div>
                    </div>

                    <div className="receipt-result-actions">
                        <button type="button" onClick={applyReceipt}>Use receipt</button>
                        <button type="button" className="secondary" onClick={() => setReceipt(null)}>
                            Scan again
                        </button>
                    </div>
                </div>
            )}

            {error && (
                <div className="receipt-error" role="alert">
                    <span>{error}</span>
                    <button type="button" className="text-button" onClick={continueManually}>
                        Enter manually instead
                    </button>
                </div>
            )}
        </section>
    );
}

export default ReceiptScanner;
