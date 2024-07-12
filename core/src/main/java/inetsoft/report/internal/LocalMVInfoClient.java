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
package inetsoft.report.internal;

import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.uql.asset.AssetEntry;

import java.util.*;

class LocalMVInfoClient implements MVInfoClient {
   @Override
   public Date getDataRefreshedTime(AssetEntry entry) {
      if(entry != null && entry.getType() == AssetEntry.Type.WORKSHEET) {
         MVManager manager = MVManager.getManager();
         MVDef[] mvs = manager.list(
            true, def -> def.isWSMV() && def.getMetaData().isRegistered(entry.toIdentifier()));
         return Arrays.stream(mvs)
            .map(MVDef::getLastUpdateTime)
            .min(Comparator.naturalOrder())
            .map(Date::new)
            .orElse(null);
      }

      return null;
   }
}
