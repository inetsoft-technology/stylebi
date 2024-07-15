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
package inetsoft.mv;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.TableAssembly;

/**
 * MVExecutor, responsible for executing mv query and returning a result
 * in form of a table lens
 *
 * @version 12.2
 * @author InetSoft Technology Corp
 */
public abstract class AbstractMVExecutor implements MVExecutor {
   /**
    * Creates a new instance of <tt>AbstractMVExecutor</tt>.
    *
    * @param table  the bound table assembly.
    * @param mvName the view name.
    * @param vars   the query parameters.
    * @param user   a principal that identifies the user executing the query.
    */
   protected AbstractMVExecutor(TableAssembly table, String mvName, VariableTable vars,
                                XPrincipal user)
   {
      this.table = table;
      this.mvName = mvName;
      this.vars = vars;
      this.user = user;
   }

   protected TableAssembly table;
   protected String mvName;
   protected VariableTable vars;
   protected XPrincipal user;
}
