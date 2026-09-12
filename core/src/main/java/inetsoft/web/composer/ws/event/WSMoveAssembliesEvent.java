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

public class WSMoveAssembliesEvent implements Serializable {
   public String[] getAssemblyNames() {
      return assemblyNames;
   }

   public void setAssemblyNames(String[] assemblyNames) {
      this.assemblyNames = assemblyNames;
   }

   public int getOffsetTop() {
      return offsetTop;
   }

   public void setOffsetTop(int offsetTop) {
      this.offsetTop = offsetTop;
   }

   public int getOffsetLeft() {
      return offsetLeft;
   }

   public void setOffsetLeft(int offsetLeft) {
      this.offsetLeft = offsetLeft;
   }

   private String[] assemblyNames;
   private int offsetTop;
   private int offsetLeft;
}
