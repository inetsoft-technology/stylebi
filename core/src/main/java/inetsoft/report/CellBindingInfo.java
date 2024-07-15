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
package inetsoft.report;

import inetsoft.report.internal.binding.Field;

import java.awt.*;

/**
 * CellBindingInfo, infomations for cell binding.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public interface CellBindingInfo {
   /**
    * Check if the cell binding is virtual.
    */
   public boolean isVirtual();

   /**
    * Get the value for the cell binding.
    */
   public String getValue();

   /**
    * Set the value for the cell binding.
    */
   public void setValue(String value);

   /**
    * Get the binding data type for the cell.
    */
   public int getType();

   /**
    * Get the binding cell type for the cell, group, summary or detail.
    */
   public int getBType();

   /**
    * Set the binding cell type for the cell, group, summary or detail.
    */
   public void setBType(int btype);

   /**
    * Get the cell binding field.
    */
   public Field getField();

   /**
    * Set the cell binding field.
    */
   public void setField(Field cfield);

   /**
    * Get expansion.
    */
   public int getExpansion();

   /**
    * Set expansion.
    */
   public void setExpansion(int expansion);

   /**
    * Get the band type.
    */
   public int getBandType();

   /**
    * Get the band level for the cell.
    */
   public int getBandLevel();

   /**
    * Get the position for the cell in the band.
    */
   public Point getPosition();

   /**
    * Set value as group.
    */
   public void setAsGroup(boolean asGroup);
}