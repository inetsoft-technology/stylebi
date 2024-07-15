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
package inetsoft.report.internal;

import inetsoft.report.TableLens;

/**
 * This is the base class of all binable element classes. 
 * 
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface NonScalar extends BindableElement {
   /**
    * Reset cached tables.
    */
   public void resetFilter();

   /**
    * Set the top level table lens.
    */
   public void setTable(TableLens table);

   /**
    * Get the top level table.
    */
   public TableLens getTable();
   
   /**
    * Get the top table without processing.
    */
   public TableLens getTopTable();
   
   /**
    * Get the base table lens.
    */
   public TableLens getBaseTable();
}

