// The app icon: a four-lobed Celtic knot in the spirit of the Dara knot (Irish "doire", oak),
// a gold band on navy. The knot is one closed curve, z(t) = e^(3it) + 0.6 e^(-it), which
// crosses itself eight times; the band goes alternately over and under at the crossings, and
// is cut where it passes under, so every output works on any background.
//
// Run by scripts/app-icons.py (java scripts/KnotIcon.java <job>...). Each job is one of:
//   png <px> tile <margin> <out.png>  rounded navy square inset by margin (fraction), transparent around it
//   png <px> full <out.png>           opaque navy square (iOS rounds the corners itself)
//   svg <out.svg>                     the tile as SVG artwork (1024 x 1024)
//   android <res dir>                 adaptive icon: foreground, monochrome and background
//   feature <out.png>                 Google Play feature graphic (1024 x 500): knot, name and tagline
// Uses only the JDK.
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

public class KnotIcon {
    static final Color NAVY = new Color(0x1c2a4a), GOLD = new Color(0xe6c36a);
    static int samples = 1440; // fewer for the vector outputs, to keep them small

    /** The curve, with its largest extent scaled to 1. */
    static double[][] curve() {
        double[][] p = new double[samples][2];
        double m = 0;
        for (int k = 0; k < samples; k++) {
            double t = 2 * Math.PI * k / samples;
            p[k][0] = Math.cos(3 * t) + 0.6 * Math.cos(-t);
            p[k][1] = Math.sin(3 * t) + 0.6 * Math.sin(-t);
            m = Math.max(m, Math.max(Math.abs(p[k][0]), Math.abs(p[k][1])));
        }
        for (double[] q : p) { q[0] /= m; q[1] /= m; }
        return p;
    }

    /**
     * The knot as one filled shape, in the curve's units: the band ([width] wide) along the
     * whole curve, with a strip [gap] wider than the band cleared along the over-pass at each
     * crossing and the over-pass put back, so the under-pass stops [gap] short of it.
     */
    static Area shape(double width, double gap) {
        double[][] p = curve();
        int n = p.length;
        // Crossings: positions (segment index + fraction) of the two passes.
        List<double[]> xs = new ArrayList<>();
        for (int i = 0; i < n; i++)
            for (int j = i + 2; j < n; j++) {
                if (i == 0 && j == n - 1) continue;
                double[] a = p[i], b = p[(i + 1) % n], c = p[j], d = p[(j + 1) % n];
                double den = (b[0] - a[0]) * (d[1] - c[1]) - (b[1] - a[1]) * (d[0] - c[0]);
                if (Math.abs(den) < 1e-12) continue;
                double s = ((c[0] - a[0]) * (d[1] - c[1]) - (c[1] - a[1]) * (d[0] - c[0])) / den;
                double u = ((c[0] - a[0]) * (b[1] - a[1]) - (c[1] - a[1]) * (b[0] - a[0])) / den;
                if (s >= 0 && s < 1 && u >= 0 && u < 1) xs.add(new double[] {i + s, j + u});
            }
        // Along a closed curve the passes can alternate over, under, over, ...: every other
        // pass, in order along the curve, is an over-pass.
        double[] passes = xs.stream().flatMapToDouble(Arrays::stream).sorted().toArray();
        Area band = new Area(stroke(polyline(p, 0, n, true), width));
        // At each crossing, the under-pass loses the part of it that comes within [gap] of the
        // over-pass; the over-pass isn't touched. Both are taken only near the crossing (up to the
        // neighboring crossings along each strand), so other crossings are unaffected.
        List<Area> cuts = new ArrayList<>();
        for (double[] x : xs) {
            boolean firstOver = Arrays.binarySearch(passes, x[0]) % 2 == 0;
            double o = firstOver ? x[0] : x[1], u = firstOver ? x[1] : x[0];
            double ro = reach(passes, o, n), ru = reach(passes, u, n);
            Area under = new Area(stroke(polyline(p, u - 2 * ru, u + 2 * ru, false), width));
            Area over = new Area(stroke(polyline(p, o - 2 * ro, o + 2 * ro, false), width));
            under.intersect(new Area(stroke(polyline(p, o - 2 * ro, o + 2 * ro, false), width + 2 * gap)));
            under.subtract(over);
            cuts.add(under);
        }
        cuts.forEach(band::subtract);
        return band;
    }

    /** Halfway from pass [pos] to the nearest other pass along the curve, in samples. */
    static double reach(double[] passes, double pos, int n) {
        double best = n;
        for (double q : passes) {
            double d = Math.abs(q - pos);
            d = Math.min(d, n - d);
            if (d > 1e-9) best = Math.min(best, d);
        }
        return 0.5 * best;
    }

    static Path2D polyline(double[][] p, double from, double to, boolean closed) {
        Path2D path = new Path2D.Double();
        if (closed) {
            for (int i = 0; i < p.length; i++) if (i == 0) path.moveTo(p[i][0], p[i][1]); else path.lineTo(p[i][0], p[i][1]);
            path.closePath();
            return path;
        }
        double[] a = at(p, from);
        path.moveTo(a[0], a[1]);
        for (int i = (int) Math.ceil(from); i < to; i++) { double[] q = at(p, i); path.lineTo(q[0], q[1]); }
        double[] b = at(p, to);
        path.lineTo(b[0], b[1]);
        return path;
    }

    static Shape stroke(Shape s, double w) {
        return new BasicStroke((float) w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND).createStrokedShape(s);
    }

    static double[] at(double[][] p, double pos) {
        int n = p.length;
        pos = ((pos % n) + n) % n;
        int i = (int) Math.floor(pos);
        double f = pos - i;
        double[] a = p[i], b = p[(i + 1) % n];
        return new double[] {a[0] + f * (b[0] - a[0]), a[1] + f * (b[1] - a[1])};
    }

    // Band and gap, in the curve's units. Small icons get a bolder band so the weave still shows.
    static double[] style(double px) { return px <= 40 ? new double[] {0.27, 0.095} : new double[] {0.208, 0.083}; }

    /** The knot as a shape centered at (cx, cy), with the curve's extent [r]; styled for [px] icons. */
    static Shape placed(double cx, double cy, double r, double px) {
        double[] st = style(px);
        AffineTransform t = new AffineTransform(r, 0, 0, r, cx, cy);
        return shape(st[0], st[1]).createTransformedArea(t);
    }

    static void draw(Graphics2D g, double cx, double cy, double r, double px) {
        g.setColor(GOLD);
        g.fill(placed(cx, cy, r, px));
    }

    static Graphics2D smooth(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    // The knot's extent as a fraction of the square it sits on, and the square's corner radius.
    static final double EXTENT = 0.36, CORNER = 0.225;

    static void png(String[] a, int i) throws IOException {
        samples = 1440;
        int px = Integer.parseInt(a[i + 1]);
        String kind = a[i + 2];
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(img);
        String out;
        if (kind.equals("tile")) {
            double m = Double.parseDouble(a[i + 3]) * px, body = px - 2 * m;
            g.setColor(NAVY);
            g.fill(new RoundRectangle2D.Double(m, m, body, body, 2 * CORNER * body, 2 * CORNER * body));
            draw(g, px / 2.0, px / 2.0, EXTENT * body, px);
            out = a[i + 4];
        } else {
            g.setColor(NAVY);
            g.fillRect(0, 0, px, px);
            draw(g, px / 2.0, px / 2.0, EXTENT * px, px);
            out = a[i + 3];
            // iOS wants no alpha channel.
            BufferedImage rgb = new BufferedImage(px, px, BufferedImage.TYPE_INT_RGB);
            rgb.createGraphics().drawImage(img, 0, 0, null);
            img = rgb;
        }
        g.dispose();
        ImageIO.write(img, "png", new File(out));
    }

    /** Google Play's feature graphic: 1024 x 500, opaque, the knot beside the name and tagline. */
    static void feature(String out) throws IOException {
        samples = 1440;
        int w = 1024, h = 500;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = smooth(img);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(NAVY);
        g.fillRect(0, 0, w, h);
        // The knot as on a 460 px icon, so the band keeps the icon's proportions.
        double square = 460;
        draw(g, 255, h / 2.0, EXTENT * square, square);
        int x = 480;
        g.setColor(Color.WHITE);
        g.setFont(new Font("Avenir Next", Font.BOLD, 92));
        g.drawString("Dara Notes", x, 235);
        g.setColor(GOLD);
        g.setFont(new Font("Avenir Next", Font.PLAIN, 36));
        g.drawString("Markdown notes in your", x, 305);
        g.drawString("own Git repository", x, 353);
        g.dispose();
        ImageIO.write(img, "png", new File(out));
    }

    static String hex(Color c) { return String.format("#%06x", c.getRGB() & 0xffffff); }

    /** SVG / Android path data for [shape], flattened to lines. */
    static String pathData(Shape shape) {
        StringBuilder s = new StringBuilder();
        double[] c = new double[6];
        for (PathIterator it = shape.getPathIterator(null, 0.05); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO -> s.append(String.format(Locale.ROOT, "M%.2f %.2f", c[0], c[1]));
                case PathIterator.SEG_LINETO -> s.append(String.format(Locale.ROOT, "L%.2f %.2f", c[0], c[1]));
                case PathIterator.SEG_CLOSE -> s.append("Z");
            }
        }
        return s.toString();
    }

    static void svg(String out) throws IOException {
        int S = 1024;
        samples = 480;
        String svg = String.format(Locale.ROOT,
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">\n"
                + "  <rect width=\"%d\" height=\"%d\" rx=\"%.1f\" fill=\"%s\"/>\n"
                + "  <path fill=\"%s\" fill-rule=\"evenodd\" d=\"%s\"/>\n</svg>\n",
            S, S, S, S, S, S, CORNER * S, hex(NAVY), hex(GOLD), pathData(placed(S / 2.0, S / 2.0, EXTENT * S, S)));
        Files.writeString(Path.of(out), svg);
    }

    /** Adaptive icon (108 x 108 dp, artwork inside the 66 dp safe zone), plus a themed-icon layer. */
    static void android(String res) throws IOException {
        // The extent plus half the band stays within the 33 dp safe-zone radius.
        samples = 360;
        String data = pathData(placed(54, 54, 30, 108));
        String paths = "    <path android:fillColor=\"COLOR\" android:fillType=\"evenOdd\"\n        android:pathData=\"" + data + "\"/>\n";
        String head = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!-- Generated by scripts/KnotIcon.java; don't edit. -->\n"
            + "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    android:width=\"108dp\" android:height=\"108dp\" android:viewportWidth=\"108\" android:viewportHeight=\"108\">\n";
        Path drawable = Path.of(res, "drawable"), mipmap = Path.of(res, "mipmap-anydpi-v26"), values = Path.of(res, "values");
        Files.createDirectories(drawable);
        Files.createDirectories(mipmap);
        Files.createDirectories(values);
        Files.writeString(drawable.resolve("ic_launcher_foreground.xml"), head + paths.replace("COLOR", hex(GOLD)) + "</vector>\n");
        // Themed icons use only this layer's shape; the system picks the colors.
        Files.writeString(drawable.resolve("ic_launcher_monochrome.xml"), head + paths.replace("COLOR", "#ffffff") + "</vector>\n");
        Files.writeString(values.resolve("ic_launcher.xml"), "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!-- Generated by scripts/KnotIcon.java; don't edit. -->\n<resources>\n"
            + "    <color name=\"ic_launcher_background\">" + hex(NAVY) + "</color>\n</resources>\n");
        Files.writeString(mipmap.resolve("ic_launcher.xml"), "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!-- Generated by scripts/KnotIcon.java; don't edit. -->\n"
            + "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
            + "    <background android:drawable=\"@color/ic_launcher_background\"/>\n"
            + "    <foreground android:drawable=\"@drawable/ic_launcher_foreground\"/>\n"
            + "    <monochrome android:drawable=\"@drawable/ic_launcher_monochrome\"/>\n"
            + "</adaptive-icon>\n");
    }

    public static void main(String[] a) throws IOException {
        System.setProperty("java.awt.headless", "true");
        for (int i = 0; i < a.length; ) {
            switch (a[i]) {
                case "png" -> { png(a, i); i += a[i + 2].equals("tile") ? 5 : 4; }
                case "svg" -> { svg(a[i + 1]); i += 2; }
                case "android" -> { android(a[i + 1]); i += 2; }
                case "feature" -> { feature(a[i + 1]); i += 2; }
                default -> throw new IllegalArgumentException("Unknown job " + a[i]);
            }
        }
    }
}
