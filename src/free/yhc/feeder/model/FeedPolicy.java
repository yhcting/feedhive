/******************************************************************************
 * Copyright (C) 2012, 2013, 2014
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

package free.yhc.feeder.model;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.isValidValue;

//
// Policy of decisions that is made based on Feed information.
//
public class FeedPolicy {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(FeedPolicy.class);
    /**
     * Check that is this valid item?
     * (Result of parsing has enough information required by this application?)
     * @param item
     * @return
     */
    public static boolean
    verifyConstraints(Feed.Item.ParD item) {
        // 'title' is mandatory!!!
        if (!isValidValue(item.title))
            return false;

        // Item should have one of link or enclosure url.
        if (!isValidValue(item.link)
             && !isValidValue(item.enclosureUrl))
            return false;

        return true;
    }

    /**
     * Check that is this valid channel?
     * (Result of parsing has enough information required by this application?)
     * @param ch
     * @return
     */
    public static boolean
    verifyConstraints(Feed.Channel.ParD ch) {
        if (!isValidValue(ch.title))
            return false;

        return true;
    }

    /**
     * Guessing default action type from Feed data.
     * @param cParD
     * @param iParD
     * @return
     *   Feed.Channel.FActxxxx
     */
    public static long
    decideActionType(long action, Feed.Channel.ParD cParD, Feed.Item.ParD iParD) {
        long    actFlag;

        if (null == iParD) {
            if (Feed.FINVALID == action)
                return Feed.FINVALID; // do nothing if there is no items at first insertion.

            // default value
            actFlag = Feed.Channel.FACT_DEFAULT;
        }

        switch (cParD.type) {
        case NORMAL:
            actFlag = Feed.Channel.FACT_TYPE_DYNAMIC | Feed.Channel.FACT_PROG_IN;
            break;
        case EMBEDDED_MEDIA: // special for youtube!
            actFlag = Feed.Channel.FACT_TYPE_EMBEDDED_MEDIA | Feed.Channel.FACT_PROG_EX;
            break;
        default:
            eAssert(false);
            actFlag = Feed.Channel.FACT_DEFAULT;
        }

        // NOTE
        // FACT_PROG_IN/EX can be configurable by user
        // So, this flag should not be changed except for action is invalid value.
        if (Feed.FINVALID == action)
            // In case of newly inserted channel (first decision), FACT_PROG_XX should be set as recommended one.
            return Utils.bitSet(action, actFlag, Feed.Channel.MACT_TYPE | Feed.Channel.MACT_PROG);
        else
            // If this is NOT first decision, user may change FACT_PROG_XX setting (UX scenario support this.)
            // So, in this case, FACT_PROG_XX SHOULD NOT be changed.
            return Utils.bitSet(action, actFlag, Feed.Channel.MACT_TYPE);
    }

    public static String
    getDynamicActionTargetUrl(long action, String link, String enclosure) {
        if (Feed.Channel.FACT_TYPE_DYNAMIC != Feed.Channel.getActType(action))
            return null; // Not applicable for other case!

        if (Utils.isValidValue(enclosure)
            && Utils.isAudioOrVideo(enclosure))
            return enclosure;
        else {
            if (Utils.isValidValue(link))
                return link;
            else if (Utils.isValidValue(enclosure))
                return enclosure;
            else
                eAssert(false); // there is no valid link or enclosure value...
        }
        return null;
    }
}
