import { beforeEach, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ReceiptScanner from '../src/components/ReceiptScanner.jsx';
import { extractReceipt } from '../src/api/receipts.js';
import { prepareReceiptImage } from '../src/utils/receiptImage.js';

vi.mock('../src/api/receipts.js', () => ({
    extractReceipt: vi.fn(),
}));

vi.mock('../src/utils/receiptImage.js', async (importOriginal) => {
    const original = await importOriginal();
    return {
        ...original,
        prepareReceiptImage: vi.fn((file) => Promise.resolve(file)),
    };
});

const RECEIPT = {
    items: [
        { description: 'Milk', price: 4.5 },
        { description: 'Bread', price: 3.2 },
    ],
    total: 7.7,
};

beforeEach(() => {
    vi.clearAllMocks();
    extractReceipt.mockResolvedValue({ receipt: RECEIPT });
    prepareReceiptImage.mockImplementation((file) => Promise.resolve(file));
});

function imageFile() {
    return new File([new Uint8Array([0xff, 0xd8, 0xff])], 'receipt.jpg', {
        type: 'image/jpeg',
    });
}

it('AC1: starts extraction and displays progress after an image is selected', async () => {
    let finish;
    extractReceipt.mockReturnValue(new Promise((resolve) => { finish = resolve; }));
    const user = userEvent.setup();
    render(<ReceiptScanner groupId="4" onApply={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: /scan or upload receipt/i }));
    await user.upload(screen.getByLabelText('Choose image'), imageFile());

    expect(screen.getByRole('status')).toHaveTextContent('Reading receipt');
    finish({ receipt: RECEIPT });
    expect(await screen.findByDisplayValue('Milk')).toBeInTheDocument();
});

it('AC2: displays editable items and the receipt total, then applies them', async () => {
    const onApply = vi.fn();
    const user = userEvent.setup();
    render(<ReceiptScanner groupId="4" onApply={onApply} />);

    await user.click(screen.getByRole('button', { name: /scan or upload receipt/i }));
    await user.upload(screen.getByLabelText('Choose image'), imageFile());

    const breadPrice = await screen.findByLabelText('Item 2 price');
    await user.clear(breadPrice);
    await user.type(breadPrice, '4.00');

    expect(screen.getByLabelText('Receipt total')).toHaveValue(7.7);
    await user.click(screen.getByRole('button', { name: 'Use receipt' }));

    expect(onApply).toHaveBeenCalledWith({
        total: '7.70',
        description: 'Milk, Bread',
    });
    expect(screen.getByRole('button', { name: /scan or upload receipt/i })).toBeInTheDocument();
});

it('AC3: explains extraction failure and leaves manual entry available', async () => {
    extractReceipt.mockResolvedValue({
        error: "We couldn't read this receipt. Try a clearer image or enter manually.",
    });
    const user = userEvent.setup();
    render(<ReceiptScanner groupId="4" onApply={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: /scan or upload receipt/i }));
    await user.upload(screen.getByLabelText('Choose image'), imageFile());

    expect(await screen.findByRole('alert')).toHaveTextContent("couldn't read this receipt");
    expect(screen.getByRole('button', { name: 'Enter manually instead' })).toBeEnabled();
});

it('AC4: rejects a non-image format before making an API request', async () => {
    const user = userEvent.setup();
    render(<ReceiptScanner groupId="4" onApply={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: /scan or upload receipt/i }));
    const file = new File(['hello'], 'receipt.txt', { type: 'text/plain' });
    fireEvent.change(screen.getByLabelText('Choose image'), { target: { files: [file] } });

    expect(await screen.findByRole('alert')).toHaveTextContent('photo or image');
    expect(extractReceipt).not.toHaveBeenCalled();
});

it('prepares a large mobile image before making the API request', async () => {
    const user = userEvent.setup();
    render(<ReceiptScanner groupId="4" onApply={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: /scan or upload receipt/i }));
    const file = new File([new Uint8Array(7 * 1024 * 1024 + 1)], 'large.jpg', {
        type: 'image/jpeg',
    });
    const prepared = imageFile();
    prepareReceiptImage.mockResolvedValue(prepared);
    fireEvent.change(screen.getByLabelText('Choose image'), { target: { files: [file] } });

    expect(await screen.findByDisplayValue('Milk')).toBeInTheDocument();
    expect(prepareReceiptImage).toHaveBeenCalledWith(file);
    expect(extractReceipt).toHaveBeenCalledWith('4', prepared);
});

it('explains when the phone cannot convert an image format', async () => {
    const user = userEvent.setup();
    prepareReceiptImage.mockRejectedValue(new Error('This browser could not decode the selected image.'));
    render(<ReceiptScanner groupId="4" onApply={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: /scan or upload receipt/i }));
    const file = new File(['heic'], 'receipt.heic', { type: 'image/heic' });
    fireEvent.change(screen.getByLabelText('Choose image'), { target: { files: [file] } });

    expect(await screen.findByRole('alert')).toHaveTextContent('could not be prepared');
    expect(extractReceipt).not.toHaveBeenCalled();
});
