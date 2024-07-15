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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.UploadVSAssembly;
import inetsoft.uql.viewsheet.internal.UploadVSAssemblyInfo;
import org.springframework.stereotype.Component;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSUploadModel extends VSObjectModel<UploadVSAssembly> {
   public VSUploadModel(UploadVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      UploadVSAssemblyInfo assemblyInfo =
         (UploadVSAssemblyInfo) assembly.getVSAssemblyInfo();
      label = assemblyInfo.getLabelName();
      fileName = assemblyInfo.getFileName();
      refresh = assemblyInfo.isSubmitOnChange();
      submitOnChange = assemblyInfo.isSubmitOnChange();
   }

   public boolean isRefresh() {
      return refresh;
   }

   public String getLabel() {
      return label;
   }

   public String getFileName() {
      return fileName;
   }

   public boolean isSubmitOnChange() {
      return submitOnChange;
   }

   private String label;
   private String fileName;
   private boolean refresh;
   private boolean submitOnChange;

   @Component
   public static final class VSUploadModelFactory
      extends VSObjectModelFactory<UploadVSAssembly, VSUploadModel>
   {
      public VSUploadModelFactory() {
         super(UploadVSAssembly.class);
      }

      @Override
      public VSUploadModel createModel(UploadVSAssembly assembly, RuntimeViewsheet rvs) {
         return new VSUploadModel(assembly, rvs);
      }
   }
}
