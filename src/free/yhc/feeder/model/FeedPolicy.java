/*****************************************************************************
 *    Copyright (C) 2013 Younghyung Cho. <yhcting77@gmail.com>
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
