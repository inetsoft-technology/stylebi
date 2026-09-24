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
package inetsoft.util.stall;

import java.io.Serial;

/**
 * Thrown by a wait that made no progress for {@code stall.watchdog.noProgressMillis}, most
 * likely because of a lock cycle (bug #76967). It fails the query like a query timeout
 * does, and it is never to be taken for the end of a table.
 */
public class LockStallException extends RuntimeException {
   /**
    * @param site          the wait site, e.g. {@code SummaryFilter.waitForRow}.
    * @param threadName    the waiting thread.
    * @param stalledMillis how long the wait saw no progress.
    * @param dumpPath      the thread dump file, or {@code null} if none could be written.
    */
   public LockStallException(String site, String threadName, long stalledMillis, String dumpPath) {
      super(buildMessage(site, threadName, stalledMillis, dumpPath));
      this.site = site;
      this.threadName = threadName;
      this.stalledMillis = stalledMillis;
      this.dumpPath = dumpPath;
   }

   /**
    * Rethrow a stall on another thread, e.g. to the reader of a lens whose worker stalled.
    */
   public LockStallException(LockStallException cause) {
      super(cause.getMessage(), cause);
      this.site = cause.site;
      this.threadName = cause.threadName;
      this.stalledMillis = cause.stalledMillis;
      this.dumpPath = cause.dumpPath;
   }

   public String getSite() {
      return site;
   }

   public String getThreadName() {
      return threadName;
   }

   public long getStalledMillis() {
      return stalledMillis;
   }

   public String getDumpPath() {
      return dumpPath;
   }

   private static String buildMessage(String site, String threadName, long stalledMillis,
                                      String dumpPath)
   {
      return "Query stalled: no progress for " + stalledMillis + " ms waiting in " + site +
         " on thread \"" + threadName + "\"" +
         (dumpPath != null ? ", thread dump: " + dumpPath : "");
   }

   private final String site;
   private final String threadName;
   private final long stalledMillis;
   private final String dumpPath;
   @Serial
   private static final long serialVersionUID = 1L;
}
