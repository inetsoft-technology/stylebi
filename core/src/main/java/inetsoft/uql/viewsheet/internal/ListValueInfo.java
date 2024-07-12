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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.viewsheet.*;

/**
 * ListValueInfo, the assembly info of a list bindable assembly.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public interface ListValueInfo {
   /**
    * Get the list data.
    * @return the list data of this assembly info.
    */
   public ListData getListData();

   /**
    * Set the list data to this assembly info.
    * @param data the specified list data.
    */
   public void setListData(ListData data);

   /**
    * Set the source type to this assembly info.
    * @param stype the specified source type.
    */
   public void setSourceType(int style);

   /**
    * Get the source type to this assembly info.
    * @return the type of the data source.
    */
   public int getSourceType();

   /**
    * Get the binding info.
    * @return the binding info of this assembly info.
    */
   public BindingInfo getBindingInfo();

   /**
    * Get the list binding info.
    * @return the list binding info of this assembly info.
    */
   public ListBindingInfo getListBindingInfo();

   /**
    * Get the target data type.
    * @return the target data type of this assembly info.
    */
   public String getDataType();

   /**
    * Set the data type to this assembly info.
    * @param dtype the specified data type.
    */
   public void setDataType(String dtype);
}
