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

import inetsoft.uql.XDataSource;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.internal.AssetUtil;

/**
 * Test-only stand-in for a Spring-proxied data source helper ({@code inetsoft.uql}) a script
 * calls (Bug #78237, see ScriptDataSourceAccessProxyTest). Not final, so a CGLIB proxy can
 * subclass it.
 */
public class ProxiedScriptDataSourceHelper {
   public String dataSourceName(TableAssembly table) throws Exception {
      XDataSource ds = AssetUtil.getDataSource(table);
      return ds == null ? null : ds.getFullName();
   }
}
