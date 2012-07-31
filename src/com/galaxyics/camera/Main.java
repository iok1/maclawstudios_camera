/************************************************************************/
/* MaclawStudios Camera App for Samsung Galaxy Ace and Gio              */
/* Copyright (C) 2012 Pavel Kirpichyov & Marcin Chojnacki & MaclawStudios                  */
/*                                                                      */
/* This program is free software: you can redistribute it and/or modify */
/* it under the terms of the GNU General Public License as published by */
/* the Free Software Foundation, either version 3 of the License, or    */
/* (at your option) any later version.                                  */
/*                                                                      */
/* This program is distributed in the hope that it will be useful,      */
/* but WITHOUT ANY WARRANTY; without even the implied warranty of       */
/* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the         */
/* GNU General Public License for more details.                         */
/*                                                                      */
/* You should have received a copy of the GNU General Public License    */
/* along with this program.  If not, see <http://www.gnu.org/licenses/> */
/************************************************************************/

package com.galaxyics.camera;

import java.io.File;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

public class Main extends Activity implements Camera.PreviewCallback, SurfaceHolder.Callback {
	static {
		System.loadLibrary("nativeyuv");
	}

	private native void nativeYUV(byte[] yuv, int[] rgb, int width, int height);

	private Camera mCameraDevice;
	private Bitmap mBitmap;
	private int[] buffer;
	private int orient;

	private int alpha;
	private Thumbnail mThumbnail;
	private String mStorage;
	private RotateImageView mThumbnailView;
	private ImageSaver mImageSaver;
	public static final String TAG = "Camera";
	private static final int PREVIEW_STOPPED = 0;
	private static final int IDLE = 1; // preview is active
	// Focus is in progress. The exact focus state is in Focus.java.
	private static final int FOCUSING = 2;
	private Parameters mParameters;
	private boolean mFirstTimeInitialized;
	private ContentResolver mContentResolver;
	private static final int SNAPSHOT_IN_PROGRESS = 3;
	private int mCameraState = PREVIEW_STOPPED;
	private boolean mOpenCameraFail = false;
	private SurfaceHolder mSurfaceHolder = null;
	private static final String model = Build.MODEL;
	private int mDisplayRotation;
	private int mDisplayOrientation;
	private long mPicturesRemaining;

	Thread mCameraOpenThread = new Thread(new Runnable() {
		public void run() {
			try {
				mCameraDevice = Camera.open();
			} catch (RuntimeException e) {
				mOpenCameraFail = true;
			}
		}
	});

	Thread mCameraPreviewThread = new Thread(new Runnable() {
		public void run() {
			initializeCapabilities();
			startPreview();
		}
	});

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mStorage = Environment.getExternalStorageDirectory().toString();
		/*
		 * To reduce startup time, we start the camera open and preview threads.
		 * We make sure the preview is started at the end of onCreate.
		 */
		mCameraOpenThread.start();

		if (!(model.compareTo("GT-S5830") == 0 || model.compareTo("GT-S5660") == 0)) {
			Util.showErrorAndFinish(this, R.string.unsupported);
		}

		setContentView(R.layout.main);

		mThumbnailView = (RotateImageView) findViewById(R.id.thumbnail);
		mThumbnailView.enableFilter(false);
		mThumbnailView.setVisibility(View.VISIBLE);

		mBitmap = Bitmap.createBitmap(320, 240, Config.RGB_565);
		buffer = new int[320 * 240];

		SurfaceView preview = (SurfaceView) findViewById(R.id.camera_preview);
		SurfaceHolder holder = preview.getHolder();
		holder.addCallback(this);

		// Make sure camera device is opened.
		try {

			mCameraOpenThread.join();
			mCameraOpenThread = null;
			if (mOpenCameraFail) {
				Util.showErrorAndFinish(this, R.string.cannot_connect_camera);
				return;
			}
		} catch (InterruptedException ex) {
			// ignore
		}
		mCameraPreviewThread.start();

		// Wait until the camera settings are retrieved.
		synchronized (mCameraPreviewThread) {
			try {
				mCameraPreviewThread.wait();
			} catch (InterruptedException ex) {
				// ignore
			}
		}

		// Make sure preview is started.
		try {
			mCameraPreviewThread.join();
		} catch (InterruptedException ex) {
			// ignore
		}
		mCameraPreviewThread = null;

		Toast.makeText(getApplicationContext(), getString(R.string.hello), Toast.LENGTH_LONG).show();
	}

	@Override
	protected void onPause() {
		Log.i(TAG, "ONPAUSE");
		stopCamera();
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (mOpenCameraFail)
			return;

		if (mCameraState == PREVIEW_STOPPED) {
			try {
				mCameraDevice = Camera.open();
				initializeCapabilities();
				startPreview();
			} catch (RuntimeException e) {
				Util.showErrorAndFinish(this, R.string.cannot_connect_camera);
				return;
			}
		}

		if (mSurfaceHolder != null) {
			// If first time initialization is not finished, put it in the
			// message queue.
			if (!mFirstTimeInitialized) {
				initializeFirstTime();
			} else {
				initializeSecondTime();
			}
		}
	}

	private void initializeSecondTime() {
		mImageSaver = new ImageSaver();
		checkStorage();
	}

	private void initializeFirstTime() {
		if (mFirstTimeInitialized)
			return;

		// Initialize last picture button.
		checkStorage();

		mContentResolver = getContentResolver();
		initThumbnailButton();

		Util.initialize(getApplicationContext());

		mImageSaver = new ImageSaver();

		mFirstTimeInitialized = true;
	}

	private void initializeCapabilities() {

		if (mCameraDevice == null) {
			return;
		}
		mParameters = mCameraDevice.getParameters();

		if (model.compareTo("GT-S5830") == 0) {
			mParameters.setPictureSize(2560, 1920);
			mParameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
		} else if (model.compareTo("GT-S5660") == 0) {
			mParameters.setPictureSize(2048, 1536);
		}

		mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
		mCameraDevice.setParameters(mParameters);

		setVolumeControlStream(AudioManager.STREAM_MUSIC);

	}

	private void startPreview() {

		if (mCameraState != PREVIEW_STOPPED)
			stopPreview();

		setDisplayOrientation();

		// Inform the mainthread to go on the UI initialization.
		if (mCameraPreviewThread != null) {
			synchronized (mCameraPreviewThread) {
				mCameraPreviewThread.notify();
			}
		}

		try {
			Log.v(TAG, "startPreview");
			mCameraDevice.setPreviewCallback(this);
			mCameraDevice.startPreview();
		} catch (Throwable ex) {
			closeCamera();
			throw new RuntimeException("startPreview failed", ex);
		}

		setCameraState(IDLE);
	}

	private void stopCamera() {
		stopPreview();
		closeCamera();

		if (mFirstTimeInitialized) {
			if (mImageSaver != null) {
				mImageSaver.finish();
				mImageSaver = null;
			}
			if (mThumbnail != null && !mThumbnail.fromFile()) {
				mThumbnail.saveTo(new File(getFilesDir(), Thumbnail.LAST_THUMB_FILENAME));
			}
		}
	}

	private void initThumbnailButton() {
		// Load the thumbnail from the disk.
		mThumbnail = Thumbnail.loadFrom(new File(getFilesDir(), Thumbnail.LAST_THUMB_FILENAME));
		updateThumbnailButton();
	}

	private void checkStorage() {
		mPicturesRemaining = Storage.getAvailableSpace(mStorage);
		if (mPicturesRemaining > Storage.LOW_STORAGE_THRESHOLD) {
			mPicturesRemaining = (mPicturesRemaining - Storage.LOW_STORAGE_THRESHOLD) / Storage.PICTURE_SIZE;
		} else if (mPicturesRemaining > 0) {
			mPicturesRemaining = 0;
		}

		updateStorageHint();
	}

	private void updateStorageHint() {
		String noStorageText = null;

		if (mPicturesRemaining == Storage.UNAVAILABLE) {
			noStorageText = "no storage";
		} else if (mPicturesRemaining == Storage.PREPARING) {
			noStorageText = "preparing sd";
		} else if (mPicturesRemaining == Storage.UNKNOWN_SIZE) {
			noStorageText = "access_sd_fail";
		} else if (mPicturesRemaining < 1L) {
			noStorageText = "not_enough_space";
		}

		if (noStorageText != null) {
			Toast.makeText(getApplicationContext(), noStorageText, Toast.LENGTH_SHORT).show();
		}
	}

	private void updateThumbnailButton() {
		// Update last image if URI is invalid and the storage is ready.
		Log.i(TAG, "UPDATE BUTTON");
		if ((mThumbnail == null || !Util.isUriValid(mThumbnail.getUri(), mContentResolver)) && mPicturesRemaining >= 0) {
			mThumbnail = Thumbnail.getLastThumbnail(mContentResolver, Storage.generateBucketId(mStorage));
			if (mThumbnail != null) {
				Log.i(TAG, "UPDATE BUTTON 1: " + mThumbnail.getBitmap().getWidth());
			}
		} else {
			Log.i(TAG, "UPDATE BUTTON 2: " + mThumbnail.getBitmap().getWidth());
		}

		if (mThumbnail != null) {
			mThumbnailView.setBitmap(mThumbnail.getBitmap());
		} else {
			mThumbnailView.setBitmap(null);
		}
	}

	private void closeCamera() {
		Log.i(TAG, "CLOSE_CAMERA");
		if (mCameraDevice != null) {
			Log.i(TAG, "RELEASE_CAMERA");
			mCameraDevice.release();
			mCameraDevice = null;

			setCameraState(PREVIEW_STOPPED);
		}

	}

	private void setCameraState(int state) {
		mCameraState = state;
		switch (state) {
		case SNAPSHOT_IN_PROGRESS:
		case FOCUSING:
			enableCameraControls(false);
			break;
		case IDLE:
		case PREVIEW_STOPPED:
			enableCameraControls(true);
			break;
		}
	}

	private void enableCameraControls(boolean enabled) {

		final ImageView shutter = (ImageView) findViewById(R.id.shutter_button);
		shutter.setClickable(enabled);
	}

	public void onThumbnailClicked(View v) {

	}

	private void stopPreview() {
		if (mCameraDevice != null && mCameraState != PREVIEW_STOPPED) {
			Log.v(TAG, "stopPreview");
			mCameraDevice.cancelAutoFocus();
			mCameraDevice.stopPreview();
		}
		setCameraState(PREVIEW_STOPPED);
	}

	public void onPreviewFrame(byte[] data, Camera camera) {
		try {
			nativeYUV(data, buffer, 320, 240);
			mBitmap.setPixels(buffer, 0, 320, 0, 0, 320, 240);
			Canvas canvas = mSurfaceHolder.lockCanvas();

			canvas.scale((float) canvas.getWidth() / 320, (float) canvas.getHeight() / 240);
			canvas.drawBitmap(mBitmap, 0, 0, null);

			mSurfaceHolder.unlockCanvasAndPost(canvas);
		} catch (Exception e) {
			return;
		}
	}

	private void blink() {
		alpha = 250;
		final Timer timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				SurfaceHolder holder = ((SurfaceView) findViewById(R.id.camera_preview)).getHolder();
				Canvas canvas = holder.lockCanvas();
				canvas.scale((float) canvas.getWidth() / 320, (float) canvas.getHeight() / 240);
				canvas.drawBitmap(mBitmap, 0, 0, null);
				Paint paint = new Paint();
				paint.setColor(Color.WHITE);
				paint.setAlpha(alpha);
				canvas.drawRect(0, 0, 320, 240, paint);
				holder.unlockCanvasAndPost(canvas);
				alpha -= 5;
				if (alpha == 0)
					timer.cancel();
			}
		}, 10, 10);
		MediaPlayer mp = MediaPlayer.create(this, R.raw.shutter);
		mp.start();
		mp.setOnCompletionListener(new OnCompletionListener() {
			public void onCompletion(MediaPlayer mp) {
				mp.release();
			}
		});

	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		if (holder.getSurface() == null) {
			Log.d(TAG, "holder.getSurface() == null");
			return;
		}
		Log.v(TAG, "surfaceChanged. w=" + w + ". h=" + h);
		// We need to save the holder for later use, even when the mCameraDevice
		// is null. This could happen if onResume() is invoked after this
		// function.
		mSurfaceHolder = holder;
		// The mCameraDevice will be null if it fails to connect to the camera
		// hardware. In this case we will show a dialog and then finish the
		// activity, so it's OK to ignore it.
		if (mCameraDevice == null)
			return;

		if (mCameraState == PREVIEW_STOPPED) {
			startPreview();
		} else {

			if (Util.getDisplayRotation(this) != mDisplayRotation) {
				setDisplayOrientation();
			}
		}

		if (!mFirstTimeInitialized) {
			initializeFirstTime();
		} else {
			initializeSecondTime();
		}
	}

	private void setDisplayOrientation() {
		mDisplayRotation = Util.getDisplayRotation(this);
		mDisplayOrientation = Util.getDisplayOrientation(mDisplayRotation);
		mCameraDevice.setDisplayOrientation(mDisplayOrientation);
	}

	public void surfaceCreated(SurfaceHolder holder) {
		// TODO Auto-generated method stub

	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		stopPreview();
		mSurfaceHolder = null;
	}

	private class ImageSaver extends Thread {
		private static final int QUEUE_LIMIT = 3;

		private ArrayList<SaveRequest> mQueue;
		private Thumbnail mPendingThumbnail;
		private Object mUpdateThumbnailLock = new Object();
		private boolean mStop;

		// Runs in main thread
		public ImageSaver() {
			mQueue = new ArrayList<SaveRequest>();
			start();
		}

		// Runs in main thread
		public void addImage(final byte[] data, int width, int height) {

			SaveRequest r = new SaveRequest();
			r.data = data;
			r.width = width;
			r.height = height;
			r.dateTaken = System.currentTimeMillis();

			synchronized (this) {
				while (mQueue.size() >= QUEUE_LIMIT) {
					try {
						wait();
					} catch (InterruptedException ex) {
						// ignore.
					}
				}
				mQueue.add(r);
				notifyAll(); // Tell saver thread there is new work to do.
			}
		}

		// Runs in saver thread
		@Override
		public void run() {
			while (true) {
				SaveRequest r;
				synchronized (this) {
					if (mQueue.isEmpty()) {
						notifyAll(); // notify main thread in waitDone

						// Note that we can only stop after we saved all images
						// in the queue.
						if (mStop)
							break;

						try {
							wait();
						} catch (InterruptedException ex) {
							// ignore.
						}
						continue;
					}
					r = mQueue.get(0);
				}
				storeImage(r.data, r.width, r.height, r.dateTaken, r.previewWidth);
				synchronized (this) {
					mQueue.remove(0);
					notifyAll(); // the main thread may wait in addImage
				}
			}
		}

		// Runs in main thread
		public void waitDone() {
			synchronized (this) {
				while (!mQueue.isEmpty()) {
					try {
						wait();
					} catch (InterruptedException ex) {
						// ignore.
					}
				}
			}
			updateThumbnail();
		}

		// Runs in main thread
		public void finish() {
			waitDone();
			synchronized (this) {
				mStop = true;
				notifyAll();
			}
			try {
				join();
			} catch (InterruptedException ex) {
				// ignore.
			}
		}

		// Runs in main thread (because we need to update mThumbnailView in the
		// main thread)
		public void updateThumbnail() {
			Thumbnail t;
			synchronized (mUpdateThumbnailLock) {
				t = mPendingThumbnail;
				mPendingThumbnail = null;
			}

			if (t != null) {
				mThumbnail = t;
				mThumbnailView.setBitmap(mThumbnail.getBitmap());
			}

		}

		// Runs in saver thread
		private void storeImage(final byte[] data, int width, int height, long dateTaken, int previewWidth) {
			String title = Util.createJpegName(dateTaken);
			int orientation = Exif.getOrientation(data);
			Uri uri = Storage.addImage(mContentResolver, mStorage, title, dateTaken, orientation, data, width, height);
			if (uri != null) {
				boolean needThumbnail;
				synchronized (this) {
					// If the number of requests in the queue (include the
					// current one) is greater than 1, we don't need to generate
					// thumbnail for this image. Because we'll soon replace it
					// with the thumbnail for some image later in the queue.
					needThumbnail = (mQueue.size() <= 1);
				}
				if (needThumbnail) {
					// Create a thumbnail whose width is equal or bigger than
					// that of the preview.
					int ratio = (int) Math.ceil((double) width / previewWidth);
					int inSampleSize = Integer.highestOneBit(ratio);
					Thumbnail t = Thumbnail.createThumbnail(data, orientation, inSampleSize, uri);
					synchronized (mUpdateThumbnailLock) {
						// We need to update the thumbnail in the main thread,
						// so send a message to run updateThumbnail().
						mPendingThumbnail = t;
					}
				}

				Util.broadcastNewPicture(Main.this, uri);
			}
		}
	}

	public void onSnapshot(View v) {
		Camera.PictureCallback callback = new Camera.PictureCallback() {
			public void onPictureTaken(byte[] jpegData, Camera camera) {
				setCameraState(PREVIEW_STOPPED);

				startPreview();

				Size size = mParameters.getPictureSize();
				mImageSaver.addImage(jpegData, size.width, size.height);

				checkStorage();
			}
		};
		setCameraState(SNAPSHOT_IN_PROGRESS);
		mCameraDevice.takePicture(null, null, callback);
		blink();
	}
}