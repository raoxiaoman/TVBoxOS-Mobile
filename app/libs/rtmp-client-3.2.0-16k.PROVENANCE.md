# 16 KB RTMP client build provenance

- Upstream: https://github.com/ant-media/Librtmp-Client-for-Android
- Source commit: `3af67842f517f9c6bbd22b961878624d0c44b713`
- Artifact: `rtmp-client-3.2.0-16k.aar`
- SHA-256: `683057f575d0c38a55baa7f89b03fb06da7a2e6011c353b1d250ce05f8d76a5d`
- Build task: `:rtmp-client:assembleRelease`
- NDK: `21.4.7075529`

The native target was linked with:

```text
-Wl,-z,max-page-size=16384
-Wl,-z,common-page-size=16384
```

All packaged ABIs were checked with `llvm-readelf`: every `PT_LOAD` segment has alignment `0x4000`, and each `PT_GNU_RELRO` range ends on a 16 KB boundary. The public Java class set, public signatures, and JNI-exported symbol names match the upstream Maven Central `io.antmedia:rtmp-client:3.2.0` artifact.

Licenses for the upstream project and its embedded librtmp source are retained beside the AAR.
