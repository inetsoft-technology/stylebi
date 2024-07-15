/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util;

import java.util.*;

/**
 * Enumeration implementation that wraps an Iterator.
 *
 * @author InetSoft Technology Corp.
 * @since 6.0
 */
public class IteratorEnumeration<E> implements Enumeration<E> {
   /**
    * Create a new instance of IteratorEnumeration.
    *
    * @param iterator the Iterator to be wrapped.
    */
   public IteratorEnumeration(Iterator<E> iterator) {
      this.iterator = iterator;
   }
   
   /**
    * Tests if this enumeration contains more elements.
    *
    * @return <code>true</code> if and only if this enumeration object contains
    *         at least one more element to provide; <code>false</code>
    *         otherwise.
    */
   @Override
   public final boolean hasMoreElements() {
      return iterator.hasNext();
   }
   
   /**
    * Returns the next element of this enumeration if this enumeration object
    * has at least one more element to provide.
    *
    * @return the next element of this enumeration.
    *
    * @throws NoSuchElementException if no more elements exist.
    */
   @Override
   public final E nextElement() {
      return iterator.next();
   }
   
   private Iterator<E> iterator;
}
