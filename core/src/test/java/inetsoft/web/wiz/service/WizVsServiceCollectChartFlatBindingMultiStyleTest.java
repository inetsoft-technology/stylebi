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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import inetsoft.web.wiz.model.DimensionFieldInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end regression (through the public {@link WizVsService#collectFlatBinding}) for the
 * bug-75971 chain: a pie/donut chart built by {@code DonutChartFilter} sets
 * {@code VSChartInfo.setMultiStyles(true)} and binds the slice dimension via the Y measure's OWN
 * {@code setColorField} (a per-{@link VSChartAggregateRef} aesthetic), not the chart-info-level
 * {@code VSChartInfo.setColorField}. {@code collectChartFlatBinding} used to read only the
 * chart-info-level aesthetics, so that dimension was invisible to every caller that re-derives
 * fields from a pie/donut source (e.g. a wiz chart-type change re-slotting into a crosstab): the
 * rebuild silently lost its only dimension and produced a measures-only binding that pie/donut/
 * crosstab cannot render — the empty-panel bug reported for "change to crosstab, then to donut."
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServiceCollectChartFlatBindingMultiStyleTest {
   @Test
   void collectFlatBindingReadsTheDimensionFromAPerMeasureColorAestheticRef() {
      WizVsService service = new WizVsService(
         mock(ViewsheetService.class), mock(AssetRepository.class), mock(SecurityEngine.class), null, null);

      VSChartDimensionRef productName = new VSChartDimensionRef();
      productName.setGroupColumnValue("PRODUCT_NAME");

      VSAestheticRef colorField = new VSAestheticRef();
      colorField.setDataRef(productName);

      VSChartAggregateRef sumInStock = new VSChartAggregateRef();
      sumInStock.setColumnValue("NUMBER_INSTOCK");
      sumInStock.setFormulaValue("Sum");
      // The multi-style donut/pie pattern: the dimension rides on the MEASURE's own color field,
      // not VSChartInfo's — see DonutChartFilter.putInside/createChartInfo.
      sumInStock.setColorField(colorField);

      VSChartInfo info = new VSChartInfo();
      info.setMultiStyles(true);
      info.addYField(sumInStock);

      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(info);

      CreateViewsheetResult.FlatBinding binding = service.collectFlatBinding(assembly);

      List<DimensionFieldInfo> dimensions = binding.getDimensions();
      assertEquals(1, dimensions.size());
      assertEquals("PRODUCT_NAME", dimensions.get(0).getField());
   }

   /**
    * Regression for the fix itself: {@link VSChartInfo#getAggregateAestheticRefs} gates on
    * {@code isMultiAesthetic()}, so a stale per-aggregate {@code colorField} left over from a
    * chart that used to be multi-styles (see {@code ChangeSeparateStatusProcessor}, which clears
    * the aesthetic only on the first aggregate when "separate" is turned off) must NOT resurface
    * as a phantom dimension once {@code multiStyles} is false.
    */
   @Test
   void collectFlatBindingIgnoresAStaleAggregateColorFieldWhenNotMultiStyles() {
      WizVsService service = new WizVsService(
         mock(ViewsheetService.class), mock(AssetRepository.class), mock(SecurityEngine.class), null, null);

      VSChartDimensionRef productName = new VSChartDimensionRef();
      productName.setGroupColumnValue("PRODUCT_NAME");

      VSAestheticRef colorField = new VSAestheticRef();
      colorField.setDataRef(productName);

      VSChartAggregateRef sumInStock = new VSChartAggregateRef();
      sumInStock.setColumnValue("NUMBER_INSTOCK");
      sumInStock.setFormulaValue("Sum");
      // Stale leftover from a prior multi-styles binding -- setMultiStyles(false) below does not
      // clear it (ChangeSeparateStatusProcessor only clears the first aggregate).
      sumInStock.setColorField(colorField);

      VSChartInfo info = new VSChartInfo();
      info.setMultiStyles(false);
      info.addYField(sumInStock);

      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(info);

      CreateViewsheetResult.FlatBinding binding = service.collectFlatBinding(assembly);

      List<DimensionFieldInfo> dimensions = binding == null ? List.of() : binding.getDimensions();
      assertEquals(0, dimensions.size());
   }
}
