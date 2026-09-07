# 📸 360° Camera

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green?style=for-the-badge&logo=android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-purple?style=for-the-badge&logo=kotlin" />
  <img src="https://img.shields.io/badge/CameraX-Android-blue?style=for-the-badge" />
  <img src="https://img.shields.io/badge/OpenCV-Image%20Processing-white?style=for-the-badge&logo=opencv" />
  <img src="https://img.shields.io/badge/Supabase-Storage-green?style=for-the-badge&logo=supabase" />
</p>

<p align="center">
  <b>An Android 360° panorama camera application built with Kotlin, CameraX, OpenCV and Supabase.</b>
</p>

---

## 📱 About The Project

**360° Camera** is an Android application that allows users to capture multiple images while rotating their phone and combine them into a panoramic 360° image.

The application uses the device's **gyroscope sensor** to track rotation and automatically captures frames at different angles. The captured frames are processed and converted into a wide panoramic image.

The generated panorama can then be viewed inside the application and uploaded to **Supabase Storage**.

---

## ✨ Features

- 📷 Camera preview using **CameraX**
- 🔄 360° panorama capture
- 📱 Gyroscope-based rotation tracking
- 🎯 Automatic frame capture while rotating
- 🧩 Panorama image generation
- 🖼️ 360° panorama viewer
- 🔍 Pinch-to-zoom support
- ↔️ Horizontal panorama navigation
- ☁️ Supabase Storage integration
- 🗂️ Gallery for captured panoramas
- ⚡ Native Android implementation using Kotlin

---

## 🛠️ Tech Stack

| Technology | Usage |
|------------|-------|
| **Kotlin** | Application development |
| **Android Studio** | Development environment |
| **CameraX** | Camera preview and image capture |
| **OpenCV** | Image processing and panorama generation |
| **Gyroscope Sensor** | Device rotation tracking |
| **Supabase** | Cloud storage |
| **XML** | Android UI layouts |
| **Gradle** | Project build system |

---

## 🏗️ Project Architecture

```text
360Camera
│
├── app/
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com.example.threesixtycamera/
│           │       │
│           │       ├── MainActivity.kt
│           │       ├── PanoramaStitcher.kt
│           │       ├── Panorama360View.kt
│           │       ├── GalleryActivity.kt
│           │       ├── GalleryAdapter.kt
│           │       │
│           │       └── network/
│           │           ├── SupabaseConfig.kt
│           │           └── SupabaseUploader.kt
│           │
│           └── res/
│               └── layout/
│                   └── activity_main.xml
│
└── README.md
