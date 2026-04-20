# Update Mechanism and Semantic Versioning

## Overview

This document describes the update mechanism that checks for the latest version on GitHub releases and enforces semantic versioning for all updates.

## Semantic Versioning

All versions must follow the format: `MAJOR.MINOR.PATCH[-PRERELEASE]`

- **MAJOR**: Incompatible API changes
- **MINOR**: Backward-compatible functionality additions
- **PATCH**: Backward-compatible bug fixes
- **PRERELEASE**: Optional pre-release identifier (e.g., `1.0.4-beta.1`)

## Version Management

### Build Configuration

Update the version in `app/build.gradle.kts`:

```kotlin
defaultConfig {
    versionCode = 4  // Must be an integer that increases with each release
    versionName = "1.0.4"  // Must follow semantic versioning
}
```

**Important:**
- `versionCode` must be an integer that increases with each release
- `versionName` must follow semantic versioning format
- Do not include release metadata (like `-release`) in `versionName`

### GitHub Release Tags

When creating a release on GitHub, use tags that follow semantic versioning:

```
v1.0.4
v1.1.0
v1.1.1-beta
v2.0.0-alpha
```

## Update Mechanism

### Components

1. **SemanticVersion.kt**: Data model for semantic version comparison
2. **GitHubModels.kt**: Data models for GitHub API responses
3. **GitHubService.kt**: Service for interacting with GitHub API
4. **UpdateManager.kt**: Manages update checking and downloading
5. **UpdateViewModel.kt**: ViewModel for update UI state management

### Update Flow

1. User opens Settings → About section
2. App checks current version against latest GitHub release
3. If a newer version is available, update button appears
4. User can download the update (progress shown)
5. After download, user can install the APK

### GitHub Configuration

Update the `repoOwner` and `repoName` in the `UpdateManager`:

```kotlin
val updateManager = remember {
    UpdateManager(
        context = context,
        githubService = GitHubService(),
        repoOwner = "your-username",
        repoName = "Taizi"
    )
}
```

## Creating a Release

### 1. Update Version

```bash
# Increment versionCode
# Update versionName to new semantic version
# Example: versionCode = 5, versionName = "1.0.5"
```

### 2. Build Release APK

```bash
./gradlew assembleRelease
```

### 3. Create GitHub Release

1. Go to GitHub repository
2. Click "Releases" → "Create a new release"
3. Tag version: `v1.0.5` (follows semantic versioning)
4. Title: `Version 1.0.5`
5. Description: Release notes
6. Upload APK to release assets
7. Publish release

## Validation

### Manual Version Check

The update mechanism automatically checks for updates when:
- User opens Settings → About section
- User clicks "Check for updates"

### Automatic Update Checks

Consider adding automatic checks in the app lifecycle:
- On first launch after update
- Periodically (e.g., weekly)
- Only when network is available

## Troubleshooting

### Update Not Showing

1. Verify GitHub release tag follows semantic versioning
2. Check `repoOwner` and `repoName` are correct
3. Ensure APK is uploaded to release assets
4. Verify network connectivity

### Download Fails

1. Check GitHub API rate limits
2. Verify APK URL is accessible
3. Ensure sufficient storage space
4. Check network permissions

### Version Comparison Issues

1. Verify version names follow semantic versioning
2. Ensure no extra metadata in version names
3. Check for pre-release identifiers in latest version

## Best Practices

1. **Always increment versionCode** with each release
2. **Follow semantic versioning** for versionName
3. **Include release notes** in GitHub releases
4. **Test updates** thoroughly before releasing
5. **Keep versionName clean** (no `-release`, `-beta`, etc.)
6. **Update changelog** for each release
7. **Monitor GitHub API rate limits** for scraping

## Examples

### Valid Versions

```
1.0.0
1.0.1
1.1.0
1.1.1
2.0.0
1.0.0-beta
1.0.0-rc.1
```

### Invalid Versions

```
1.0.0-release
v1.0.0
1.0.0.0
1.0-beta
1.0.0-beta.1.0  (multiple segments in pre-release)
```