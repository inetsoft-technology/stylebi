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
package inetsoft.web.composer.vs.objects.event;

public class UploadCompleteEvent extends VSObjectEvent{

   /**
    * Gets the fileName of the object.
    *
    * @return the fileName of the object.
    */
   public String getFileName() {
      return fileName;
   }

   /**
    * Sets the fileName of the object.
    *
    * @param fileName the value of the object.
    */
   public void setFileName(String fileName) {
      this.fileName = fileName;
   }

   @Override
   public String toString() {
      return "UploadCompleteEvent{" +
         "name='" + this.getName() + '\'' +
         ", fileName=" + fileName +
         '}';
   }

   private String fileName;

}