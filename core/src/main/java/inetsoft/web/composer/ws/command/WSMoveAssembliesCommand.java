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
package inetsoft.web.composer.ws.command;

import inetsoft.web.viewsheet.command.ViewsheetCommand;

public class WSMoveAssembliesCommand implements ViewsheetCommand {
   public String[] getAssemblyNames() {
      return assemblyNames;
   }

   public void setAssemblyNames(String[] assemblyNames) {
      this.assemblyNames = assemblyNames;
   }

   public double[] getTops() {
      return tops;
   }

   public void setTops(double[] tops) {
      this.tops = tops;
   }

   public double[] getLefts() {
      return lefts;
   }

   public void setLefts(double[] lefts) {
      this.lefts = lefts;
   }

   private String[] assemblyNames;
   private double[] tops;
   private double[] lefts;
}
