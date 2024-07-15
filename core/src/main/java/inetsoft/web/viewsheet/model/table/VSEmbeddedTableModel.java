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
package inetsoft.web.viewsheet.model.table;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.EmbeddedTableVSAssembly;
import inetsoft.web.viewsheet.model.VSObjectModelFactory;
import org.springframework.stereotype.Component;

public class VSEmbeddedTableModel extends SimpleTableModel<EmbeddedTableVSAssembly> {
   public VSEmbeddedTableModel(EmbeddedTableVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
   }

   @Component
   public static final class VSEmbeddedTableModelFactory
      extends VSObjectModelFactory<EmbeddedTableVSAssembly, VSEmbeddedTableModel>
   {
      public VSEmbeddedTableModelFactory() {
         super(EmbeddedTableVSAssembly.class);
      }

      @Override
      public VSEmbeddedTableModel createModel(EmbeddedTableVSAssembly assembly, RuntimeViewsheet rvs) {
         try {
            return new VSEmbeddedTableModel(assembly, rvs);
         }
         catch(RuntimeException e) {
            throw e;
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to get runtime viewsheet instance", e);
         }
      }
   }
}
