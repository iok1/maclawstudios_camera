package com.galaxyics.camera;

import java.io.File;
import java.io.FileOutputStream;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;

public class Storage {
	private static final String TAG = "CameraStorage";

	public static final long UNAVAILABLE = -1L;
	public static final long PREPARING = -2L;
	public static final long UNKNOWN_SIZE = -3L;
	public static final long LOW_STORAGE_THRESHOLD = 50000000;
	public static final long PICTURE_SIZE = 1500000;

	public static Uri addImage(ContentResolver resolver, String storage, String title, long date, int orientation, byte[] jpeg, int width, int height) {
		// Save the image.
		String path = generateFilepath(storage, title);
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(path);
			out.write(jpeg);
		} catch (Exception e) {
			Log.e(TAG, "Failed to write image", e);
			return null;
		} finally {
			try {
				out.close();
			} catch (Exception e) {
			}
		}

		// Insert into MediaStore.
		ContentValues values = new ContentValues(9);
		values.put(ImageColumns.TITLE, title);
		values.put(ImageColumns.DISPLAY_NAME, title + ".jpg");
		values.put(ImageColumns.DATE_TAKEN, date);
		values.put(ImageColumns.MIME_TYPE, "image/jpeg");
		values.put(ImageColumns.ORIENTATION, orientation);
		values.put(ImageColumns.DATA, path);
		values.put(ImageColumns.SIZE, jpeg.length);
		// values.put(ImageColumns.WIDTH, width);
		// values.put(ImageColumns.HEIGHT, height);

		Uri uri = null;
		try {
			uri = resolver.insert(Images.Media.EXTERNAL_CONTENT_URI, values);
		} catch (Throwable th) {
			// This can happen when the external volume is already mounted, but
			// MediaScanner has not notify MediaProvider to add that volume.
			// The picture is still safe and MediaScanner will find it and
			// insert it into MediaProvider. The only problem is that the user
			// cannot click the thumbnail to review the picture.
			Log.e(TAG, "Failed to write MediaStore" + th);
		}
		return uri;
	}

	public static String generateDCIM(String storage) {
		return new File(storage, Environment.DIRECTORY_DCIM).toString();
	}

	public static String generateDirectory(String storage) {
		return generateDCIM(storage) + "/Camera";
	}

	public static String generateFilepath(String storage, String title) {
		return generateDirectory(storage) + '/' + title + ".jpg";
	}

	public static String generateBucketId(String storage) {
		// Match the code in MediaProvider.computeBucketValues().
		return String.valueOf(generateDirectory(storage).toLowerCase().hashCode());
	}

	public static long getAvailableSpace(String storage) {
		String state = Environment.getExternalStorageState();
		Log.d(TAG, "External storage state=" + state);
		if (Environment.MEDIA_CHECKING.equals(state)) {
			return PREPARING;
		}
		if (!Environment.MEDIA_MOUNTED.equals(state)) {
			return UNAVAILABLE;
		}

		String directory = generateDirectory(storage);
		File dir = new File(directory);
		dir.mkdirs();
		if (!dir.isDirectory() || !dir.canWrite()) {
			return UNAVAILABLE;
		}

		try {
			StatFs stat = new StatFs(directory);
			return stat.getAvailableBlocks() * (long) stat.getBlockSize();
		} catch (Exception e) {
			Log.i(TAG, "Fail to access external storage", e);
		}
		return UNKNOWN_SIZE;
	}

	/**
	 * OSX requires plugged-in USB storage to have path /DCIM/NNNAAAAA to be
	 * imported. This is a temporary fix for bug#1655552.
	 */
	public static void ensureOSXCompatible(String storage) {
		File nnnAAAAA = new File(generateDCIM(storage), "100ANDRO");
		if (!(nnnAAAAA.exists() || nnnAAAAA.mkdirs())) {
			Log.e(TAG, "Failed to create " + nnnAAAAA.getPath());
		}
	}
}