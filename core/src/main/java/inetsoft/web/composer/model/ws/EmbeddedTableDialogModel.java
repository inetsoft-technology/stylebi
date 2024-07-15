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
package inetsoft.web.composer.model.ws;

public class EmbeddedTableDialogModel {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public int getRows() {
      return rows;
   }

   public void setRows(int rows) {
      this.rows = rows;
   }

   public int getCols() {
      return cols;
   }

   public void setCols(int cols) {
      this.cols = cols;
   }

   private String name;
   private int rows;
   private int cols;
}
