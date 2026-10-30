package com.example.assignmenttask;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Pet and profile photos. A picked photo is copied into the app's private storage
 * (scaled down, as a JPEG) and loaded back later as a thumbnail. Photos never leave the phone.
 */
final class Images {

    /** The longest side of a saved photo, in pixels. */
    private static final int MAX_SIDE = 1024;

    private Images() { }

    /** Copies a picked photo into private storage. Returns the stored file name, or null if the image could not be read. */
    static String save(Context context, Uri uri) {
        try {
            // First read only the image size, so a huge photo can be decoded at a smaller size.
            BitmapFactory.Options sizeOnly = new BitmapFactory.Options();
            sizeOnly.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, sizeOnly);
            }
            if (sizeOnly.outWidth <= 0 || sizeOnly.outHeight <= 0) {
                return null;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(sizeOnly.outWidth, sizeOnly.outHeight, MAX_SIDE);
            Bitmap bitmap;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, options);
            }
            if (bitmap == null) {
                return null;
            }

            bitmap = rotateAndShrink(bitmap, readRotation(context, uri));

            String fileName = "pet_" + UUID.randomUUID() + ".jpg";
            try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), fileName))) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
            }
            return fileName;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Loads a stored photo scaled to roughly maxPx on its longest side, or returns null if it is missing. */
    static Bitmap load(Context context, String fileName, int maxPx) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        File file = new File(context.getFilesDir(), fileName);
        if (!file.exists()) {
            return null;
        }

        BitmapFactory.Options sizeOnly = new BitmapFactory.Options();
        sizeOnly.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), sizeOnly);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(sizeOnly.outWidth, sizeOnly.outHeight, maxPx);
        return BitmapFactory.decodeFile(file.getPath(), options);
    }

    /** Removes a stored photo file. */
    static void delete(Context context, String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            new File(context.getFilesDir(), fileName).delete();
        }
    }

    /**
     * How much to shrink an image while decoding. The result is a power of two,
     * chosen so the decoded image is still at least targetPx on its longest side.
     */
    private static int sampleSize(int width, int height, int targetPx) {
        int sample = 1;
        while (Math.max(width, height) / (sample * 2) >= targetPx) {
            sample *= 2;
        }
        return sample;
    }

    /** Reads how far the camera rotated the photo (0, 90, 180 or 270 degrees) from its EXIF data. */
    private static int readRotation(Context context, Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            int orientation = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (IOException | RuntimeException e) {
            return 0;   // no rotation info: use the photo as it is
        }
    }

    /** Rotates the photo upright and shrinks it so its longest side is at most MAX_SIDE. */
    private static Bitmap rotateAndShrink(Bitmap bitmap, int rotation) {
        float scale = Math.min(1f, (float) MAX_SIDE / Math.max(bitmap.getWidth(), bitmap.getHeight()));
        if (rotation == 0 && scale >= 1f) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        matrix.postScale(scale, scale);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
}
