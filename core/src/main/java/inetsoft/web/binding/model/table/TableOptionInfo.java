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
package inetsoft.web.binding.model.table;

public class TableOptionInfo extends BindingOptionInfo {
   /**
    * Constructor.
    */
   public TableOptionInfo() {
   }

   /**
    * Get grand total.
    * @return true if is grand total.
    */
   public boolean getGrandTotal() {
      return grandTotal;
   }

   /**
    * Set grand total.
    * @param grand is grand total or not.
    */
   public void setGrandTotal(boolean grand) {
      this.grandTotal = grand;
   }

   /**
    * Get distinct.
    * @return true if distinct.
    */
   public boolean getDistinct() {
      return distinct;
   }

   /**
    * Set distinct.
    * @param distinct is distinct.
    */
   public void setDistinct(boolean distinct) {
      this.distinct = distinct;
   }

   private boolean grandTotal;
   private boolean distinct;
}
