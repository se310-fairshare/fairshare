import { afterEach, describe, expect, it, vi } from 'vitest';
import {
    MAX_UPLOAD_BYTES,
    isReceiptImage,
    needsReceiptConversion,
    prepareReceiptImage,
} from '../src/utils/receiptImage.js';

afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

it('recognises mobile image formats and files with a missing MIME type', () => {
    expect(isReceiptImage(new File(['heic'], 'receipt.HEIC', { type: 'image/heic' }))).toBe(true);
    expect(isReceiptImage(new File(['jpeg'], 'receipt.jpg', { type: '' }))).toBe(true);
    expect(isReceiptImage(new File(['text'], 'notes.txt', { type: 'text/plain' }))).toBe(false);
});

it('converts formats the server cannot read', () => {
    expect(needsReceiptConversion(new File(['heic'], 'receipt.heic', { type: 'image/heic' }))).toBe(true);
    expect(needsReceiptConversion(new File(['jpeg'], 'receipt.jpg', { type: 'image/jpeg' }))).toBe(false);
});

it('converts images that exceed the upload limit', () => {
    const oversizedJpeg = new File(
        [new Uint8Array(MAX_UPLOAD_BYTES + 1)],
        'large.jpg',
        { type: 'image/jpeg' },
    );

    expect(needsReceiptConversion(oversizedJpeg)).toBe(true);
});

it('returns a compatible image without modifying it', async () => {
    const jpeg = new File(['jpeg'], 'receipt.jpg', { type: 'image/jpeg' });

    await expect(prepareReceiptImage(jpeg)).resolves.toBe(jpeg);
});

it('rejects a non-image before trying to decode it', async () => {
    const text = new File(['not an image'], 'receipt.txt', { type: 'text/plain' });

    await expect(prepareReceiptImage(text)).rejects.toThrow('Choose a photo or image file.');
});

describe('image conversion', () => {
    function installBrowserImageMocks({ width = 4800, height = 2400, blobs } = {}) {
        class MockImage {
            naturalWidth = width;
            naturalHeight = height;

            set src(value) {
                this.source = value;
                queueMicrotask(() => this.onload());
            }
        }

        const createObjectURL = vi.fn(() => 'blob:receipt');
        const revokeObjectURL = vi.fn();
        vi.stubGlobal('Image', MockImage);
        vi.stubGlobal('URL', { createObjectURL, revokeObjectURL });

        const drawImage = vi.fn();
        const encodedBlobs = blobs ?? [new Blob(['converted'], { type: 'image/jpeg' })];
        const toBlob = vi.fn((callback) => callback(encodedBlobs.shift()));
        const canvas = {
            width: 0,
            height: 0,
            getContext: vi.fn(() => ({ drawImage })),
            toBlob,
        };
        const originalCreateElement = document.createElement.bind(document);
        const createElement = vi.spyOn(document, 'createElement');
        createElement.mockImplementation((tagName) => {
            if (tagName === 'canvas') return canvas;
            return originalCreateElement(tagName);
        });

        return { canvas, createObjectURL, drawImage, MockImage, revokeObjectURL, toBlob };
    }

    it('resizes and converts a mobile image to a backend-compatible JPEG', async () => {
        const mocks = installBrowserImageMocks();
        const heic = new File(['heic'], 'shop-receipt.heic', { type: 'image/heic' });

        const result = await prepareReceiptImage(heic);

        expect(result).toBeInstanceOf(File);
        expect(result.name).toBe('shop-receipt.jpg');
        expect(result.type).toBe('image/jpeg');
        expect(mocks.canvas.width).toBe(2400);
        expect(mocks.canvas.height).toBe(1200);
        expect(mocks.drawImage).toHaveBeenCalledWith(expect.any(mocks.MockImage), 0, 0, 2400, 1200);
        expect(mocks.toBlob).toHaveBeenCalledWith(expect.any(Function), 'image/jpeg', 0.88);
        expect(mocks.createObjectURL).toHaveBeenCalledWith(heic);
        expect(mocks.revokeObjectURL).toHaveBeenCalledWith('blob:receipt');
    });

    it('compresses a JPEG over 7 MB through the conversion path', async () => {
        const mocks = installBrowserImageMocks();
        const oversizedJpeg = new File(
            [new Uint8Array(MAX_UPLOAD_BYTES + 1)],
            'large-receipt.jpg',
            { type: 'image/jpeg' },
        );

        const result = await prepareReceiptImage(oversizedJpeg);

        expect(result).not.toBe(oversizedJpeg);
        expect(result.name).toBe('large-receipt.jpg');
        expect(result.type).toBe('image/jpeg');
        expect(result.size).toBeLessThanOrEqual(MAX_UPLOAD_BYTES);
        expect(mocks.toBlob).toHaveBeenCalledWith(expect.any(Function), 'image/jpeg', 0.88);
    });

    it('retries at lower quality until the converted image fits', async () => {
        const tooLarge = { size: MAX_UPLOAD_BYTES + 1 };
        const finalBlob = new Blob(['small enough'], { type: 'image/jpeg' });
        const mocks = installBrowserImageMocks({
            blobs: [tooLarge, tooLarge, finalBlob],
        });
        const webp = new File(['webp'], 'receipt.webp', { type: 'image/webp' });

        await expect(prepareReceiptImage(webp)).resolves.toMatchObject({
            name: 'receipt.jpg',
            type: 'image/jpeg',
        });
        expect(mocks.toBlob.mock.calls.map((call) => call[2])).toEqual([0.88, 0.78, 0.68]);
    });

    it('fails clearly when the browser cannot decode the selected image', async () => {
        class FailingImage {
            set src(value) {
                this.source = value;
                queueMicrotask(() => this.onerror());
            }
        }

        const revokeObjectURL = vi.fn();
        vi.stubGlobal('Image', FailingImage);
        vi.stubGlobal('URL', {
            createObjectURL: vi.fn(() => 'blob:invalid'),
            revokeObjectURL,
        });
        const heic = new File(['invalid'], 'receipt.heic', { type: 'image/heic' });

        await expect(prepareReceiptImage(heic)).rejects.toThrow('could not decode');
        expect(revokeObjectURL).toHaveBeenCalledWith('blob:invalid');
    });
});
