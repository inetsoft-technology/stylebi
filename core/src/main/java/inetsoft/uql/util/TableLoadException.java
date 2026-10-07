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
package inetsoft.uql.util;

import java.util.*;

/**
 * Thrown to a reader that must fail rather than use partial data (a scheduled run) when it
 * reaches the end of the rows of a query result whose loading failed, e.g. a database error
 * while the rows were fetched (Bug #77901). A table that reads its base on a worker keeps it
 * for its own readers, so the failure is not taken for the end of the table.
 */
public class TableLoadException extends RuntimeException {
   /**
    * @param message the message, the database error.
    * @param cause   the load failure.
    */
   public TableLoadException(String message, Throwable cause) {
      super(message, cause);
   }

   /**
    * Rethrow a load failure on another thread, e.g. to the reader of a lens whose worker read
    * the failed table.
    */
   public TableLoadException(TableLoadException cause) {
      super(cause.getMessage(), cause);
   }

   /**
    * Find the load failure in the cause chain of {@code failure}, e.g. one a table wrapped.
    *
    * @return the outermost load failure of the chain, or {@code null} if there is none.
    */
   public static TableLoadException find(Throwable failure) {
      Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());

      for(Throwable t = failure; t != null && seen.add(t); t = t.getCause()) {
         if(t instanceof TableLoadException) {
            return (TableLoadException) t;
         }
      }

      return null;
   }
}
