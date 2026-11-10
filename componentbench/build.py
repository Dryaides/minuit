#!/usr/bin/env python3
"""Build and preview minuit components with no Minecraft and no Gradle.

    python build.py setup      check the tools and make the test pictures in bench/images
    python build.py compile    compile core, protocol and bench/ (the components live in core)
    python build.py gallery    compile, render every demo, write build/gallery/index.html
    python build.py gallery --demo Button --themes soft:grey:light,glass:clear:dark --scale 3
    python build.py gallery --demo Button --state hover,disabled    redraw only these states, keep the rest
    python build.py ingame     run the repo's Gradle wrapper in ingame/ (build, runClient ...)

Tools: a JDK 17 or newer, from $MINUIT_JDK (the JDK folder) or else the java and javac on PATH; Gson from
$MINUIT_GSON (the jar) or else the newest Gson in the Gradle cache (~/.gradle). Nothing is downloaded.
Game textures and the font come from the Minecraft jar in $BENCH_MC_JAR, else from Loom's cache under
$GRADLE_USER_HOME or ~/.gradle; without one the bench draws stand-ins.

Each run compiles into its own build/run/<pid> folder.
"""
import os, re, shutil, subprocess, sys, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PROJECT = ROOT.parent
GRADLE_HOME = Path(os.environ.get("GRADLE_USER_HOME") or Path.home() / ".gradle")
CORE_SRC = [PROJECT / "core" / "src" / "main" / "java", PROJECT / "protocol" / "src" / "main" / "java"]
CORE_ASSETS = PROJECT / "core" / "src" / "main" / "resources" / "assets"
# Themes and textures sit in core, the lang files in common: the bench reads one merged folder.
ASSET_DIRS = [CORE_ASSETS, PROJECT / "common" / "src" / "main" / "resources" / "assets"]
BUILD = ROOT / "build"
EXE = ".exe" if os.name == "nt" else ""



def find_tool(name):
    jdk = os.environ.get("MINUIT_JDK")
    if jdk:
        p = Path(jdk) / "bin" / (name + EXE)
        if p.exists():
            return str(p)
        sys.exit(f"MINUIT_JDK={jdk} has no bin/{name}{EXE}")
    found = shutil.which(name)
    if not found:
        sys.exit(f"no {name} on PATH: install a JDK 17 or newer, or set MINUIT_JDK to its folder")
    return found


def find_gson():
    env = os.environ.get("MINUIT_GSON")
    if env:
        if not Path(env).is_file():
            sys.exit(f"MINUIT_GSON={env} is not a file")
        return Path(env)
    hits = sorted((GRADLE_HOME / "caches" / "modules-2" / "files-2.1" / "com.google.code.gson" / "gson").glob("*/*/gson-*.jar"))
    hits = [h for h in hits if "sources" not in h.name]
    if not hits:
        sys.exit("no Gson found: set MINUIT_GSON to a gson jar (or run any Gradle build of this repo once)")
    return hits[-1]


# The level core is built at.
RELEASE = "17"


def setup():
    """Checks the tools and makes the test pictures (bench/images/*.png are not in git)."""
    print("javac:", find_tool("javac"), " Gson:", find_gson())
    subprocess.run([find_tool("java"), "bench/images/MakeImages.java", "bench/images"], cwd=ROOT, check=True)


def sources(*dirs):
    out = []
    for d in dirs:
        if d.exists():
            out += sorted(str(p) for p in d.rglob("*.java"))
    return out


def javac(dest, classpath, files):
    dest.mkdir(parents=True, exist_ok=True)
    if not files:
        return
    argfile = dest.parent / (dest.name + ".args")
    argfile.write_text("\n".join('"' + f.replace("\\", "/") + '"' for f in files), encoding="utf-8")
    cmd = [find_tool("javac"), "-J-Xms32m", "-J-Xmx512m", "--release", RELEASE, "-encoding", "UTF-8", "-proc:none",
           "-Xlint:-options", "-implicit:none", "-sourcepath", "", "-d", str(dest),
           "-cp", os.pathsep.join(str(c) for c in classpath), "@" + str(argfile)]
    r = subprocess.run(cmd)
    if r.returncode:
        sys.exit(r.returncode)


def compile_all(out):
    """Core and protocol from source (no Minecraft on the classpath), then the bench on top."""
    main, bench = out / "main", out / "bench"
    javac(main, [find_gson()], sources(*CORE_SRC))
    javac(bench, [main, find_gson()], sources(ROOT / "bench"))
    # core's resources hold the built-in themes, read from the classpath
    return [bench, main, CORE_ASSETS.parent, find_gson()]


def merged_assets(d):
    out = d / "assets"
    for a in ASSET_DIRS:
        shutil.copytree(a, out, dirs_exist_ok=True)
    return out


def run_dir():
    d = BUILD / "run" / str(os.getpid())
    shutil.rmtree(d, ignore_errors=True)
    d.mkdir(parents=True)
    return d


def mc_jar():
    """The game jar Loom cached, used only for the font and game textures. Optional."""
    env = os.environ.get("BENCH_MC_JAR")
    if env:
        return env
    home = GRADLE_HOME / "caches" / "fabric-loom"
    if home.exists():
        hits = sorted(home.rglob("*.jar"), key=lambda p: p.stat().st_size, reverse=True)
        for h in hits:
            try:
                with zipfile.ZipFile(h) as z:
                    z.getinfo("assets/minecraft/textures/font/ascii.png")
                    return str(h)
            except (KeyError, zipfile.BadZipFile):
                pass
    return ""


def main():
    args = sys.argv[1:]
    run(args[0] if args else "gallery", args)


def run(cmd, args):
    if cmd == "setup":
        setup()
    elif cmd == "compile":
        d = run_dir()
        try:
            compile_all(d)
            print("compile ok at --release", RELEASE)
        finally:
            shutil.rmtree(d, ignore_errors=True)
    elif cmd == "gallery":
        d = run_dir()
        code = 1
        try:
            cp = compile_all(d)
            base = [find_tool("java"), "-Xms32m", "-Xmx768m", "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8",
                    "-Dbench.classes=" + str(cp[0]), "-Dbench.assets=" + str(merged_assets(d)),
                    "-Dbench.mcjar=" + mc_jar(), "-Dbench.out=" + str(BUILD / "gallery"),
                    "-cp", os.pathsep.join(str(c) for c in cp), "bench.render.Main"]
            # One JVM per theme keeps the heap under 768 MB however many themes there are; every pass
            # fits the stage heights over all the themes and adds its pictures to the same pages.
            listed = subprocess.run(base + [*args[1:], "--list"], capture_output=True, text=True)
            if listed.returncode != 0:
                sys.stderr.write(listed.stderr)
                sys.exit(listed.returncode)
            themes = [t for t in listed.stdout.splitlines() if re.fullmatch(r"[a-z0-9_]+:[a-z0-9_]+:(light|dark)", t)]
            if not themes:
                sys.exit("no theme to draw")
            code = 0
            for i, theme in enumerate(themes):
                extra = ["--only", theme] + (["--add"] if i else [])
                code = subprocess.run(base + args[1:] + extra).returncode or code
        finally:
            shutil.rmtree(d, ignore_errors=True)
        sys.exit(code)
    elif cmd == "perf":
        d = run_dir()
        try:
            cp = compile_all(d)
            sys.exit(subprocess.run([find_tool("java"), "-Xms32m", "-Xmx768m", "-Djava.awt.headless=true",
                                     "-Dfile.encoding=UTF-8", "-Dbench.classes=" + str(cp[0]),
                                     "-Dbench.assets=" + str(merged_assets(d)), "-Dbench.mcjar=" + mc_jar(),
                                     "-cp", os.pathsep.join(str(c) for c in cp), "bench.render.PerfBench",
                                     *args[1:]]).returncode)
        finally:
            shutil.rmtree(d, ignore_errors=True)
    elif cmd == "ingame":
        wrapper = PROJECT / ("gradlew.bat" if os.name == "nt" else "gradlew")
        sys.exit(subprocess.run([str(wrapper), "--no-daemon", "--console=plain", *(args[1:] or ["build"])],
                                cwd=ROOT / "ingame").returncode)
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
