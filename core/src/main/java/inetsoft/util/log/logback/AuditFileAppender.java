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
package inetsoft.util.log.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.core.AppenderBase;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.SingletonRunnableTask;

public class AuditFileAppender extends AppenderBase<ILoggingEvent> {
   @Override
   protected void append(ILoggingEvent event) {
      Cluster.getInstance().submit("audit_log", new AuditFileTask(event));
   }

   public static final class AuditFileTask implements SingletonRunnableTask {
      public AuditFileTask(ILoggingEvent event) {
         this.event = LoggingEventVO.build(event);
      }

      @Override
      public void run() {
         AuditFileService.getInstance().append(event);
      }

      private final LoggingEventVO event;
   }
}
