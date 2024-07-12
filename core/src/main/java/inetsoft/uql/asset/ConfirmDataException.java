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

import inetsoft.util.Tool;

/**
 * Confirm data exception, the exception may be confirmed.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class ConfirmDataException extends ConfirmException {
   /**
    * Constructor.
    */
   public ConfirmDataException() {
      super();
   }

   /**
    * Constructor.
    */
   public ConfirmDataException(String message) {
      super(message);
   }

   /**
    * Set the assembly name to set time limit in viewsheet sandbox.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the assembly name.
    * @return an number.
    */
   public String getName() {
      return name;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, </tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof ConfirmDataException)) {
         return false;
      }

      ConfirmDataException exc = (ConfirmDataException) obj;

      if(!Tool.equals(getName(), exc.getName())) {
         return false;
      }

      return true;
   }

   private String name;
}
