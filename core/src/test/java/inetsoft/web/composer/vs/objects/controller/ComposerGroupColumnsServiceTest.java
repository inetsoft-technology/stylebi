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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.binding.ExpertNamedGroupInfo;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.util.MessageException;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.objects.event.GroupFieldsEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@code group()} ("right-click a column -> Group columns") hand-picks specific literal cell
 * values into ad-hoc groups -- an operation with no sensible generalization to an Expert or
 * Asset-typed named group's own conditions. Before this fix, a dimension bound to one crashed
 * with an unexplained {@link ClassCastException} the moment this dialog touched it (four
 * unconditional {@code SNamedGroupInfo} casts); now it is refused loudly instead.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ComposerGroupColumnsServiceTest {
   private static ChartVSAssembly chartWithExpertGroupedXField(Viewsheet vs) {
      VSChartDimensionRef ref = new VSChartDimensionRef();
      ref.setDataRef(new ColumnRef(new AttributeRef("REGION")));
      ref.setDataType(XSchema.STRING);
      ExpertNamedGroupInfo info = new ExpertNamedGroupInfo();
      info.setGroupCondition("West", new inetsoft.uql.ConditionList());
      ref.setNamedGroupInfo(info);

      VSChartInfo chartInfo = new VSChartInfo();
      chartInfo.addXField(ref);

      ChartVSAssembly assembly = new ChartVSAssembly(vs, "Chart1");
      assembly.setVSChartInfo(chartInfo);
      vs.addAssembly(assembly);
      return assembly;
   }

   private static GroupFieldsEvent groupEvent() {
      GroupFieldsEvent event = new GroupFieldsEvent();
      event.setName("Chart1");
      event.setColumnName("REGION");
      event.setGroupName("NewGroup");
      event.setLabels(new String[]{ "West" });
      return event;
   }

   @Test
   void refusesGroupingAColumnBoundToAnExpertNamedGroup() throws Exception {
      Viewsheet vs = new Viewsheet();
      chartWithExpertGroupedXField(vs);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);

      ComposerGroupColumnsService service = new ComposerGroupColumnsService(
         viewsheetService, mock(VSBindingService.class), mock(VSObjectPropertyService.class));

      Exception thrown = assertThrows(MessageException.class,
         () -> service.group("runtime1", groupEvent(), "/", null, null));
      assertNotNull(thrown.getMessage());
   }
}
