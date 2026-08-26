# Release SMTP Login Fix Design

## Goal

Restore Gmail SMTP login and report delivery in the signed release APK while preserving the 1.0.0 setup flow in which an administrator enters Gmail credentials on the device, and publish the correction as version 2.0.1 (`versionCode` 3).

## Confirmed Failure

The 2.0.0 release APK contains `META-INF/javamail.default.providers`, whose `smtps` entry names `com.sun.mail.smtp.SMTPSSLTransport`. R8 renamed that class to `i7.c`, and no keep rule preserves the provider name. JavaMail therefore cannot load the SMTP provider by its resource-declared class name. This occurs before Gmail can authenticate the supplied account.

Empty `BuildConfig.SMTP_USER` and `BuildConfig.SMTP_APP_PASSWORD` values are valid. They mean the APK has no optional prefilled account; an administrator supplies the sender and App Password in Settings, and the app authenticates before saving them in encrypted preferences.

## Chosen Approach

Keep release shrinking enabled. Add narrow R8 rules for the JavaMail SMTP implementation and the MIME handlers loaded dynamically from `META-INF` resources. This preserves the runtime names that JavaMail and Activation resolve without disabling optimization for the rest of the application.

Keep Gmail build defaults optional for every build type. Runtime Settings validates a syntactically valid sender email and an App Password containing exactly 16 non-whitespace characters, authenticates with Gmail, and saves only accepted values. This preserves flexible per-device setup and credential rotation without rebuilding the APK.

## Alternatives Rejected

1. Disable R8 for release. This would restore the provider names but unnecessarily increases APK size and gives up optimization across the whole app.
2. Rewrite provider resource contents to obfuscated names. Those names are build-specific and therefore unsuitable as stable configuration.
3. Require embedded Gmail credentials for release builds. This conflicts with the established per-device setup flow and unnecessarily couples APK production to an account secret.

## Changes

- Add regression checks asserting that release keep rules cover SMTP and MIME handlers.
- Add a release-build policy check proving an APK can be assembled with empty optional Gmail defaults.
- Increment the app to version 2.0.1 / code 3.
- Rebuild the signed APK using the existing stable keystore and place it in `dist` under a 2.0.1 filename.
- Update release documentation where the current version or artifact name is stated.

## Error and Security Handling

Runtime validation never prints the Gmail address or App Password. Errors remain categorized without exposing secrets. The App Password continues to be normalized by removing whitespace and is stored only after successful authentication.

## Verification

Automated verification must prove:

- the new policy tests fail before the production configuration change and pass afterward;
- all existing PowerShell and Android unit tests pass;
- lint and debug builds pass;
- the signed 2.0.1 release builds without embedded Gmail credentials;
- the release APK still contains the original SMTP provider resource name and R8 mapping preserves the matching class name;
- the APK version, signature continuity, and SHA-256 digest are recorded.

Real Gmail authentication on an Android device remains a manual acceptance step. If no device is connected, it must be reported as pending rather than passed.
