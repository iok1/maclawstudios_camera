package com.galaxyics.camera;

import java.io.Closeable;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Camera;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Surface;

public class Util {

	private static final String TAG = "Util";
	private static ImageFileNamer sImageFileNamer;

	public static void initialize(Context context) {

		sImageFileNamer = new ImageFileNamer(context.getString(R.string.image_file_name_format));
	}

	public static void showErrorAndFinish(final Activity activity, int msgId) {
		DialogInterface.OnClickListener buttonListener = new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				activity.finish();
			}
		};
		new AlertDialog.Builder(activity).setCancelable(false).setIconAttribute(android.R.attr.alertDialogIcon).setTitle(R.string.camera_error_title).setMessage(msgId)
				.setNeutralButton(R.string.dialog_ok, buttonListener).show();
	}

	public static int getDisplayRotation(Activity activity) {
		int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
		switch (rotation) {
		case Surface.ROTATION_0:
			return 0;
		case Surface.ROTATION_90:
			return 90;
		case Surface.ROTATION_180:
			return 180;
		case Surface.ROTATION_270:
			return 270;
		}
		return 0;
	}

	public static String createJpegName(long dateTaken) {
		synchronized (sImageFileNamer) {
			return sImageFileNamer.generateName(dateTaken);
		}
	}

	public static int getDisplayOrientation(int degrees) {
		// See android.hardware.Camera.setDisplayOrientation for
		// documentation.
		Camera.CameraInfo info = new Camera.CameraInfo();
		Camera.getCameraInfo(0, info);
		int result;
		/*
		 * if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) { result =
		 * (info.orientation + degrees) % 360; result = (360 - result) % 360; //
		 * compensate the mirror } else { // back-facing
		 */
		result = (info.orientation - degrees + 360) % 360;
		// }
		return result;
	}

	public static boolean isUriValid(Uri uri, ContentResolver resolver) {
		if (uri == null)
			return false;

		try {
			ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r");
			if (pfd == null) {
				Log.e(TAG, "Fail to open URI. URI=" + uri);
				return false;
			}
			pfd.close();
		} catch (IOException ex) {
			return false;
		}
		return true;
	}

	public static void closeSilently(Closeable c) {
		if (c == null)
			return;
		try {
			c.close();
		} catch (Throwable t) {
			// do nothing
		}
	}

	private static class ImageFileNamer {
		private SimpleDateFormat mFormat;

		// The date (in milliseconds) used to generate the last name.
		private long mLastDate;

		// Number of names generated for the same second.
		private int mSameSecondCount;

		public ImageFileNamer(String format) {
			mFormat = new SimpleDateFormat(format);
		}

		public String generateName(long dateTaken) {
			Date date = new Date(dateTaken);
			String result = mFormat.format(date);

			// If the last name was generated for the same second,
			// we append _1, _2, etc to the name.
			if (dateTaken / 1000 == mLastDate / 1000) {
				mSameSecondCount++;
				result += "_" + mSameSecondCount;
			} else {
				mLastDate = dateTaken;
				mSameSecondCount = 0;
			}

			return result;
		}
	}

	public static void broadcastNewPicture(Context context, Uri uri) {
		context.sendBroadcast(new Intent(Camera.ACTION_NEW_PICTURE, uri));
		// Keep compatibility
		context.sendBroadcast(new Intent("com.android.camera.NEW_PICTURE", uri));
	}
}
