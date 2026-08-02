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
package inetsoft.web.wiz.service;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.TitleDescriptor;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.web.wiz.model.DimensionFieldInfo;
import inetsoft.web.wiz.model.MeasureFieldInfo;
import inetsoft.web.wiz.model.SimpleFieldInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * Regression tests for {@link WizAutoBindingService#applyAxisTitle}.
 *
 * <p>The bug: a caller-supplied {@code fieldConfigs[].title} was accepted, forwarded to
 * {@code /viewsheet/autoBinding}, and applied to the chart ref as its CAPTION — and the rendered
 * axis still read the raw column name, because an axis title is not a ref caption. It comes from
 * the chart descriptor's {@code TitlesDescriptor}, which nothing on this path was writing, so
 * StyleBI fell back to the field's full name. Live on the openproject dataset, a bar created with
 * {@code title: "Project"} / {@code title: "Estimated Hours"} rendered its axes as {@code name} and
 * {@code Sum(total_estimated_hours)} — the setting taken without complaint and silently discarded.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizAutoBindingServiceAxisTitleTest {
   private static VSChartDimensionRef dimRef(String field) {
      VSChartDimensionRef ref = mock(VSChartDimensionRef.class);
      when(ref.getGroupColumnValue()).thenReturn(field);
      return ref;
   }

   private static VSChartAggregateRef aggRef(String field) {
      VSChartAggregateRef ref = mock(VSChartAggregateRef.class);
      when(ref.getColumnValue()).thenReturn(field);
      return ref;
   }

   private static SimpleFieldInfo dimFc(String field, String title) {
      DimensionFieldInfo fc = new DimensionFieldInfo();
      fc.setField(field);
      fc.setTitle(title);
      return fc;
   }

   private static SimpleFieldInfo measureFc(String field, String title) {
      MeasureFieldInfo fc = new MeasureFieldInfo();
      fc.setField(field);
      fc.setTitle(title);
      return fc;
   }

   @Test
   void titlesTheAxisFromTheBoundDimensionsFieldConfig() {
      TitleDescriptor td = mock(TitleDescriptor.class);
      ChartRef[] refs = { dimRef("name") };

      WizAutoBindingService.applyAxisTitle(td, refs, Map.of("name", dimFc("name", "Project")));

      verify(td).setTitleValue("Project");
   }

   @Test
   void titlesTheAxisFromTheBoundMeasuresFieldConfig() {
      TitleDescriptor td = mock(TitleDescriptor.class);
      ChartRef[] refs = { aggRef("total_estimated_hours") };

      WizAutoBindingService.applyAxisTitle(
         td, refs, Map.of("total_estimated_hours", measureFc("total_estimated_hours", "Estimated Hours")));

      verify(td).setTitleValue("Estimated Hours");
   }

   @Test
   void firstTitledFieldWinsWhenAnAxisCarriesSeveral() {
      // An axis has exactly one title but may carry several fields (a multi-measure Y). Rendering
      // two titles in one slot is impossible and concatenating them invents a label nobody asked
      // for, so the first titled field is taken and the rest ignored.
      TitleDescriptor td = mock(TitleDescriptor.class);
      ChartRef[] refs = { aggRef("revenue"), aggRef("cost") };

      WizAutoBindingService.applyAxisTitle(td, refs, Map.of(
         "revenue", measureFc("revenue", "Revenue"),
         "cost", measureFc("cost", "Cost")));

      verify(td).setTitleValue("Revenue");
      verify(td, never()).setTitleValue("Cost");
   }

   @Test
   void skipsUntitledFieldsRatherThanTitlingTheAxisEmpty() {
      // A partially-titled axis must still get the title that WAS supplied — an untitled leading
      // field may not blank the axis or short-circuit the search.
      TitleDescriptor td = mock(TitleDescriptor.class);
      ChartRef[] refs = { aggRef("revenue"), aggRef("cost") };

      WizAutoBindingService.applyAxisTitle(td, refs, Map.of(
         "revenue", measureFc("revenue", null),
         "cost", measureFc("cost", "Cost")));

      verify(td).setTitleValue("Cost");
   }

   @Test
   void leavesTheAxisUntouchedWhenNoBoundFieldCarriesATitle() {
      // No title supplied ⇒ StyleBI's own default (the field's full name) must stand. Writing an
      // empty value here would blank an axis the caller never asked to change.
      TitleDescriptor td = mock(TitleDescriptor.class);
      ChartRef[] refs = { dimRef("name") };

      WizAutoBindingService.applyAxisTitle(td, refs, Map.of("name", dimFc("name", null)));

      verify(td, never()).setTitleValue(anyString());
   }

   @Test
   void anEmptyTitleIsTreatedAsNoTitle() {
      TitleDescriptor td = mock(TitleDescriptor.class);
      ChartRef[] refs = { dimRef("name") };

      WizAutoBindingService.applyAxisTitle(td, refs, Map.of("name", dimFc("name", "")));

      verify(td, never()).setTitleValue(anyString());
   }

   @Test
   void ignoresARefWithNoMatchingFieldConfig() {
      TitleDescriptor td = mock(TitleDescriptor.class);
      ChartRef[] refs = { dimRef("unbound") };

      WizAutoBindingService.applyAxisTitle(td, refs, Map.of("name", dimFc("name", "Project")));

      verify(td, never()).setTitleValue(anyString());
   }

   @Test
   void isANoOpOnANullDescriptorOrNullRefs() {
      TitleDescriptor td = mock(TitleDescriptor.class);

      WizAutoBindingService.applyAxisTitle(null, new ChartRef[] { dimRef("name") },
                                           Map.of("name", dimFc("name", "Project")));
      WizAutoBindingService.applyAxisTitle(td, null, Map.of("name", dimFc("name", "Project")));

      verify(td, never()).setTitleValue(anyString());
   }

   @Test
   void skipsANullRefWithoutThrowing() {
      TitleDescriptor td = mock(TitleDescriptor.class);
      ChartRef[] refs = { null, dimRef("name") };

      WizAutoBindingService.applyAxisTitle(td, refs, Map.of("name", dimFc("name", "Project")));

      verify(td).setTitleValue("Project");
   }
}
