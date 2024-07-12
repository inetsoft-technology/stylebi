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
package inetsoft.uql.asset.delete;

import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.sync.RenameInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class DeleteDependencyInfo extends RenameInfo {
   public List<DeleteInfo> getDeleteInfos() {
      return this.dinfos;
   }

   public void addDeleteInfo(AssetObject entry, DeleteInfo info) {
      List<DeleteInfo> list = map.computeIfAbsent(entry, k -> new ArrayList<>());
      list.add(info);
   }

   public void setDeleteInfo(AssetObject entry, List<DeleteInfo> infos) {
      map.put(entry, infos);
   }

   public List<DeleteInfo> getDeleteInfo(AssetObject entry) {
      return map.getOrDefault(entry, new ArrayList<>());
   }

   public AssetObject[] getAssetObjects() {
      Set<AssetObject> keys = map.keySet();
      return keys.toArray(new AssetObject[0]);
   }

   private List<DeleteInfo> dinfos = new ArrayList<>();
   private Map<AssetObject, List<DeleteInfo>> map = new HashMap<>();

   private static final Logger LOG = LoggerFactory.getLogger(DeleteDependencyInfo.class);
}
