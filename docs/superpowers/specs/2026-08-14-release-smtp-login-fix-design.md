# Release SMTP Login Fix Design

## Goal

Restore Gmail SMTP login and report delivery in the signed release APK, prevent a release APK from being packaged without its required Gmail defaults, and publish the correction as version 2.0.1 (`versionCode` 3).

## Confirmed Failure

The 2.0.0 release APK contains `META-INF/javamail.default.providers`, whose `smtps` entry names `com.sun.mail.smtp.SMTPSSLTransport`. R8 renamed that class to `i7.c`, and no keep rule preserves the provider name. JavaMail therefore cannot load the SMTP provider by its resource-declared class name. This occurs before Gmail can authenticate the supplied account.

The same APK was built with empty `BuildConfig.SMTP_USER` and `BuildConfig.SMTP_APP_PASSWORD` values because `gmail-secrets.properties` was absent and the release build accepted empty fallbacks. Existing encrypted settings can mask this on an upgrade, but a clean install has no usable defaults.

## Chosen Approach

Keep release shrinking enabled. Add narrow R8 rules for the JavaMail SMTP implementation and the MIME handlers loaded dynamically from `META-INF` resources. This preserves the runtime names that JavaMail and Activation resolve without disabling optimization for the rest of the application.

Require release packaging tasks to receive:

- a syntactically valid sender email;
- a Gmail App Password containing exactly 16 non-whitespace characters.

Debug and ordinary unit-test builds continue to allow empty defaults. The guard applies to release packaging, so an incomplete production artifact fails during the build rather than on a phone.

## Alternatives Rejected

1. Disable R8 for release. This would restore the provider names but unnecessarily increases APK size and gives up optimization across the whole app.
2. Rewrite provider resource contents to obfuscated names. Those names are build-specific and therefore unsuitable as stable configuration.
3. Rely only on administrators entering credentials after installation. This does not solve the broken provider lookup and makes clean-install deployment easier to misconfigure.

## Changes

- Add regression checks asserting that release keep rules cover SMTP and MIME handlers.
- Add release-build policy checks for non-empty, valid Gmail build credentials without printing their values.
- Increment the app to version 2.0.1 / code 3.
- Rebuild the signed APK using the existing stable keystore and place it in `dist` under a 2.0.1 filename.
- Update release documentation where the current version or artifact name is stated.

## Error and Security Handling

Build validation reports only which credential field is missing or malformed. It never prints the Gmail address or App Password. Runtime errors remain categorized without exposing secrets. The App Password continues to be normalized by removing whitespace.

## Verification

Automated verification must prove:

- the new policy tests fail before the production configuration change and pass afterward;
- all existing PowerShell and Android unit tests pass;
- lint and debug builds pass;
- the signed 2.0.1 release builds with valid local credentials;
- the release APK still contains the original SMTP provider resource name and R8 mapping preserves the matching class name;
- the APK version, signature continuity, and SHA-256 digest are recorded.

Real Gmail authentication on an Android device remains a manual acceptance step. If no device is connected, it must be reported as pending rather than passed.
