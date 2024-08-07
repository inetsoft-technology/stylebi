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
package inetsoft.web.admin.monitoring;

import inetsoft.sree.internal.SUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public abstract class AbstractMonitoringController {
   protected String getServerClusterNode(String server) {
      String node = null;

      if(StringUtils.hasText(server) && !"/".equals(server)) {
         try {
            node = SUtil.computeServerClusterNode(server);
         }
         catch(Exception e) {
            LOG.warn("Failed to get cluster node for address {}", server, e);
         }
      }

      return node;
   }

   private final Logger LOG = LoggerFactory.getLogger(AbstractMonitoringController.class);
}
