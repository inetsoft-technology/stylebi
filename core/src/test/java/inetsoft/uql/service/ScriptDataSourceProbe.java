/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.uql.service;

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;

/**
 * Test-only data source plumbing a script can call (Bug #77539, see
 * ScriptDataSourceAccessTest): the registry's read check of a data source, as its lookups
 * make it.
 */
public final class ScriptDataSourceProbe {
   private ScriptDataSourceProbe() {
   }

   /** The registry's data source names, sorted and joined with commas. */
   public static String names() {
      String[] names = DataSourceRegistry.getRegistry().getDataSourceFullNames();
      java.util.Arrays.sort(names);
      return String.join(",", names);
   }

   public static boolean canRead(String dataSource) {
      return DataSourceRegistry.getRegistry().checkPermission(
         ResourceType.DATA_SOURCE, dataSource, ResourceAction.READ);
   }
}
