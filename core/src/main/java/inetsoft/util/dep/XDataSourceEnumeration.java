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

import inetsoft.uql.service.DataSourceRegistry;

/**
 * XDataSourceEnumeration implements the XAssetEnumeration interface,
 * generates a series of XDataSourceAssets, one at a time.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class XDataSourceEnumeration implements XAssetEnumeration<XDataSourceAsset> {
   /**
    * Constructor.
    */
   public XDataSourceEnumeration() {
      DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      dataSources = registry.getDataSourceFullNames();
   }

   /**
    * Tests if this enumeration contains more elements.
    * @return <code>true</code> if and only if this enumeration object contains
    * at least one more element to provide; <code>false</code> otherwise.
    */
   @Override
   public boolean hasMoreElements() {
      return dataSources != null && currentIndex < dataSources.length;
   }

   /**
    * Returns the next element of this enumeration if this enumeration object
    * has at least one more element to provide.
    * @return the next element of this enumeration.
    */
   @Override
   public XDataSourceAsset nextElement() {
      String dataSource = dataSources[currentIndex++];
      return new XDataSourceAsset(dataSource);
   }

   private String[] dataSources;
   private int currentIndex;
}