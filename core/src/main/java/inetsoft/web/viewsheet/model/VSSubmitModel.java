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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.SubmitVSAssembly;
import inetsoft.uql.viewsheet.internal.SubmitVSAssemblyInfo;
import org.springframework.stereotype.Component;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSSubmitModel extends VSOutputModel<SubmitVSAssembly> {
   public VSSubmitModel(SubmitVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      SubmitVSAssemblyInfo assemblyInfo =
         (SubmitVSAssemblyInfo) assembly.getVSAssemblyInfo();
      label = assemblyInfo.getLabelName();
      refresh = assemblyInfo.isRefresh();
   }

   public boolean isRefresh() {
      return refresh;
   }

   public String getLabel() {
      return label;
   }

   private String label;
   private boolean refresh;

   @Component
   public static final class VSSubmitModelFactory
      extends VSObjectModelFactory<SubmitVSAssembly, VSSubmitModel>
   {
      public VSSubmitModelFactory() {
         super(SubmitVSAssembly.class);
      }

      @Override
      public VSSubmitModel createModel(SubmitVSAssembly assembly, RuntimeViewsheet rvs) {
         return new VSSubmitModel(assembly, rvs);
      }
   }
}
