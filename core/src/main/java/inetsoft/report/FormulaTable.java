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
package inetsoft.report;

import inetsoft.uql.util.XSourceInfo;
import inetsoft.util.script.ScriptEnv;

public interface FormulaTable {
  /**
    * Get the table layout of the table.
    */
   public TableLayout getTableLayout();

   /**
    * Set the table layout of the table.
    */
   public void setTableLayout(TableLayout layout);

   /**
    * Get the base table lens.
    */
   public TableLens getBaseTable();

   /**
    * Set the calc table lens.
    */
   public void setTable(TableLens table);

   /**
    * Get the source attr.
    */
   public XSourceInfo getXSourceInfo();

   /**
    * Get the id of this element.
    */
   public String getID();

   /**
    * Get the script environment.
    * @return the script enrironment.
    */
   public ScriptEnv getScriptEnv();

   /**
    * Get script base table.
    */
   public TableLens getScriptTable();
}