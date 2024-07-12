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
package inetsoft.util.dep;

import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XUtil;
import inetsoft.util.EnumEnumeration;

import java.util.*;

/**
 * XLogicalModelEnumeration implements the XAssetEnumeration interface,
 * generates a series of XLogicalModelAssets, one at a time.
 *
 * @version 9.5
 * @author InetSoft Technology Corp
 */
public class XLogicalModelEnumeration implements XAssetEnumeration<XLogicalModelAsset> {
   /**
    * Constructor.
    */
   public XLogicalModelEnumeration() {
      DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      datasources = registry.getDataSourceFullNames();
      List<XLogicalModel> logicalModels = new ArrayList<>();
      List<XLogicalModel> extLogicalModels = new ArrayList<>();

      for(String datasource : datasources) {
         XDataModel model = registry.getDataModel(datasource);

         if(model == null) {
            continue;
         }

         final ArrayList<String> names = new ArrayList<>();
         for(String baseName: model.getLogicalModelNames()) {
            XLogicalModel lmodel = model.getLogicalModel(baseName);

            if(lmodel != null) {
               logicalModels.add(lmodel);

               for(String lname : lmodel.getLogicalModelNames()) {
                  XLogicalModel extModel = lmodel.getLogicalModel(lname);

                  if(extModel != null) {
                     extLogicalModels.add(extModel);
                  }
               }
            }
         }

         lmodels.put(datasource, names.iterator());
      }

      this.logicalModels = new EnumEnumeration<>(Arrays.asList(
         Collections.enumeration(logicalModels), Collections.enumeration(extLogicalModels)));
   }

   /**
    * Tests if this enumeration contains more elements.
    * @return <code>true</code> if and only if this enumeration object contains
    * at least one more element to provide; <code>false</code> otherwise.
    */
   @Override
   public boolean hasMoreElements() {
      return logicalModels.hasMoreElements();
   }

   /**
    * Returns the next element of this enumeration if this enumeration object
    * has at least one more element to provide.
    * @return the next element of this enumeration.
    */
   @Override
   public XLogicalModelAsset nextElement() {
      XLogicalModel lg = logicalModels.nextElement();
      XDataModel model = lg.getDataModel();
      String ds = model.getDataSource();
      XLogicalModel base = lg.getBaseModel();
      String folder = base != null ? base.getFolder() : lg.getFolder();
      String baseName = base != null ? base.getName() : null;
      String path = XUtil.getDataModelDisplayPath(ds, folder, baseName, lg.getName());
      return new XLogicalModelAsset(path);
    }

   /**
    * Get current enumeration.
    */
   private Iterator<String> getCurrentEnumeration() {
      return lmodels.get(datasources[currDataSource]);
   }

   private final EnumEnumeration<XLogicalModel> logicalModels;
   private Map<String, Iterator<String>> lmodels = new HashMap<>();
   private String[] datasources;
   private int currDataSource = 0;
}
