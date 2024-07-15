/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.report.composition.graph;

import inetsoft.graph.data.DataSet;

/**
 * A map for data compare, it is used for data calculation,
 * like Change, RunningTotal or Moving.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public interface Router {
   public static final Object NOT_EXIST = new Object();
   public static final Object INVALID = new Object();

   /**
    * Get first value on the scale.
    */
   public Object getFirst();

   /**
    * Get last value on the scale.
    */
   public Object getLast();

   /**
    * Get index of a value in the mapper.
    */
   public int getIndex(Object val);

   /**
    * Get all values.
    */
   public Object[] getValues();

   /**
    * Get a value on scale that diff with current value.
    * @param val the given value.
    * @param diff before or after position of given value.
    */
   public Object getValue(Object val, int diff);

   /**
    * Get all previous values.
    */
   public Object[] getAllPrevious(Object val);

   /**
    * Check if this router can be used on the specified dataset.
    */
   public boolean isValidFor(DataSet dataSet);
}