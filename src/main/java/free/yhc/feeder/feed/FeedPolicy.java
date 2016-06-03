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

package free.yhc.feeder.feed;

import free.yhc.baselib.Logger;
import free.yhc.feeder.core.Util;

//
// Policy of decisions that is made based on Feed information.
//
public class FeedPolicy {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(FeedPolicy.class, Logger.LOGLV_DEFAULT);
    /**
     * Check that is this valid item?
     * (Result of parsing has enough information required by this application?)
     */
    public static boolean
    verifyConstraints(Feed.Item.ParD item) {
        // 'title' is mandatory!!!
        if (!Util.isValidValue(item.title))
            return false;

        // Item should have one of link or enclosure url.
        return Util.isValidValue(item.link)
               || Util.isValidValue(item.enclosureUrl);
    }

    /**
     * Check that is this valid channel?
     * (Result of parsing has enough information required by this application?)
     */
    @SuppressWarnings("unused")
    public static boolean
    verifyConstraints(Feed.Channel.ParD ch) {
        return Util.isValidValue(ch.title);
    }

    /**
     * Guessing default action type from Feed data.
     * @return Feed.Channel.FActxxxx
     */
    public static long
    decideActionType(long action, Feed.Channel.ParD cParD, Feed.Item.ParD iParD) {
        long actFlag;

        if (null == iParD) {
            if (Feed.FINVALID == action)
                return Feed.FINVALID; // do nothing if there is no items at first insertion.
        }

        switch (cParD.type) {
        case NORMAL:
            //noinspection PointlessBitwiseExpression
            actFlag = Feed.Channel.FACT_TYPE_DYNAMIC | Feed.Channel.FACT_PROG_IN;
            break;
        case EMBEDDED_MEDIA: // special for youtube!
            actFlag = Feed.Channel.FACT_TYPE_EMBEDDED_MEDIA | Feed.Channel.FACT_PROG_EX;
            break;
        default:
            P.bug(false);
            actFlag = Feed.Channel.FACT_DEFAULT;
        }

        // NOTE
        // FACT_PROG_IN/EX can be configurable by user
        // So, this flag should not be changed except for action is invalid value.
        if (Feed.FINVALID == action)
            // In case of newly inserted channel (first decision), FACT_PROG_XX should be set as recommended one.
            return Util.bitSet(action, actFlag, Feed.Channel.MACT_TYPE | Feed.Channel.MACT_PROG);
        else
            // If this is NOT first decision, user may change FACT_PROG_XX setting (UX scenario support this.)
            // So, in this case, FACT_PROG_XX SHOULD NOT be changed.
            return Util.bitSet(action, actFlag, Feed.Channel.MACT_TYPE);
    }

    public static String
    getDynamicActionTargetUrl(long action, String link, String enclosure) {
        if (Feed.Channel.FACT_TYPE_DYNAMIC != Feed.Channel.getActType(action))
            return null; // Not applicable for other case!

        if (Util.isValidValue(enclosure)
            && Util.isAudioOrVideo(enclosure))
            return enclosure;
        else {
            if (Util.isValidValue(link))
                return link;
            else if (Util.isValidValue(enclosure))
                return enclosure;
            else
                P.bug(false); // there is no valid link or enclosure value...
        }
        return null;
    }
}
