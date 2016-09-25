/*
 * Copyright (C) 2016 tkv
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.vesalainen.web.cache;

import java.util.concurrent.Callable;

/**
 *
 * @author tkv
 */
public interface Runner extends Callable<Boolean>
{
    /**
     * Updates active timestamp
     */
    void active();
    /**
     * Returns millis after last active() call.
     * @return 
     */
    long idle();
    /**
     * Return number of (re)starts
     * @return 
     */
    int getStartCount();
    /**
     * Release all waiting resources.
     */
    void releaseAll();
    /**
     * Returns true if Runner is in condition where it is able to restart.
     * @return 
     */
    boolean needsStart();
    /**
     * Return true if Runner has dependent clients
     * @return 
     */
    boolean hasClients();
}
