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
package inetsoft.mv.fs.internal;

import inetsoft.mv.MVManager;
import inetsoft.mv.fs.FSService;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.*;
import inetsoft.util.FileSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class FSMessageHandler implements MessageListener {
   public FSMessageHandler() {
       FSService.getConfig();
       String home = FSService.getConfig().getWorkDir("localhost");

      if(home != null) {
         home = FileSystemService.getInstance().getFile(home).getAbsolutePath();
         Cluster cluster = Cluster.getInstance();
         ClusterUtil.setWorkDir(cluster.getLocalMember(), home);
      }
   }

   @Override
   public void messageReceived(MessageEvent event) {
      Cluster cluster = Cluster.getInstance();

      if(!Objects.equals(event.getSender(), cluster.getLocalMember())) {
         if(event.getMessage() instanceof ClearSecurityCacheMessage) {
            clearSecurityCache();
         }
         else if(event.getMessage() instanceof RefreshFSMessage) {
            refresh();
         }
      }
   }

   private void clearSecurityCache() {
      try {
         SecurityProvider provider =
            SecurityEngine.getSecurity().getSecurityProvider();

         if(provider instanceof AbstractSecurityProvider) {
            provider.clearCache();
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to clear security cache", e);
      }
   }

   private void refresh() {
      FSService.refresh();
      MVManager.getManager().refresh();
   }

   private static final Logger LOG = LoggerFactory.getLogger(FSMessageHandler.class);
}
