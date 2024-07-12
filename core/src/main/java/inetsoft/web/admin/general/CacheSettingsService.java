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
package inetsoft.web.admin.general;

import inetsoft.sree.SreeEnv;
import inetsoft.util.FileSystemService;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.general.model.CacheSettingsModel;
import inetsoft.web.cluster.ServerClusterClient;
import inetsoft.web.cluster.ServerClusterStatus;
import inetsoft.web.viewsheet.AuditUser;
import inetsoft.web.viewsheet.Audited;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
public class CacheSettingsService {
   public CacheSettingsModel getModel() throws Exception {
      String directory = SreeEnv.getProperty("replet.cache.directory");

      if(directory == null) {
         directory = FileSystemService.getInstance().getCacheDirectory();
      }

      if(directory != null && directory.contains(SreeEnv.getProperty("sree.home"))) {
         directory = "$(sree.home)" +
            directory.substring(SreeEnv.getProperty("sree.home").length());
      }
      else if(directory == null) {
         directory = "";
      }

      return CacheSettingsModel.builder()
         .directory(directory)
         .cleanUpStartup("true".equals(SreeEnv.getProperty("replet.cache.clean")))
         .build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "General-Cache",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setModel(CacheSettingsModel model,
                        @SuppressWarnings("unused") @AuditUser Principal principal)
      throws Exception
   {
      SreeEnv.setProperty("replet.cache.directory", model.directory());
      SreeEnv.setProperty("replet.cache.clean", model.cleanUpStartup() + "");
      SreeEnv.save();
   }

   public void cleanUpCache() {
      if("server_cluster".equals(SreeEnv.getProperty("server.type"))) {
         ServerClusterClient client = new ServerClusterClient();

         for(String server : client.getConfiguredServers()) {
            ServerClusterStatus status = client.getStatus(server);

            if(status.getStatus() != ServerClusterStatus.Status.DOWN) {
               client.cleanCache(server);
            }
         }
      }
      else {
         FileSystemService.getInstance().clearCacheFiles(null);
      }
   }
}
