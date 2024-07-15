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
import inetsoft.uql.viewsheet.InputVSAssembly;
import inetsoft.uql.viewsheet.internal.ClickableInputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.InputVSAssemblyInfo;

public abstract class VSInputModel<T extends InputVSAssembly> extends VSObjectModel<T> {
   protected VSInputModel(T assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);

      InputVSAssemblyInfo assemblyInfo = (InputVSAssemblyInfo) assembly.getVSAssemblyInfo();
      setWriteBackDirectly(assemblyInfo.getWriteBackValue());
      refresh = assemblyInfo.isSubmitOnChange();

      String onClick = assemblyInfo instanceof ClickableInputVSAssemblyInfo
         ? ((ClickableInputVSAssemblyInfo) assemblyInfo).getOnClick() : null;
      hasOnClick = onClick != null && !onClick.isEmpty();

      if(assemblyInfo instanceof InputVSAssemblyInfo) {
         setWriteBackDirectly(assemblyInfo.getWriteBackValue());
      }
   }

   public boolean isRefresh() {
      return refresh;
   }

   public void setRefresh(boolean refresh) {
      this.refresh = refresh;
   }

   public void setHasOnClick(boolean hasOnClick) {
      this.hasOnClick = hasOnClick;
   }

   public boolean isHasOnClick() {
      return this.hasOnClick;
   }

   public boolean isWriteBackDirectly() {
      return writeBackDirectly;
   }

   public void setWriteBackDirectly(boolean writeBackDirectly) {
      this.writeBackDirectly = writeBackDirectly;
   }

   private boolean refresh;
   private boolean hasOnClick;
   private boolean writeBackDirectly;
}
