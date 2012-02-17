package free.yhc.feeder.model;

import java.util.regex.Pattern;

import android.text.Html;

// Private implementation is NOT used.
// Now this is just wrapper of predefined Html class of Android.
class HtmlParser {
    private static String[] tagsRegex = new String[] {
        "\\!\\-\\-",
        "\\!DOCTYPE",
        "a",
        "abbr",
        "acronym",
        "address",
        "applet",
        "area",
        "b",
        "base",
        "basefont",
        "bdo",
        "big",
        "blockquote",
        "body",
        "br",
        "button",
        "caption",
        "center",
        "cite",
        "code",
        "col",
        "colgroup",
        "dd",
        "del",
        "dfn",
        "dir",
        "div",
        "dl",
        "dt",
        "em",
        "fieldset",
        "font",
        "form",
        "frame",
        "frameset",
        "head",
        "h1", "h2", "h3", "h4", "h5", "h6",
        "hr",
        "html",
        "i",
        "iframe",
        "img",
        "input",
        "ins",
        "kbd",
        "label",
        "legend",
        "li",
        "link",
        "map",
        "menu",
        "meta",
        "noframes",
        "noscript",
        "object",
        "ol",
        "optgroup",
        "option",
        "p",
        "param",
        "pre",
        "q",
        "s",
        "samp",
        "script",
        "select",
        "small",
        "span",
        "strike",
        "strong",
        "style",
        "sub",
        "sup",
        "table",
        "tbody",
        "td",
        "textarea",
        "tfoot",
        "th",
        "thead",
        "title",
        "tr",
        "tt",
        "u",
        "ul",
        "var",
    };

    private static Pattern tagPattern;
    static {
        StringBuilder sbuilder = new StringBuilder("</?(");
        for (String s : tagsRegex)
            sbuilder.append(s).append("|");

        sbuilder.append("\\!DOCTYPE)(\\s*|\\s+[^>]+)/?>");
        tagPattern = Pattern.compile(sbuilder.toString(), Pattern.CASE_INSENSITIVE);
    }

    static boolean
    guessIsHttpText(String text) {
        // And '&nbsp;' is most popular used in text
        return tagPattern.matcher(text).find() || text.contains("&nbsp;");
    }

    // Remove all TAG-LIKE text.
    static String
    removeTags(String text) {
        return tagPattern.matcher(text).replaceAll("");
    }

    static String
    fromHtml(String source) {
        return Html.fromHtml(source).toString();
    }
}
