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
package inetsoft.web.binding.handler;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.XFactory;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.*;
import inetsoft.uql.util.XSourceInfo;
import org.apache.commons.lang3.StringUtils;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Optional;

public class VSBindingHelper {
   /**
    * Get the formula defined in logical model entity attribute.
    */
   public static String getModelDefaultFormula(AssetEntry entry, SourceInfo sinfo,
                                               RuntimeViewsheet rvs,
                                               AnalyticRepository analyticRepository)
         throws RemoteException
   {
      AssetEntry baseEntry = rvs.getViewsheet().getBaseEntry();

      if(baseEntry != null && baseEntry.getType() == AssetEntry.Type.LOGIC_MODEL &&
         sinfo != null && sinfo.getType() == XSourceInfo.ASSET)
      {
         Principal user = rvs.getUser();
         String dataSourceName = baseEntry.getParentPath();
         XRepository repository = XFactory.getRepository();
         String orgId = entry.getOrgID();

         if(orgId == null) {
            orgId = OrganizationManager.getInstance().getCurrentOrgID();
         }

         String[] dataSourceNames = repository.getDataSourceFullNames(new IdentityID("", orgId));
         boolean dataSourceFound = false;

         for(String fullName : dataSourceNames) {
            if(fullName.equals(dataSourceName)) {
               // data source name matched exactly, can be used as-is
               dataSourceFound = true;
               break;
            }
            else if(dataSourceName.startsWith(fullName + "/")) {
               XDataModel dataModel = repository.getDataModel(fullName);

               if(dataModel != null) {
                  // if the parent path contains a data model folder, it needs to be stripped off
                  String[] dataModelFolders = dataModel.getFolders();
                  String dataModelFolder = dataSourceName.substring(fullName.length() + 1);

                  for(String folder : dataModelFolders) {
                     if(folder.equals(dataModelFolder)) {
                        dataSourceFound = true;
                        dataSourceName = fullName;
                        break;
                     }
                  }

                  if(dataSourceFound) {
                     break;
                  }
               }
            }
         }

         if(!dataSourceFound) {
            throw new IllegalArgumentException("Invalid data source name: " + dataSourceName);
         }

         XLogicalModel lm = analyticRepository
            .getLogicalModel(baseEntry.getName() + "::" + dataSourceName, user);

         if(lm != null) {
            String ename = entry.getName();
            ename = ename.contains(":") ? ename : entry.getProperty("attribute");
            String[] names = ename.split(":");

            if(names.length == 2) {
               XEntity entity = lm.getEntity(names[0]);

               if(entity != null) {
                  XAttribute attr = entity.getAttribute(names[1]);

                  if(attr != null) {
                     String formula = attr.getDefaultFormula();
                     return "None".equalsIgnoreCase(formula) ? null : formula;
                  }
               }
            }
         }
      }

      return null;
   }
}
