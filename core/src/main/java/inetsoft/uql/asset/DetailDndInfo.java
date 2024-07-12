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

/**
 * Sort info contains the sort information of a <tt>TableAssembly</tt>.
 *
 * @version 13.3
 * @author InetSoft Technology Corp
 */
public class DetailDndInfo {
   /**
    * Constructor.
    */
   public DetailDndInfo() {
   }

   public void setDragIndexes(int[] drag) {
      this.dragIndexes = drag;
   }

   public void setDropIndex(int drop) {
      this.dropIndex = drop;
   }

   public int[] getDragIndexes() {
      return this.dragIndexes;
   }

   public int getDropIndex() {
      return this.dropIndex;
   }

   private int[] dragIndexes;
   private int dropIndex;
}
