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
package inetsoft.web.portal.model.database;

import inetsoft.util.data.CommonKVModel;
import inetsoft.uql.asset.AssetEntry;

import java.io.Serializable;
import java.util.List;

public class DrillSubQueryModel implements Serializable {
   public List<CommonKVModel<String, String>> getParams() {
      return params;
   }

   public void setParams(List<CommonKVModel<String, String>> params) {
      this.params = params;
   }

   public AssetEntry getEntry() {
      return entry;
   }

   public void setEntry(AssetEntry entry) {
      this.entry = entry;
   }

   private AssetEntry entry;
   private List<CommonKVModel<String, String>> params;
}
