package com.lillibrary.jblg.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import com.google.android.gms.common.images.Size;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.lillibrary.jblg.R;
import com.lillibrary.jblg.Utils;
import com.lillibrary.jblg.settings.PreferenceUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class CameraSource {

  public static final int CAMERA_FACING_BACK = Camera.CameraInfo.CAMERA_FACING_BACK;

  private static final String TAG = "CameraSource";

  private static final int IMAGE_FORMAT = ImageFormat.NV21;
  private static final int MIN_CAMERA_PREVIEW_WIDTH = 400;
  private static final int MAX_CAMERA_PREVIEW_WIDTH = 1300;
  private static final int DEFAULT_REQUESTED_CAMERA_PREVIEW_WIDTH = 640;
  private static final int DEFAULT_REQUESTED_CAMERA_PREVIEW_HEIGHT = 360;
  private static final float REQUESTED_CAMERA_FPS = 30.0f;

  private Camera camera;
  @FirebaseVisionImageMetadata.Rotation private int rotation;

  private Size previewSize;

  /**
   * Dedicated thread and associated runnable for calling into the detector with frames, as the
   * frames become available from the camera.
   */
  private Thread processingThread;
  private final FrameProcessingRunnable processingRunnable = new FrameProcessingRunnable();

  private final Object processorLock = new Object();
  private FrameProcessor frameProcessor;

  /**
   * Map to convert between a byte array, received from the camera, and its associated byte buffer.
   * We use byte buffers internally because this is a more efficient way to call into native code
   * later (avoids a potential copy).
   *
   * <p><b>Note:</b> uses IdentityHashMap here instead of HashMap because the behavior of an array's
   * equals, hashCode and toString methods is both useless and unexpected. IdentityHashMap enforces
   * identity ('==') check on the keys.
   */
  private final Map<byte[], ByteBuffer> bytesToByteBuffer = new IdentityHashMap<>();

  private final Context context;
  private final GraphicOverlay graphicOverlay;

  public CameraSource(GraphicOverlay graphicOverlay) {
    this.context = graphicOverlay.getContext();
    this.graphicOverlay = graphicOverlay;
  }

  /**
   * Opens the camera and starts sending preview frames to the underlying detector. The supplied
   * surface holder is used for the preview so frames can be displayed to the user.
   *
   * @param surfaceHolder the surface holder to use for the preview frames.
   * @throws IOException if the supplied surface holder could not be used as the preview display.
   */
  synchronized void start(SurfaceHolder surfaceHolder) throws IOException {
    if (camera != null) {
      return;
    }

    camera = createCamera();
    camera.setPreviewDisplay(surfaceHolder);
    camera.startPreview();

    processingThread = new Thread(processingRunnable);
    processingRunnable.setActive(true);
    processingThread.start();
  }

  /**
   * Closes the camera and stops sending frames to the underlying frame detector.
   *
   * <p>This camera source may be restarted again by calling {@link #start(SurfaceHolder)}.
   *
   * <p>Call {@link #release()} instead to completely shut down this camera source and release the
   * resources of the underlying detector.
   */
  synchronized void stop() {
    processingRunnable.setActive(false);
    if (processingThread != null) {
      try {
        // Waits for the thread to complete to ensure that we can't have multiple threads executing
        // at the same time (i.e., which would happen if we called start too quickly after stop).
        processingThread.join();
      } catch (InterruptedException e) {
        Log.e(TAG, "Frame processing thread interrupted on stop.");
      }
      processingThread = null;
    }

    if (camera != null) {
      camera.stopPreview();
      camera.setPreviewCallbackWithBuffer(null);
      try {
        camera.setPreviewDisplay(/* holder= */ null);
      } catch (Exception e) {
        Log.e(TAG, "Failed to clear camera preview: " + e);
      }
      camera.release();
      camera = null;
    }

    // Release the reference to any image buffers, since these will no longer be in use.
    bytesToByteBuffer.clear();
  }

  /** Stops the camera and releases the resources of the camera and underlying detector. */
  public void release() {
    graphicOverlay.clear();
    synchronized (processorLock) {
      stop();
      if (frameProcessor != null) {
        frameProcessor.stop();
      }
    }
  }

  public void setFrameProcessor(FrameProcessor processor) {
    graphicOverlay.clear();
    synchronized (processorLock) {
      if (frameProcessor != null) {
        frameProcessor.stop();
      }
      frameProcessor = processor;
    }
  }

  public void updateFlashMode(String flashMode) {
    Camera.Parameters parameters = camera.getParameters();
    parameters.setFlashMode(flashMode);
    camera.setParameters(parameters);
  }

  /** Returns the preview size that is currently in use by the underlying camera. */
  Size getPreviewSize() {
    return previewSize;
  }

  /**
   * Opens the camera and applies the user settings.
   *
   * @throws IOException if camera cannot be found or preview cannot be processed.
   */
  private Camera createCamera() throws IOException {
    Camera camera = Camera.open();
    if (camera == null) {
      throw new IOException("There is no back-facing camera.");
    }

    Camera.Parameters parameters = camera.getParameters();
    setPreviewAndPictureSize(camera, parameters);
    setRotation(camera, parameters);

    int[] previewFpsRange = selectPreviewFpsRange(camera);
    if (previewFpsRange == null) {
      throw new IOException("Could not find suitable preview frames per second range.");
    }
    parameters.setPreviewFpsRange(
            previewFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
            previewFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);

    parameters.setPreviewFormat(IMAGE_FORMAT);

    if (parameters
            .getSupportedFocusModes()
            .contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
      parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
    } else {
      Log.i(TAG, "Camera auto focus is not supported on this device.");
    }

    camera.setParameters(parameters);

    camera.setPreviewCallbackWithBuffer(processingRunnable::setNextFrame);

    // Four frame buffers are needed for working with the camera:
    //
    //   one for the frame that is currently being executed upon in doing detection
    //   one for the next pending frame to process immediately upon completing detection
    //   two for the frames that the camera uses to populate future preview images
    //
    // Through trial and error it appears that two free buffers, in addition to the two buffers
    // used in this code, are needed for the camera to work properly. Perhaps the camera has one
    // thread for acquiring images, and another thread for calling into user code. If only three
    // buffers are used, then the camera will spew thousands of warning messages when detection
    // takes a non-trivial amount of time.
    camera.addCallbackBuffer(createPreviewBuffer(previewSize));
    camera.addCallbackBuffer(createPreviewBuffer(previewSize));
    camera.addCallbackBuffer(createPreviewBuffer(previewSize));
    camera.addCallbackBuffer(createPreviewBuffer(previewSize));

    return camera;
  }

  private void setPreviewAndPictureSize(Camera camera, Parameters parameters) throws IOException {
    // Gives priority to the preview size specified by the user if exists.
    CameraSizePair sizePair = PreferenceUtils.getUserSpecifiedPreviewSize(context);
    if (sizePair == null) {
      // Camera preview size is based on the landscape mode, so we need to also use the aspect
      // ration of display in the same mode for comparison.
      float displayAspectRatioInLandscape;
      if (Utils.isPortraitMode(graphicOverlay.getContext())) {
        displayAspectRatioInLandscape =
                (float) graphicOverlay.getHeight() / graphicOverlay.getWidth();
      } else {
        displayAspectRatioInLandscape =
                (float) graphicOverlay.getWidth() / graphicOverlay.getHeight();
      }
      sizePair = selectSizePair(camera, displayAspectRatioInLandscape);
    }
    if (sizePair == null) {
      throw new IOException("Could not find suitable preview size.");
    }

    previewSize = sizePair.preview;
    Log.v(TAG, "Camera preview size: " + previewSize);
    parameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
    PreferenceUtils.saveStringPreference(
            context, R.string.pref_key_rear_camera_preview_size, previewSize.toString());

    Size pictureSize = sizePair.picture;
    if (pictureSize != null) {
      Log.v(TAG, "Camera picture size: " + pictureSize);
      parameters.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());
      PreferenceUtils.saveStringPreference(
              context, R.string.pref_key_rear_camera_picture_size, pictureSize.toString());
    }
  }

  /**
   * Calculates the correct rotation for the given camera id and sets the rotation in the
   * parameters. It also sets the camera's display orientation and rotation.
   *
   * @param parameters the camera parameters for which to set the rotation.
   */
  private void setRotation(Camera camera, Camera.Parameters parameters) {
    WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    int deviceRotation = windowManager.getDefaultDisplay().getRotation();
    int degrees = 0;
    switch (deviceRotation) {
      case Surface.ROTATION_0:
        degrees = 0;
        break;
      case Surface.ROTATION_90:
        degrees = 90;
        break;
      case Surface.ROTATION_180:
        degrees = 180;
        break;
      case Surface.ROTATION_270:
        degrees = 270;
        break;
      default:
        Log.e(TAG, "Bad device rotation value: " + deviceRotation);
    }

    CameraInfo cameraInfo = new CameraInfo();
    Camera.getCameraInfo(CAMERA_FACING_BACK, cameraInfo);
    int angle = (cameraInfo.orientation - degrees + 360) % 360;
    // This corresponds to the rotation constants in FirebaseVisionImageMetadata.
    this.rotation = angle / 90;
    camera.setDisplayOrientation(angle);
    parameters.setRotation(angle);
  }

  /**
   * Creates one buffer for the camera preview callback. The size of the buffer is based off of the
   * camera preview size and the format of the camera image.
   *
   * @return a new preview buffer of the appropriate size for the current camera settings.
   */
  private byte[] createPreviewBuffer(Size previewSize) {
    int bitsPerPixel = ImageFormat.getBitsPerPixel(IMAGE_FORMAT);
    long sizeInBits = (long) previewSize.getHeight() * previewSize.getWidth() * bitsPerPixel;
    int bufferSize = (int) Math.ceil(sizeInBits / 8.0d) + 1;

    // Creating the byte array this way and wrapping it, as opposed to using .allocate(),
    // should guarantee that there will be an array to work with.
    byte[] byteArray = new byte[bufferSize];
    ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray);
    if (!byteBuffer.hasArray() || (byteBuffer.array() != byteArray)) {
      // This should never happen. If it does, then we wouldn't be passing the preview content to
      // the underlying detector later.
      throw new IllegalStateException("Failed to create valid buffer for camera source.");
    }

    bytesToByteBuffer.put(byteArray, byteBuffer);
    return byteArray;
  }

  /**
   * Selects the most suitable preview and picture size, given the display aspect ratio in landscape
   * mode.
   *
   * <p>It's firstly trying to pick the one that has closest aspect ratio to display view with its
   * width be in the specified range [{@link #MIN_CAMERA_PREVIEW_WIDTH}, {@link
   * #MAX_CAMERA_PREVIEW_WIDTH}]. If there're multiple candidates, choose the one having longest
   * width.
   *
   * <p>If the above looking up failed, chooses the one that has the minimum sum of the differences
   * between the desired values and the actual values for width and height.
   *
   * <p>Even though we only need to find the preview size, it's necessary to find both the preview
   * size and the picture size of the camera together, because these need to have the same aspect
   * ratio. On some hardware, if you would only set the preview size, you will get a distorted
   * image.
   *
   * @param camera the camera to select a preview size from
   * @return the selected preview and picture size pair
   */
  private static CameraSizePair selectSizePair(Camera camera, float displayAspectRatioInLandscape) {
    List<CameraSizePair> validPreviewSizes = Utils.generateValidPreviewSizeList(camera);

    CameraSizePair selectedPair = null;
    // Picks the preview size that has closest aspect ratio to display view.
    float minAspectRatioDiff = Float.MAX_VALUE;
    for (CameraSizePair sizePair : validPreviewSizes) {
      Size previewSize = sizePair.preview;
      if (previewSize.getWidth() < MIN_CAMERA_PREVIEW_WIDTH
              || previewSize.getWidth() > MAX_CAMERA_PREVIEW_WIDTH) {
        continue;
      }

      float previewAspectRatio = (float) previewSize.getWidth() / previewSize.getHeight();
      float aspectRatioDiff = Math.abs(displayAspectRatioInLandscape - previewAspectRatio);
      if (Math.abs(aspectRatioDiff - minAspectRatioDiff) < Utils.ASPECT_RATIO_TOLERANCE) {
        if (selectedPair == null || selectedPair.preview.getWidth() < sizePair.preview.getWidth()) {
          selectedPair = sizePair;
        }
      } else if (aspectRatioDiff < minAspectRatioDiff) {
        minAspectRatioDiff = aspectRatioDiff;
        selectedPair = sizePair;
      }
    }

    if (selectedPair == null) {
      // Picks the one that has the minimum sum of the differences between the desired values and
      // the actual values for width and height.
      int minDiff = Integer.MAX_VALUE;
      for (CameraSizePair sizePair : validPreviewSizes) {
        Size size = sizePair.preview;
        int diff =
                Math.abs(size.getWidth() - CameraSource.DEFAULT_REQUESTED_CAMERA_PREVIEW_WIDTH)
                        + Math.abs(size.getHeight() - CameraSource.DEFAULT_REQUESTED_CAMERA_PREVIEW_HEIGHT);
        if (diff < minDiff) {
          selectedPair = sizePair;
          minDiff = diff;
        }
      }
    }

    return selectedPair;
  }

  /**
   * Selects the most suitable preview frames per second range.
   *
   * @param camera the camera to select a frames per second range from
   * @return the selected preview frames per second range
   */
  private static int[] selectPreviewFpsRange(Camera camera) {
    // The camera API uses integers scaled by a factor of 1000 instead of floating-point frame
    // rates.
    int desiredPreviewFpsScaled = (int) (REQUESTED_CAMERA_FPS * 1000f);

    // The method for selecting the best range is to minimize the sum of the differences between
    // the desired value and the upper and lower bounds of the range.  This may select a range
    // that the desired value is outside of, but this is often preferred.  For example, if the
    // desired frame rate is 29.97, the range (30, 30) is probably more desirable than the
    // range (15, 30).
    int[] selectedFpsRange = null;
    int minDiff = Integer.MAX_VALUE;
    for (int[] range : camera.getParameters().getSupportedPreviewFpsRange()) {
      int deltaMin = desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX];
      int deltaMax = desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX];
      int diff = Math.abs(deltaMin) + Math.abs(deltaMax);
      if (diff < minDiff) {
        selectedFpsRange = range;
        minDiff = diff;
      }
    }
    return selectedFpsRange;
  }

  /**
   * This runnable controls access to the underlying receiver, calling it to process frames when
   * available from the camera. This is designed to run detection on frames as fast as possible
   * (i.e., without unnecessary context switching or waiting on the next frame).
   *
   * <p>While detection is running on a frame, new frames may be received from the camera. As these
   * frames come in, the most recent frame is held onto as pending. As soon as detection and its
   * associated processing is done for the previous frame, detection on the mostly recently received
   * frame will immediately start on the same thread.
   */
  private class FrameProcessingRunnable implements Runnable {

    // This lock guards all of the member variables below.
    private final Object lock = new Object();
    private boolean active = true;

    // These pending variables hold the state associated with the new frame awaiting processing.
    private ByteBuffer pendingFrameData;

    FrameProcessingRunnable() {}

    /** Marks the runnable as active/not active. Signals any blocked threads to continue. */
    void setActive(boolean active) {
      synchronized (lock) {
        this.active = active;
        lock.notifyAll();
      }
    }

    /**
     * Sets the frame data received from the camera. This adds the previous unused frame buffer (if
     * present) back to the camera, and keeps a pending reference to the frame data for future use.
     */
    @SuppressWarnings("ByteBufferBackingArray")
    void setNextFrame(byte[] data, Camera camera) {
      synchronized (lock) {
        if (pendingFrameData != null) {
          camera.addCallbackBuffer(pendingFrameData.array());
          pendingFrameData = null;
        }

        if (!bytesToByteBuffer.containsKey(data)) {
          Log.d(
                  TAG,
                  "Skipping frame. Could not find ByteBuffer associated with the image "
                          + "data from the camera.");
          return;
        }

        pendingFrameData = bytesToByteBuffer.get(data);

        // Notify the processor thread if it is waiting on the next frame (see below).
        lock.notifyAll();
      }
    }

    /**
     * As long as the processing thread is active, this executes detection on frames continuously.
     * The next pending frame is either immediately available or hasn't been received yet. Once it
     * is available, we transfer the frame info to local variables and run detection on that frame.
     * It immediately loops back for the next frame without pausing.
     *
     * <p>If detection takes longer than the time in between new frames from the camera, this will
     * mean that this loop will run without ever waiting on a frame, avoiding any context switching
     * or frame acquisition time latency.
     *
     * <p>If you find that this is using more CPU than you'd like, you should probably decrease the
     * FPS setting above to allow for some idle time in between frames.
     */
    @SuppressWarnings({"GuardedBy", "ByteBufferBackingArray"})
    @Override
    public void run() {
      ByteBuffer data;

      while (true) {
        synchronized (lock) {
          while (active && (pendingFrameData == null)) {
            try {
              // Wait for the next frame to be received from the camera, since we don't have it yet.
              lock.wait();
            } catch (InterruptedException e) {
              Log.e(TAG, "Frame processing loop terminated.", e);
              return;
            }
          }

          if (!active) {
            // Exit the loop once this camera source is stopped or released.  We check this here,
            // immediately after the wait() above, to handle the case where setActive(false) had
            // been called, triggering the termination of this loop.
            return;
          }

          // Hold onto the frame data locally, so that we can use this for detection
          // below.  We need to clear pendingFrameData to ensure that this buffer isn't
          // recycled back to the camera before we are done using that data.
          data = pendingFrameData;
          pendingFrameData = null;
        }

        try {
          synchronized (processorLock) {
            FrameMetadata frameMetadata =
                    new FrameMetadata(previewSize.getWidth(), previewSize.getHeight(), rotation);
            frameProcessor.process(data, frameMetadata, graphicOverlay);
          }
        } catch (Exception t) {
          Log.e(TAG, "Exception thrown from receiver.", t);
        } finally {
          camera.addCallbackBuffer(data.array());
        }
      }
    }
  }
}