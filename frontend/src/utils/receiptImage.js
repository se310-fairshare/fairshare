const MAX_UPLOAD_BYTES = 7 * 1024 * 1024;
const MAX_IMAGE_DIMENSION = 2400;
const JPEG_QUALITIES = [0.88, 0.78, 0.68, 0.58];

const SERVER_IMAGE_TYPES = new Set(['image/jpeg', 'image/png']);
const IMAGE_FILE_EXTENSION = /\.(?:avif|heic|heif|jpe?g|png|webp)$/i;

export function isReceiptImage(file) {
    return Boolean(file && (
        file.type?.toLowerCase().startsWith('image/')
        || IMAGE_FILE_EXTENSION.test(file.name ?? '')
    ));
}

export function needsReceiptConversion(file) {
    return !SERVER_IMAGE_TYPES.has(file.type?.toLowerCase())
        || file.size > MAX_UPLOAD_BYTES;
}

function loadImage(file) {
    return new Promise((resolve, reject) => {
        const objectUrl = URL.createObjectURL(file);
        const image = new Image();

        image.onload = () => {
            URL.revokeObjectURL(objectUrl);
            resolve(image);
        };
        image.onerror = () => {
            URL.revokeObjectURL(objectUrl);
            reject(new Error('This browser could not decode the selected image.'));
        };
        image.src = objectUrl;
    });
}

function canvasToJpeg(canvas, quality) {
    return new Promise((resolve, reject) => {
        canvas.toBlob((blob) => {
            if (blob) {
                resolve(blob);
            } else {
                reject(new Error('This browser could not convert the selected image.'));
            }
        }, 'image/jpeg', quality);
    });
}

function jpegName(filename) {
    const basename = (filename || 'receipt').replace(/\.[^.]+$/, '');
    return `${basename}.jpg`;
}

export async function prepareReceiptImage(file) {
    if (!isReceiptImage(file)) {
        throw new Error('Choose a photo or image file.');
    }

    if (!needsReceiptConversion(file)) {
        return file;
    }

    const image = await loadImage(file);
    const sourceWidth = image.naturalWidth || image.width;
    const sourceHeight = image.naturalHeight || image.height;
    if (!sourceWidth || !sourceHeight) {
        throw new Error('The selected image has no readable dimensions.');
    }

    const scale = Math.min(1, MAX_IMAGE_DIMENSION / Math.max(sourceWidth, sourceHeight));
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(sourceWidth * scale));
    canvas.height = Math.max(1, Math.round(sourceHeight * scale));

    const context = canvas.getContext('2d');
    if (!context) {
        throw new Error('This browser could not prepare the selected image.');
    }
    context.drawImage(image, 0, 0, canvas.width, canvas.height);

    for (const quality of JPEG_QUALITIES) {
        const blob = await canvasToJpeg(canvas, quality);
        if (blob.size <= MAX_UPLOAD_BYTES) {
            return new File([blob], jpegName(file.name), {
                type: 'image/jpeg',
                lastModified: Date.now(),
            });
        }
    }

    throw new Error('The image is still larger than 7 MB after compression.');
}

export { MAX_UPLOAD_BYTES };
