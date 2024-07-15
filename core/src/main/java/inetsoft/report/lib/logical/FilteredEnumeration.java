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
package inetsoft.report.lib.logical;

import java.util.*;
import java.util.function.Predicate;

class FilteredEnumeration implements Enumeration<String> {
   FilteredEnumeration(Predicate<String> predicate, Collection<String> elements) {
      this.predicate = predicate;
      iterator = elements.iterator();
      next = findNext();
   }

   @Override
   public boolean hasMoreElements() {
      return next != null;
   }

   @Override
   public String nextElement() {
      if(next == null) {
         throw new NoSuchElementException();
      }

      String current = next;
      next = findNext();
      return current;
   }

   protected String findNext() {
      String result = null;

      while(iterator.hasNext()) {
         String value = iterator.next();

         if(predicate.test(value)) {
            result = value;
            break;
         }
      }

      return result;
   }

   private final Predicate<String> predicate;
   private final Iterator<String> iterator;
   private String next;
}
