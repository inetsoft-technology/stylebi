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
import inetsoft.uql.viewsheet.PageBreakVSAssembly;
import org.springframework.stereotype.Component;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSPageBreakModel extends VSObjectModel<PageBreakVSAssembly> {
   public VSPageBreakModel(PageBreakVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
   }

   @Component
   public static final class VSPageBreakModelFactory
      extends VSObjectModelFactory<PageBreakVSAssembly, VSPageBreakModel> {
      public VSPageBreakModelFactory() {
         super(PageBreakVSAssembly.class);
      }

      @Override
      public VSPageBreakModel createModel(PageBreakVSAssembly assembly, RuntimeViewsheet rvs) {
         return new VSPageBreakModel(assembly, rvs);
      }
   }
}
