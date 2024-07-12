/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.monitoring;

import inetsoft.sree.internal.cluster.Cluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.OffsetDateTime;

public class MonitorSchedulingTask implements Runnable, Serializable {
   @Override
   public void run() {
      UpdateStatusMessage message = new UpdateStatusMessage();
      message.setTimestamp(calculateTimestamp());

      try {
         Cluster.getInstance().sendMessage(message);
      }
      catch(Exception e) {
         LOG.warn("Failed to send update status message", e);
      }
   }

   private long calculateTimestamp() {
      OffsetDateTime now = OffsetDateTime.now();
      now = now.withNano(0);

      if(now.getSecond() >= 45 || now.getSecond() < 15) {
         now = now.withSecond(0);
      }
      else {
         now = now.withSecond(30);
      }

      return now.toInstant().toEpochMilli();
   }

   private static final Logger LOG = LoggerFactory.getLogger(MonitorSchedulingTask.class);
}
