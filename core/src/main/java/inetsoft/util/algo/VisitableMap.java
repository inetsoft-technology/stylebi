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
package inetsoft.util.algo;

import java.util.Map;

/**
 * A map that accepts visitors on its entries. This interface follows the GOF
 * visitor pattern.
 * <p>
 * This type of map should always be sorted, even though it does not implement
 * the <tt>SortedMap</tt> interface. The intention is that classes of this type
 * are implementation of different tree data structures.
 *
 * @author InetSoft Technology
 * @since  9.5
 */
public interface VisitableMap extends Map {
   /**
    * Visits the entries of this map in ascending order.
    *
    * @param visitor the visitor that will be invoked at each entry.
    */
   void acceptAscending(MapVisitor visitor);

   /**
    * Visits the entries of this map in descending order.
    *
    * @param visitor the visitor that will be invoked at each entry.
    */
   void acceptDescending(MapVisitor visitor);

   /**
    * Visits all entries whose keys fall in a given range. The entries will be
    * visited in ascending order.
    *
    * @param visitor the visitor that will be invoked at each entry.
    * @param minKey  the minimum key value to visit.
    * @param maxKey  the maximum key value to visit.
    * @param minInclude true to be inclusive on min value.
    * @param maxInclude true to be inclusive on max value.
    */
   void acceptBetween(MapVisitor visitor, Object minKey, Object maxKey,
                      boolean minInclude, boolean maxInclude);

   static class BetweenVisitor implements MapVisitor {
      public BetweenVisitor(MapVisitor visitor, Map.Entry first, Map.Entry last,
                            boolean includeFirst, boolean includeLast)
      {
         this.visitor = visitor;
         this.first = first;
         this.last = last;
         this.includeFirst = includeFirst;
         this.includeLast = this.includeFirst && (first == last) ?
            true : includeLast;
      }

      @Override
      public void visit(Map.Entry entry) {
         if(!started && entry == first) {
            started = true;
         }

         if(!started) {
            return;
         }

         if(!includeFirst && entry == first) {
            if(entry == last) {
               throw new VisitorFinishedException();
            }
            else {
               return;
            }
         }
         else if(!includeLast && entry == last) {
            throw new VisitorFinishedException();
         }

         visitor.visit(entry);

         if(entry == last) {
            throw new VisitorFinishedException();
         }
      }

      private final MapVisitor visitor;
      private final Map.Entry first;
      private final Map.Entry last;
      private final boolean includeFirst;
      private final boolean includeLast;
      private boolean started = false;
   }

   static class VisitorFinishedException extends RuntimeException {
   }
}
