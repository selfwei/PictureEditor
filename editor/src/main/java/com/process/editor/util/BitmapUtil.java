package com.process.editor.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import com.process.editor.PictureEditor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class BitmapUtil {
    public static Bitmap getBitmapFromUri(Context context, Uri imageUri, int reqWidth, int reqHeight) {
        if (imageUri == null) {
            return null;
        }
        InputStream inputStream = null;
        InputStream exifInterfaceStream = null;
        InputStream optionStream = null;
        ExifInterface exifInterface = null;
        Bitmap scaledBitmap = null;
        try {
            optionStream = context.getContentResolver().openInputStream(imageUri);
            BitmapFactory.Options options = getImageDimensions(optionStream);
            optionStream.close();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                exifInterfaceStream = context.getContentResolver().openInputStream(imageUri);
                exifInterface = new ExifInterface(exifInterfaceStream);
                exifInterfaceStream.close();
            }
            inputStream = context.getContentResolver().openInputStream(imageUri);
            scaledBitmap = decodeSampledBitmapFromStream(inputStream, options, exifInterface, reqWidth, reqHeight, false);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Utils.closeAll(inputStream, exifInterfaceStream, optionStream);
        }
        return scaledBitmap;
    }

    public static BitmapFactory.Options getImageDimensions(InputStream inputStream) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        FlushedInputStream fis = new FlushedInputStream(inputStream);
        BitmapFactory.decodeStream(fis, null, options);
        try {
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return options;
    }


    public static Bitmap decodeSampledBitmapFromStream(InputStream inputStream, BitmapFactory.Options options, ExifInterface exif, int reqWidth, int reqHeight, boolean isForThumbnail) throws IOException {
        options.inJustDecodeBounds = true;
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        Bitmap scaledBitmap = BitmapFactory.decodeStream(inputStream, null, options);
        if (exif != null && scaledBitmap != null) {//????????????scaledBitmap?????? ???????????????
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
            Matrix matrix = new Matrix();
            if (orientation == 6) {
                matrix.postRotate(90);
            } else if (orientation == 3) {
                matrix.postRotate(180);
            } else if (orientation == 8) {
                matrix.postRotate(270);
            }

            int bitmapHeight = scaledBitmap.getHeight();
            int bitmapWidth = scaledBitmap.getWidth();
            //?????????????????????????????????????????????????????????????????????????????????
            int picSizeLimit = PictureEditor.getInstance().getPicSizeLimit();
            if (isForThumbnail) {
                if (scaledBitmap.getHeight() > picSizeLimit) {
                    bitmapHeight = picSizeLimit;
                }
                if (scaledBitmap.getWidth() > picSizeLimit) {
                    bitmapWidth = picSizeLimit;
                }
            }
            scaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, bitmapWidth, bitmapHeight, matrix, true);

        }
        return scaledBitmap;
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * ????????????????????????????????????????????????????????????????????????????????????Android Q?????????????????????????????????????????????
     * ??????????????????????????? MediaStore ????????????
     */
    public static boolean saveBitmapFile(Bitmap bitmap, String filePath) {
        if (bitmap == null) { //
            return false;
        }
        FileOutputStream fos = null;
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            Utils.closeAll(fos);
        }
    }

    public static boolean saveBitmapWithAndroidQ(Context context, Bitmap bitmap, String displayName) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
        String savePath = Environment.DIRECTORY_DCIM + File.separator + PictureEditor.getInstance().getRelativePath() + File.separator;
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, savePath);
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        ContentResolver contentResolver = context.getContentResolver();
        Uri insertUri = insert(contentResolver, uri, contentValues);
        if (insertUri != null) {
            try (OutputStream outputStream = contentResolver.openOutputStream(insertUri)) {
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                    return true;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private static Uri insert(ContentResolver resolver, Uri uri, ContentValues values) {
        Uri insertUri = resolver.insert(uri, values);
        if (insertUri == null) {
            String originFileName = values.getAsString(MediaStore.Images.Media.DISPLAY_NAME);
            values.put(MediaStore.Images.Media.DISPLAY_NAME, appendFileNameTimeSuffix(originFileName));
            insertUri = resolver.insert(uri, values);
        }
        return insertUri;
    }

    /**
     * ???????????????????????? (?????????) ??????
     *
     */
    private static String appendFileNameTimeSuffix(String originFileName) {
        String appendName = "(" + System.currentTimeMillis() + ")";
        return appendFileSuffix(originFileName, appendName);
    }

    /**
     * ?????????????????????
     *
     * ???????????????
     * 1. ??????????????????????????? . ?????????????????????????????????
     * 2. ??????????????????. , ?????????????????????????????????????????????????????????????????????????????????
     */
    private static String appendFileSuffix(String originFileName, String suffix) {
        String resultFileName;
        int i = originFileName.lastIndexOf(".");
        if (i == -1) {
            resultFileName = originFileName + suffix;
        } else if (i == 0) {
            resultFileName = suffix + originFileName;
        } else {
            resultFileName = originFileName.substring(0, i) + suffix + originFileName.substring(i);
        }
        return resultFileName;
    }

}
