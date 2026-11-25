/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.report.composition.execution;

import inetsoft.uql.VariableTable;

import java.util.*;

/**
 * The variable tables of the member queries of a join or concatenated table.
 * <p>
 * Bug #82290: when the members run in parallel, each one writes its own execution hints
 * (the max rows, the timeout and so on) into the variable table it is given and the data
 * handler reads them back later. With one shared table a member could run with a sibling's
 * max rows, or without its own. So every member gets its own copy, all taken on the
 * calling thread before the first member starts. After a member returns,
 * {@link #writeBack(int)} copies the variables it added, changed or removed (for example a
 * parameter set by a VPM trigger script) back into the parent's table, as the shared table
 * carried them before, except the hints, which are per query and stay as the parent had
 * them.
 * <p>
 * When the members run one after another on the calling thread, each one finishes before
 * the next starts, so they keep sharing the parent's table as before.
 */
final class MemberQueryVariables {
   /**
    * @param vars the variable table of the join or concatenated query, may be null.
    * @param count the number of member queries.
    * @param copy <tt>true</tt> to give every member its own copy (the members run in
    *             parallel), <tt>false</tt> to let them share <tt>vars</tt>.
    */
   MemberQueryVariables(VariableTable vars, int count, boolean copy) {
      this.vars = vars;
      this.copies = new VariableTable[count];

      if(vars == null || !copy) {
         Arrays.fill(copies, vars);
         this.snapshot = null;
         return;
      }

      this.snapshot = vars.clone();

      for(int i = 0; i < count; i++) {
         copies[i] = vars.clone();
      }
   }

   /**
    * Get the variable table the member query at the index runs with.
    */
   VariableTable get(int index) {
      return copies[index];
   }

   /**
    * Copy the variables the member query at the index added, changed or removed into the
    * parent's table, except the execution hints. Called on the calling thread after the
    * member returned, in member order.
    */
   void writeBack(int index) throws Exception {
      if(snapshot == null) {
         return;
      }

      VariableTable copy = copies[index];
      Set<String> names = new HashSet<>();

      for(Enumeration<String> keys = copy.keys(); keys.hasMoreElements(); ) {
         names.add(keys.nextElement());
      }

      for(String name : names) {
         if(isHint(name)) {
            continue;
         }

         Object value = copy.get(name);

         if(!Objects.deepEquals(value, snapshot.get(name)) || !snapshot.contains(name)) {
            vars.put(name, value);
         }

         if(copy.isAsIs(name) != snapshot.isAsIs(name)) {
            vars.setAsIs(name, copy.isAsIs(name));
         }
      }

      for(Enumeration<String> keys = snapshot.keys(); keys.hasMoreElements(); ) {
         String name = keys.nextElement();

         if(!isHint(name) && !names.contains(name)) {
            vars.remove(name);
         }
      }
   }

   /**
    * Check if a variable is an execution hint (XQuery.HINT_MAX_ROWS, XQuery.HINT_TIMEOUT,
    * XQuery.HINT_TOUCH_TIMESTAMP, UniformSQL.HINT_INPUT_MAXROWS, the cache samples and so
    * on), which a query sets for its own execution.
    */
   static boolean isHint(String name) {
      return name != null && name.startsWith("__HINT_");
   }

   private final VariableTable vars;
   private final VariableTable snapshot;
   private final VariableTable[] copies;
}
