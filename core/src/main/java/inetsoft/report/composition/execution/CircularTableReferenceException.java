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

import java.io.Serial;
import java.util.*;

/**
 * Thrown when a table's build reads that same table by name, e.g. a worksheet expression
 * column {@code T['col']} on table {@code T}. The build would wait on its own executing
 * marker until the cache wait timeout, so it fails at once instead (bug #78283).
 *
 * <p>Only a read on the building thread is detected. A cycle that spans threads (two tables
 * built concurrently by two requests, or a script worker reading the table its builder owns)
 * is still bounded by the cache wait timeout.
 */
public class CircularTableReferenceException extends RuntimeException {
   /**
    * @param table the table that references itself, or {@code null} if not known yet.
    */
   public CircularTableReferenceException(String table) {
      super(buildMessage(table));
   }

   private CircularTableReferenceException(String message, boolean raw) {
      super(message);
   }

   /**
    * The circular reference a failed build recorded as its failure message. A table whose
    * build hit the circular reference comes back to its reader as a failed-query lens, and a
    * script that reads it must fail too instead of using that lens's design data.
    *
    * @return the exception to rethrow, or {@code null} if {@code failedQueryMessage} is not a
    *         circular reference.
    */
   public static CircularTableReferenceException ofFailedQuery(String failedQueryMessage) {
      return failedQueryMessage != null && failedQueryMessage.contains(MARKER) ?
         new CircularTableReferenceException(failedQueryMessage, true) : null;
   }

   /**
    * Find the exception in the cause chain of {@code failure}, which a script engine may wrap.
    *
    * @return the outermost circular reference of the chain, or {@code null} if there is none.
    */
   public static CircularTableReferenceException find(Throwable failure) {
      Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());

      for(Throwable t = failure; t != null && seen.add(t); t = t.getCause()) {
         if(t instanceof CircularTableReferenceException) {
            return (CircularTableReferenceException) t;
         }
      }

      return null;
   }

   private static String buildMessage(String table) {
      return "Table '" + (table == null ? "?" : table) + "' " + MARKER + " (an expression " +
         "column reads its own table by name, directly or through another table). Use " +
         "field['column'] to read another column of the same row.";
   }

   private static final String MARKER = "references itself while it is being built";

   @Serial
   private static final long serialVersionUID = 1L;
}
