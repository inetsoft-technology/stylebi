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
package inetsoft.web.composer.vs.objects.event;

/**
 * Class that encapsulates the parameters for changing the value used in an object.
 *
 * @since 12.3
 */
public class ChangeVSObjectValueEvent extends VSObjectEvent {
   /**
    * Gets the value of the object.
    *
    * @return the value of the object.
    */
   public Number getValue() {
      return value;
   }

   /**
    * Sets the value of the object.
    *
    * @param value the value of the object.
    */
   public void setValue(Number value) {
      this.value = value;
   }

   @Override
   public String toString() {
      return "ChangeVSObjectValueEvent{" +
         "name='" + this.getName() + '\'' +
         ", value=" + value +
         '}';
   }

   private Number value;
}
