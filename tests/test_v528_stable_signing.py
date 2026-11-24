from pathlib import Path
base=Path("app/build.gradle").read_text(encoding="utf-8")
workflow=Path(".github/workflows/build-apk.yml").read_text(encoding="utf-8")
verify=Path("scripts/verify-stable-apk.sh").read_text(encoding="utf-8")
cert=Path("signing/expected-cert-sha256.txt").read_text(encoding="utf-8").strip()
assert "applicationId 'com.newsvoice.tts.stable'" in base
assert "def releaseVersion = '5.41'" in base
assert "versionCode 49" in base
assert len(cert)==64 and all(c in "0123456789ABCDEF" for c in cert)
assert "SIGNING_KEYSTORE_B64" in workflow
assert "NEWSVOICE_SIGNING_STORE_PASSWORD" in workflow
assert "NEWSVOICE_SIGNING_KEY_PASSWORD" in workflow
assert "verify-stable-apk.sh" in workflow
assert "release-unsigned" in workflow
assert "release" in workflow
assert "com.newsvoice.tts.stable" in verify
assert "BLOCKED: APK signed by unexpected certificate" in verify
assert "git" not in cert.lower()
assert not Path("signing/newsvoice-signing.p12").exists()
assert not list(Path("signing").glob("*.jks"))
print("PASS: v5.41 permanent signature fingerprint, distinct package, fail-closed verification")
