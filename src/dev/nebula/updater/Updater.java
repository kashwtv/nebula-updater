package dev.nebula.updater;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Downloads the newest Nebula jar and swaps it into a Minecraft mods folder.
 * Only touches files: no process attaching, no injection.
 */
public final class Updater {
    public static final String VERSION = "1.0.0";
    static final String MOD_ID = "nebula";

    final Path configFile;
    String url = "";
    String modsFolder = defaultMods();
    String lastSha256 = "";
    String lastVersion = "";

    Updater(Path configFile) { this.configFile = configFile; }

    static String defaultMods() {
        String appdata = System.getenv("APPDATA");
        Path base = appdata != null ? Paths.get(appdata) : Paths.get(System.getProperty("user.home"));
        return base.resolve(".minecraft").resolve("mods").toString();
    }

    @SuppressWarnings("unchecked")
    void load() throws IOException {
        if (!Files.exists(configFile)) { save(); return; }
        Object o = Json.parse(Files.readString(configFile));
        if (!(o instanceof Map)) throw new IOException("updater.json must be a JSON object");
        Map<String, Object> m = (Map<String, Object>) o;
        url = str(m.get("url"), url);
        modsFolder = expand(str(m.get("modsFolder"), modsFolder));
        lastSha256 = str(m.get("lastSha256"), "");
        lastVersion = str(m.get("lastVersion"), "");
    }

    private static String str(Object o, String def) { return o instanceof String s && !s.isBlank() ? s : def; }

    static String expand(String p) {
        Matcher m = Pattern.compile("%([A-Za-z_]+)%").matcher(p);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            String v = System.getenv(m.group(1));
            m.appendReplacement(b, Matcher.quoteReplacement(v != null ? v : m.group()));
        }
        m.appendTail(b);
        return b.toString();
    }

    void save() throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("url", url);
        m.put("modsFolder", modsFolder);
        m.put("lastSha256", lastSha256);
        m.put("lastVersion", lastVersion);
        Files.writeString(configFile, Json.write(m));
    }

    /** What the remote side offers. sha256 may be null when the source doesn't publish one. */
    record Remote(String downloadUrl, String fileName, String version, String sha256) {}

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();

    private HttpRequest.Builder req(String u) {
        return HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofMinutes(2)).header("User-Agent", "nebula-updater/" + VERSION);
    }

    @SuppressWarnings("unchecked")
    Remote resolve() throws Exception {
        if (url.isBlank()) throw new IOException("No download URL set in updater.json");
        if (url.contains("api.github.com/repos/") && url.contains("/releases")) {
            HttpResponse<String> r = http.send(req(url).header("Accept", "application/vnd.github+json").build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) throw new IOException("GitHub API returned HTTP " + r.statusCode());
            Map<String, Object> rel = (Map<String, Object>) Json.parse(r.body());
            String tag = str(rel.get("tag_name"), "?");
            List<Object> assets = (List<Object>) rel.getOrDefault("assets", List.of());
            Map<String, Object> pick = null;
            for (Object a : assets) {
                Map<String, Object> am = (Map<String, Object>) a;
                String n = str(am.get("name"), "");
                if (n.endsWith(".jar") && n.toLowerCase().startsWith("nebula") && !n.contains("sources")) { pick = am; break; }
            }
            if (pick == null) throw new IOException("Release " + tag + " has no nebula*.jar asset");
            String digest = str(pick.get("digest"), "");
            String sha = digest.startsWith("sha256:") ? digest.substring(7).toLowerCase() : null;
            return new Remote(str(pick.get("browser_download_url"), ""), str(pick.get("name"), "nebula.jar"), tag, sha);
        }
        String name = url.substring(url.lastIndexOf('/') + 1);
        int q = name.indexOf('?');
        if (q >= 0) name = name.substring(0, q);
        if (!name.endsWith(".jar")) name = "nebula.jar";
        return new Remote(url, name, null, null);
    }

    Path download(Remote r, Consumer<String> status) throws Exception {
        Path tmp = Files.createTempFile("nebula-update-", ".jar");
        if (r.downloadUrl().startsWith("file:")) {
            Files.copy(Paths.get(URI.create(r.downloadUrl())), tmp, StandardCopyOption.REPLACE_EXISTING);
            return tmp;
        }
        status.accept("Downloading " + r.fileName() + "...");
        HttpResponse<Path> resp = http.send(req(r.downloadUrl()).build(), HttpResponse.BodyHandlers.ofFile(tmp));
        if (resp.statusCode() != 200) { Files.deleteIfExists(tmp); throw new IOException("Download failed: HTTP " + resp.statusCode()); }
        return tmp;
    }

    static String sha256(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(p)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder b = new StringBuilder();
        for (byte x : md.digest()) b.append(String.format("%02x", x));
        return b.toString();
    }

    /** Returns the mod version if the jar is a real Nebula mod jar (modId "nebula", dev.nebula classes); throws otherwise. */
    static String verifyJar(Path jar) throws IOException {
        try (java.util.jar.JarFile z = new java.util.jar.JarFile(jar.toFile())) {
            ZipEntry toml = z.getEntry("META-INF/mods.toml");
            if (toml == null) throw new IOException("not a Forge mod (no META-INF/mods.toml)");
            String t;
            try (InputStream in = z.getInputStream(toml)) { t = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
            if (!Pattern.compile("modId\\s*=\\s*\"" + MOD_ID + "\"").matcher(t).find()) throw new IOException("mods.toml has no modId=\"" + MOD_ID + "\"");
            if (z.getEntry("dev/nebula/Nebula.class") == null) throw new IOException("jar has no dev.nebula.Nebula class");
            Matcher v = Pattern.compile("(?m)^\\s*version\\s*=\\s*\"([^\"]+)\"").matcher(t);
            String ver = v.find() ? v.group(1) : "?";
            if (ver.contains("${")) {
                var mf = z.getManifest();
                String iv = mf == null ? null : mf.getMainAttributes().getValue("Implementation-Version");
                ver = iv != null ? iv : ver;
            }
            return ver;
        } catch (java.util.zip.ZipException ze) {
            throw new IOException("downloaded file is not a valid jar: " + ze.getMessage());
        }
    }

    List<Path> installedJars(Path mods) throws IOException {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(mods)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(mods, "nebula*.jar")) {
            for (Path p : ds) {
                try { verifyJar(p); out.add(p); } catch (IOException ignored) { /* some other mod named nebula*, leave it */ }
            }
        }
        return out;
    }

    /** Full check-and-update. Returns a one-line result. */
    String run(Consumer<String> status) throws Exception {
        load();
        Path mods = Paths.get(modsFolder);
        if (!Files.isDirectory(mods)) throw new IOException("Mods folder not found: " + mods);
        status.accept("Checking " + (url.contains("api.github.com") ? "GitHub release" : "download link") + "...");
        Remote r = resolve();
        List<Path> installed = installedJars(mods);
        Set<String> haveHashes = new HashSet<>();
        for (Path p : installed) haveHashes.add(sha256(p));
        if (r.sha256() != null && haveHashes.contains(r.sha256()))
            return "Up to date (" + (r.version() != null ? r.version() : installed.get(0).getFileName()) + ")";
        Path tmp = download(r, status);
        try {
            status.accept("Verifying...");
            String sha = sha256(tmp);
            if (r.sha256() != null && !r.sha256().equals(sha)) throw new IOException("SHA-256 mismatch: download is corrupt or was changed");
            String ver = verifyJar(tmp);
            if (haveHashes.contains(sha)) {
                lastSha256 = sha; lastVersion = ver; save();
                return "Up to date (" + ver + ")";
            }
            status.accept("Installing " + ver + "...");
            Path backups = mods.getParent() == null ? mods.resolve("nebula-backups") : mods.getParent().resolve("nebula-backups");
            Files.createDirectories(backups);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path target = mods.resolve(r.fileName());
            // move old Nebula jars OUT of mods (Forge would load both copies otherwise)
            for (Path old : installed) {
                try {
                    Files.move(old, backups.resolve(stamp + "_" + old.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                } catch (FileSystemException fse) {
                    throw new IOException("Can't move " + old.getFileName() + ", is Minecraft still running? Close it and try again.");
                }
            }
            Path part = mods.resolve(r.fileName() + ".part");
            Files.copy(tmp, part, StandardCopyOption.REPLACE_EXISTING);
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            lastSha256 = sha;
            lastVersion = ver;
            save();
            return "Updated to " + ver + " -> " + target.getFileName() + (installed.isEmpty() ? "" : " (old jar backed up in " + backups + ")");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    static Path configPath() {
        try {
            Path jar = Paths.get(Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path dir = Files.isDirectory(jar) ? jar : jar.getParent();
            return dir.resolve("updater.json");
        } catch (Exception e) {
            return Paths.get("updater.json");
        }
    }

    public static void main(String[] args) throws Exception {
        Path cfg = configPath();
        for (int i = 0; i < args.length - 1; i++) if (args[i].equals("--config")) cfg = Paths.get(args[i + 1]);
        if (Arrays.asList(args).contains("--cli") || java.awt.GraphicsEnvironment.isHeadless()) {
            Updater u = new Updater(cfg);
            try {
                System.out.println(u.run(s -> System.out.println("... " + s)));
            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
                System.exit(1);
            }
            return;
        }
        Path c = cfg;
        javax.swing.SwingUtilities.invokeLater(() -> new UpdaterWindow(new Updater(c)).setVisible(true));
    }
}
