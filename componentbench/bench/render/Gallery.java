package bench.render;

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

/** The static page. Each demo keeps its own page.json, so a partial run updates only its demo. */
final class Gallery {
    static final class Cell {
        String family, chroma, variant, file, error;
        Cell(String family, String chroma, String variant, String file, String error) {
            this.family = family; this.chroma = chroma; this.variant = variant; this.file = file; this.error = error;
        }
    }

    static final class StateRow {
        String name; int w; int h; List<Cell> cells;
        StateRow(String name, int w, int h, List<Cell> cells) {
            this.name = name; this.w = w; this.h = h; this.cells = cells;
        }
        List<Cell> cells() { return cells; }
    }

    static final class DemoPage {
        String name; List<StateRow> states;
        DemoPage(String name, List<StateRow> states) { this.name = name; this.states = states; }
        List<StateRow> states() { return states; }
    }

    private static final Gson GSON = new Gson();

    /** The page an earlier pass wrote in {@code dir}, or null. */
    static DemoPage readPage(Path dir) throws IOException {
        Path json = dir.resolve("page.json");
        return Files.exists(json) ? GSON.fromJson(Files.readString(json, StandardCharsets.UTF_8), DemoPage.class) : null;
    }

    static void writePage(Path dir, DemoPage page) throws IOException {
        Files.writeString(dir.resolve("page.json"), GSON.toJson(page), StandardCharsets.UTF_8);
    }

    /** One picture per demo with every state and theme, wrapped so that it stays readable when viewed whole. */
    static void sheet(Path dir, DemoPage page, Path out) throws IOException {
        int gap = 6;
        int label = 14;
        int max = 1400;
        int width = 0;
        int height = gap;
        for (StateRow row : page.states) {
            int perLine = Math.max(1, (max - gap) / (row.w + gap));
            int count = Math.min(perLine, row.cells.size());
            int lines = (row.cells.size() + perLine - 1) / perLine;
            width = Math.max(width, gap + count * (row.w + gap));
            height += lines * (label + row.h + gap);
        }
        if (width == 0) {
            return;
        }
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x24262B));
        g.fillRect(0, 0, width, height);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        int y = gap;
        for (StateRow row : page.states) {
            int x = gap;
            for (Cell c : row.cells) {
                if (x + row.w + gap > Math.max(width, row.w + 2 * gap)) {
                    x = gap;
                    y += label + row.h + gap;
                }
                g.setColor(new Color(0xC8CCD4));
                g.drawString(row.name + " - " + c.family + " " + c.variant, x, y + 11);
                g.drawImage(ImageIO.read(dir.resolve(c.file).toFile()), x, y + label, null);
                x += row.w + gap;
            }
            y += label + row.h + gap;
        }
        g.dispose();
        Path sheets = out.resolve("sheets");
        Files.createDirectories(sheets);
        ImageIO.write(img, "png", sheets.resolve(page.name + ".png").toFile());
    }

    static void writeIndex(Path out, boolean gameFont, int scale) throws IOException {
        List<DemoPage> pages = new ArrayList<>();
        File[] dirs = out.resolve("img").toFile().listFiles(File::isDirectory);
        if (dirs != null) {
            Arrays.sort(dirs);
            for (File f : dirs) {
                Path json = f.toPath().resolve("page.json");
                if (Files.exists(json)) {
                    pages.add(GSON.fromJson(Files.readString(json, StandardCharsets.UTF_8), DemoPage.class));
                }
            }
        }
        Set<String> families = new LinkedHashSet<>();
        for (DemoPage p : pages) {
            for (StateRow r : p.states) {
                for (Cell c : r.cells) {
                    families.add(c.family);
                }
            }
        }

        StringBuilder h = new StringBuilder();
        h.append("""
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1"><title>minuit bench</title>
                <style>
                :root{color-scheme:dark;--bg:#1b1d21;--fg:#e4e6ea;--soft:#9aa0aa;--line:#33363d}
                body{margin:0;background:var(--bg);color:var(--fg);font:14px system-ui,sans-serif}
                header{position:sticky;top:0;z-index:2;background:var(--bg);border-bottom:1px solid var(--line);
                  padding:10px 16px;display:flex;flex-wrap:wrap;gap:6px 16px;align-items:center}
                header b{font-size:15px}label{white-space:nowrap;color:var(--soft)}label:has(:checked){color:var(--fg)}
                nav a{color:var(--soft);margin-right:10px}main{padding:0 16px 40px}
                h2{margin:28px 0 4px;font-size:18px}h3{margin:16px 0 6px;font-size:13px;font-weight:600;color:var(--soft)}
                .row{display:flex;flex-wrap:wrap;gap:12px;align-items:flex-start}
                figure{margin:0}figure[hidden]{display:none}figcaption{font-size:11px;color:var(--soft);margin-bottom:3px}
                img{display:block;image-rendering:pixelated;max-width:100%}.err{color:#ff8a80}
                .note{color:var(--soft);font-size:12px}
                </style></head><body><header><b>minuit bench</b>
                """);
        for (String f : families) {
            h.append("<label><input type=checkbox data-f=\"").append(esc(f)).append("\" checked> ").append(esc(f)).append("</label>");
        }
        h.append("<label><input type=checkbox data-v=light checked> light</label><label><input type=checkbox data-v=dark checked> dark</label>");
        h.append("<label>zoom <select id=z><option value=1>1x</option><option value=2>2x</option><option value=0.5>0.5x</option></select></label>");
        h.append("<nav>");
        for (DemoPage p : pages) {
            h.append("<a href=\"#").append(esc(p.name)).append("\">").append(esc(p.name)).append("</a>");
        }
        h.append("</nav><span class=note>").append(gameFont ? "game font" : "stand-in font (no game jar found)")
                .append(", GUI scale ").append(scale).append(", not the game's renderer</span></header><main>");
        for (DemoPage p : pages) {
            h.append("<h2 id=\"").append(esc(p.name)).append("\">").append(esc(p.name)).append("</h2>");
            for (StateRow r : p.states) {
                h.append("<h3>").append(esc(r.name)).append("</h3><div class=row>");
                for (Cell c : r.cells) {
                    h.append("<figure data-family=\"").append(esc(c.family)).append("\" data-variant=\"").append(esc(c.variant)).append("\"><figcaption>")
                            .append(esc(c.family)).append(' ').append(esc(c.chroma)).append(' ').append(esc(c.variant)).append("</figcaption>");
                    if (c.error != null) {
                        h.append("<div class=err>").append(esc(c.error)).append("</div>");
                    }
                    h.append("<img loading=lazy width=").append(r.w).append(" height=").append(r.h).append(" data-w=").append(r.w)
                            .append(" alt=\"").append(esc(p.name + " " + r.name + ", " + c.family + " " + c.variant))
                            .append("\" src=\"img/").append(esc(p.name)).append('/').append(esc(c.file)).append("\"></figure>");
                }
                h.append("</div>");
            }
        }
        h.append("""
                </main><script>
                const on=a=>new Set([...document.querySelectorAll('input['+a+']:checked')].map(i=>i.getAttribute(a)));
                function apply(){const f=on('data-f'),v=on('data-v'),z=+document.getElementById('z').value;
                 document.querySelectorAll('figure').forEach(e=>{e.hidden=!(f.has(e.dataset.family)&&v.has(e.dataset.variant))});
                 document.querySelectorAll('img').forEach(i=>{i.style.width=(i.dataset.w*z)+'px';i.style.height='auto'});}
                document.querySelectorAll('input,select').forEach(i=>i.addEventListener('change',apply));apply();
                </script></body></html>
                """);
        Files.writeString(out.resolve("index.html"), h.toString(), StandardCharsets.UTF_8);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
