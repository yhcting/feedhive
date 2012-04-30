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

import java.util.HashMap;

// Simple Memory or Map Cache
class MCache <T> {
    private String                  name = ""; // usually for debugging.
    private HashMap<String, T>      m = new HashMap<String, T>();

    /**
     *
     * @param name: name of this cache
     */
    MCache(String name) {
        this.name = name;
    }

    /**
     *
     * @param key
     * @param value
     */
    void put(String key, T value) {
        synchronized (m) {
            m.put(key, value);
        }
    }

    /**
     *
     * @param key
     * @return
     *   null if there is no cached value.
     */
    T get(String key) {
        synchronized (m) {
            return m.get(key);
        }
    }

    /**
     * Say that this cache value is no more valid.
     * @param key
     */
    void invalidate(String key) {
        synchronized (m) {
            m.remove(key);
        }
    }

    /**
     * Clean cache.
     */
    void clean() {
        synchronized (m) {
            m.clear();
        }
    }
}
