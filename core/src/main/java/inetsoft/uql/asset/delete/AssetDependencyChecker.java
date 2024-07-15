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
package inetsoft.uql.asset.delete;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.sync.AssetDependencyTransformer;
import inetsoft.uql.asset.sync.DependencyTool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.*;

public class AssetDependencyChecker extends DependencyChecker {
   public AssetDependencyChecker(AssetEntry asset) {
      this.entry = asset;
      ws = DependencyTool.getAssetElement(entry);
   }

   public AssetEntry getWorksheet() {
      return entry;
   }

   public List<DeleteInfo> hasDependency(List<DeleteInfo> infos, boolean checkAll) {
      NodeList assemblies = DependencyTool.getChildNodes(
         xpath, ws, AssetDependencyTransformer.ASSET_DEPEND_ELEMENTS);

      if(assemblies == null || assemblies.getLength() < 1) {
         return null;
      }

      DeleteInfo dinfo;
      List<DeleteInfo> result = new ArrayList<>();

      for(DeleteInfo info : infos) {
         for(int i = 0; i < assemblies.getLength(); i++) {
            Element assembly = (Element) assemblies.item(i);

            if(assembly == null || !isSameSource(assembly, info)) {
               continue;
            }

            dinfo = null;

            if(checkDataRef(assembly, info)) {
               dinfo = info;
            }

            if(dinfo != null) {
               if(!checkAll) {
                  return Collections.singletonList(dinfo);
               }

               result.add(dinfo);
            }
         }
      }

      return makeEmptyToNone(result);
   }

   @Override
   protected boolean isSameSource(Element elem, DeleteInfo info) {
      return DependencyTool.isSameWorkSheetSource(elem, info);
   }

   private AssetEntry entry;
   private Element ws;
}
