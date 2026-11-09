# Widget benches: compile minuit's core, then draw and check every standard widget (WidgetBench),
# the radial wheel, colour picker and window (WindowBench), the data-driven screens (FormBench) and the speech bubble (BubbleBench).
#   bash testbench/run.sh [shots folder] [looks for WidgetBench and SlotBench, e.g. glass/clear,bubbly/default]
#   PX=6 bash testbench/run.sh ... draws every shot at 6 image pixels per GUI pixel (default 3).
cd "$(dirname "$0")/.."
OUT=${1:-build/testbench-shots}
./gradlew :core:compileJava -Pmc_version=26.1.2 --no-daemon --max-workers=4 -Dorg.gradle.jvmargs=-Xmx2G -q 2>&1 | grep -i "error" | head
MC=$(cygpath -w "$HOME/.gradle/caches/fabric-loom/26.1.2/minecraft-merged.jar")
GSON=$(cygpath -w $(ls $HOME/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.11.0/*/gson-2.11.0.jar | head -1))
CP="core/build/classes/java/main;protocol/build/classes/java/main;core/src/main/resources;$GSON"
mkdir -p build/testbench
javac -encoding UTF-8 -cp "$CP" -d build/testbench testbench/BenchDraw.java testbench/WidgetBench.java  testbench/BenchMock.java testbench/site/SiteScenes.java testbench/site/SlotScenes.java testbench/site/McScenes.java testbench/WindowBench.java testbench/FormBench.java testbench/ReelBench.java testbench/BubbleBench.java testbench/McBench.java testbench/SlotBench.java 2>&1 | grep -iA2 error | head -20
java -Xmx1G -Dfile.encoding=UTF-8 -Dbench.px=${PX:-3} -cp "build/testbench;$CP;$MC" WidgetBench "$OUT" $2 2>&1 | tail -40
java -Xmx1G -Dfile.encoding=UTF-8 -Dbench.px=${PX:-3} -cp "build/testbench;$CP;$MC" WindowBench "$OUT/window" 2>&1 | tail -40
java -Xmx1G -Dfile.encoding=UTF-8 -Dbench.px=${PX:-3} -cp "build/testbench;$CP;$MC" FormBench "$OUT/form" 2>&1 | tail -40
java -Xmx1G -Dfile.encoding=UTF-8 -Dbench.px=${PX:-3} -cp "build/testbench;$CP;$MC" ReelBench "$OUT/reel" 2>&1 | tail -40
java -Xmx1G -Dfile.encoding=UTF-8 -Dbench.px=${PX:-3} -cp "build/testbench;$CP;$MC" BubbleBench "$OUT/bubble" 2>&1 | tail -5
java -Xmx1G -Dfile.encoding=UTF-8 -Dbench.px=${PX:-3} -cp "build/testbench;$CP;$MC" McBench "$OUT/mc" 2>&1 | tail -40
java -Xmx1G -Dfile.encoding=UTF-8 -Dbench.px=${PX:-3} -cp "build/testbench;$CP;$MC" SlotBench "$OUT/slot" $2 2>&1 | tail -40
