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

package inetsoft.web.admin.server;

import inetsoft.util.config.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ServiceLoader;

@Service
public class NodeProtectionService {

   /**
    * Initializes the service by choosing which NodeProtector implementation to use depending on the config
    */
   @PostConstruct
   public void init() {
      NodeProtectionConfig config = InetsoftConfig.getInstance().getNodeProtection();

      if(config != null && config.getType() != null) {
         for(NodeProtectorFactory factory : ServiceLoader.load(NodeProtectorFactory.class)) {
            if(config.getType().equals(factory.getType())) {
               nodeProtector = factory.create();
               break;
            }
         }
      }
   }

   /**
    * Enables or disables protection for the node, so it will not be removed by scale-in operations
    * @param enabled  whether to enable or disable protection
    */
   public void updateNodeProtection(boolean enabled) {
      if(nodeProtector != null) {
         nodeProtector.updateNodeProtection(enabled);
      }
   }

   /**
    * Get the status of the current node's protection against scale-in operations
    * @return  true if protection is enabled
    */
   public boolean getNodeProtection() {
      if(nodeProtector != null) {
         return nodeProtector.getNodeProtection();
      }

      return false;
   }

   /**
    * Returns the node protection expiration time in case of a failure to extend protection time.
    */
   public long getExpirationTime() {
      if(nodeProtector != null) {
         return nodeProtector.getExpirationTime();
      }

      return 0;
   }

   @PreDestroy
   private void close() throws Exception {
      nodeProtector.close();
   }

   NodeProtector nodeProtector;
   private static final Logger LOG = LoggerFactory.getLogger(NodeProtectionService.class);
}
