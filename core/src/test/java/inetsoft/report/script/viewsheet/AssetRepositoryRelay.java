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

package inetsoft.report.script.viewsheet;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.VSAssembly;

/**
 * Test-only statics a script calls in {@link AssetRepositoryScriptAccessTest}. Each one is
 * Java code between the script and the repository, so the getAssetRepository guard lets it
 * through. The relay stands in for a future route that hands the engine to a script.
 */
public final class AssetRepositoryRelay {
   private AssetRepositoryRelay() {
   }

   public static Object repo() {
      return AssetUtil.getAssetRepository(false);
   }

   public static Object storage(AssetEntry entry) throws Exception {
      return AssetUtil.getAssetRepository(false).getStorage(entry);
   }

   public static VSAssembly assembly() {
      return assembly;
   }

   public static ColumnSelection columns() {
      return columns;
   }

   static VSAssembly assembly;
   static ColumnSelection columns;
}
