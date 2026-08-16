/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.binding;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.AssemblyBinding;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("core")
class BindingReadServiceTest {
   @Test
   void readsAChartsShelvesIntoTheSharedVocabulary() {
      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(new ChartBindingModel());

      AssemblyBinding result = new BindingReadService(binding)
         .read(runtimeWith("Chart1", mock(ChartVSAssembly.class)), "Chart1");

      assertEquals("Chart1", result.assembly());
      assertTrue(result.shelves().containsKey("x"), "a chart exposes an x shelf");
      assertTrue(result.shelves().containsKey("y"));
      assertTrue(result.shelves().containsKey("group"));
   }

   @Test
   void refusesACalcTablePointingAtTheLayoutTool() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> new BindingReadService(mock(VSBindingService.class))
            .read(runtimeWith("Calc1", mock(CalcTableVSAssembly.class)), "Calc1"));

      assertTrue(thrown.getMessage().contains("get_calc_layout"),
                 "must point at the calc-table tool, got: " + thrown.getMessage());
   }

   @Test
   void namesTheUnknownAssemblyRatherThanReturningAnEmptyBinding() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> new BindingReadService(mock(VSBindingService.class))
            .read(runtimeWith("Ghost", null), "Ghost"));

      assertTrue(thrown.getMessage().contains("Ghost"));
   }

   private static RuntimeViewsheet runtimeWith(String name, VSAssembly assembly) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(name)).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }
}
