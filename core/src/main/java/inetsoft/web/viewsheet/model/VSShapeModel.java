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
package inetsoft.web.viewsheet.model;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.ShapeVSAssembly;
import inetsoft.uql.viewsheet.internal.ShapeVSAssemblyInfo;

public abstract class VSShapeModel<T extends ShapeVSAssembly> extends VSObjectModel<T> {
   protected VSShapeModel(T assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      ShapeVSAssemblyInfo assemblyInfo =
         (ShapeVSAssemblyInfo) assembly.getVSAssemblyInfo();
      locked = assemblyInfo.getLocked();
      lineStyle = assemblyInfo.getLineStyle();
      shadow = assemblyInfo.isShadow();
   }

   public boolean isLocked() {
      return locked;
   }

   public int getLineStyle() {
      return lineStyle;
   }

   public boolean isShadow() {
      return shadow;
   }

   private boolean locked;
   private int lineStyle;
   private boolean shadow;
}
