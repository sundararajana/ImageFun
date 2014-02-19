/*
 * Copyright (C) 2014 Sundararajan Athijegannathan
 * 
 * This file is part of ImageFun.
 * 
 * ImageFun is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ImageFun is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ImageFun.  If not, see <http://www.gnu.org/licenses/>.
 */

package in.coursera.assign3.imagefun;

import java.io.*;

import android.net.Uri;
import android.os.*;
import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.graphics.Bitmap.CompressFormat;
import android.util.*;
import android.view.*;
import android.view.View.OnKeyListener;
import android.widget.*;

/**
 * Simple "Image Fun" tool to edit/save/share external SD card images.
 */
public class MainActivity extends Activity {
	private static final int REQUEST_CODE = 100;
	private static final String TAG = MainActivity.class.getName();

	// screen width and height
	private int mDisplayWidth, mDisplayHeight;

	// External SD card pictures directory
	private File mPicturesDir;
	private ImageView imageView;

	// details of the current image
	private Uri mUri;
	private Bitmap mBitmap;
	private int mBitmapWidth, mBitmapHeight;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		DisplayMetrics dm = getResources().getDisplayMetrics();
		mDisplayWidth = dm.widthPixels;
		mDisplayHeight = dm.heightPixels;
		mPicturesDir = Environment
				.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
		Log.d(TAG, "external pictures dir = " + mPicturesDir);
		mPicturesDir.mkdirs();
		imageView = (ImageView) findViewById(R.id.imageView);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Menu handling for various image editing operations
		switch (item.getItemId()) {
		case R.id.action_gray_scale:
			if (mBitmap != null) {
				grayScale();
			}
			return true;

		case R.id.action_red_filter:
			if (mBitmap != null) {
				redFilter();
			}
			return true;

		case R.id.action_green_filter:
			if (mBitmap != null) {
				greenFilter();
			}
			return true;

		case R.id.action_blue_filter:
			if (mBitmap != null) {
				blueFilter();
			}
			return true;

		case R.id.action_watermark:
			if (mBitmap != null) {
				watermark();
			}
			return true;
		}

		return super.onOptionsItemSelected(item);
	}
	
	public void onChooseImage(View view) {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("image/*");
		startActivityForResult(Intent.createChooser(intent, "Select Image..."),
				REQUEST_CODE);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
			openMutableBitmap(data.getData());
			if (mBitmap != null) {
				imageView.setImageBitmap(mBitmap);
			}
		}
	}

	// calculate 'safe' sample size to avoid OutOfMemoryError
	private int calculateSampleSize(Uri uri) {
		InputStream stream = null;
		int sample = 1;
		try {
			stream = getContentResolver().openInputStream(uri);
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeStream(stream, null, options);

			while (options.outWidth > mDisplayWidth * sample
					|| options.outHeight > mDisplayHeight * sample) {
				sample = sample * 2;
			}
			return sample;
		} catch (Exception exp) {
			Log.e(TAG, "cannot get image size", exp);
			return sample;
		} finally {
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	// Open a mutable Bitmap referred by Uri after adjusting sample size, if
	// needed
	private void openMutableBitmap(Uri uri) {
		if (mBitmap != null) {
			mBitmap.recycle();
		}
		final int sampleSize = calculateSampleSize(uri);
		InputStream stream = null;
		Bitmap bm = null;
		try {
			stream = getContentResolver().openInputStream(uri);
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = sampleSize;
			bm = BitmapFactory.decodeStream(stream, null, options);
		} catch (Exception exp) {
			Log.e(TAG, "cannot make mutable bitmap", exp);
			return;
		} finally {
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException ignored) {
				}
			}
		}

		mBitmap = Bitmap.createBitmap(bm.getWidth(), bm.getHeight(),
				Bitmap.Config.ARGB_8888);
		mBitmapWidth = mBitmap.getWidth();
		mBitmapHeight = mBitmap.getHeight();
		Canvas c = new Canvas(mBitmap);
		c.drawBitmap(bm, 0, 0, null);
		bm.recycle();
	}

	// save the current bitmap to a file in the external storage
	public void onSaveImage(View view) {
		if (mBitmap == null) {
			return;
		}
		String filename = "ImageFun_" + System.currentTimeMillis() + ".png";
		File file = new File(mPicturesDir, filename);
		FileOutputStream stream = null;
		try {
			stream = new FileOutputStream(file);
			mBitmap.compress(CompressFormat.PNG, 100, stream);
		} catch (Exception e) {
			Log.e(TAG, "cannot save bitmap", e);
			return;
		}

		// advertise this new image to android
		mUri = Uri.fromFile(file);
		Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
		intent.setData(mUri);
		sendBroadcast(intent);
	}

	// share the current bitmap via an Intent
	public void onShareImage(View view) {
		if (mBitmap == null) {
			return;
		}
		onSaveImage(view);
		Intent share = new Intent(Intent.ACTION_SEND);
		share.setType("image/png");
		share.putExtra(Intent.EXTRA_STREAM, mUri);
		startActivity(Intent.createChooser(share, "Share using..."));
	}

	// filter to produce gray scale image out of color image
	private void grayScale() {
		int[] pixels = getPixels(mBitmapWidth, mBitmapHeight);
		for (int i = 0; i < pixels.length; i++) {
			int color = pixels[i];
			int alpha = Color.alpha(color);
			int red = Color.red(color);
			int green = Color.green(color);
			int blue = Color.blue(color);
			int avg = (red + green + blue) / 3;
			pixels[i] = Color.argb(alpha, avg, avg, avg);
		}
		mBitmap.setPixels(pixels, 0, mBitmapWidth, 0, 0, mBitmapWidth,
				mBitmapHeight);
	}

	// filter other colors except red
	private void redFilter() {
		int[] pixels = getPixels(mBitmapWidth, mBitmapHeight);
		for (int i = 0; i < pixels.length; i++) {
			int color = pixels[i];
			pixels[i] = Color.argb(Color.alpha(color), Color.red(color), 0, 0);
		}
		mBitmap.setPixels(pixels, 0, mBitmapWidth, 0, 0, mBitmapWidth,
				mBitmapHeight);
	}

	// filter other colors except green
	private void greenFilter() {
		int[] pixels = getPixels(mBitmapWidth, mBitmapHeight);
		for (int i = 0; i < pixels.length; i++) {
			int color = pixels[i];
			pixels[i] = Color
					.argb(Color.alpha(color), 0, Color.green(color), 0);
		}
		mBitmap.setPixels(pixels, 0, mBitmapWidth, 0, 0, mBitmapWidth,
				mBitmapHeight);
	}

	// filter other colors except blue
	private void blueFilter() {
		int[] pixels = getPixels(mBitmapWidth, mBitmapHeight);
		for (int i = 0; i < pixels.length; i++) {
			int color = pixels[i];
			pixels[i] = Color.argb(Color.alpha(color), 0, 0, Color.blue(color));
		}
		mBitmap.setPixels(pixels, 0, mBitmapWidth, 0, 0, mBitmapWidth,
				mBitmapHeight);
	}

	// add a watermark text to the image
	private void watermark() {
		// show a dialog box asking for watermark text
		final Dialog dialog = new Dialog(this);
		dialog.setContentView(R.layout.text_dialog);
		dialog.setTitle("Enter text...");
		final EditText editText = (EditText) dialog
				.findViewById(R.id.watermarkText);
		editText.setOnKeyListener(new OnKeyListener() {
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if ((event.getAction() == KeyEvent.ACTION_DOWN)
						&& (keyCode == KeyEvent.KEYCODE_ENTER)) {
					String text = editText.getText().toString();
					if (!text.isEmpty()) {
						putTextInBitmap(text);
					}
					dialog.dismiss();
					return true;
				}
				return false;
			}
		});
		dialog.show();
	}

	// put the given text into the image
	private void putTextInBitmap(String text) {
		Canvas canvas = new Canvas(mBitmap);
		Paint paint = new Paint();
		paint.setTextSize(30);
		paint.setColor(0xffff0000);
		canvas.drawText(text, 50, 50, paint);
	}

	// retrieves pixels array of the current image
	private int[] getPixels(int w, int h) {
		int[] pixels = new int[w * h];
		mBitmap.getPixels(pixels, 0, w, 0, 0, w, h);
		return pixels;
	}
}
// This is line 300! Done within assignment limit ;-)
