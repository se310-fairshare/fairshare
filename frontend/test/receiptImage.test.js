import { expect, it } from 'vitest';
import {
    isReceiptImage,
    needsReceiptConversion,
} from '../src/utils/receiptImage.js';

it('recognises mobile image formats and files with a missing MIME type', () => {
    expect(isReceiptImage(new File(['heic'], 'receipt.HEIC', { type: 'image/heic' }))).toBe(true);
    expect(isReceiptImage(new File(['jpeg'], 'receipt.jpg', { type: '' }))).toBe(true);
    expect(isReceiptImage(new File(['text'], 'notes.txt', { type: 'text/plain' }))).toBe(false);
});

it('converts formats the server cannot read', () => {
    expect(needsReceiptConversion(new File(['heic'], 'receipt.heic', { type: 'image/heic' }))).toBe(true);
    expect(needsReceiptConversion(new File(['jpeg'], 'receipt.jpg', { type: 'image/jpeg' }))).toBe(false);
});
