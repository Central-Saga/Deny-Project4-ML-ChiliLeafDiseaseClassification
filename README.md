# Chili Leaf Disease Classification — ChiliLeaf AI

Machine Learning-based chili leaf disease classification integrated into a native Android application using Kotlin and LiteRT (TensorFlow Lite).

## Project Overview

ChiliLeaf AI is an Android application that analyzes chili leaf images using an on-device Machine Learning model.

Users can capture photos with the camera or select images from the gallery. The application runs inference offline and displays the predicted leaf condition, confidence score, and initial treatment recommendations.

The application also includes a dashboard, detection history, and a disease information guide.

## Technologies

- Python
- TensorFlow / Keras
- Google Colab
- MobileNetV2
- TensorFlow Lite (.tflite)
- LiteRT for Android
- Kotlin
- Android Studio
- Gradle

## Project Structure

- `ml/notebooks/` — Model training and experiments
- `ml/models/` — Machine Learning model artifacts
- `ml/results/` — Evaluation outputs and visualizations
- `android-app/` — Native Android application
- `android-app/app/src/main/assets/` — Bundled model and class labels
- `docs/` — Project documentation

## Machine Learning Model

| Property | Value |
|---|---|
| Model file | `mobilenetv2_chili_float32.tflite` |
| Model family | MobileNetV2 |
| Deployment format | TensorFlow Lite |
| Tensor data type | Float32 |
| Input image size | 224 × 224 pixels |
| Color format | RGB |
| Number of classes | 4 |
| Model file size | 8,906,396 bytes (approximately 8.49 MiB) |
| Inference runtime | LiteRT |
| Execution backend | CPU |
| Execution mode | Offline |

**Bundled model location:**

`android-app/app/src/main/assets/mobilenetv2_chili_float32.tflite`

**Labels location:**

`android-app/app/src/main/assets/labels.txt`

### Model Identity

SHA-256:

`989F6CE8AF959779C524BA7E85254E6DE957D4145AD51ECE341A3A3FDEA47159`

This checksum identifies the exact model artifact bundled with the Android application.

A formal training-version identifier has not yet been verified. The SHA-256 fingerprint is used to identify the deployed model revision.

### Classification Labels

The Android application maps model outputs to these labels in the following order:

1. Anthracnose
2. Cercospora Leaf Spot
3. Fresh Leaf
4. Leaf Curl Disease

The label ordering is defined in `labels.txt` and must match the class-index ordering used during model training and export.

## Real Model Inference

The Android application uses a real Machine Learning model, not mocked classification results.

The model is loaded in `ChiliLeafClassifier.kt` using LiteRT:

```kotlin
private val model = CompiledModel.create(
    context.assets,
    MODEL_NAME,
    CompiledModel.Options.CPU
)
```

The classifier writes the processed image data to the input buffer, executes the model, and reads its output:

```kotlin
inputBuffers[0].writeFloat(input)

model.run(inputBuffers, outputBuffers)

val probabilities = outputBuffers[0].readFloat()
```

The model output is used to determine the predicted class and confidence displayed in the application.

### Image Processing

The current Android implementation:

1. Loads the selected or captured image.
2. Resizes it to fit a 224 × 224 canvas while maintaining its aspect ratio.
3. Uses white padding when required.
4. Extracts RGB channel values.
5. Constructs a Float32 input array using RGB values in the 0–255 range.
6. Sends the input data to LiteRT for inference.
7. Reads model output scores and selects the highest-scoring class.

The compatibility of Android preprocessing with the original training pipeline should be verified against the model export configuration.

## Core Android Features

### Home

Displays the ChiliLeaf AI dashboard and information about four chili leaf conditions.

### Detection

- Capture a chili leaf image using the camera.
- Select an image from the gallery.
- Perform on-device model inference.
- Display the predicted condition.
- Display model confidence and class scores.
- Show initial treatment recommendations based on the predicted class.

### Detection History

Stores recent detection results locally, including:

- Predicted class
- Confidence score
- Detection timestamp
- Saved scan photo

### Disease Guide

Provides information about the four supported chili leaf conditions and initial plant-care guidance.

## Android Build Instructions

### Requirements

- Android Studio
- Compatible Java Development Kit (JDK)
- Android SDK
- Internet access for initial Gradle dependency downloads

### Build Debug APK

From the repository root, open PowerShell:

```powershell
cd android-app
.\gradlew.bat assembleDebug
```

Ensure the Android SDK is configured through `ANDROID_HOME` or the machine-local `local.properties` file.

Expected output:

```text
BUILD SUCCESSFUL
```

Generated APK:

`android-app/app/build/outputs/apk/debug/app-debug.apk`

Build outputs, local SDK configuration, APK files, and Gradle caches are excluded from Git tracking through `.gitignore`.

## Testing and Verification

The application has been exercised on a physical Samsung Android device.

The following functionality has been tested during development:

- Application launch and navigation
- Camera and gallery image selection
- Offline model inference
- Classification results and confidence display
- Initial treatment recommendations
- Local detection history and scan photos

The Android debug build has also been successfully generated using Gradle.

A separate fresh clone of the repository was built successfully on the development machine after configuring the installed Android SDK.

### Verification Scope

These checks demonstrate that the bundled model executes and that its outputs are consumed by the application's core detection workflow.

Model accuracy, calibration, preprocessing consistency, and class-index alignment require separate verification against the training and evaluation artifacts.

## Development Roadmap

- [x] Initialize GitHub repository
- [x] Create project structure
- [x] Prepare model training resources
- [x] Export a TFLite classification model
- [x] Develop the Android application in Kotlin
- [x] Integrate real model inference into the detection feature
- [x] Test the application on a physical Android device
- [x] Verify debug APK generation
- [x] Audit repository structure and `.gitignore`
- [x] Document the deployed model artifact
- [ ] Complete the documented cross-check of training preprocessing and output label ordering
- [ ] Confirm and document the formal training model version and evaluation metrics

## Limitations

- Predictions are based on input images and may be incorrect.
- Classification results should not be treated as definitive agricultural diagnoses.
- Image quality, lighting, and background conditions can affect results.
- The current model recognizes four predefined classes only.

## Future Development

This project may be expanded into undergraduate thesis research using chili leaf images collected under real agricultural field conditions.

Future work may include more extensive external validation, model optimization, dataset expansion, and improved disease detection performance.