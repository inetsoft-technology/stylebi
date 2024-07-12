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
package inetsoft.web.composer.ws.assembly.tableassembly;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.SQLBoundTableAssembly;
import inetsoft.web.composer.ws.assembly.TableAssemblyModel;

public class SQLBoundTableAssemblyModel extends TableAssemblyModel {
   public SQLBoundTableAssemblyModel(SQLBoundTableAssembly assembly, RuntimeWorksheet rws, boolean sqlEnabled) {
      super(assembly, rws);
      sqlEdited = assembly.isSQLEdited();
      this.sqlEnabled = sqlEnabled;
   }

   public boolean isSqlEdited() {
      return sqlEdited;
   }

   public void setSqlEdited(boolean sqlEdited) {
      this.sqlEdited = sqlEdited;
   }

   public boolean isSqlEnabled() {
      return sqlEnabled;
   }

   public void setSqlEnabled(boolean sqlEnabled) {
      this.sqlEnabled = sqlEnabled;
   }

   private boolean sqlEdited;
   private boolean sqlEnabled;
}
