/*****************************************************************************
 *    Copyright (C) 2012 Younghyung Cho. <yhcting77@gmail.com>
 *
 *    This file is part of Feeder.
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as
 *    published by the Free Software Foundation either version 3 of the
 *    License, or (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License
 *    (<http://www.gnu.org/licenses/lgpl.html>) for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

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
