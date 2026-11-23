package com.trae.floatsolver;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 把模型偶尔吐出的 LaTeX 变成手机浮层里能直接读的纯文本 */
public final class Tex {

    private static final String SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹";
    private static final Pattern FRAC = Pattern.compile("\\\\d?frac\\{([^{}]*)\\}\\{([^{}]*)\\}");
    private static final Pattern SQRT = Pattern.compile("\\\\sqrt\\{([^{}]*)\\}");
    private static final Pattern SUPB = Pattern.compile("\\^\\{(-?\\d+)\\}");
    private static final Pattern SUPS = Pattern.compile("\\^(-?\\d)");
    private static final Pattern SUBB = Pattern.compile("_\\{([^{}]*)\\}");
    private static final Pattern SUBS = Pattern.compile("_([A-Za-z0-9])");
    private static final Pattern CMD = Pattern.compile("\\\\[A-Za-z]+");

    private Tex() {}

    public static String plain(String s) {
        if (s == null) return "";
        s = s.replace("$$", "").replace("$", "").replace("**", "").replace("`", "");
        s = FRAC.matcher(s).replaceAll("$1/$2");
        s = SQRT.matcher(s).replaceAll("√$1");
        s = replace(s, SUPB, true);
        s = replace(s, SUPS, true);
        s = s.replaceAll("\\\\lim_\\{([^{}]*)\\}", "lim($1)");
        s = SUBB.matcher(s).replaceAll("_($1)");
        s = SUBS.matcher(s).replaceAll("_$1");
        Matcher m = CMD.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(word(m.group())));
        }
        m.appendTail(sb);
        s = sb.toString();
        s = s.replace("\\{", "{").replace("\\}", "}");
        s = s.replaceAll("[ \\t]+\\n", "\n");
        return s;
    }

    private static String replace(String s, Pattern p, boolean toSup) {
        Matcher m = p.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(sup(m.group(1))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String word(String cmd) {
        switch (cmd) {
            case "\\cdot": return "·";
            case "\\times": return "×";
            case "\\div": return "÷";
            case "\\to": return "→";
            case "\\rightarrow": return "→";
            case "\\Rightarrow": return "⇒";
            case "\\Leftrightarrow": return "⇔";
            case "\\quad": return "  ";
            case "\\qquad": return "   ";
            case "\\,": return " ";
            case "\\;": return " ";
            case "\\!": return "";
            case "\\lim": return "lim";
            case "\\ln": return "ln";
            case "\\log": return "log";
            case "\\sin": return "sin";
            case "\\cos": return "cos";
            case "\\tan": return "tan";
            case "\\pi": return "π";
            case "\\alpha": return "α";
            case "\\beta": return "β";
            case "\\theta": return "θ";
            case "\\in": return "∈";
            case "\\ge": return "≥";
            case "\\geq": return "≥";
            case "\\le": return "≤";
            case "\\leq": return "≤";
            case "\\neq": return "≠";
            case "\\approx": return "≈";
            case "\\pm": return "±";
            case "\\infty": return "∞";
            case "\\left": return "";
            case "\\right": return "";
            default: return cmd.substring(1);
        }
    }

    private static String sup(String num) {
        StringBuilder sb = new StringBuilder();
        for (char c : num.toCharArray()) {
            if (c >= '0' && c <= '9') sb.append(SUP.charAt(c - '0'));
            else if (c == '-') sb.append('⁻');
            else sb.append(c);
        }
        return sb.toString();
    }
}
