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
package inetsoft.uql.asset;

import inetsoft.uql.asset.internal.AssemblyInfo;

import java.awt.*;
import java.util.Set;

/**
 * Assembly represents one assembly contained in a sheet. In the sheet, it will
 * be laid out by its position and size and painted in the region.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public interface Assembly extends AssetObject {
   /**
    * Field mark.
    */
   String FIELD = "field";
   /**
    * Selection prefix.
    */
   String SELECTION = "S_";
   /**
    * Concatenated table prefix for selection union.
    */
   String CONCATENATED_SELECTION ="C_";
   /**
    * Table prefix for a subtable of a concatenated table for a selection union.
    */
   String CONCATENATED_SELECTION_SUBTABLE = "CS_";
   /**
    * Detail prefix.
    */
   String DETAIL = "D_";
   /**
    * Table assembly for viewsheet prefix.
    */
   String TABLE_VS = "V_";
   /**
    * Cube table assembly for viewsheet prefix.
    */
   String CUBE_VS = "___inetsoft_cube_";
   /**
    * Prefix for a table that is bound to a vs table
    */
   String TABLE_VS_BOUND = "__vs_assembly__";

   /**
    * Get the name.
    * @return the name of the assembly.
    */
   String getName();

   /**
    * Get the absolute name of this assembly.
    * @return the absolute name of this assembly.
    */
   String getAbsoluteName();

   /**
    * Get the assembly entry.
    * @return the assembly entry.
    */
   AssemblyEntry getAssemblyEntry();

   /**
    * Get the assembly info.
    * @return the associated assembly info.
    */
   AssemblyInfo getInfo();

   /**
    * Get the assemblies depended on by this table. For example, if an expression in this
    * table uses another table A, A is added to the set.
    * @param set the set stores the assemblies depended on.
    */
   void getDependeds(Set<AssemblyRef> set);

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   void renameDepended(String oname, String nname);

   /**
    * Check if the dependency is valid.
    */
   void checkDependency() throws InvalidDependencyException;

   /**
    * Get the position.
    * @return the position of the assembly.
    */
   Point getPixelOffset();

   /**
    * Set the position.
    */
   void setPixelOffset(Point pos);

   /**
    * Get the size.
    * @return the size of the assembly.
    */
   Dimension getPixelSize();

   /**
    * Set the size.
    * @param size the specified size.
    */
   void setPixelSize(Dimension size);

   /**
    * Set the bounds.
    * @param bounds the specified bounds.
    */
   void setBounds(Rectangle bounds);

   /**
    * Get the bounds.
    * @return the bounds of the assembly.
    */
   Rectangle getBounds();

   /**
    * Get the minimum size.
    * @return the minimum size of the assembly.
    */
   Dimension getMinimumSize();

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   int getAssemblyType();

   /**
    * Check if is visible.
    * @return <tt>true</tt> if visible, <tt>false</tt> otherwise.
    */
   boolean isVisible();

   /**
    * Check if is editable.
    * @return <tt>true</tt> if editable, <tt>false</tt> otherwise.
    */
   boolean isEditable();

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   Object clone();

   /**
    * Get the original hash code.
    * @return the original hash code.
    */
   int addr();

   /**
    * Get the sheet container.
    * @return the sheet container.
    */
   AbstractSheet getSheet();

   default boolean isUndoable() {
      return true;
   }
}
