package com.lillibrary.jblg.barcodedetection;


import android.animation.ValueAnimator;
import android.graphics.RectF;
import android.util.Log;

import androidx.annotation.MainThread;

import com.google.android.gms.tasks.Task;
import com.lillibrary.jblg.camera.CameraReticleAnimator;
import com.lillibrary.jblg.camera.FrameProcessorBase;
import com.lillibrary.jblg.camera.GraphicOverlay;
import com.lillibrary.jblg.camera.WorkflowModel;
import com.lillibrary.jblg.camera.WorkflowModel.WorkflowState;
import com.lillibrary.jblg.settings.PreferenceUtils;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** A processor to run the barcode detector. */
public class BarcodeProcessor extends FrameProcessorBase<List<FirebaseVisionBarcode>> {

  private static final String TAG = "BarcodeProcessor";

  private final FirebaseVisionBarcodeDetector detector =
      FirebaseVision.getInstance().getVisionBarcodeDetector();
  private final WorkflowModel workflowModel;
  private final CameraReticleAnimator cameraReticleAnimator;

  public BarcodeProcessor(GraphicOverlay graphicOverlay, WorkflowModel workflowModel) {
    this.workflowModel = workflowModel;
    this.cameraReticleAnimator = new CameraReticleAnimator(graphicOverlay);
  }

  @Override
  protected Task<List<FirebaseVisionBarcode>> detectInImage(FirebaseVisionImage image) {
    return detector.detectInImage(image);
  }

  @MainThread
  @Override
  protected void onSuccess(
      FirebaseVisionImage image,
      List<FirebaseVisionBarcode> results,
      GraphicOverlay graphicOverlay) {
    if (!workflowModel.isCameraLive()) {
      return;
    }

    Log.d(TAG, "Barcode result size: " + results.size());

    // Picks the barcode, if exists, that covers the center of graphic overlay.
    FirebaseVisionBarcode barcodeInCenter = null;
    for (FirebaseVisionBarcode barcode : results) {
      RectF box = graphicOverlay.translateRect(Objects.requireNonNull(barcode.getBoundingBox()));
      if (box.contains(graphicOverlay.getWidth() / 2f, graphicOverlay.getHeight() / 2f)) {
        barcodeInCenter = barcode;
        break;
      }
    }

    graphicOverlay.clear();
    if (barcodeInCenter == null) {
      cameraReticleAnimator.start();
      graphicOverlay.add(new com.lillibrary.jblg.barcodedetection.BarcodeReticleGraphic(graphicOverlay, cameraReticleAnimator));
      workflowModel.setWorkflowState(WorkflowState.DETECTING);

    } else {
      cameraReticleAnimator.cancel();
      float sizeProgress =
          PreferenceUtils.getProgressToMeetBarcodeSizeRequirement(graphicOverlay, barcodeInCenter);
      if (sizeProgress < 1) {
        // Barcode in the camera view is too small, so prompt user to move camera closer.
        graphicOverlay.add(new com.lillibrary.jblg.barcodedetection.BarcodeConfirmingGraphic(graphicOverlay, barcodeInCenter));
        workflowModel.setWorkflowState(WorkflowState.CONFIRMING);

      } else {
        // Barcode size in the camera view is sufficient.
        if (PreferenceUtils.shouldDelayLoadingBarcodeResult(graphicOverlay.getContext())) {
          ValueAnimator loadingAnimator = createLoadingAnimator(graphicOverlay, barcodeInCenter);
          loadingAnimator.start();
          graphicOverlay.add(new BarcodeLoadingGraphic(graphicOverlay, loadingAnimator));
          workflowModel.setWorkflowState(WorkflowState.SEARCHING);

        } else {
          workflowModel.setWorkflowState(WorkflowState.DETECTED);
          workflowModel.detectedBarcode.setValue(barcodeInCenter);
        }
      }
    }
    graphicOverlay.invalidate();
  }

  private ValueAnimator createLoadingAnimator(
      GraphicOverlay graphicOverlay, FirebaseVisionBarcode barcode) {
    float endProgress = 1.1f;
    ValueAnimator loadingAnimator = ValueAnimator.ofFloat(0f, endProgress);
    loadingAnimator.setDuration(2000);
    loadingAnimator.addUpdateListener(
        animation -> {
          if (Float.compare((float) loadingAnimator.getAnimatedValue(), endProgress) >= 0) {
            graphicOverlay.clear();
            workflowModel.setWorkflowState(WorkflowState.SEARCHED);
            workflowModel.detectedBarcode.setValue(barcode);
          } else {
            graphicOverlay.invalidate();
          }
        });
    return loadingAnimator;
  }

  @Override
  protected void onFailure(Exception e) {
    Log.e(TAG, "Barcode detection failed!", e);
  }

  @Override
  public void stop() {
    try {
      detector.close();
    } catch (IOException e) {
      Log.e(TAG, "Failed to close barcode detector!", e);
    }
  }
}
