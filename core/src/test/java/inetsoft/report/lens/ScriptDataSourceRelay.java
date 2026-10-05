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
package inetsoft.report.lens;

import inetsoft.uql.XDataSource;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.service.ScriptDataSourceProbe;

/**
 * Test-only stand-in for product code a script calls that reads a data source while it runs
 * (Bug #77539, see ScriptDataSourceAccessTest), outside the data source plumbing.
 */
public final class ScriptDataSourceRelay {
   private ScriptDataSourceRelay() {
   }

   public static boolean canRead(String dataSource) {
      return ScriptDataSourceProbe.canRead(dataSource);
   }

   public static String dataSourceName(TableAssembly table) throws Exception {
      XDataSource ds = AssetUtil.getDataSource(table);
      return ds == null ? null : ds.getFullName();
   }
}
