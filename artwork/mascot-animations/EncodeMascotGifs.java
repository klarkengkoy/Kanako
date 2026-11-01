import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.*;

public class EncodeMascotGifs {
    static final int SIZE = 256;
    record Sprite(BufferedImage image, Rectangle bounds) {}
    record ColorBin(int key, int count) {}
    static int component(int key, int axis) { return ((key >> (axis == 0 ? 10 : axis == 1 ? 5 : 0)) & 31) * 8 + 4; }

    static Sprite extract(BufferedImage sheet, int column, int row) {
        int x0 = column * sheet.getWidth() / 4, x1 = (column + 1) * sheet.getWidth() / 4;
        int y0 = row * sheet.getHeight() / 4, y1 = (row + 1) * sheet.getHeight() / 4;
        int w = x1 - x0, h = y1 - y0;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h], largest = new int[0];
        for (int p = 0; p < seen.length; p++) {
            if (seen[p] || (sheet.getRGB(x0 + p % w, y0 + p / w) >>> 24) < 100) continue;
            int head = 0, tail = 1; queue[0] = p; seen[p] = true;
            while (head < tail) {
                int current = queue[head++], x = current % w, y = current / w;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
                    int next = yy * w + xx;
                    if (!seen[next] && (sheet.getRGB(x0 + xx, y0 + yy) >>> 24) >= 100) {
                        seen[next] = true; queue[tail++] = next;
                    }
                }
            }
            if (tail > largest.length) largest = Arrays.copyOf(queue, tail);
        }
        if (largest.length < w * h / 10) throw new IllegalStateException("Missing character in cell " + column + "," + row);
        BufferedImage cutout = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int left = w, top = h, right = 0, bottom = 0;
        for (int p : largest) {
            int x = p % w, y = p / w;
            cutout.setRGB(x, y, sheet.getRGB(x0 + x, y0 + y));
            left = Math.min(left, x); right = Math.max(right, x);
            top = Math.min(top, y); bottom = Math.max(bottom, y);
        }
        return new Sprite(cutout, new Rectangle(left, top, right - left + 1, bottom - top + 1));
    }

    static IndexColorModel palette(List<BufferedImage> frames) {
        int[] histogram = new int[32768];
        for (BufferedImage frame : frames) for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int color = frame.getRGB(x, y);
            if ((color >>> 24) < 128) continue;
            histogram[((color >> 19) & 31) << 10 | ((color >> 11) & 31) << 5 | ((color >> 3) & 31)]++;
        }
        List<ColorBin> colors = new ArrayList<>();
        for (int k = 0; k < histogram.length; k++) if (histogram[k] != 0) colors.add(new ColorBin(k, histogram[k]));
        List<List<ColorBin>> boxes = new ArrayList<>(); boxes.add(colors);
        while (boxes.size() < 255) {
            int chosen = -1, axis = 0; long best = -1;
            for (int i = 0; i < boxes.size(); i++) {
                var box = boxes.get(i);
                if (box.size() < 2) continue;
                long population = box.stream().mapToLong(ColorBin::count).sum();
                for (int a = 0; a < 3; a++) {
                    final int aa = a;
                    int min = box.stream().mapToInt(c -> component(c.key(), aa)).min().orElse(0);
                    int max = box.stream().mapToInt(c -> component(c.key(), aa)).max().orElse(0);
                    long score = (max - min) * population;
                    if (score > best) { best = score; chosen = i; axis = a; }
                }
            }
            if (chosen < 0) break;
            var box = boxes.remove(chosen); final int selectedAxis = axis;
            box.sort(Comparator.comparingInt(c -> component(c.key(), selectedAxis)));
            long half = box.stream().mapToLong(ColorBin::count).sum() / 2, sum = 0;
            int split = 0;
            while (split < box.size() - 1 && sum < half) sum += box.get(split++).count();
            split = Math.max(1, split);
            boxes.add(new ArrayList<>(box.subList(0, split)));
            boxes.add(new ArrayList<>(box.subList(split, box.size())));
        }
        byte[] r = new byte[256], g = new byte[256], b = new byte[256], alpha = new byte[256];
        for (int i = 0; i < boxes.size(); i++) {
            var box = boxes.get(i); long total = box.stream().mapToLong(ColorBin::count).sum();
            long rr = 0, gg = 0, bb = 0;
            for (ColorBin c : box) { rr += (long)component(c.key(), 0) * c.count(); gg += (long)component(c.key(), 1) * c.count(); bb += (long)component(c.key(), 2) * c.count(); }
            r[i + 1] = (byte)(rr / total); g[i + 1] = (byte)(gg / total); b[i + 1] = (byte)(bb / total); alpha[i + 1] = (byte)255;
        }
        return new IndexColorModel(8, 256, r, g, b, alpha);
    }

    static BufferedImage indexed(BufferedImage frame, IndexColorModel palette, Map<Integer, Integer> cache) {
        BufferedImage indexed = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_BYTE_INDEXED, palette);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int color = frame.getRGB(x, y), selected = 0;
            if ((color >>> 24) >= 128) {
                int key = color & 0xffffff;
                Integer known = cache.get(key);
                if (known == null) {
                    int rr = (color >> 16) & 255, gg = (color >> 8) & 255, bb = color & 255, best = Integer.MAX_VALUE;
                    for (int p = 1; p < palette.getMapSize(); p++) {
                        if (palette.getAlpha(p) == 0) continue;
                        int dr = rr - palette.getRed(p), dg = gg - palette.getGreen(p), db = bb - palette.getBlue(p);
                        int distance = dr * dr + dg * dg + db * db;
                        if (distance < best) { best = distance; selected = p; }
                    }
                    cache.put(key, selected);
                } else selected = known;
            }
            indexed.getRaster().setSample(x, y, 0, selected);
        }
        return indexed;
    }

    static IIOMetadataNode node(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) if (root.item(i).getNodeName().equals(name)) return (IIOMetadataNode)root.item(i);
        IIOMetadataNode value = new IIOMetadataNode(name); root.appendChild(value); return value;
    }

    static void write(Path path, List<BufferedImage> frames, int[] order, IndexColorModel palette) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        Map<Integer, Integer> cache = new HashMap<>();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(path.toFile())) {
            writer.setOutput(output); writer.prepareWriteSequence(null);
            for (int i = 0; i < order.length; i++) {
                BufferedImage frame = indexed(frames.get(order[i]), palette, cache);
                IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(frame), writer.getDefaultWriteParam());
                String format = metadata.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode)metadata.getAsTree(format);
                IIOMetadataNode control = node(root, "GraphicControlExtension");
                control.setAttribute("disposalMethod", "restoreToBackgroundColor");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "TRUE");
                control.setAttribute("transparentColorIndex", "0");
                control.setAttribute("delayTime", i == 0 ? "40" : i == order.length - 1 ? "90" : "16");
                metadata.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(frame, null, metadata), writer.getDefaultWriteParam());
            }
            writer.endWriteSequence();
        } finally { writer.dispose(); }
        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            reader.setInput(input);
            if (reader.getNumImages(true) != order.length) throw new IOException("Wrong frame count");
            for (int i = 0; i < order.length; i++) {
                BufferedImage decoded = reader.read(i);
                if (decoded.getWidth() != SIZE || decoded.getHeight() != SIZE || !decoded.getColorModel().hasAlpha())
                    throw new IOException("Invalid GIF dimensions/transparency");
            }
            System.out.println(path.getFileName() + ": " + order.length + " frames, 256x256, transparent, one-shot; " + Files.size(path) + " bytes");
        } finally { reader.dispose(); }
    }

    public static void main(String[] args) throws Exception {
        BufferedImage sheet = ImageIO.read(Path.of(args[0]).toFile());
        Path destination = Path.of(args[1]); Files.createDirectories(destination);
        List<Sprite> sprites = new ArrayList<>();
        for (int row = 0; row < 4; row++) for (int column = 0; column < 4; column++) sprites.add(extract(sheet, column, row));
        int largest = sprites.stream().mapToInt(s -> Math.max(s.bounds().width, s.bounds().height)).max().orElseThrow();
        double scale = 224.0 / largest;
        List<BufferedImage> frames = new ArrayList<>();
        for (Sprite sprite : sprites) {
            BufferedImage frame = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = frame.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int width = (int)Math.round(sprite.bounds().width * scale), height = (int)Math.round(sprite.bounds().height * scale);
            Rectangle r = sprite.bounds();
            graphics.drawImage(sprite.image(), (SIZE - width) / 2, 244 - height, (SIZE + width) / 2, 244,
                r.x, r.y, r.x + r.width, r.y + r.height, null);
            graphics.dispose(); frames.add(frame);
        }
        IndexColorModel palette = palette(frames);
        String[] names = {"kanako_greeting_wave", "kanako_curious_tilt", "kanako_success", "kanako_error_reaction"};
        for (int row = 0; row < 4; row++) {
            int base = row * 4;
            int[] order = row == 0 ? new int[]{base, base + 1, base, base + 1, base}
                : row == 1 ? new int[]{base, base + 1, base, base + 2, base}
                : new int[]{base, base + 1, base + 2, base + 1, base};
            write(destination.resolve(names[row] + ".gif"), frames, order, palette);
        }
        BufferedImage contact = new BufferedImage(SIZE * 4, SIZE * 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = contact.createGraphics();
        for (int i = 0; i < frames.size(); i++) graphics.drawImage(frames.get(i), (i % 4) * SIZE, (i / 4) * SIZE, null);
        graphics.dispose(); ImageIO.write(contact, "png", destination.resolve("kanako_animation_frames.png").toFile());
    }
}
