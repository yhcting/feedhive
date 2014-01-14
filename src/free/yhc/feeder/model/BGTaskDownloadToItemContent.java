/******************************************************************************
 *    Copyright (C) 2012, 2013, 2014 Younghyung Cho. <yhcting77@gmail.com>
 *
 *    This file is part of FeedHive
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
 *    along with this program.	If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

package free.yhc.feeder.model;

import java.io.File;

public class BGTaskDownloadToItemContent extends BGTaskDownloadToFile {
    private long mId;
    public BGTaskDownloadToItemContent(String url, long id) {
        super(new Arg(url,
                      ContentsManager.get().getItemInfoDataFile(id),
                      Utils.getNewTempFile()));
        mId = id;
    }

    @Override
    protected Err
    doBgTask(Arg arg) {
        if (null == arg.toFile)
            return Err.IO_FILE;

        new File(arg.toFile.getParent()).mkdirs();
        ContentsManager cm = ContentsManager.get();
        Err ret = super.doBgTask(arg);
        if (Err.NO_ERR == ret)
            cm.addItemContent(cm.getItemInfoDataFile(mId), mId);
        return ret;
    }
}
