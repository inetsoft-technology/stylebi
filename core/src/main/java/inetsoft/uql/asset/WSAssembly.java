/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.asset;

import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.internal.VariableProvider;
import inetsoft.uql.asset.internal.WSAssemblyInfo;
import inetsoft.web.composer.model.ws.DependencyType;

import java.util.Map;
import java.util.Set;

/**
 * WSAssembly represents one assembly contained in a <tt>Worksheet</tt>, it should
 * be one of the prefined type namely <tt>WorkSheet.CONDITION_ASSET</tt>,
 * <tt>WorkSheet.NAMED_GROUP_ASSET</tt>, <tt>WorkSheet.VARIABLE_ASSET</tt> and
 * <tt>WorkSheet.TABLE_ASSET</tt>. In its <tt>Worksheet</tt>, it will be laid
 * out by its position and size and painted in the region.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public interface WSAssembly extends Assembly, VariableProvider {
   /**
    * Get the worksheet assembly info.
    * @return the worksheet assembly info.
    */
   WSAssemblyInfo getWSAssemblyInfo();

   /**
    * Check if is composed.
    * @return <tt>true</tt> if composed, <tt>false</tt> otherwise.
    */
   boolean isComposed();

   /**
    * Get the description.
    * @return the description of the assembly.
    */
   String getDescription();

   /**
    * Set the description.
    * @param desc the specified description.
    */
   void setDescription(String desc);

   /**
    * Check if the assembly is iconized.
    * @return <tt>true</tt> if iconized, <tt>false</tt> otherwise.
    */
   boolean isIconized();

   /**
    * Set iconized option.
    * @param iconized <tt>true</tt> indicated iconized.
    */
   void setIconized(boolean iconized);

   /**
    * Check if the assembly is outer.
    * @return <tt>true</tt> if outer, <tt>false</tt> otherwise.
    */
   boolean isOuter();

   /**
    * Set outer option.
    * @param outer <tt>true</tt> indicated outer.
    */
   void setOuter(boolean outer);

   /**
    * Check if the assembly is valid.
    */
   default void checkValidity() throws Exception {
      checkValidity(true);
   }

   /**
    * Check if the assembly is valid.
    *
    * @param checkCrossJoins {@code true} to check if an unintended cross join is present or
    *                        {@code false} to ignore cross joins.
    *
    * @throws Exception if the assembly is invalid.
    */
   void checkValidity(boolean checkCrossJoins) throws Exception;

   /**
    * Check if is a condition assembly.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   boolean isCondition();

   /**
    * Check if is a date condition assembly.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   boolean isDateCondition();

   /**
    * Check if is a named group assembly.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   boolean isNamedGroup();

   /**
    * Check if is a variable assembly.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   boolean isVariable();

   /**
    * Check if is a table assembly.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   boolean isTable();

   /**
    * Set the worksheet.
    * @param ws the specified worksheet.
    */
   void setWorksheet(Worksheet ws);

   /**
    * Get the worksheet.
    * @return the worksheet of the assembly.
    */
   Worksheet getWorksheet();

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   void replaceVariables(VariableTable vars);

   /**
    * Update the assembly.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   boolean update();

   /**
    * This is called when an assembly used by this assembly as changed.
    * @param depname the name of the changed assembly.
    */
   void dependencyChanged(String depname);

   /**
    * Copy the assembly.
    * @param name the specified new assembly name.
    * @return the copied assembly.
    */
   WSAssembly copyAssembly(String name);

   /**
    * Set the visible flag.
    * @param visible <tt>true</tt> if visible, <tt>false</tt> otherwise.
    */
   void setVisible(boolean visible);

   /**
    * Reset the assembly.
    */
   void reset();

   /**
    * Called when an assembly is created for copy and paste (called on the new assembly).
    */
   void pasted();

   void getAugmentedDependeds(Map<String, Set<DependencyType>> dependeds);
}
