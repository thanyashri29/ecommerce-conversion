import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.Function;

/**
 * E-Commerce Conversion Rate Analysis  |  Codtech Internship Task 3
 *
 * What this program does:
 *   1. Generates a realistic (simulated) e-commerce sessions dataset and saves it as CSV
 *   2. Cleans the data (duplicates, missing values, logic checks)
 *   3. Calculates KPIs and conversion rate by source, device, user type, country
 *   4. Monthly trend, day-of-week pattern, funnel and abandonment analysis
 *   5. Statistical tests (chi-square, two-proportion z-test, Wilson confidence intervals)
 *   6. Opportunity sizing and auto-generated insights
 *   7. Writes a visual report (report.html) with charts - no external libraries needed
 *
 * Run:  java src/EcommerceConversionAnalysis.java     (Java 11 or newer)
 */
public class EcommerceConversionAnalysis {

    // ======================================================================
    //  DATA MODEL
    // ======================================================================
    static class Session {
        String id, source, device, userType, country, category;
        LocalDate date;
        boolean viewed, carted, checkout, purchased;
        double orderValue;

        Session copy() {
            Session s = new Session();
            s.id = id; s.source = source; s.device = device; s.userType = userType;
            s.country = country; s.category = category; s.date = date;
            s.viewed = viewed; s.carted = carted; s.checkout = checkout;
            s.purchased = purchased; s.orderValue = orderValue;
            return s;
        }
    }

    /** Result of grouping sessions by one attribute (e.g. device). */
    static class Seg {
        String name;
        int sessions, orders;
        double revenue;
        double rate()  { return sessions == 0 ? 0 : (double) orders / sessions; }
        double aov()   { return orders == 0 ? 0 : revenue / orders; }
        double rps()   { return sessions == 0 ? 0 : revenue / sessions; }
        double lo()    { return wilson(orders, sessions)[0]; }
        double hi()    { return wilson(orders, sessions)[1]; }
    }

    // ======================================================================
    //  1. DATA GENERATION
    // ======================================================================
    static final int N = 60_000;
    static final String[] SOURCES = {"Organic Search", "Paid Search", "Social Media", "Email", "Direct", "Referral"};
    static final double[] SOURCE_P = {.30, .20, .18, .12, .14, .06};
    static final String[] DEVICES = {"Mobile", "Desktop", "Tablet"};
    static final double[] DEVICE_P = {.60, .33, .07};
    static final String[] USERS = {"New", "Returning"};
    static final double[] USER_P = {.65, .35};
    static final String[] COUNTRIES = {"India", "USA", "UK", "UAE", "Germany"};
    static final double[] COUNTRY_P = {.45, .25, .12, .10, .08};
    static final String[] CATEGORIES = {"Electronics", "Fashion", "Home & Kitchen", "Beauty", "Books"};
    static final double[] CATEGORY_P = {.22, .30, .22, .16, .10};
    static final double[] CATEGORY_AOV = {140, 55, 75, 35, 22};

    static final double[] MONTH_TRAFFIC = {0, .85, .80, .90, .90, .95, .95, .90, .95, 1.0, 1.1, 1.6, 1.5};
    static final double[] MONTH_EFFECT  = {0, .90, .85, .95, .95, 1.0, 1.0, .95, 1.0, 1.0, 1.1, 1.4, 1.5};

    static int pick(Random r, double[] p) {
        double u = r.nextDouble(), c = 0;
        for (int i = 0; i < p.length; i++) { c += p[i]; if (u < c) return i; }
        return p.length - 1;
    }
    static double clip(double x) { return Math.max(0, Math.min(1, x)); }

    static List<Session> generate() {
        Random rnd = new Random(42);

        // date sampling weights: busy months + weekends
        List<LocalDate> days = new ArrayList<>();
        List<Double> cum = new ArrayList<>();
        double total = 0;
        for (LocalDate d = LocalDate.of(2025, 1, 1); !d.isAfter(LocalDate.of(2025, 12, 31)); d = d.plusDays(1)) {
            double w = MONTH_TRAFFIC[d.getMonthValue()] * (d.getDayOfWeek().getValue() >= 6 ? 1.15 : 1.0);
            total += w; days.add(d); cum.add(total);
        }

        double[] srcEff = {1.00, 1.10, 0.50, 2.20, 1.50, 0.90};
        double[] devEff = {0.75, 1.50, 1.00};
        double[] usrEff = {0.75, 1.80};

        List<Session> list = new ArrayList<>();
        double[] mult = new double[N];
        double sumM = 0;
        int[] si = new int[N], di = new int[N], ui = new int[N];

        for (int i = 0; i < N; i++) {
            Session s = new Session();
            s.id = "S" + (100000 + i);
            double u = rnd.nextDouble() * total;
            int idx = Collections.binarySearch(cum, u);
            if (idx < 0) idx = -idx - 1;
            s.date = days.get(Math.min(idx, days.size() - 1));
            si[i] = pick(rnd, SOURCE_P); di[i] = pick(rnd, DEVICE_P); ui[i] = pick(rnd, USER_P);
            s.source = SOURCES[si[i]]; s.device = DEVICES[di[i]]; s.userType = USERS[ui[i]];
            s.country = COUNTRIES[pick(rnd, COUNTRY_P)];
            mult[i] = srcEff[si[i]] * devEff[di[i]] * usrEff[ui[i]] * MONTH_EFFECT[s.date.getMonthValue()];
            sumM += mult[i];
            list.add(s);
        }
        double meanM = sumM / N;

        for (int i = 0; i < N; i++) {
            Session s = list.get(i);
            double m = mult[i] / meanM;
            double pView = clip(0.78 * Math.pow(m, 0.05));
            double pCart = clip(0.13 * Math.pow(m, 0.60));
            double pChk  = clip(0.60 * Math.pow(m, 0.20));
            double pBuy  = clip(0.55 * Math.pow(m, 0.20));
            if (s.device.equals("Mobile")) pBuy *= 0.92;           // mobile checkout friction

            s.viewed    = rnd.nextDouble() < pView;
            s.carted    = s.viewed && rnd.nextDouble() < pCart;
            s.checkout  = s.carted && rnd.nextDouble() < pChk;
            s.purchased = s.checkout && rnd.nextDouble() < pBuy;

            int c = pick(rnd, CATEGORY_P);
            if (s.purchased) {
                s.category = CATEGORIES[c];
                s.orderValue = Math.round(Math.exp(Math.log(CATEGORY_AOV[c]) + 0.45 * rnd.nextGaussian()) * 100) / 100.0;
            } else {
                s.category = "No purchase";
                s.orderValue = 0;
            }
        }

        // make the data messy like a real export
        for (int k = 0; k < 300; k++) list.get(rnd.nextInt(N)).country = "";          // missing country
        for (int k = 0; k < 120; k++) list.add(list.get(rnd.nextInt(N)).copy());      // duplicate rows
        return list;
    }

    static void saveCsv(List<Session> data, String file) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Paths.get(file), StandardCharsets.UTF_8))) {
            w.println("session_id,date,traffic_source,device,user_type,country,viewed_product,added_to_cart,started_checkout,purchased,category,order_value");
            for (Session s : data) {
                w.println(String.join(",", s.id, s.date.toString(), s.source, s.device, s.userType, s.country,
                        String.valueOf(s.viewed), String.valueOf(s.carted), String.valueOf(s.checkout),
                        String.valueOf(s.purchased), "\"" + s.category + "\"", String.format(Locale.US, "%.2f", s.orderValue)));
            }
        }
    }

    // ======================================================================
    //  2. CLEANING
    // ======================================================================
    static List<Session> clean(List<Session> raw) {
        System.out.println("Rows before cleaning : " + raw.size());
        long missing = raw.stream().filter(s -> s.country == null || s.country.isEmpty()).count();
        Set<String> seen = new HashSet<>();
        List<Session> out = new ArrayList<>();
        int dupes = 0;
        for (Session s : raw) {
            if (!seen.add(s.id)) { dupes++; continue; }
            if (s.country == null || s.country.isEmpty()) s.country = "Unknown";
            // logic check: a later funnel stage cannot happen without the earlier one
            if ((s.purchased && !s.checkout) || (s.checkout && !s.carted) || (s.carted && !s.viewed))
                throw new IllegalStateException("Funnel logic error in " + s.id);
            out.add(s);
        }
        System.out.println("Missing country values: " + missing + "  -> filled with 'Unknown'");
        System.out.println("Duplicate sessions    : " + dupes + "  -> removed");
        System.out.println("Rows after cleaning   : " + out.size());
        return out;
    }

    // ======================================================================
    //  3. ANALYSIS HELPERS
    // ======================================================================
    static List<Seg> group(List<Session> data, Function<Session, String> key) {
        Map<String, Seg> map = new LinkedHashMap<>();
        for (Session s : data) {
            Seg g = map.computeIfAbsent(key.apply(s), k -> { Seg x = new Seg(); x.name = k; return x; });
            g.sessions++;
            if (s.purchased) { g.orders++; g.revenue += s.orderValue; }
        }
        List<Seg> list = new ArrayList<>(map.values());
        list.sort((a, b) -> Double.compare(b.rate(), a.rate()));
        return list;
    }

    /** 95% Wilson confidence interval for a proportion. */
    static double[] wilson(int k, int n) {
        if (n == 0) return new double[]{0, 0};
        double z = 1.96, p = (double) k / n;
        double centre = (p + z * z / (2.0 * n)) / (1 + z * z / n);
        double half = z * Math.sqrt(p * (1 - p) / n + z * z / (4.0 * n * n)) / (1 + z * z / n);
        return new double[]{centre - half, centre + half};
    }

    // ---- statistics: normal CDF, gamma function, chi-square p-value ----
    static double normalCdf(double x) {
        double t = 1.0 / (1.0 + 0.2316419 * Math.abs(x));
        double d = 0.3989423 * Math.exp(-x * x / 2.0);
        double p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));
        return x > 0 ? 1 - p : p;
    }
    static double logGamma(double x) {
        double[] c = {76.18009172947146, -86.50532032941677, 24.01409824083091,
                -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5};
        double y = x, tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        double ser = 1.000000000190015;
        for (double cj : c) ser += cj / ++y;
        return -tmp + Math.log(2.5066282746310005 * ser / x);
    }
    /** Upper regularised incomplete gamma Q(a,x) - used for chi-square p-values. */
    static double gammaQ(double a, double x) {
        if (x <= 0) return 1;
        if (x < a + 1) {                         // series for P(a,x)
            double ap = a, sum = 1 / a, del = sum;
            for (int n = 0; n < 500; n++) { ap++; del *= x / ap; sum += del; if (Math.abs(del) < Math.abs(sum) * 1e-12) break; }
            return 1 - sum * Math.exp(-x + a * Math.log(x) - logGamma(a));
        }
        double b = x + 1 - a, c = 1e300, d = 1 / b, h = d;   // continued fraction
        for (int i = 1; i < 500; i++) {
            double an = -i * (i - a);
            b += 2;
            d = an * d + b; if (Math.abs(d) < 1e-300) d = 1e-300;
            c = b + an / c; if (Math.abs(c) < 1e-300) c = 1e-300;
            d = 1 / d;
            double del = d * c; h *= del;
            if (Math.abs(del - 1) < 1e-12) break;
        }
        return Math.exp(-x + a * Math.log(x) - logGamma(a)) * h;
    }
    /** Chi-square test of independence between a segment and 'purchased'. Returns {chi2, p}. */
    static double[] chiSquare(List<Seg> segs) {
        int totalN = 0, totalBuy = 0;
        for (Seg s : segs) { totalN += s.sessions; totalBuy += s.orders; }
        double chi2 = 0;
        for (Seg s : segs) {
            double eBuy = (double) s.sessions * totalBuy / totalN;
            double eNo  = (double) s.sessions * (totalN - totalBuy) / totalN;
            chi2 += Math.pow(s.orders - eBuy, 2) / eBuy + Math.pow((s.sessions - s.orders) - eNo, 2) / eNo;
        }
        int dof = segs.size() - 1;
        return new double[]{chi2, gammaQ(dof / 2.0, chi2 / 2.0)};
    }

    static Seg find(List<Seg> segs, String name) {
        for (Seg s : segs) if (s.name.equals(name)) return s;
        throw new NoSuchElementException(name);
    }
    static String pct(double v)  { return String.format(Locale.US, "%.2f%%", v * 100); }
    static String num(double v)  { return String.format(Locale.US, "%,.0f", v); }
    static String pval(double p) { return p < 0.0001 ? "< 0.0001" : String.format(Locale.US, "%.4f", p); }

    static void printSegments(String title, List<Seg> segs) {
        System.out.println("\n--- " + title + " ---");
        System.out.printf("%-16s %9s %8s %10s %20s %9s%n", "Segment", "Sessions", "Orders", "Conv.rate", "95% CI", "AOV");
        for (Seg s : segs)
            System.out.printf("%-16s %9s %8s %10s %20s %9s%n", s.name, num(s.sessions), num(s.orders), pct(s.rate()),
                    pct(s.lo()) + " - " + pct(s.hi()), String.format(Locale.US, "%.2f", s.aov()));
    }

    // ======================================================================
    //  HTML / SVG REPORT HELPERS (no libraries needed)
    // ======================================================================
    static final String[] PALETTE = {"#4c78a8", "#f58518", "#54a24b", "#e45756", "#72b7b2", "#b279a2", "#9d755d"};

    static String barChart(String title, List<String> labels, List<Double> vals, double baseline, boolean asPct) {
        int w = 560, h = 320, ml = 20, mr = 20, mt = 40, mb = 55;
        double max = baseline;
        for (double v : vals) max = Math.max(max, v);
        max *= 1.18;
        int n = vals.size();
        double bw = (double) (w - ml - mr) / n;
        StringBuilder sb = new StringBuilder();
        sb.append("<svg viewBox='0 0 ").append(w).append(' ').append(h).append("' class='chart'>");
        sb.append("<text x='").append(w / 2).append("' y='22' text-anchor='middle' class='ct'>").append(title).append("</text>");
        for (int i = 0; i < n; i++) {
            double bh = vals.get(i) / max * (h - mt - mb);
            double x = ml + i * bw + bw * 0.14, y = h - mb - bh;
            sb.append(String.format(Locale.US, "<rect x='%.1f' y='%.1f' width='%.1f' height='%.1f' rx='4' fill='%s'/>", x, y, bw * 0.72, bh, PALETTE[i % PALETTE.length]));
            String v = asPct ? pct(vals.get(i)) : num(vals.get(i));
            sb.append(String.format(Locale.US, "<text x='%.1f' y='%.1f' text-anchor='middle' class='cv'>%s</text>", x + bw * 0.36, y - 6, v));
            sb.append(String.format(Locale.US, "<text x='%.1f' y='%d' text-anchor='middle' class='cl'>%s</text>", x + bw * 0.36, h - mb + 18, labels.get(i)));
        }
        if (baseline > 0) {
            double by = h - mb - baseline / max * (h - mt - mb);
            sb.append(String.format(Locale.US, "<line x1='%d' x2='%d' y1='%.1f' y2='%.1f' stroke='#d62728' stroke-dasharray='5,4'/>", ml, w - mr, by, by));
            sb.append(String.format(Locale.US, "<text x='%d' y='%.1f' text-anchor='end' class='cb'>overall %s</text>", w - mr, by - 5, asPct ? pct(baseline) : num(baseline)));
        }
        sb.append("</svg>");
        return sb.toString();
    }

    static String segChart(String title, List<Seg> segs, double overall) {
        List<String> l = new ArrayList<>(); List<Double> v = new ArrayList<>();
        for (Seg s : segs) { l.add(s.name); v.add(s.rate()); }
        return barChart(title, l, v, overall, true);
    }

    static String lineChart(String title, List<String> labels, List<Double> vals) {
        int w = 1120, h = 320, ml = 40, mr = 40, mt = 40, mb = 45;
        double max = 0;
        for (double v : vals) max = Math.max(max, v);
        max *= 1.2;
        int n = vals.size();
        double step = (double) (w - ml - mr) / (n - 1);
        StringBuilder pts = new StringBuilder(), dots = new StringBuilder();
        for (int i = 0; i < n; i++) {
            double x = ml + i * step, y = h - mb - vals.get(i) / max * (h - mt - mb);
            pts.append(String.format(Locale.US, "%.1f,%.1f ", x, y));
            dots.append(String.format(Locale.US, "<circle cx='%.1f' cy='%.1f' r='5' fill='#e45756'/>", x, y));
            dots.append(String.format(Locale.US, "<text x='%.1f' y='%.1f' text-anchor='middle' class='cv'>%s</text>", x, y - 11, pct(vals.get(i))));
            dots.append(String.format(Locale.US, "<text x='%.1f' y='%d' text-anchor='middle' class='cl'>%s</text>", x, h - mb + 20, labels.get(i)));
        }
        return "<svg viewBox='0 0 " + w + " " + h + "' class='chart wide'><text x='" + (w / 2) + "' y='22' text-anchor='middle' class='ct'>" + title + "</text>"
                + "<polyline points='" + pts + "' fill='none' stroke='#e45756' stroke-width='3'/>" + dots + "</svg>";
    }

    static String funnelChart(String[] names, int[] counts, int total) {
        int w = 1120, rowH = 52, h = rowH * names.length + 50, ml = 170;
        StringBuilder sb = new StringBuilder("<svg viewBox='0 0 " + w + " " + h + "' class='chart wide'>");
        sb.append("<text x='").append(w / 2).append("' y='22' text-anchor='middle' class='ct'>Conversion funnel</text>");
        for (int i = 0; i < names.length; i++) {
            double bw = (double) counts[i] / total * (w - ml - 330);
            int y = 40 + i * rowH;
            sb.append(String.format(Locale.US, "<text x='%d' y='%d' text-anchor='end' class='cl'>%s</text>", ml - 10, y + 26, names[i]));
            sb.append(String.format(Locale.US, "<rect x='%d' y='%d' width='%.1f' height='34' rx='4' fill='%s'/>", ml, y + 4, bw, PALETTE[i % PALETTE.length]));
            String extra = i == 0 ? "" : String.format(Locale.US, "  (%.0f%% of previous step)", 100.0 * counts[i] / counts[i - 1]);
            sb.append(String.format(Locale.US, "<text x='%.1f' y='%d' class='cv2'>%s | %s of sessions%s</text>", ml + bw + 10, y + 26, num(counts[i]), pct((double) counts[i] / total), extra));
        }
        return sb.append("</svg>").toString();
    }

    static String heatmap(List<Session> data) {
        StringBuilder sb = new StringBuilder("<table class='heat'><tr><th>Traffic source</th>");
        for (String d : DEVICES) sb.append("<th>").append(d).append("</th>");
        sb.append("</tr>");
        double max = 0;
        double[][] r = new double[SOURCES.length][DEVICES.length];
        for (int i = 0; i < SOURCES.length; i++)
            for (int j = 0; j < DEVICES.length; j++) {
                int n = 0, k = 0;
                for (Session s : data) if (s.source.equals(SOURCES[i]) && s.device.equals(DEVICES[j])) { n++; if (s.purchased) k++; }
                r[i][j] = n == 0 ? 0 : (double) k / n; max = Math.max(max, r[i][j]);
            }
        for (int i = 0; i < SOURCES.length; i++) {
            sb.append("<tr><td><b>").append(SOURCES[i]).append("</b></td>");
            for (int j = 0; j < DEVICES.length; j++) {
                int a = (int) (255 - 175 * (r[i][j] / max));
                sb.append(String.format(Locale.US, "<td style='background:rgb(%d,%d,255);color:%s'>%s</td>", a, Math.min(255, a + 20), r[i][j] / max > 0.6 ? "#fff" : "#123", pct(r[i][j])));
            }
            sb.append("</tr>");
        }
        return sb.append("</table>").toString();
    }

    static String kpiCard(String label, String value) {
        return "<div class='kpi'><div class='kv'>" + value + "</div><div class='kl'>" + label + "</div></div>";
    }

    // ======================================================================
    //  MAIN
    // ======================================================================
    public static void main(String[] args) throws IOException {
        System.out.println("=== E-COMMERCE CONVERSION RATE ANALYSIS ===\n");

        // 1. generate + save raw data
        List<Session> raw = generate();
        saveCsv(raw, "ecommerce_sessions.csv");
        System.out.println("Saved ecommerce_sessions.csv (" + raw.size() + " rows)\n");

        // 2. clean
        List<Session> data = clean(raw);

        // 3. KPIs
        int sessions = data.size(), orders = 0;
        double revenue = 0;
        for (Session s : data) if (s.purchased) { orders++; revenue += s.orderValue; }
        double overall = (double) orders / sessions, aov = revenue / orders, rps = revenue / sessions;

        System.out.println("\n--- KPI OVERVIEW ---");
        System.out.println("Sessions                : " + num(sessions));
        System.out.println("Orders                  : " + num(orders));
        System.out.println("Conversion rate         : " + pct(overall));
        System.out.println("Revenue                 : " + num(revenue));
        System.out.println("Average order value     : " + String.format(Locale.US, "%.2f", aov));
        System.out.println("Revenue per session     : " + String.format(Locale.US, "%.2f", rps));

        // 4. segments
        List<Seg> bySource = group(data, s -> s.source);
        List<Seg> byDevice = group(data, s -> s.device);
        List<Seg> byUser = group(data, s -> s.userType);
        List<Seg> byCountry = group(data, s -> s.country);
        printSegments("Conversion by traffic source", bySource);
        printSegments("Conversion by device", byDevice);
        printSegments("Conversion by user type", byUser);
        printSegments("Conversion by country", byCountry);

        // category revenue
        List<Seg> byCategory = new ArrayList<>();
        for (Seg g : group(data, s -> s.category)) if (!g.name.equals("No purchase")) byCategory.add(g);
        byCategory.sort((a, b) -> Double.compare(b.revenue, a.revenue));

        // 5. monthly + weekday
        List<Seg> monthly = new ArrayList<>(group(data, s -> String.format("%02d", s.date.getMonthValue())));
        monthly.sort(Comparator.comparing(g -> g.name));
        List<String> mLabels = new ArrayList<>(); List<Double> mVals = new ArrayList<>();
        System.out.println("\n--- MONTHLY TREND ---");
        for (Seg g : monthly) {
            String mn = Month.of(Integer.parseInt(g.name)).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            mLabels.add(mn); mVals.add(g.rate());
            System.out.printf("%-4s sessions=%7s  orders=%5s  conv=%s%n", mn, num(g.sessions), num(g.orders), pct(g.rate()));
        }
        int bestM = 0, worstM = 0;
        for (int i = 0; i < mVals.size(); i++) {
            if (mVals.get(i) > mVals.get(bestM)) bestM = i;
            if (mVals.get(i) < mVals.get(worstM)) worstM = i;
        }
        List<Seg> weekday = new ArrayList<>(group(data, s -> String.valueOf(s.date.getDayOfWeek().getValue())));
        weekday.sort(Comparator.comparing(g -> g.name));
        List<String> wLabels = new ArrayList<>(); List<Double> wVals = new ArrayList<>();
        for (Seg g : weekday) {
            wLabels.add(DayOfWeek.of(Integer.parseInt(g.name)).getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            wVals.add(g.rate());
        }

        // 6. funnel
        int v = 0, c = 0, k = 0;
        for (Session s : data) { if (s.viewed) v++; if (s.carted) c++; if (s.checkout) k++; }
        String[] fNames = {"All sessions", "Product view", "Add to cart", "Checkout started", "Purchased"};
        int[] fCounts = {sessions, v, c, k, orders};
        double cartAband = 1 - (double) orders / c, chkAband = 1 - (double) orders / k;
        System.out.println("\n--- FUNNEL ---");
        for (int i = 0; i < fNames.length; i++)
            System.out.printf("%-18s %8s  (%s of sessions)%n", fNames[i], num(fCounts[i]), pct((double) fCounts[i] / sessions));
        System.out.println("Cart abandonment rate     : " + pct(cartAband));
        System.out.println("Checkout abandonment rate : " + pct(chkAband));
        String worstStage = ""; double worstLoss = -1;
        for (int i = 1; i < fNames.length; i++) {
            double loss = 1 - (double) fCounts[i] / fCounts[i - 1];
            if (loss > worstLoss) { worstLoss = loss; worstStage = fNames[i]; }
        }

        // 7. statistical tests
        System.out.println("\n--- STATISTICAL TESTS (chi-square: is conversion related to the segment?) ---");
        String[] testNames = {"Traffic source", "Device", "User type", "Country"};
        List<List<Seg>> tests = Arrays.asList(bySource, byDevice, byUser, byCountry);
        StringBuilder testRows = new StringBuilder();
        for (int i = 0; i < tests.size(); i++) {
            double[] r = chiSquare(tests.get(i));
            String verdict = r[1] < 0.05 ? "Significant" : "Not significant";
            System.out.printf("%-15s chi2=%8.1f  p=%-9s -> %s%n", testNames[i], r[0], pval(r[1]), verdict);
            testRows.append("<tr><td>").append(testNames[i]).append("</td><td>").append(String.format(Locale.US, "%.1f", r[0]))
                    .append("</td><td>").append(pval(r[1])).append("</td><td>").append(verdict).append("</td></tr>");
        }
        Seg desk = find(byDevice, "Desktop"), mob = find(byDevice, "Mobile");
        double pPool = (double) (desk.orders + mob.orders) / (desk.sessions + mob.sessions);
        double z = (desk.rate() - mob.rate()) / Math.sqrt(pPool * (1 - pPool) * (1.0 / desk.sessions + 1.0 / mob.sessions));
        double zp = 2 * (1 - normalCdf(Math.abs(z)));
        System.out.printf("%nDesktop %s vs Mobile %s | z = %.2f | p %s%n", pct(desk.rate()), pct(mob.rate()), z, zp < 0.0001 ? "< 0.0001" : "= " + pval(zp));
        System.out.printf("Desktop converts %.2fx better than Mobile%n", desk.rate() / mob.rate());

        // 8. opportunity sizing
        System.out.println("\n--- OPPORTUNITY SIZING (if a weak segment reached the overall average) ---");
        List<Double> oppRev = new ArrayList<>(); List<String> oppLabel = new ArrayList<>();
        Object[][] dims = {{"Source", bySource}, {"Device", byDevice}, {"User type", byUser}};
        List<double[]> tmp = new ArrayList<>(); List<String> tmpName = new ArrayList<>();
        for (Object[] d : dims) {
            @SuppressWarnings("unchecked") List<Seg> segs = (List<Seg>) d[1];
            for (Seg s : segs) {
                double gap = overall - s.rate();
                if (gap > 0) { tmp.add(new double[]{gap * s.sessions, gap * s.sessions * aov, s.rate(), s.sessions}); tmpName.add(d[0] + ": " + s.name); }
            }
        }
        Integer[] order = new Integer[tmp.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(tmp.get(b)[1], tmp.get(a)[1]));
        StringBuilder oppRows = new StringBuilder();
        for (int i : order) {
            double[] t = tmp.get(i);
            System.out.printf("%-26s conv=%-7s extra orders=%6s  extra revenue=%s%n", tmpName.get(i), pct(t[2]), num(t[0]), num(t[1]));
            oppRows.append("<tr><td>").append(tmpName.get(i)).append("</td><td>").append(num(t[3])).append("</td><td>").append(pct(t[2]))
                    .append("</td><td>").append(num(t[0])).append("</td><td>").append(num(t[1])).append("</td></tr>");
            oppLabel.add(tmpName.get(i)); oppRev.add(t[1]);
        }
        int top = order[0];

        // 9. insights
        Seg bestSrc = bySource.get(0), worstSrc = bySource.get(bySource.size() - 1);
        List<String> insights = new ArrayList<>();
        insights.add(String.format(Locale.US, "Overall conversion rate is %s from %s sessions, with an average order value of %.2f.", pct(overall), num(sessions), aov));
        insights.add(String.format(Locale.US, "Best channel: %s (%s). Weakest: %s (%s).", bestSrc.name, pct(bestSrc.rate()), worstSrc.name, pct(worstSrc.rate())));
        insights.add(String.format(Locale.US, "Desktop converts at %s vs Mobile at %s (%.1fx gap), although mobile brings %s of the traffic.", pct(desk.rate()), pct(mob.rate()), desk.rate() / mob.rate(), pct((double) mob.sessions / sessions)));
        insights.add(String.format(Locale.US, "Returning users convert %.1fx better than new users.", find(byUser, "Returning").rate() / find(byUser, "New").rate()));
        insights.add(String.format(Locale.US, "Peak month: %s (%s). Lowest month: %s (%s).", mLabels.get(bestM), pct(mVals.get(bestM)), mLabels.get(worstM), pct(mVals.get(worstM))));
        insights.add(String.format(Locale.US, "Biggest funnel leak: %s step (%.0f%% of visitors lost). Checkout abandonment is %s.", worstStage, worstLoss * 100, pct(chkAband)));
        insights.add(String.format(Locale.US, "Largest opportunity: lifting '%s' to the average would add about %s orders (~%s revenue).", tmpName.get(top), num(tmp.get(top)[0]), num(tmp.get(top)[1])));
        System.out.println("\n--- KEY INSIGHTS ---");
        for (int i = 0; i < insights.size(); i++) System.out.println((i + 1) + ". " + insights.get(i));

        // 10. HTML report
        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html><html><head><meta charset='utf-8'><title>E-Commerce Conversion Rate Analysis</title><style>")
         .append("body{font-family:Segoe UI,Arial,sans-serif;background:#f4f6fb;color:#1c2540;margin:0;padding:30px 5vw}")
         .append("h1{margin:0 0 4px}h2{margin:34px 0 12px;border-left:5px solid #4c78a8;padding-left:10px}.sub{color:#667}")
         .append(".kpis{display:flex;gap:14px;flex-wrap:wrap;margin-top:18px}.kpi{background:#fff;border-radius:12px;padding:16px 22px;box-shadow:0 1px 6px #0001;min-width:150px}")
         .append(".kv{font-size:26px;font-weight:700;color:#4c78a8}.kl{color:#667;font-size:13px;margin-top:4px}")
         .append(".row{display:flex;gap:16px;flex-wrap:wrap}.card{background:#fff;border-radius:12px;padding:12px;box-shadow:0 1px 6px #0001;flex:1;min-width:300px}")
         .append(".chart{width:100%;height:auto}.ct{font-weight:700;font-size:15px;fill:#1c2540}.cv{font-size:12px;font-weight:600;fill:#1c2540}.cv2{font-size:13px;fill:#1c2540}")
         .append(".cl{font-size:12px;fill:#445}.cb{font-size:11px;fill:#d62728}")
         .append("table{border-collapse:collapse;background:#fff;border-radius:10px;overflow:hidden;box-shadow:0 1px 6px #0001}th,td{padding:9px 16px;text-align:left;border-bottom:1px solid #e6e9f2}th{background:#4c78a8;color:#fff}")
         .append(".heat td{text-align:center;font-weight:600}li{margin:7px 0}</style></head><body>")
         .append("<h1>E-Commerce Conversion Rate Analysis</h1><div class='sub'>Codtech Internship - Task 3 | Simulated 2025 data, ")
         .append(num(sessions)).append(" sessions</div>")
         .append("<div class='kpis'>").append(kpiCard("Sessions", num(sessions))).append(kpiCard("Orders", num(orders)))
         .append(kpiCard("Conversion rate", pct(overall))).append(kpiCard("Revenue", num(revenue)))
         .append(kpiCard("Avg order value", String.format(Locale.US, "%.2f", aov))).append(kpiCard("Revenue / session", String.format(Locale.US, "%.2f", rps)))
         .append("</div>")
         .append("<h2>Key insights</h2><div class='card'><ol>");
        for (String s : insights) h.append("<li>").append(s).append("</li>");
        h.append("</ol></div>");

        h.append("<h2>Conversion rate by segment</h2><div class='row'>")
         .append("<div class='card'>").append(segChart("By traffic source", bySource, overall)).append("</div>")
         .append("<div class='card'>").append(segChart("By device", byDevice, overall)).append("</div></div>")
         .append("<div class='row' style='margin-top:16px'>")
         .append("<div class='card'>").append(segChart("By user type", byUser, overall)).append("</div>")
         .append("<div class='card'>").append(segChart("By country", byCountry, overall)).append("</div></div>");

        List<String> cl = new ArrayList<>(); List<Double> cv = new ArrayList<>();
        for (Seg s : byCategory) { cl.add(s.name); cv.add(s.revenue); }
        h.append("<h2>Source x Device heatmap</h2>").append(heatmap(data))
         .append("<h2>Trend and seasonality</h2><div class='card'>").append(lineChart("Monthly conversion rate", mLabels, mVals)).append("</div>")
         .append("<div class='row' style='margin-top:16px'><div class='card'>").append(barChart("Conversion by day of week", wLabels, wVals, overall, true)).append("</div>")
         .append("<div class='card'>").append(barChart("Revenue by product category", cl, cv, 0, false)).append("</div></div>")
         .append("<h2>Funnel analysis</h2><div class='card'>").append(funnelChart(fNames, fCounts, sessions)).append("</div>")
         .append("<p>Cart abandonment: <b>").append(pct(cartAband)).append("</b> &nbsp;|&nbsp; Checkout abandonment: <b>").append(pct(chkAband)).append("</b></p>")
         .append("<h2>Statistical tests</h2><table><tr><th>Segment</th><th>Chi-square</th><th>p-value</th><th>Result</th></tr>").append(testRows).append("</table>")
         .append(String.format(Locale.US, "<p>Desktop vs Mobile two-proportion z-test: z = %.2f, p %s. Desktop converts <b>%.2fx</b> better.</p>", z, zp < 0.0001 ? "&lt; 0.0001" : "= " + pval(zp), desk.rate() / mob.rate()))
         .append("<h2>Opportunity sizing</h2><div class='card'>").append(barChart("Extra revenue if weak segment reached the average", oppLabel, oppRev, 0, false)).append("</div>")
         .append("<br><table><tr><th>Segment</th><th>Sessions</th><th>Conv. rate</th><th>Extra orders</th><th>Extra revenue</th></tr>").append(oppRows).append("</table>")
         .append("<h2>Recommendations</h2><div class='card'><ul>")
         .append("<li><b>Fix mobile checkout:</b> guest checkout, UPI / wallet payments, fewer form fields, bigger buttons.</li>")
         .append("<li><b>Social media:</b> use dedicated landing pages, retargeting and shoppable posts; judge it on assisted conversions too.</li>")
         .append("<li><b>Email and returning users convert best:</b> grow the email list, run cart-abandonment and win-back mails, add loyalty rewards.</li>")
         .append("<li><b>New users:</b> add trust signals (reviews, return policy, secure-payment badges) and a first-order offer.</li>")
         .append("<li><b>Seasonality:</b> plan stock, ad budget and server capacity before Nov-Dec; start campaigns in October.</li>")
         .append("<li><b>Abandonment:</b> show shipping and tax early, add exit-intent offers, send reminder emails within 1 hour.</li>")
         .append("</ul></div><p class='sub'>Limitation: data is simulated, so the numbers illustrate the method, not a real business.</p></body></html>");
        Files.write(Paths.get("report.html"), h.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("\nSaved report.html - open it in your browser to see all charts.");
    }
}