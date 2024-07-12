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

import inetsoft.uql.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.Catalog;

import java.util.List;

public interface AutoDrillAsset extends XAsset {
   default void getAutoDrillDependency(XMetaInfo metaInfo,
                                       List<XAssetDependency> dependencies, String fromDesc)
   {
      XDrillInfo drillInfo = metaInfo == null ? null : metaInfo.getXDrillInfo();

      if(drillInfo == null) {
         return;
      }

      Catalog catalog = Catalog.getCatalog();

      for(int i = 0; i < drillInfo.getDrillPathCount(); i++) {
         DrillPath path = drillInfo.getDrillPath(i);
         DrillSubQuery dquery = path.getQuery();
         String ws = dquery == null ? null : dquery.getWsIdentifier();

         // check auto-drill browse data from query
         if(ws != null) {
            AssetEntry entry = AssetEntry.createAssetEntry(ws);
            String desc = generateDescription(catalog, fromDesc,
               catalog.getString("common.xasset.worksheet", entry.getPath()));
            dependencies.add(new XAssetDependency(new WorksheetAsset(entry), this,
                                                  XAssetDependency.XLOGICALMODEL_DRILL_XQUERY, desc));
         }

         // check auto drill link to vs.
         if(path.getLinkType() == DrillPath.VIEWSHEET_LINK) {
            String vsIdentifier = path.getLink();
            AssetEntry entry = AssetEntry.createAssetEntry(vsIdentifier);
            String desc = generateDescription(catalog, fromDesc,
               catalog.getString("common.xasset.viewsheet", entry.getPath()));
            dependencies.add(new XAssetDependency(new ViewsheetAsset(entry), this,
                                                  XAssetDependency.XLOGICALMODEL_DRILL_XQUERY, desc));
         }
      }
   }

   /**
    * Generate dependency description.
    */
   private String generateDescription(Catalog catalog, String from, String to) {
      return catalog.getString("common.xasset.depends", from, to);
   }
}
