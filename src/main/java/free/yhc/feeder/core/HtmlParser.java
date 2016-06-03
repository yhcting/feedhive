/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015, 2016
 * Younghyung Cho. <yhcting77@gmail.com>
 * All rights reserved.
 *
 * This file is part of FeedHive
 *
 * This program is licensed under the FreeBSD license
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation
 * are those of the authors and should not be interpreted as representing
 * official policies, either expressed or implied, of the FreeBSD Project.
 *****************************************************************************/

package free.yhc.feeder.core;

import java.util.regex.Pattern;

import android.text.Html;

import free.yhc.baselib.Logger;

// Private implementation is NOT used.
// Now this is just wrapper of predefined Html class of Android.
@SuppressWarnings("unused")
class HtmlParser {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(HtmlParser.class, Logger.LOGLV_DEFAULT);

    private static final String[] sTagsRegex = new String[] {
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
        for (String s : sTagsRegex)
            sbuilder.append(s).append("|");

        sbuilder.append("\\!DOCTYPE)(\\s*|\\s+[^>]+)/?>");
        tagPattern = Pattern.compile(sbuilder.toString(), Pattern.CASE_INSENSITIVE);
    }

    /**
     * Guess that give text is html string or not.
     */
    static boolean
    guessIsHttpText(String text) {
        // And '&nbsp;' is most popular used in text
        return tagPattern.matcher(text).find() || text.contains("&nbsp;");
    }

    /**
     * Removes TAG-LIKE text from string.
     */
    static String
    removeTags(String text) {
        return tagPattern.matcher(text).replaceAll("");
    }

    /**
     * Convert html string to normal text string.
     */
    static String
    fromHtml(String source) {
        return Html.fromHtml(source).toString();
    }
}
