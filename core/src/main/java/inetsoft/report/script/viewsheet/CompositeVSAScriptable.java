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
package inetsoft.report.script.viewsheet;

import org.mozilla.javascript.Scriptable;

/**
 * The composite viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public interface CompositeVSAScriptable extends Scriptable {
   /**
    * The null object indicates that the scriptable has no cell value. So that
    * we could distinguish no cell value from null cel value.
    */
   public static final Object NULL = "__null__";

   /**
    * Get the cell value.
    * @return the cell value, <tt>NULL</tt> no cell value.
    */
   public Object getCellValue();

   /**
    * Set the cell value.
    * @param val the specified cell value, <tt>NULL</tt> clear cell value.
    */
   public void setCellValue(Object val);
}
