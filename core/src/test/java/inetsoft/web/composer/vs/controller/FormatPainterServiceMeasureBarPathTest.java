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
package inetsoft.web.composer.vs.controller;

import inetsoft.report.TableDataPath;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.analytic.composition.ViewsheetService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Bug #80443 sub-gap 2 refutation finding: {@code isMeasureTextBar} recognized
 * {@code "Measure Text"}/{@code "Measure Bar"} but not {@code "Measure Bar(-)"} (the path element
 * {@link inetsoft.uql.viewsheet.internal.SelectionBaseVSAssemblyInfo#getMeasureNBarPath} uses),
 * which would have let an ID-mode Selection Tree's {@code measureBarNegative} write be silently
 * rewritten to the shared item-label path instead of the negative-bar path
 * (see {@code FormatPainterService.setFormat}'s {@code isIDSelectTree} branch).
 *
 * <p>A plain {@code mock()}-built instance, not {@code @ExtendWith(MockitoExtension.class)}:
 * {@code isMeasureTextBar} is a pure function of its {@code TableDataPath} argument and touches
 * none of the constructor's collaborators, so stubbing any of them here would be flagged as
 * unnecessary by Mockito's strict stubs.
 */
@Tag("core")
class FormatPainterServiceMeasureBarPathTest {
   @Test
   void isMeasureTextBarRecognizesTheNegativeMeasureBarPath() throws Exception {
      FormatPainterService service = new FormatPainterService(
         mock(CoreLifecycleService.class), mock(ChartRegionHandler.class),
         mock(ViewsheetService.class), mock(VSObjectModelFactoryService.class),
         mock(VSBindingService.class), mock(VSLayoutService.class));

      Method method =
         FormatPainterService.class.getDeclaredMethod("isMeasureTextBar", TableDataPath.class);
      method.setAccessible(true);

      TableDataPath negativeBar = new TableDataPath(
         0, TableDataPath.DETAIL, XSchema.STRING, new String[]{ "Measure Bar(-)" }, false, false);
      TableDataPath positiveBar = new TableDataPath(
         0, TableDataPath.DETAIL, XSchema.STRING, new String[]{ "Measure Bar" }, false, false);
      TableDataPath measureText = new TableDataPath(
         0, TableDataPath.DETAIL, XSchema.STRING, new String[]{ "Measure Text" }, false, false);
      TableDataPath unrelated = new TableDataPath(
         0, TableDataPath.DETAIL, XSchema.STRING, new String[]{ "Some Item" }, false, false);

      assertEquals(Boolean.TRUE, method.invoke(service, negativeBar));
      assertEquals(Boolean.TRUE, method.invoke(service, positiveBar));
      assertEquals(Boolean.TRUE, method.invoke(service, measureText));
      assertEquals(Boolean.FALSE, method.invoke(service, unrelated));
   }
}
