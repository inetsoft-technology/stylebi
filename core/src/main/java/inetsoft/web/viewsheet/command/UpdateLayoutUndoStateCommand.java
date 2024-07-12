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
package inetsoft.web.viewsheet.command;

public class UpdateLayoutUndoStateCommand implements ViewsheetCommand {
   public int getLayoutPoints() {
      return layoutPoints;
   }

   public void setLayoutPoints(int layoutPoints) {
      this.layoutPoints = layoutPoints;
   }

   public int getLayoutPoint() {
      return layoutPoint;
   }

   public void setLayoutPoint(int layoutPoint) {
      this.layoutPoint = layoutPoint;
   }

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   private int layoutPoints;
   private int layoutPoint;
   private String id;
}
