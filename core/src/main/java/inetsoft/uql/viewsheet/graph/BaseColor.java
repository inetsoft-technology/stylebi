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

package inetsoft.uql.viewsheet.graph;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.css.CSSDictionary;

import java.util.HashMap;
import java.util.Map;

public class BaseColor {
   protected static CSSDictionary getDictionary() {
      String orgID = OrganizationManager.getInstance().getCurrentOrgID();
      CSSDictionary dict = dictMap.get(orgID);
      long lastModified = lastModifiedMap.containsKey(orgID) ? lastModifiedMap.get(orgID): -1;
      long latestModifiedTime = CSSDictionary.getOrgScopedCSSLastModified(dict);

      if(dict == null || latestModifiedTime > lastModified) {
         dict = CSSDictionary.getDictionary();
         dictMap.put(orgID, dict);
      }

      lastModifiedMap.put(orgID, latestModifiedTime);
      return dict;
   }

   private static final Map<String, Long> lastModifiedMap = new HashMap<>();
   private static final Map<String, CSSDictionary> dictMap = new HashMap<>();
}
