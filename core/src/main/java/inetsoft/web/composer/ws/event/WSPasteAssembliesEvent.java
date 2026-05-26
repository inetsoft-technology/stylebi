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
package inetsoft.web.composer.ws.event;

import java.io.Serializable;

public class WSPasteAssembliesEvent implements Serializable {
   public String[] getAssemblies() {
      return assemblies;
   }

   public void setAssemblies(String[] assemblies) {
      this.assemblies = assemblies;
   }

   public int[] getColumnIndices() {
      return columns;
   }

   public void setColumnIndices(int[] cols) {
      this.columns = cols;
   }

   public String getSourceRuntimeId() {
      return sourceRuntimeId;
   }

   public void setSourceRuntimeId(String sourceRuntimeId) {
      this.sourceRuntimeId = sourceRuntimeId;
   }

   public int getTop() {
      return top;
   }

   public void setTop(int top) {
      this.top = top;
   }

   public int getLeft() {
      return left;
   }

   public void setLeft(int left) {
      this.left = left;
   }

   public boolean getCut() {
      return cut;
   }

   public void setCut(boolean cut) {
      this.cut = cut;
   }

   public boolean isDragColumn() {
      return isCol;
   }

   public void setDragColumn(boolean isCol) {
      this.isCol = isCol;
   }

   private String[] assemblies;
   private int[] columns;
   private String sourceRuntimeId;
   private int top;
   private int left;
   private boolean cut;
   private boolean isCol;
}
