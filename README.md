# Infinite Uploader

Infinite Uploader is a modern Android application designed for seamless file management on AWS S3. Built with Jetpack Compose and the AWS SDK for Kotlin, it provides a robust and user-friendly interface for interacting with your S3 buckets.

## 🚀 Features

-   **AWS S3 Explorer**: Browse your buckets with a familiar folder-based navigation system.
-   **Background Uploads**: Uses Android's `WorkManager` to handle file uploads. Uploads continue even if the app is closed or the device restarts.
-   **Real-time Progress**: Track upload progress through in-app UI indicators and system notifications.
-   **Secure Credential Management**: Save and switch between multiple AWS profiles. Credentials are encrypted and stored securely using `EncryptedSharedPreferences`.
-   **File Actions**:
    -   **View**: Generate presigned URLs to view files directly in your browser.
    -   **Download**: Easy access to download files from your bucket.
    -   **Delete**: Remove files from your S3 bucket with a single tap.
    -   **Upload**: Select any file from your device and upload it to the current S3 directory.

## 🛠 Tech Stack

-   **Language**: [Kotlin](https://kotlinlang.org/)
-   **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
-   **AWS Integration**: [AWS SDK for Kotlin](https://aws.amazon.com/sdk-for-kotlin/)
-   **Background Tasks**: [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
-   **Networking**: [OkHttp](https://square.github.io/okhttp/) (used for presigned URL PUT requests)
-   **Architecture**: MVVM (Model-View-ViewModel)
-   **Dependency Injection**: Manual / ViewModel Factory

## 📋 Prerequisites

Before you begin, ensure you have:

1.  An **AWS Account**.
2.  An **IAM User** with programmatic access (Access Key ID and Secret Access Key) and `AmazonS3FullAccess` (or specific permissions for your bucket).
3.  An **S3 Bucket** created in a supported AWS region.

## ⚙️ Setup & Installation

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/yourusername/InfiniteUploader.git
    ```
2.  **Open in Android Studio**:
    Open the project in Android Studio (Ladybug or newer recommended).
3.  **Sync Gradle**:
    Let Android Studio download the necessary dependencies, including the AWS SDK and Jetpack Compose libraries.
4.  **Run the App**:
    Connect an Android device or start an emulator (API 26+) and click **Run**.

## 📖 Usage

1.  **Login**: Enter your AWS Access Key, Secret Key, Bucket Name, and Select your Region.
2.  **Save Profile**: Check "Save Credentials" to store your profile for later use.
3.  **Browse**: Tap on folders to navigate deeper. Use the breadcrumb trail at the top to navigate back.
4.  **Upload**: Click the floating action button (FAB) to select a file from your device.
5.  **File Options**: Tap on any file to open the action menu (View, Download, Delete).

## 🛡 Security Note

This app uses `EncryptedSharedPreferences` to store your AWS credentials locally on the device. However, always follow AWS best practices:
-   Use IAM users with the least privilege necessary.
-   Do not share your Access Keys or commit them to version control.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
