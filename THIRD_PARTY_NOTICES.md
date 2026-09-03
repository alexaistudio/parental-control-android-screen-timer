# Third-party notices

The `controller` APK uses the following libraries for local Android Debug Bridge communication. They do not add analytics, advertising, or a cloud service.

- **LibADB Android 3.1.1**, copyright Muntashir Al-Islam. Used under the Apache License 2.0 option of its dual license. Source: <https://github.com/MuntashirAkon/libadb-android>
- **sun-security-android 1.1**, copyright Muntashir Al-Islam and contributors. Source and license: <https://github.com/MuntashirAkon/sun-security-android>
- **Conscrypt 2.5.3**, copyright The Android Open Source Project. Apache License 2.0. Source: <https://github.com/google/conscrypt>
- **Bouncy Castle**, used transitively by LibADB Android. MIT license. Source: <https://www.bouncycastle.org/>
- **Spake2 Java / spake2-android**, used transitively by LibADB Android for Android Wireless Debugging pairing. See the exact source and license published by the dependency: <https://github.com/MuntashirAkon/spake2-java>

The corresponding source repositories and build dependency versions are public so recipients can inspect and replace the libraries. Each upstream license governs its own component. The project license in `LICENSE.md` governs the original Android Screen Timer code and does not replace third-party licenses.
