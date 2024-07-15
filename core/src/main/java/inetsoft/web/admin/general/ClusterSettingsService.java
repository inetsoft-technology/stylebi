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
package inetsoft.web.admin.general;

import inetsoft.sree.SreeEnv;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.general.model.ClusterSettingsModel;
import inetsoft.web.viewsheet.AuditUser;
import inetsoft.web.viewsheet.Audited;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.Principal;

@Service
public class ClusterSettingsService {
   public ClusterSettingsModel getModel() {
      String homeString = SreeEnv.getProperty("sree.home");
      String proxyString =
         SreeEnv.getProperty("replet.repository.servlet", "http://localhost:8080/");
      boolean cluster = "server_cluster".equals(SreeEnv.getProperty("server.type"));

      return ClusterSettingsModel.builder()
         .home(homeString)
         .proxy(proxyString)
         .cluster(cluster)
         .build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "General-Cluster",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY)
   public void setModel(ClusterSettingsModel model,
                        @SuppressWarnings("unused") @AuditUser Principal principal)
      throws IOException
   {
      String serverType;

      if(model.cluster()) {
         serverType = "server_cluster";
      }
      else {
         serverType = "servlet";
      }

      SreeEnv.setProperty("server.type", serverType);
      SreeEnv.setProperty("replet.repository.protocol", "LOCAL");
      SreeEnv.save();
   }
}
