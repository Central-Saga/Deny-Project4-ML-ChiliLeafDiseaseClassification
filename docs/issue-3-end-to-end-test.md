# Issue #3 — End-to-End Functional Test

## Project Information

- **Project:** ChiliLeaf AI — Chili Leaf Disease Classification
- **Issue:** #3 — Core functionality works with real data
- **Test Date:** 16 October 2026
- **Platform:** Native Android Application (Kotlin)
- **Test Device:** Physical Samsung Android smartphone
- **Inference Engine:** LiteRT CPU
- **Model:** MobileNetV2 TensorFlow Lite Float32
- **Test Type:** Manual End-to-End Functional Testing

## Test Objective

Verify that the ChiliLeaf AI application can execute its complete core workflow using a real chili leaf image rather than mocked input or hardcoded prediction results.

The expected workflow is:

Real Image → Image Preprocessing → TFLite Model Inference → Classification Result → Treatment Guidance → Detection History.

## Test Results

| No. | Test Scenario | Result |
|---|---|---|
| 1 | Launch ChiliLeaf AI on a physical Samsung device | PASS |
| 2 | Select or capture a real chili leaf image | PASS |
| 3 | Display model classification and confidence | PASS |
| 4 | Display treatment guidance according to predicted label | PASS |
| 5 | Save and display the detected image and result in History | PASS |
| 6 | Close and reopen the application and verify History persistence | PASS |

**Total: 6 passed / 6 tested**

## Real Model Integration

The application loads a bundled TensorFlow Lite model through `ChiliLeafClassifier.kt`.

The classifier performs model inference using:

```kotlin
inputBuffers[0].writeFloat(input)
model.run(inputBuffers, outputBuffers)
val probabilities = outputBuffers[0].readFloat()
```

The results are consumed by `MainActivity.kt` to display the predicted condition, confidence, all class scores, and initial treatment recommendations.

Detection records are saved locally, including the predicted label, confidence, timestamp, and scan photo.

## Persistence Verification

After successful detection, the History screen displayed the saved detection result and its corresponding scanned image.

The History records remained available after closing and reopening the application.

## Test Scope and Limitations

- Manual functional testing was performed on a physical Samsung Android smartphone.
- The test used a real image rather than an application mock.
- The successful workflow confirms functionality, not the medical or agricultural correctness of every prediction.
- Model accuracy and robustness require separate evaluation.
- A complete automated UI test suite was not part of this manual test.
- The specific predicted label and confidence value were not recorded in this report.

## Conclusion

**PASS — End-to-end core functionality verified on a physical Android device.**

ChiliLeaf AI successfully completed the input, model inference, output display, treatment guidance, and local history persistence workflow using real image input.

This manual test satisfies the demonstrated core functional workflow required by Issue #3.

Closes #3