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
package inetsoft.uql.rest;

import java.io.Closeable;

/**
 * Iterator which produces REST data for consumption.
 *
 * @param <E> the data's class.
 */
public interface RestDataIteratorStrategy<E> extends Closeable {
   /**
    * Returns {@code true} if the iteration has more elements.
    * (In other words, returns {@code true} if {@link #next} would
    * return an element rather than throwing an exception.)
    *
    * @return {@code true} if the iteration has more elements
    */
   boolean hasNext() throws Exception;

   /**
    * Returns the next element in the iteration.
    * @return the next element in the iteration, or null if none exists
    */
   E next() throws Exception;

   /**
    * Check if this query is for design time preview.
    */
   default boolean isLiveMode() {
      return false;
   }

   /**
    * Set if this query is for design time preview.
    */
   default void setLiveMode(boolean livemode) {
   }

   /**
    * Check if this query is a lookup query.
    */
   default boolean isLookup() {
      return false;
   }

   /**
    * Set if this query is a lookup query.
    */
   default void setLookup(boolean lookup) {
   }
}
