# TODO

## Build / repository architecture

- [ ] **Multirepo in one tree:** Remove dependency on the original author’s repositories or pre-built blobs by including all necessary components in this repo so they are **compiled and packaged at build time**—essentially turning this repository into a **multirepo** (single checkout that builds JNI, core tarball, and the APK without downloading artifacts from `cSploit/android`, `cSploit/android.native` releases, or vendored copies of those outputs).
