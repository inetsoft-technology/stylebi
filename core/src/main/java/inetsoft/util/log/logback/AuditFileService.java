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
package inetsoft.util.log.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import inetsoft.util.SingletonManager;

public class AuditFileService implements AutoCloseable {
   public static AuditFileService getInstance() {
      return SingletonManager.getInstance(AuditFileService.class);
   }

   @Override
   public void close() throws Exception {
      if(appender != null) {
         appender.stop();
         appender = null;
      }
   }

   public void append(ILoggingEvent event) {
      if(appender != null) {
         appender.doAppend(event);
      }
   }

   void setAppender(Appender<ILoggingEvent> appender) {
      if(this.appender != null) {
         this.appender.stop();
      }

      this.appender = appender;
   }

   private Appender<ILoggingEvent> appender = null;
}
