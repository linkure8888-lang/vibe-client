package com.vibe.launcher.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft-style version comparison and Fabric dependency range matching.
 *
 * Why this is needed: the launcher used to decide "is this mod already
 * installed?" by looking at the file name alone. A `fabric-api` built for
 * 1.21.1 therefore satisfied the check while the user had selected 1.21.11,
 * so the launcher skipped the download and the game died on startup with
 * "Incompatible mods found". Deciding correctly means reading the version range
 * the mod declares and testing it against the version actually being launched.
 *
 * Version comparison is component-wise and numeric: 1.21.11 -> [1,21,11], and
 * 1.21.2 -> [1,21,2], so 1.21.11 is GREATER than 1.21.2. That matters: a range
 * of `>=1.21 <1.21.2` must reject 1.21.11, and string comparison would get
 * this backwards in the other direction.
 *
 * Supported range syntax (the subset Fabric mods actually use):
 *   *             any version
 *   1.21.1        exactly
 *   1.21.x / *    wildcard, same as [1.21, 1.22)
 *   ~1.21.1       compatible, same as [1.21.1, 1.22)
 *   >=1.21 <1.21.2  space-separated AND of comparisons
 *   =1.21.1, >1.21, <=1.22, <1.21
 */
public final class VersionRange {

    private VersionRange() {
    }

    /** Parses "1.21.11" into [1, 21, 11]. Non-numeric parts become 0. */
    public static int[] parse(String version) {
        if (version == null) return new int[]{0};
        // Drop build metadata and pre-release suffixes ("1.21.1-rc.1+abc").
        String cleaned = version.trim();
        int dash = cleaned.indexOf('-');
        if (dash > 0) cleaned = cleaned.substring(0, dash);
        int plus = cleaned.indexOf('+');
        if (plus > 0) cleaned = cleaned.substring(0, plus);

        String[] parts = cleaned.split("\\.");
        List<Integer> numbers = new ArrayList<>(parts.length);
        for (String part : parts) {
            numbers.add(toInt(part));
        }
        int[] result = new int[numbers.size()];
        for (int i = 0; i < numbers.size(); i++) result[i] = numbers.get(i);
        return result;
    }

    private static int toInt(String value) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) digits.append(c);
            else break;
        }
        if (digits.length() == 0) return 0;
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Component-wise comparison. Missing components count as 0. */
    public static int compare(String left, String right) {
        int[] a = parse(left);
        int[] b = parse(right);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    /**
     * True when [version] satisfies the Fabric [range].
     *
     * An empty or null range means "no constraint", which is how a mod with no
     * declared minecraft dependency is treated: accept it rather than refusing
     * to install something that might be fine.
     */
    public static boolean matches(String version, String range) {
        if (range == null || range.isBlank()) return true;
        if (version == null || version.isBlank()) return false;

        String trimmed = range.trim();
        if ("*".equals(trimmed)) return true;

        // Space-separated terms are combined with AND, which is what Fabric's
        // predicate syntax means: ">=1.21 <1.21.2".
        String[] terms = trimmed.split("\\s+");
        for (String term : terms) {
            if (!matchesTerm(version, term.trim())) return false;
        }
        return true;
    }

    /** One comparison operator plus its operand. */
    private static boolean matchesTerm(String version, String term) {
        if (term.isEmpty() || "*".equals(term)) return true;

        if (term.startsWith(">=")) return compare(version, term.substring(2)) >= 0;
        if (term.startsWith("<=")) return compare(version, term.substring(2)) <= 0;
        if (term.startsWith(">")) return compare(version, term.substring(1)) > 0;
        if (term.startsWith("<")) return compare(version, term.substring(1)) < 0;
        if (term.startsWith("=")) return compare(version, term.substring(1)) == 0;
        if (term.startsWith("~")) return matchesTilde(version, term.substring(1));
        if (term.startsWith("^")) return matchesCaret(version, term.substring(1));
        if (term.indexOf('x') >= 0 || term.indexOf('X') >= 0 || term.indexOf('*') >= 0) {
            return matchesWildcard(version, term);
        }
        return compare(version, term) == 0;
    }

    /**
     * `~1.21.1` means "compatible with 1.21.1", i.e. [1.21.1, 1.22).
     *
     * The last component is dropped and the new last one incremented, so
     * ~1.21.1 -> upper bound 1.22 and ~1.21 -> upper bound 1.3.
     */
    private static boolean matchesTilde(String version, String operand) {
        if (compare(version, operand) < 0) return false;
        int[] parts = parse(operand);
        if (parts.length <= 1) return true;

        int[] upper = new int[parts.length];
        System.arraycopy(parts, 0, upper, 0, parts.length - 1);
        upper[parts.length - 2] = upper[parts.length - 2] + 1;
        // Everything after the incremented component becomes 0.
        for (int i = parts.length - 1; i < upper.length; i++) upper[i] = 0;

        return compare(version, join(upper)) < 0;
    }

    /** `^1.2.3` means [1.2.3, 2.0.0) for non-zero, the common npm-style rule. */
    private static boolean matchesCaret(String version, String operand) {
        if (compare(version, operand) < 0) return false;
        int[] parts = parse(operand);
        int[] upper = new int[parts.length];
        System.arraycopy(parts, 0, upper, 0, parts.length);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] > 0) {
                upper[i] = upper[i] + 1;
                for (int j = i + 1; j < upper.length; j++) upper[j] = 0;
                return compare(version, join(upper)) < 0;
            }
        }
        return true;
    }

    /** `1.21.x` matches any version whose prefix agrees up to the wildcard. */
    private static boolean matchesWildcard(String version, String operand) {
        String[] pattern = operand.toLowerCase().split("\\.");
        int[] actual = parse(version);
        for (int i = 0; i < pattern.length; i++) {
            String part = pattern[i];
            if (part.equals("x") || part.equals("*")) return true;
            int expected = toInt(part);
            int got = i < actual.length ? actual[i] : 0;
            if (expected != got) return false;
        }
        return true;
    }

    private static String join(int[] parts) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) builder.append('.');
            builder.append(parts[i]);
        }
        return builder.toString();
    }
}
