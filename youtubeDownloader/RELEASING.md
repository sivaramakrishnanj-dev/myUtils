# Releasing

Steps to cut a release of youtube-downloader.

## Prerequisites

- All tests pass: `mvn clean verify`
- Integration tests pass: `mvn verify -P integration`
- Fat jar exists at `yt-cli/target/youtube-downloader-1.0.0.jar`
- [GitHub CLI (`gh`)](https://cli.github.com/) installed and authenticated

## Release v1.0.0

### 1. Verify the build

```bash
mvn clean verify
java -jar yt-cli/target/youtube-downloader-1.0.0.jar --version
# Should print: 1.0.0
```

### 2. Tag the release

```bash
git tag -a v1.0.0 -m "v1.0.0 — initial release"
git push origin v1.0.0
```

### 3. Create the GitHub Release with fat-jar artifact

```bash
gh release create v1.0.0 \
  yt-cli/target/youtube-downloader-1.0.0.jar \
  --title "v1.0.0" \
  --notes-file RELEASE_NOTES.md
```

### 4. Verify

- Check https://github.com/sivarj/youtubeDownloader/releases/tag/v1.0.0
- Confirm the `.jar` artifact is downloadable
- Confirm release notes render correctly

## Future releases

For subsequent versions:

1. Update `<version>` in all `pom.xml` files
2. Add a new section to `CHANGELOG.md`
3. Update `RELEASE_NOTES.md`
4. Follow steps 1–4 above with the new version tag
