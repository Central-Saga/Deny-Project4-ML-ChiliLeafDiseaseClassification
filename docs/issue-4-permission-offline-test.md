# Issue #4 — Permission, Offline & Error Handling Test

- **Project:** ChiliLeaf AI — Chili Leaf Disease Classification
- **Test Date:** 16 October 2026
- **Test Device:** Physical Samsung Android smartphone
- **Branch:** `fix/issue-4-permission-offline-handling`
**Test Method:** Manual functional testing

## Objective

Verify that the application handles image selection cancellation, offline operation, loading indicators, and common image-processing errors without unexpectedly crashing.

## Permission Handling

ChiliLeaf AI uses Android system activities for image acquisition:

- `ActivityResultContracts.GetContent()` for selecting images.
- `ActivityResultContracts.TakePicture()` for capturing photos.
- `FileProvider` for securely sharing the temporary camera image URI.

The current implementation does not require direct runtime CAMERA or storage permissions for these workflows.

Permission requests are therefore not introduced unnecessarily. Permission-denial handling within an external camera application is managed by that application, while ChiliLeaf AI handles cancellation and common launch failures.

A direct runtime permission-denial test is not applicable to the current permission-free image acquisition implementation.

## Offline Operation

The application's MobileNetV2 TensorFlow Lite model performs inference locally on the Android device.

The tested image classification workflow does not require an internet connection.

## Changes

- Display a Toast when gallery image selection is canceled.
- Display a Toast when camera capture is canceled.
- Handle exceptions when opening image acquisition activities.
- Preserve the existing loading indicator during image classification.
- Preserve offline classification, treatment guidance, and detection history functionality.

## Manual Test Results

| Test Scenario | Expected Result | Status |
|---|---|---|
| Launch app in Airplane Mode | Application opens | PASS |
| Select locally stored image while offline | Image can be selected | PASS |
| Run image classification | Loading indicator appears | PASS |
| Complete inference | Prediction and confidence displayed | PASS |
| Save detection | Result and photo stored in History | PASS |
| Offline stability | No unexpected crash | PASS |
| Cancel gallery selection | Cancellation Toast displayed, no crash | PASS |
| Cancel camera capture | Cancellation Toast displayed, no crash | PASS |

**Result: 8/8 manual test scenarios passed.**

## Additional Notes

Network failure from an external API was not tested because the implemented classification workflow operates without a network request.

Invalid-image and unavailable-camera error paths are present in the code, but have not been independently verified through dedicated fault-injection testing.

The tests demonstrate the observed application behavior on the test device, not universal crash-free behavior on every Android device.

## Conclusion

The tested ChiliLeaf AI workflow successfully operates offline, displays loading during classification, provides visible cancellation feedback, and retains detection results.

The application is ready for final build verification and code review before merging Issue #4.