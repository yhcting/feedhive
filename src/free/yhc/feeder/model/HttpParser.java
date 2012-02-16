package free.yhc.feeder.model;

import java.util.regex.Pattern;


// Full parser is not implemented yet
// Just for simple use case
class HttpParser {
    private static String[] tags = new String[] {
        // Structure
        "html",
        "head",
        "body",
        "div",
        "span",

        // Meta information
        "DOCTYPE", // NOTE : 'DOCTYPE'? or '!DOCTYPE'
        "title",
        "link",
        "meta",
        "style",

        // Text
        "p",
        "h1", "h2", "h3", "h4", "h5", "h6",
        "strong",
        "em",
        "abbr",
        "acronym",
        "address",
        "bdo",
        "blockquote",
        "cite",
        "q",
        "code",
        "ins",
        "del",
        "dfn",
        "kbd",
        "pre",
        "samp",
        "var",
        "br",

        // Links
        "a",
        "base",

        // Images and Objects
        "img",
        "area",
        "map",
        "object",
        "param",

        // Lists
        "ul",
        "ol",
        "li",
        "dl",
        "dt",
        "dd",

        // Tables
        "table",
        "tr",
        "td",
        "th",
        "tbody",
        "thread",
        "tfoot",
        "col",
        "colgroup",
        "caption",

        // Forms
        "form",
        "input",
        "textarea",
        "select",
        "option",
        "optgroup",
        "button",
        "label",
        "fieldset",
        "legend",

        // Scripting
        "script",
        "noscript",

        // Presentational
        "b",
        "i",
        "tt",
        "sub",
        "sup",
        "big",
        "small",
        "hr"
    };

    private static Pattern tagPattern;
    static {
        StringBuilder sbuilder = new StringBuilder("</?(");
        for (String s : tags)
            sbuilder.append(s).append("|");

        sbuilder.append("\\!DOCTYPE)(\\s*|\\s+[^>]+)/?>");
        tagPattern = Pattern.compile(sbuilder.toString(), Pattern.CASE_INSENSITIVE);
    }

    static boolean
    guessIsHttpText(String text) {
        // And '&nbsp;' is most popular used in text
        return text.contains("&nbsp;")
                || tagPattern.matcher(text).find();
    }

    // Remove all TAG-LIKE text.
    static String
    removeTags(String text) {
        return tagPattern.matcher(text).replaceAll("");
    }
}
