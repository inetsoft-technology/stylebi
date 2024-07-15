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
package inetsoft.report.script.formula;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuery;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.XTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is the javascript object for table assembly table data.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class CubeTableAssemblyScriptable extends TableAssemblyScriptable {
   public CubeTableAssemblyScriptable(String tname, AssetQuerySandbox box,
                                  int mode, TableLens lens) {
      super(tname, box, mode);
      this.lens = lens;
   }

   @Override
   public XTable getTable() {
      // don't cache table data to keep in sync with AssetQuerySandbox.
      try {
         TableLens table = lens;
         table = AssetQuery.shuckOffFormat(table);
         return table;
      }
      catch(Exception ex) {
         // ignore if box has been disposed
         if(box.getWorksheet() != null) {
            LOG.warn("Failed to get table", ex);
         }
         
         return null;
      }
   }
   
   private TableLens lens;
   private static final Logger LOG =
      LoggerFactory.getLogger(TableAssemblyScriptable.class);
}
