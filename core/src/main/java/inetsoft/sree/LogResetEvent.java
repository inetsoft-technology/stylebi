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

package inetsoft.sree;

import org.springframework.context.ApplicationEvent;

public class LogResetEvent extends ApplicationEvent {
   public LogResetEvent(Object source, String logFile, String logFileDiscriminator, boolean console,
                        long maxFileSize, int maxFileCount, boolean performance)
   {
      super(source);
      this.logFile = logFile;
      this.logFileDiscriminator = logFileDiscriminator;
      this.console = console;
      this.maxFileSize = maxFileSize;
      this.maxFileCount = maxFileCount;
      this.performance = performance;
   }

   public String getLogFile() {
      return logFile;
   }

   public String getLogFileDiscriminator() {
      return logFileDiscriminator;
   }

   public boolean isConsole() {
      return console;
   }

   public long getMaxFileSize() {
      return maxFileSize;
   }

   public int getMaxFileCount() {
      return maxFileCount;
   }

   public boolean isPerformance() {
      return performance;
   }

   private final String logFile;
   private final String logFileDiscriminator;
   private final boolean console;
   private final long maxFileSize;
   private final int maxFileCount;
   private final boolean performance;
}
