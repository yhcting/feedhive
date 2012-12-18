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


public class FeederException extends Exception {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(FeederException.class);

    private Err err;

    public FeederException() {
        super();
    }

    public FeederException(String message, Throwable cause) {
        super(message, cause);
    }

    public FeederException(String message) {
        super(message);
    }

    public FeederException(Throwable cause) {
        super(cause);
    }

    public FeederException(Err err) {
        this.err = err;
    }

    public Err
    getError() {
        return err;
    }
}
