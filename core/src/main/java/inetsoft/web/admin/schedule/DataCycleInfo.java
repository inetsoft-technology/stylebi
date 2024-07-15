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
package inetsoft.web.admin.schedule;

import java.io.Serializable;

/**
 * The DataCycleInfo defines dataCycle condition.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public class DataCycleInfo implements Serializable {
   public DataCycleInfo() {
   }

   public DataCycleInfo(String name) {
      this.name = name;
   }

   /**
    * Get conditions.
    * @return conditions from the datacycle info.
    */
   public String[] getConditions() {
      return this.conditions;
   }

   /**
    * Set conditions.
    * @param conditions the data cycle's conditions array.
    */
   public void setConditions(String[] conditions) {
      this.conditions = conditions;
   }

   /**
    * Get cycle name.
    * @return data cycle name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set cycle name.
    * @param name the data cycle name.
    */
   public void setName(String name) {
      this.name = name;
   }

   private String name;
   private String[] conditions;
}
