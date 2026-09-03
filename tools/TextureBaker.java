import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Пекарня текстур: доводит картинки из генератора до вида, пригодного для Minecraft.
 *
 * Генераторы выдают шумные, часто тёмные и слишком большие картинки. Пекарня
 * ужимает их до 16 x 16, вытягивает яркость, сводит палитру к десятку цветов
 * и при необходимости выбивает фон в прозрачность. На выходе — PNG, который
 * не выпадает из ванильных текстур, и лист превью, чтобы проверить глазами.
 *
 * Запуск (JDK 21 умеет исполнять .java напрямую, сборка не нужна):
 *
 *   java tools/TextureBaker.java --in textures_src --out plaguecore/src/main/resources/assets/plaguecore/textures/block
 *
 * В Git Bash под Windows консоль не в UTF-8, и русский отчёт превращается в кашу.
 * Лечится ключом JVM: java -Dstdout.encoding=UTF-8 tools/TextureBaker.java ...
 *
 * Ключи:
 *   --in <файл|папка>   что печём (обязательный)
 *   --out <папка>       куда кладём готовое (обязательный)
 *   --size 16           сторона результата в пикселях
 *   --colors 12         сколько цветов оставить в палитре
 *   --brightness 0.34   целевая средняя яркость, 0..1
 *   --alpha none        выбивание фона: none | auto | #RRGGBB
 *   --alpha-tol 40      порог похожести на фоновый цвет, 0..255
 *   --seamless          сшить противоположные края, чтобы текстура тайлилась
 *   --names <файл>      карта имён «русское_имя = latin_name», по строке на файл
 *   --preview <файл>    лист превью (по умолчанию <out>/_preview.png)
 */
public final class TextureBaker {

    private static final double R_LUM = 0.2126, G_LUM = 0.7152, B_LUM = 0.0722;

    public static void main(String[] args) throws Exception {
        Map<String, String> opt = parseArgs(args);
        if (!opt.containsKey("in") || !opt.containsKey("out")) {
            System.out.println("Нужны --in и --out. Полный список ключей — в шапке файла.");
            return;
        }

        Path in = Path.of(opt.get("in"));
        Path out = Path.of(opt.get("out"));
        int size = Integer.parseInt(opt.getOrDefault("size", "16"));
        int colors = Integer.parseInt(opt.getOrDefault("colors", "12"));
        double brightness = Double.parseDouble(opt.getOrDefault("brightness", "0.34"));
        String alpha = opt.getOrDefault("alpha", "none");
        int alphaTol = Integer.parseInt(opt.getOrDefault("alpha-tol", "40"));
        Map<String, String> names = opt.containsKey("names")
                ? readNames(Path.of(opt.get("names")))
                : Map.of();

        Files.createDirectories(out);
        List<Path> sources = collectSources(in);
        if (sources.isEmpty()) {
            System.out.println("В " + in + " нет картинок (.png, .jpg, .jpeg).");
            return;
        }

        List<Baked> baked = new ArrayList<>();
        for (Path src : sources) {
            BufferedImage raw = ImageIO.read(src.toFile());
            if (raw == null) {
                System.out.println("Не читается, пропущено: " + src.getFileName());
                continue;
            }
            Stats before = stats(raw);

            BufferedImage img = downscale(raw, size);
            if (opt.containsKey("seamless")) {
                img = makeSeamless(img);
            }
            img = normalizeLuminance(img, brightness);
            if (!"none".equals(alpha)) {
                img = keyOutBackground(img, alpha, alphaTol);
            }
            img = quantize(img, colors);

            String name = targetName(src, names);
            Path dst = out.resolve(name + ".png");
            ImageIO.write(img, "png", dst.toFile());

            Stats after = stats(img);
            Seams seams = seams(img);
            baked.add(new Baked(name, img, seams));

            System.out.printf(Locale.ROOT,
                    "%-28s -> %-22s  %dx%d  цветов %d->%d  яркость %.0f%%->%.0f%%  шов %s%n",
                    src.getFileName(), dst.getFileName(),
                    raw.getWidth(), raw.getHeight(),
                    before.colors, after.colors,
                    before.luminance * 100, after.luminance * 100,
                    seams.verdict());
        }

        if (!baked.isEmpty()) {
            Path preview = Path.of(opt.getOrDefault("preview", out.resolve("_preview.png").toString()));
            writePreview(baked, preview);
            System.out.println("Лист превью: " + preview.toAbsolutePath());
        }
    }

    // ---------- этапы обработки ----------

    /** Усреднение по площади: каждый пиксель результата — средний цвет своей области оригинала. */
    private static BufferedImage downscale(BufferedImage src, int size) {
        BufferedImage dst = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        double sx = (double) src.getWidth() / size;
        double sy = (double) src.getHeight() / size;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int x0 = (int) (x * sx), x1 = Math.max(x0 + 1, (int) ((x + 1) * sx));
                int y0 = (int) (y * sy), y1 = Math.max(y0 + 1, (int) ((y + 1) * sy));
                long r = 0, g = 0, b = 0, a = 0, n = 0;
                for (int yy = y0; yy < Math.min(y1, src.getHeight()); yy++) {
                    for (int xx = x0; xx < Math.min(x1, src.getWidth()); xx++) {
                        int p = src.getRGB(xx, yy);
                        a += (p >>> 24) & 0xFF;
                        r += (p >> 16) & 0xFF;
                        g += (p >> 8) & 0xFF;
                        b += p & 0xFF;
                        n++;
                    }
                }
                if (n == 0) n = 1;
                dst.setRGB(x, y, argb((int) (a / n), (int) (r / n), (int) (g / n), (int) (b / n)));
            }
        }
        return dst;
    }

    /**
     * Растягивает контраст по перцентилям и подтягивает среднюю яркость к цели.
     * Множитель применяется ко всем каналам сразу, поэтому оттенок не плывёт.
     */
    private static BufferedImage normalizeLuminance(BufferedImage img, double target) {
        List<Double> lums = new ArrayList<>();
        forEachOpaque(img, (x, y, p) -> lums.add(luminance(p)));
        if (lums.isEmpty()) return img;
        lums.sort(Comparator.naturalOrder());

        double lo = lums.get((int) (lums.size() * 0.05));
        double hi = lums.get(Math.min(lums.size() - 1, (int) (lums.size() * 0.95)));
        double span = Math.max(1e-3, hi - lo);

        BufferedImage stretched = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        double sum = 0;
        int count = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                double l = luminance(p);
                double stretchedL = 0.10 + (l - lo) / span * 0.75;
                double gain = l < 1e-3 ? 1.0 : clamp(stretchedL, 0.02, 1.0) / l;
                int q = scale(p, gain);
                stretched.setRGB(x, y, q);
                if (((p >>> 24) & 0xFF) > 8) {
                    sum += luminance(q);
                    count++;
                }
            }
        }

        double mean = count == 0 ? target : sum / count;
        double gain = mean < 1e-3 ? 1.0 : target / mean;
        BufferedImage dst = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                dst.setRGB(x, y, scale(stretched.getRGB(x, y), gain));
            }
        }
        return dst;
    }

    /**
     * Сшивает противоположные края: приграничные пиксели подмешиваются к своим
     * визави с другой стороны, вес падает по мере удаления от края. Картинки из
     * генератора не тайлятся никогда, а в мире одна текстура ложится рядом с собой
     * тысячи раз — без этого этапа шов виден как решётка на всю Гниль.
     */
    private static BufferedImage makeSeamless(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int band = Math.max(1, Math.min(w, h) / 8);
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double wx = edgeWeight(x, w, band);
                double wy = edgeWeight(y, h, band);
                int p = img.getRGB(x, y);
                int mixed = p;
                if (wx > 0) mixed = blend(mixed, img.getRGB(w - 1 - x, y), wx * 0.5);
                if (wy > 0) mixed = blend(mixed, img.getRGB(x, h - 1 - y), wy * 0.5);
                dst.setRGB(x, y, mixed);
            }
        }
        return dst;
    }

    /** Единица у самой кромки, ноль за пределами приграничной полосы. */
    private static double edgeWeight(int coordinate, int span, int band) {
        int distance = Math.min(coordinate, span - 1 - coordinate);
        return distance >= band ? 0 : (band - distance) / (double) band;
    }

    private static int blend(int a, int b, double t) {
        int alpha = (a >>> 24) & 0xFF;
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return argb(alpha, r, g, bl);
    }

    /** Сводит палитру к n цветам медианным сечением — так уходит шум JPEG и градиенты генератора. */
    private static BufferedImage quantize(BufferedImage img, int n) {
        List<int[]> pixels = new ArrayList<>();
        forEachOpaque(img, (x, y, p) -> pixels.add(new int[]{(p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF}));
        if (pixels.isEmpty()) return img;

        List<List<int[]>> boxes = new ArrayList<>();
        boxes.add(pixels);
        while (boxes.size() < n) {
            List<int[]> widest = null;
            int widestChannel = 0, widestRange = -1, widestIndex = -1;
            for (int i = 0; i < boxes.size(); i++) {
                List<int[]> box = boxes.get(i);
                if (box.size() < 2) continue;
                for (int c = 0; c < 3; c++) {
                    int min = 255, max = 0;
                    for (int[] px : box) {
                        min = Math.min(min, px[c]);
                        max = Math.max(max, px[c]);
                    }
                    if (max - min > widestRange) {
                        widestRange = max - min;
                        widest = box;
                        widestChannel = c;
                        widestIndex = i;
                    }
                }
            }
            if (widest == null || widestRange <= 0) break;
            final int channel = widestChannel;
            widest.sort(Comparator.comparingInt(px -> px[channel]));
            int mid = widest.size() / 2;
            List<int[]> left = new ArrayList<>(widest.subList(0, mid));
            List<int[]> right = new ArrayList<>(widest.subList(mid, widest.size()));
            boxes.set(widestIndex, left);
            boxes.add(right);
        }

        List<int[]> palette = new ArrayList<>();
        for (List<int[]> box : boxes) {
            if (box.isEmpty()) continue;
            long r = 0, g = 0, b = 0;
            for (int[] px : box) {
                r += px[0];
                g += px[1];
                b += px[2];
            }
            palette.add(new int[]{(int) (r / box.size()), (int) (g / box.size()), (int) (b / box.size())});
        }

        BufferedImage dst = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                int a = (p >>> 24) & 0xFF;
                if (a <= 8) {
                    dst.setRGB(x, y, 0);
                    continue;
                }
                int[] best = palette.get(0);
                int bestDist = Integer.MAX_VALUE;
                for (int[] c : palette) {
                    int dr = ((p >> 16) & 0xFF) - c[0];
                    int dg = ((p >> 8) & 0xFF) - c[1];
                    int db = (p & 0xFF) - c[2];
                    int d = dr * dr + dg * dg + db * db;
                    if (d < bestDist) {
                        bestDist = d;
                        best = c;
                    }
                }
                dst.setRGB(x, y, argb(a, best[0], best[1], best[2]));
            }
        }
        return dst;
    }

    /** Выбивает фон в прозрачность: цвет берётся из ключа или как самый частый по краю картинки. */
    private static BufferedImage keyOutBackground(BufferedImage img, String spec, int tolerance) {
        int key;
        if ("auto".equals(spec)) {
            Map<Integer, Integer> border = new HashMap<>();
            int w = img.getWidth(), h = img.getHeight();
            for (int x = 0; x < w; x++) {
                border.merge(img.getRGB(x, 0) & 0xFFFFFF, 1, Integer::sum);
                border.merge(img.getRGB(x, h - 1) & 0xFFFFFF, 1, Integer::sum);
            }
            for (int y = 0; y < h; y++) {
                border.merge(img.getRGB(0, y) & 0xFFFFFF, 1, Integer::sum);
                border.merge(img.getRGB(w - 1, y) & 0xFFFFFF, 1, Integer::sum);
            }
            key = border.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(0);
        } else {
            key = Integer.parseInt(spec.replace("#", ""), 16);
        }

        int kr = (key >> 16) & 0xFF, kg = (key >> 8) & 0xFF, kb = key & 0xFF;
        BufferedImage dst = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                int dr = ((p >> 16) & 0xFF) - kr;
                int dg = ((p >> 8) & 0xFF) - kg;
                int db = (p & 0xFF) - kb;
                boolean isBackground = Math.sqrt(dr * dr + dg * dg + db * db) <= tolerance;
                dst.setRGB(x, y, isBackground ? 0 : p);
            }
        }
        return dst;
    }

    // ---------- проверки и превью ----------

    /** Насколько разойдутся края, если положить текстуру рядом с собой. Для полных кубов это важно. */
    private static Seams seams(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        double horizontal = 0, vertical = 0;
        for (int y = 0; y < h; y++) horizontal += distance(img.getRGB(0, y), img.getRGB(w - 1, y));
        for (int x = 0; x < w; x++) vertical += distance(img.getRGB(x, 0), img.getRGB(x, h - 1));
        return new Seams(horizontal / h, vertical / w);
    }

    private static void writePreview(List<Baked> baked, Path file) throws Exception {
        int cell = 128, tiled = 192, pad = 16, labelHeight = 22;
        int rowHeight = Math.max(cell, tiled) + labelHeight + pad;
        int width = pad + cell + pad + tiled + pad + 260;
        BufferedImage sheet = new BufferedImage(width, pad + rowHeight * baked.size(), BufferedImage.TYPE_INT_RGB);

        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x2B2B2B));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());

        int y = pad;
        for (Baked b : baked) {
            drawChecker(g, pad, y, cell, cell);
            g.drawImage(b.image(), pad, y, cell, cell, null);

            int tx = pad + cell + pad;
            drawChecker(g, tx, y, tiled, tiled);
            int tile = tiled / 3;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    g.drawImage(b.image(), tx + i * tile, y + j * tile, tile, tile, null);
                }
            }

            int textX = tx + tiled + pad;
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            g.drawString(b.name() + ".png", textX, y + 20);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.setColor(new Color(0xBBBBBB));
            g.drawString("слева: как выглядит блок", textX, y + 44);
            g.drawString("справа: три на три, ищем швы", textX, y + 64);
            g.setColor(b.seams().ok() ? new Color(0x6FBF73) : new Color(0xD08770));
            g.drawString("шов: " + b.seams().verdict(), textX, y + 88);

            g.setColor(new Color(0x444444));
            g.drawLine(pad, y + rowHeight - pad / 2, sheet.getWidth() - pad, y + rowHeight - pad / 2);
            y += rowHeight;
        }
        g.dispose();

        if (file.getParent() != null) Files.createDirectories(file.getParent());
        ImageIO.write(sheet, "png", file.toFile());
    }

    private static void drawChecker(Graphics2D g, int x, int y, int w, int h) {
        int square = 8;
        for (int i = 0; i < w; i += square) {
            for (int j = 0; j < h; j += square) {
                g.setColor(((i / square + j / square) % 2 == 0) ? new Color(0x808080) : new Color(0x999999));
                g.fillRect(x + i, y + j, Math.min(square, w - i), Math.min(square, h - j));
            }
        }
    }

    // ---------- мелочи ----------

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2);
                String value = (i + 1 < args.length && !args[i + 1].startsWith("--")) ? args[++i] : "true";
                map.put(key, value);
            }
        }
        return map;
    }

    /** Карта имён: строки вида «русское_имя = latin_name», решётка — комментарий. */
    private static Map<String, String> readNames(Path file) throws Exception {
        Map<String, String> map = new HashMap<>();
        if (!Files.exists(file)) return map;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int eq = trimmed.indexOf('=');
            if (eq < 0) continue;
            map.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
        }
        return map;
    }

    private static String targetName(Path src, Map<String, String> names) {
        String file = src.getFileName().toString();
        String bare = file.contains(".") ? file.substring(0, file.lastIndexOf('.')) : file;
        String mapped = names.get(bare);
        if (mapped != null) return mapped;
        String ascii = bare.toLowerCase(Locale.ROOT).replace(' ', '_');
        StringBuilder sb = new StringBuilder();
        for (char c : ascii.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') sb.append(c);
        }
        return sb.isEmpty() ? "texture" : sb.toString();
    }

    private static List<Path> collectSources(Path in) throws Exception {
        List<Path> list = new ArrayList<>();
        if (Files.isDirectory(in)) {
            try (var stream = Files.list(in)) {
                stream.filter(TextureBaker::isImage).sorted().forEach(list::add);
            }
        } else if (isImage(in)) {
            list.add(in);
        }
        return list;
    }

    private static boolean isImage(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
    }

    private interface PixelVisitor {
        void visit(int x, int y, int argb);
    }

    private static void forEachOpaque(BufferedImage img, PixelVisitor visitor) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                if (((p >>> 24) & 0xFF) > 8) visitor.visit(x, y, p);
            }
        }
    }

    private static Stats stats(BufferedImage img) {
        Map<Integer, Integer> hist = new HashMap<>();
        double sum = 0;
        int count = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                if (((p >>> 24) & 0xFF) <= 8) continue;
                hist.merge(p & 0xFFFFFF, 1, Integer::sum);
                sum += luminance(p);
                count++;
            }
        }
        return new Stats(hist.size(), count == 0 ? 0 : sum / count);
    }

    private static double luminance(int argb) {
        return (R_LUM * ((argb >> 16) & 0xFF) + G_LUM * ((argb >> 8) & 0xFF) + B_LUM * (argb & 0xFF)) / 255.0;
    }

    private static double distance(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private static int scale(int argb, double gain) {
        int a = (argb >>> 24) & 0xFF;
        int r = (int) Math.round(clamp(((argb >> 16) & 0xFF) * gain, 0, 255));
        int g = (int) Math.round(clamp(((argb >> 8) & 0xFF) * gain, 0, 255));
        int b = (int) Math.round(clamp((argb & 0xFF) * gain, 0, 255));
        return argb(a, r, g, b);
    }

    private static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private record Stats(int colors, double luminance) {}

    private record Baked(String name, BufferedImage image, Seams seams) {}

    private record Seams(double horizontal, double vertical) {
        boolean ok() {
            return horizontal < 40 && vertical < 40;
        }

        String verdict() {
            double worst = Math.max(horizontal, vertical);
            if (worst < 20) return "незаметен";
            if (worst < 40) return "слабый";
            return "заметный, " + Math.round(worst);
        }
    }
}
