# Nebula Updater

A small Windows-friendly tool (Java 17, one runnable jar, Swing window) that keeps the
**Nebula** Forge 1.20.1 mod up to date in a Minecraft `mods` folder.

It only downloads and moves files. No process attaching, no injection.

## What it does

1. Reads `updater.json` (next to the jar).
2. Asks the configured URL what the newest Nebula jar is.
3. If the installed `nebula*.jar` already has the same SHA-256, says **Up to date**.
4. Otherwise downloads to a temp file, checks it is a valid jar whose
   `META-INF/mods.toml` has `modId="nebula"` and contains `dev.nebula.Nebula`, and
   (for GitHub) that its SHA-256 matches the release asset digest.
5. Moves the old `nebula*.jar` into `<instance>\nebula-backups\` (outside `mods`, so
   Forge never loads two copies) and puts the new jar in `mods`.

Close Minecraft before updating (Windows locks loaded jars).

## Using it (Windows)

Put these in one folder: `nebula-updater.jar`, `Nebula Updater.bat`, `updater.json`.
Double-click **Nebula Updater.bat** (needs Java 17+, e.g. from https://adoptium.net),
check the URL and mods folder, press **Check & Update**. The window saves your
changes back to `updater.json`.

CLI / no window: `java -jar nebula-updater.jar --cli [--config path\to\updater.json]`

## updater.json

```json
{
  "url": "https://api.github.com/repos/kashwtv/nebula-updater/releases/latest",
  "modsFolder": "%APPDATA%\\.minecraft\\mods",
  "lastSha256": "",
  "lastVersion": ""
}
```

- **url**, either:
  - a GitHub Releases *latest* API URL, `https://api.github.com/repos/kashwtv/nebula-updater/releases/latest`.
    The release needs an asset named `nebula*.jar` (sources jars are ignored).
  - or a direct link to the jar, e.g. `https://example.com/nebula-1.0.0.jar`.
- **modsFolder**: default `%APPDATA%\.minecraft\mods`. For a CurseForge instance use e.g.
  `C:\\Users\\kashg\\curseforge\\minecraft\\Instances\\Fat\\mods` (double `\\` in JSON),
  or click **Browse...** in the window.
- `lastSha256` / `lastVersion` are filled in by the updater.

## Building (JDK 17)

- Linux/macOS: `bash build.sh`
- Windows: `build.bat`

Output goes to `out/` (`nebula-updater.jar`, `.bat`, `updater.json`, README).
No external dependencies.

## Releases (GitHub Actions)

`.github/workflows/build.yml` builds on every `v*` tag, on published releases, and on
manual runs. It uploads the jar and a zip as artifacts and attaches them to the release.

```
git tag v1.0.0 && git push origin v1.0.0
```

## License

MIT, see `LICENSE`.


> **Moved:** the mod now lives at https://github.com/kashwtv/nebula — updater is the `updater/` folder there.
