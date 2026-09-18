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

import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.AestheticRef;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import inetsoft.web.wiz.model.DimensionFieldInfo;
import inetsoft.web.wiz.model.MeasureFieldInfo;
import inetsoft.web.wiz.model.SimpleFieldInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Regression tests for a {@code "$(ComponentName)"} dynamic reference (#76641 — binding a shelf
 * field's column to a Form component's live value) surviving a chart type change.
 *
 * <p>{@code WizVsService#collectFlatBinding} builds its dimensions/measures from DESIGN refs, whose
 * value for a dynamically bound field is literally {@code "$(RadioButton2)"} — the RT-ref preference
 * documented on that method applies only to its {@code slots} map. That literal therefore reaches
 * {@code WizAutoBindingService}'s worksheet-column existence checks, where it matches no column.
 *
 * <p>Before this fix, {@code changeType} derived an authoritative field list from the target chart
 * and passed it in explicitly, which bypasses the tolerant {@code derivedFieldConfigsWithin} guard
 * and lands in {@code selectBindColumns} — a hard {@code IllegalArgumentException} ("Unknown
 * field(s) in fieldConfigs"), i.e. a 400 on every type change of a chart carrying one.
 */
@Tag("core")
class WizAutoBindingServiceDynamicColumnRefTest {
   private static final String DYNAMIC = "$(RadioButton2)";

   private static ColumnRef col(String name) {
      return new ColumnRef(new AttributeRef(null, name));
   }

   private static SimpleFieldInfo config(String field) {
      SimpleFieldInfo info = new SimpleFieldInfo();
      info.setField(field);
      return info;
   }

   private static Map<String, SimpleFieldInfo> configMap(SimpleFieldInfo... configs) {
      Map<String, SimpleFieldInfo> map = new HashMap<>();

      for(SimpleFieldInfo fc : configs) {
         map.put(fc.getField(), fc);
      }

      return map;
   }

   private static WizAutoBindingService serviceWith(WizVsService wizVsService) {
      return new WizAutoBindingService(null, null, null, null, null, wizVsService, null, null);
   }

   // ── The reported failure: selectBindColumns must not reject a dynamic reference ──────────────

   @Test
   void dynamicReferenceIsNotReportedAsAnUnknownField() {
      List<ColumnRef> columns = List.of(col("Households"), col("amount"));

      List<ColumnRef> result = assertDoesNotThrow(() -> WizAutoBindingService.selectBindColumns(
         columns, configMap(config(DYNAMIC), config("amount"))));

      // The dynamic reference is not a worksheet column, so it selects nothing of its own; the
      // literal fields still bind. Before the fix this threw instead.
      assertEquals(1, result.size());
      assertEquals("amount", result.get(0).getDisplayName());
   }

   @Test
   void genuinelyUnknownFieldStillThrowsAlongsideADynamicReference() {
      List<ColumnRef> columns = List.of(col("Households"), col("amount"));

      IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(
         IllegalArgumentException.class,
         () -> WizAutoBindingService.selectBindColumns(
            columns, configMap(config(DYNAMIC), config("amont"))));

      assertTrue(e.getMessage().contains("amont"));
      // The dynamic reference must not be listed as a missing column.
      assertTrue(!e.getMessage().contains(DYNAMIC));
   }

   @Test
   void allDynamicConfigMapBindsEveryColumnRatherThanNothing() {
      List<ColumnRef> columns = List.of(col("Households"), col("amount"));

      // Filtering on dynamic keys alone selects no column; binding the rebuilt chart to nothing
      // would be worse than the pre-fix hard failure, so fall back to every visible column.
      List<ColumnRef> result = WizAutoBindingService.selectBindColumns(
         columns, configMap(config(DYNAMIC)));

      assertEquals(2, result.size());
   }

   // ── The inferred-list gate must not discard the whole list over a dynamic reference ──────────

   @Test
   void derivedFieldConfigsWithinKeepsListContainingADynamicReference() {
      WizVsService wizVsService = mock(WizVsService.class);
      ChartVSAssembly target = mock(ChartVSAssembly.class);
      when(wizVsService.collectFlatBinding(target)).thenReturn(
         new CreateViewsheetResult.FlatBinding(
            List.of(dimensionConfig(DYNAMIC)), List.of(measureConfig("amount")), Map.of()));

      List<SimpleFieldInfo> derived = serviceWith(wizVsService)
         .derivedFieldConfigsWithin(target, List.of(col("Households"), col("amount")));

      // Before the fix the dynamic reference read as a missing column and discarded the whole
      // derived list, falling back to "bind every visible column".
      assertEquals(2, derived.size());
   }

   @Test
   void derivedFieldConfigsWithinStillDiscardsListWithAGenuinelyMissingColumn() {
      WizVsService wizVsService = mock(WizVsService.class);
      ChartVSAssembly target = mock(ChartVSAssembly.class);
      when(target.getName()).thenReturn("Chart1");
      when(wizVsService.collectFlatBinding(target)).thenReturn(
         new CreateViewsheetResult.FlatBinding(
            List.of(dimensionConfig(DYNAMIC)), List.of(measureConfig("gone")), Map.of()));

      List<SimpleFieldInfo> derived = serviceWith(wizVsService)
         .derivedFieldConfigsWithin(target, List.of(col("Households"), col("amount")));

      assertTrue(derived.isEmpty());
   }

   // ── Resolution: the field must survive column selection, not merely stop throwing ────────────

   @Test
   void deriveFieldConfigsResolvesDynamicReferenceToItsRuntimeColumn() {
      VSChartDimensionRef dim = mock(VSChartDimensionRef.class);
      when(dim.getGroupColumnValue()).thenReturn(DYNAMIC);
      when(dim.getName()).thenReturn("Households");

      WizVsService wizVsService = mock(WizVsService.class);
      ChartVSAssembly target = chartWith(dim);
      when(wizVsService.collectFlatBinding(target)).thenReturn(
         new CreateViewsheetResult.FlatBinding(
            List.of(dimensionConfig(DYNAMIC)), List.of(), Map.of()));

      List<SimpleFieldInfo> derived = serviceWith(wizVsService).deriveFieldConfigs(target);

      assertEquals(1, derived.size());
      // Rewritten to the real column, so it survives selectBindColumns' filter instead of being
      // silently dropped from the rebuild...
      assertEquals("Households", derived.get(0).getField());
      // ...with the original literal remembered so the dynamic binding can be restored afterward.
      assertEquals(DYNAMIC, derived.get(0).getDynamicColumnValue());
   }

   @Test
   void unresolvedDynamicReferenceIsLeftAloneRatherThanMappedToItself() {
      // A cold RVS: the runtime value was never populated, so getName() falls back to the design
      // value. Nothing to resolve to — the tolerant gates keep it from failing the call.
      VSChartDimensionRef dim = mock(VSChartDimensionRef.class);
      when(dim.getGroupColumnValue()).thenReturn(DYNAMIC);
      when(dim.getName()).thenReturn(DYNAMIC);

      WizVsService wizVsService = mock(WizVsService.class);
      ChartVSAssembly target = chartWith(dim);
      when(wizVsService.collectFlatBinding(target)).thenReturn(
         new CreateViewsheetResult.FlatBinding(
            List.of(dimensionConfig(DYNAMIC)), List.of(), Map.of()));

      List<SimpleFieldInfo> derived = serviceWith(wizVsService).deriveFieldConfigs(target);

      assertEquals(DYNAMIC, derived.get(0).getField());
      assertNull(derived.get(0).getDynamicColumnValue());
   }

   @Test
   void ordinaryFieldIsNotTouched() {
      VSChartDimensionRef dim = mock(VSChartDimensionRef.class);
      when(dim.getGroupColumnValue()).thenReturn("state");

      WizVsService wizVsService = mock(WizVsService.class);
      ChartVSAssembly target = chartWith(dim);
      when(wizVsService.collectFlatBinding(target)).thenReturn(
         new CreateViewsheetResult.FlatBinding(
            List.of(dimensionConfig("state")), List.of(), Map.of()));

      List<SimpleFieldInfo> derived = serviceWith(wizVsService).deriveFieldConfigs(target);

      assertEquals("state", derived.get(0).getField());
      assertNull(derived.get(0).getDynamicColumnValue());
      // No dynamic ref on the assembly at all — getName() is never consulted.
      verify(dim, never()).getName();
   }

   // ── Restore: the rebuilt binding must keep FOLLOWING the component ───────────────────────────

   @Test
   void restorePutsTheReferenceBackOnARebuiltChartRef() {
      VSChartDimensionRef dim = mock(VSChartDimensionRef.class);
      when(dim.getGroupColumnValue()).thenReturn("Households");

      VSChartInfo info = mock(VSChartInfo.class);
      when(info.getXFields()).thenReturn(new ChartRef[]{ dim });
      when(info.getYFields()).thenReturn(new ChartRef[0]);
      when(info.getGroupFields()).thenReturn(new ChartRef[0]);

      WizAutoBindingService.restoreChartDynamicColumnValues(info, configMap(resolved()));

      verify(dim).setGroupColumnValue(DYNAMIC);
   }

   @Test
   void restoreLeavesAnOrdinaryRefAlone() {
      VSChartDimensionRef dim = mock(VSChartDimensionRef.class);
      when(dim.getGroupColumnValue()).thenReturn("Households");

      VSChartInfo info = mock(VSChartInfo.class);
      when(info.getXFields()).thenReturn(new ChartRef[]{ dim });
      when(info.getYFields()).thenReturn(new ChartRef[0]);
      when(info.getGroupFields()).thenReturn(new ChartRef[0]);

      WizAutoBindingService.restoreChartDynamicColumnValues(
         info, configMap(config("Households")));

      verify(dim, never()).setGroupColumnValue(anyString());
   }

   @Test
   void restoreAppliesToCrosstabHeadersAndAggregates() {
      VSDimensionRef header = mock(VSDimensionRef.class);
      when(header.getGroupColumnValue()).thenReturn("Households");

      VSAggregateRef aggregate = mock(VSAggregateRef.class);
      when(aggregate.getColumnValue()).thenReturn("Households");

      VSCrosstabInfo info = mock(VSCrosstabInfo.class);
      when(info.getDesignRowHeaders()).thenReturn(new DataRef[]{ header });
      when(info.getDesignColHeaders()).thenReturn(new DataRef[0]);
      when(info.getDesignAggregates()).thenReturn(new DataRef[]{ aggregate });

      WizAutoBindingService.applyCrosstabAggregateFormulas(info, configMap(resolved()));

      verify(header).setGroupColumnValue(DYNAMIC);
      verify(aggregate).setColumnValue(DYNAMIC);
   }

   @Test
   void restoreRunsAfterTheFormulaOverrideThatKeysOffTheResolvedColumn() {
      // Ordering regression: applyAggregateFormulasTo looks its config up by the ref's CURRENT
      // column value, so restoring "$(...)" first would make that lookup miss.
      VSAggregateRef aggregate = mock(VSAggregateRef.class);
      when(aggregate.getColumnValue()).thenReturn("Households");

      VSCrosstabInfo info = mock(VSCrosstabInfo.class);
      when(info.getDesignRowHeaders()).thenReturn(new DataRef[0]);
      when(info.getDesignColHeaders()).thenReturn(new DataRef[0]);
      when(info.getDesignAggregates()).thenReturn(new DataRef[]{ aggregate });

      MeasureFieldInfo fc = new MeasureFieldInfo();
      fc.setField("Households");
      fc.setAggregateFormula("Count");
      fc.setDynamicColumnValue(DYNAMIC);

      WizAutoBindingService.applyCrosstabAggregateFormulas(info, configMap(fc));

      verify(aggregate).setFormulaValue("Count");
      verify(aggregate).setColumnValue(DYNAMIC);
   }

   private static SimpleFieldInfo resolved() {
      SimpleFieldInfo fc = config("Households");
      fc.setDynamicColumnValue(DYNAMIC);
      return fc;
   }

   private static DimensionFieldInfo dimensionConfig(String field) {
      DimensionFieldInfo fc = new DimensionFieldInfo();
      fc.setField(field);
      return fc;
   }

   private static MeasureFieldInfo measureConfig(String field) {
      MeasureFieldInfo fc = new MeasureFieldInfo();
      fc.setField(field);
      return fc;
   }

   private static ChartVSAssembly chartWith(ChartRef... refs) {
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.getBindingRefs(false)).thenReturn(refs);
      when(info.getAggregateAestheticRefs(false)).thenReturn(List.of());

      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      return chart;
   }
}
