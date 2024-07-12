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
package inetsoft.web.vswizard.command;

public class SetWizardGridCommand implements TimeSensitiveCommand {
   public int getGridRowCount() {
      return this.gridRowCount;
   }

   public void setGridRowCount(int gridRowCount) {
      this.gridRowCount = gridRowCount;
   }

   public int getGridColCount() {
      return gridColCount;
   }

   public void setGridColCount(int gridColCount) {
      this.gridColCount = gridColCount;
   }

   private int gridRowCount;
   private int gridColCount;
}
