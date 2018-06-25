package tech.degenix.artesting;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.PixelCopy;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public class MainActivity extends AppCompatActivity {

	private ArFragment arFragment;

	private PointerDrawable pointer = new PointerDrawable();
	private  boolean isTracking;
	private  boolean isHitting;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);

		arFragment.getArSceneView().getScene().setOnUpdateListener(frameTime -> {
			arFragment.onUpdate(frameTime);
			onUpdate();
		});

		initializeGallery();
		final FloatingActionButton fab = findViewById(R.id.floatingActionButton);
		fab.setOnClickListener(view -> takePhoto());
	}

	private void onUpdate() {
		boolean trackingChanged = updateTracking();

		View contentView= findViewById(android.R.id.content);
		if (trackingChanged) {


			if (isTracking)
				contentView.getOverlay().add(pointer);

			else contentView.getOverlay().remove(pointer);
			contentView.invalidate();
		}
		if (isTracking){
			boolean hitTestChanged = updateHitTest();
			if (hitTestChanged){
				pointer.setEnabled(isHitting);
				contentView.invalidate();
			}
		}


	}

	private boolean updateHitTest() {
		Frame frame = arFragment.getArSceneView().getArFrame();
		Point point = getScreenCenter();
		List<HitResult> hits;

		boolean wasHitting = isHitting;
		isHitting = false;
		if (frame!= null) {
			hits= frame.hitTest(point.x,point.y);
			for (HitResult hit : hits){
				Trackable trackable = hit.getTrackable();
				if ((trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose()))){
					isHitting= true;
					break;
				}
			}
		}
		return wasHitting != isHitting;
	}

	private Point getScreenCenter() {
		View view = findViewById(android.R.id.content);
		return new Point(view.getWidth()/2, view.getHeight()/2);
	}

	private boolean updateTracking() {
		Frame frame= arFragment.getArSceneView().getArFrame();
		boolean wasTracking = isTracking;
		isTracking = frame.getCamera().getTrackingState()== TrackingState.TRACKING;
		return  isTracking!= wasTracking;
	}
	private void initializeGallery() {
		LinearLayout gallery = findViewById(R.id.gallery_layout);

		ImageView andy = new ImageView(this);
		andy.setImageResource(R.drawable.droid_thumb);
		andy.setContentDescription("andy");
		andy.setOnClickListener(view ->{addObject(Uri.parse("andy.sfb"));});
		gallery.addView(andy);

		ImageView cabin = new ImageView(this);
		cabin.setImageResource(R.drawable.cabin_thumb);
		cabin.setContentDescription("cabin");
		cabin.setOnClickListener(view ->{addObject(Uri.parse("Cabin.sfb"));});
		gallery.addView(cabin);

		ImageView house = new ImageView(this);
		house.setImageResource(R.drawable.house_thumb);
		house.setContentDescription("house");
		house.setOnClickListener(view ->{addObject(Uri.parse("House.sfb"));});
		gallery.addView(house);

		ImageView igloo = new ImageView(this);
		igloo.setImageResource(R.drawable.igloo_thumb);
		igloo.setContentDescription("igloo");
		igloo.setOnClickListener(view ->{addObject(Uri.parse("igloo.sfb"));});
		gallery.addView(igloo);
	}
	private void addObject(Uri model) {
		Frame frame = arFragment.getArSceneView().getArFrame();
		android.graphics.Point pt = getScreenCenter();
		List<HitResult> hits;
		if (frame != null) {
			hits = frame.hitTest(pt.x, pt.y);
			for (HitResult hit : hits) {
				Trackable trackable = hit.getTrackable();
				if ((trackable instanceof Plane &&
						((Plane) trackable).isPoseInPolygon(hit.getHitPose()))) {
					placeObject(arFragment, hit.createAnchor(), model);
					break;

				}
			}
		}
	}
	private void placeObject(ArFragment fragment, Anchor anchor, Uri model) {
		CompletableFuture<Void> renderableFuture =
				ModelRenderable.builder()
						.setSource(fragment.getContext(), model)
						.build()
						.thenAccept(renderable -> addNodeToScene(fragment, anchor, renderable))
						.exceptionally((throwable -> {
							AlertDialog.Builder builder = new AlertDialog.Builder(this);
							builder.setMessage(throwable.getMessage())
									.setTitle("Codelab error!");
							AlertDialog dialog = builder.create();
							dialog.show();
							return null;
						}));
	}
	private void addNodeToScene(ArFragment fragment, Anchor anchor, Renderable renderable) {
		AnchorNode anchorNode = new AnchorNode(anchor);
		TransformableNode node = new TransformableNode(fragment.getTransformationSystem());
		node.setRenderable(renderable);
		node.setParent(anchorNode);
		fragment.getArSceneView().getScene().addChild(anchorNode);
		node.select();
	}

	private String generateFilename() {
		String date =
				new SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.getDefault()).format(new Date());
		return Environment.getExternalStoragePublicDirectory(
				Environment.DIRECTORY_PICTURES) + File.separator + "Sceneform/" + date + "_screenshot.jpg";
	}
	private void saveBitmapToDisk(Bitmap bitmap, String filename) throws IOException {

		File out = new File(filename);
		if (!out.getParentFile().exists()) {
			out.getParentFile().mkdirs();
		}
		try (FileOutputStream outputStream = new FileOutputStream(filename);
		     ByteArrayOutputStream outputData = new ByteArrayOutputStream()) {
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputData);
			outputData.writeTo(outputStream);
			outputStream.flush();
			outputStream.close();
		} catch (IOException ex) {
			throw new IOException("Failed to save bitmap to disk", ex);
		}
	}

	private void takePhoto() {
		final String filename = generateFilename();
		ArSceneView view = arFragment.getArSceneView();

		// Create a bitmap the size of the scene view.
		final Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
				Bitmap.Config.ARGB_8888);

		// Create a handler thread to offload the processing of the image.
		final HandlerThread handlerThread = new HandlerThread("PixelCopier");
		handlerThread.start();
		// Make the request to copy.
		PixelCopy.request(view, bitmap, (copyResult) -> {
			if (copyResult == PixelCopy.SUCCESS) {
				try {
					saveBitmapToDisk(bitmap, filename);
				} catch (IOException e) {
					Toast toast = Toast.makeText(MainActivity.this, e.toString(),
							Toast.LENGTH_LONG);
					toast.show();
					return;
				}
				Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content),
						"Photo saved", Snackbar.LENGTH_LONG);
				snackbar.setAction("Open in Photos", v -> {
					File photoFile = new File(filename);

					Uri photoURI = FileProvider.getUriForFile(MainActivity.this,
							MainActivity.this.getPackageName() + ".ar.codelab.name.provider",
							photoFile);
					Intent intent = new Intent(Intent.ACTION_VIEW, photoURI);
					intent.setDataAndType(photoURI, "image/*");
					intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
					startActivity(intent);

				});
				snackbar.show();
			} else {
				Toast toast = Toast.makeText(MainActivity.this,
						"Failed to copyPixels: " + copyResult, Toast.LENGTH_LONG);
				toast.show();
			}
			handlerThread.quitSafely();
		}, new Handler(handlerThread.getLooper()));
	}
}
