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
package inetsoft.uql.viewsheet;

/**
 * BindableVSAssembly represents one viewsheet assembly which may be bound to a
 * <tt>TableAssembly</tt> in the base <tt>Worksheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public interface BindableVSAssembly extends VSAssembly {
   /**
    * Get the target table.
    * @return the target table.
    */
   @Override
   public String getTableName();

   /**
    * Set the name of the target table.
    * @param table the specified name of the target table.
    */
   public void setTableName(String table);

   /**
    * Remove the binding ref from the assembly.
    */
   public void removeBindingCol(String ref);

   /**
    * Rename the binding ref from the assembly.
    */
   public void renameBindingCol(String oldname, String refname);

   /**
    * Change calc type: detail and aggregate.
    */
   public void changeCalcType(String refName, CalculateRef ref);
}
