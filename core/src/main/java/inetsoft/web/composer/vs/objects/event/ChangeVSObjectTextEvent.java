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
package inetsoft.web.composer.vs.objects.event;

/**
 * Class that encapsulates the parameters for changing the text used in an object.
 *
 * @since 12.3
 */
public class ChangeVSObjectTextEvent extends VSObjectEvent {
   /**
    * Gets the text of the object.
    *
    * @return the text of the object.
    */
   public String getText() {
      return text;
   }

   /**
    * Sets the text of the object.
    *
    * @param text the text of the object.
    */
   public void setText(String text) {
      this.text = text;
   }

   @Override
   public String toString() {
      return "ChangeVSObjectTextEvent{" +
         "name='" + this.getName() + '\'' +
         ", text=" + text +
         '}';
   }

   private String text;
}
