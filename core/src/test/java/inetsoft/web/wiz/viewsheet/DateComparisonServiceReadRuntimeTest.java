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

package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.composer.vs.dialog.DateComparisonDialogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The runtime overload of DateComparisonService.read serves the wiz mutation endpoints, which hold
 * the runtime already and have no pairing session (turn-provenance spec 3.3.3).
 */
@Tag("core")
class DateComparisonServiceReadRuntimeTest {
   private ViewsheetSessionService sessions;
   private DateComparisonDialogService dialog;
   private DateComparisonService service;
   private RuntimeViewsheet rvs;

   @BeforeEach
   void setUp() {
      sessions = mock(ViewsheetSessionService.class);
      dialog = mock(DateComparisonDialogService.class);
      service = new DateComparisonService(sessions, dialog);

      VSAssembly chart = mock(VSAssembly.class);
      when(chart.getVSAssemblyInfo()).thenReturn(mock(ChartVSAssemblyInfo.class));
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Chart1")).thenReturn(chart);
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt-1");
   }

   @Test
   void runtimeOverloadReadsWithoutResolvingASession() throws Exception {
      Map<String, Object> out = service.read(rvs, "Chart1", null);

      assertEquals("Chart1", out.get("assembly"));
      assertEquals(false, out.get("enabled"));
      verify(dialog).getDateComparison("rt-1", "Chart1", null);
      verifyNoInteractions(sessions);
   }

   @Test
   void tokenReadDelegatesToTheRuntimeOverload() throws Exception {
      when(sessions.resolve("tok", null)).thenReturn(rvs);

      assertEquals(service.read(rvs, "Chart1", null), service.read("tok", null, "Chart1"));
   }

   @Test
   void runtimeOverloadRefusesAnUnknownAssembly() {
      assertThrows(IllegalArgumentException.class, () -> service.read(rvs, "Nope", null));
   }
}
