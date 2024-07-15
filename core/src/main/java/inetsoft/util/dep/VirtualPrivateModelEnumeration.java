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
package inetsoft.util.dep;

import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.service.DataSourceRegistry;

import java.util.*;

/**
 * VirtualPrivateModelEnumeration implements the XAssetEnumeration interface,
 * generates a series of VirtualPrivateModelAssets, one at a time.
 *
 * @version 9.5
 * @author InetSoft Technology Corp
 */
public class VirtualPrivateModelEnumeration implements XAssetEnumeration<VirtualPrivateModelAsset> {
   /**
    * Constructor.
    */
   public VirtualPrivateModelEnumeration() {
      DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      datasources = registry.getDataSourceFullNames();

      for(String datasource : datasources) {
         XDataModel model = registry.getDataModel(datasource);

         if(model == null) {
            continue;
         }

         vpms.put(datasource, model.getVirtualPrivateModels());
      }
   }

   /**
    * Tests if this enumeration contains more elements.
    * @return <code>true</code> if and only if this enumeration object contains
    * at least one more element to provide; <code>false</code> otherwise.
    */
   @Override
   public boolean hasMoreElements() {
      if(currDataSource < 0 || currDataSource >= datasources.length) {
         return false;
      }

      while(currDataSource < datasources.length) {
         Enumeration currEnum = getCurrentEnumeration();

         if(currEnum != null && currEnum.hasMoreElements()) {
            return true;
         }

         currDataSource++;
      }

      return false;
   }

   /**
    * Returns the next element of this enumeration if this enumeration object
    * has at least one more element to provide.
    * @return the next element of this enumeration.
    */
   @Override
   public VirtualPrivateModelAsset nextElement() {
      VirtualPrivateModel vpm = (VirtualPrivateModel) getCurrentEnumeration().nextElement();
      VirtualPrivateModelAsset vpmAsset = new VirtualPrivateModelAsset(
         datasources[currDataSource] + "^" + vpm.getName());

      if(!hasMoreElements()) {
         currDataSource++;
      }

      return vpmAsset;
   }

   /**
    * Get current enumeration.
    */
   private Enumeration getCurrentEnumeration() {
      return vpms.get(datasources[currDataSource]);
   }

   private Map<String, Enumeration<VirtualPrivateModel>> vpms = new HashMap<>();
   private String[] datasources;
   private int currDataSource = 0;
}