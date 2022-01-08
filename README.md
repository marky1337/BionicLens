# BionicLens
![Android](https://img.shields.io/badge/platform-android-lightgrey.svg)

Android App which runs inference on camera stream

## 📱 Description
Mobile application which apply machine learning techniques using ML KIT APIs on Android. Each technique is presented as a single activity (All in one app).

Machine learning use-cases from the app:

- Object Recognition
- Face Detection
- Text Recognition and Translation
- Selfie segmentation
### 📸 Camera
- [📷 CameraX example](app/src/main/java/com/asmaamir/mlkitdemo/CameraX): Preview, Capture and Analyze functionalities

## Functionality
- Real-time object detection, face detection, selfie segmentation and text recognition and translation
  - The application process frames in real time from camera stream
- Object detection
  - For each frame from live camera feed, the activity will start do detect and recognize different type of objects (up to 80 different type of objects) with a specific               confidence, multiple objects can be present in detection.
- Face detection
   - Real-time face detection, the activity will identify key facial features and contours of detected faces, 1 face contour at a time.
   - User has the option to customize the activity to focus on specific features to detect.
- Text Recognition and Translation
   - Real-time text recognition in any Latin-based languages, support for Chinese, Japanese, Korean and Devanagari (Hindi) symbols.
   - User has options for real-time translation to either Romanian or English
- Selfie segmentation
   - For each human detected, the activity will separate the background and focuses on human body.
   - User has the option to choose a virtual background from 3 static images found in the app.
- All results will displayed in a separate UI instance. 

## Technologies:

Android Studio

Kotlin

ML Kit APIs

## Used Models
- Object Detection: [lite-model_object_detection_mobile_object_labeler_v1_1](https://tfhub.dev/google/lite-model/object_detection/mobile_object_labeler_v1/1)

## Installation
- If you clone this repo, you can run the applcation only on a physical device.
- Application APK: [BionicLens.apk](https://github.com/marky1337/BionicLens/raw/master/BionicLens.apk)

## 🤳 Screenshots
### 📝 Main Page
<div float="left">
	<img src="./screenshots/main.jpg" width="200" />
</div>

### 📝 Intro
<div float="left">
	<img src="./screenshots/intro.jpg" width="200" />
</div>

### :closed_book: Object Recognition
<div float="left">
	<img src="./screenshots/object.jpg" width="200" />
</div>

### 🎴 Face Detection
<div float="left">
	<img src="./screenshots/face.jpg" width="200" />
</div>

### :santa: Selfie Segmentation
<div float="left">
	<img src="./screenshots/selfie.jpg" width="200" />
</div>


### 🕵️‍♀️ Text Recognition and Translation
<div float="left">
	<img src="./screenshots/text2.png" width="200" />
</div>


## 💼 Authors
* **Daniel IONAȘCU** - [GitHub Profile](https://github.com/marky1337)
* **Sebastian BELEA** - [GitHub Profile](https://github.com/belea-sebastian)
