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
package inetsoft.util.script;

import java.util.*;

/**
 * A bindingInfo write with type Chart.BINDING_FIELD, which searches the x and y shelves
 * only, on a field the chart has bound on another shelf (Bug #76862). A distinct type so the
 * chart graph build can report it where it reports no other script error.
 */
public class BindingFieldShelfException extends IllegalArgumentException {
   public BindingFieldShelfException(String message) {
      super(message);
   }

   /**
    * Find this exception in the cause chain of {@code failure}.
    */
   public static BindingFieldShelfException find(Throwable failure) {
      Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());

      for(Throwable t = failure; t != null && seen.add(t); t = t.getCause()) {
         if(t instanceof BindingFieldShelfException) {
            return (BindingFieldShelfException) t;
         }
      }

      return null;
   }
}
