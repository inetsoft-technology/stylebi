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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ExpiredSheetException;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.DateCompareAbleAssemblyInfo;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import inetsoft.web.wiz.viewsheet.DateComparisonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * fillAssemblyState per assembly type, and its best-effort contract: any reader failure leaves
 * assemblyState null and never throws (turn-provenance spec 3.3.2).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServiceFillAssemblyStateTest {
   private ViewsheetService viewsheetService;
   private DateComparisonService dateComparisonService;
   private WizVsService service;
   private Viewsheet vs;
   private RuntimeViewsheet rvs;

   @BeforeEach
   void setUp() throws Exception {
      viewsheetService = mock(ViewsheetService.class);
      dateComparisonService = mock(DateComparisonService.class);
      service = new WizVsService(viewsheetService, mock(AssetRepository.class), mock(SecurityEngine.class),
                                 null, null, null);
      service.setDateComparisonService(dateComparisonService);

      vs = AssemblyStateFixtures.viewsheet(AssemblyStateFixtures.worksheetEntry());
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt-1");
      when(viewsheetService.getViewsheet("rt-1", null)).thenReturn(rvs);
   }

   private static Map<String, Object> comparison(String assembly, boolean enabled) {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("assembly", assembly);
      out.put("enabled", enabled);
      return out;
   }

   @Test
   void chartStateCarriesSourceConditionsHighlightsAndAnEnabledComparison() throws Exception {
      ChartVSAssembly chart = AssemblyStateFixtures.chart(vs, "chart1");
      chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Orders_2"));
      chart.setPreConditionList(AssemblyStateFixtures.nested());
      AssemblyStateFixtures.measure(chart).setHighlightGroup(AssemblyStateFixtures.group(
         "big", AssemblyStateFixtures.condition("Sales", XCondition.GREATER_THAN, 100)));
      ((DateCompareAbleAssemblyInfo) chart.getVSAssemblyInfo()).setComparisonShareFrom("chart0");
      Map<String, Object> comparison = comparison("chart1", true);
      when(dateComparisonService.read(rvs, "chart1", null)).thenReturn(comparison);
      CreateViewsheetResult result = new CreateViewsheetResult();

      service.fillAssemblyState(rvs, chart, null, result);

      CreateViewsheetResult.AssemblyState state = result.getAssemblyState();
      assertNotNull(state);
      assertEquals("worksheet", state.getSource().kind());
      assertEquals("Orders_2", state.getSource().table());
      assertEquals(3, state.getConditions().size());
      assertEquals("chart", state.getConditions().get(0).getPhase());
      assertEquals(comparison, state.getDateComparison());
      assertEquals(1, state.getHighlights().size());
      assertEquals("big", state.getHighlights().get(0).name());
      assertNull(state.getTableColumns());
   }

   @Test
   void crosstabStateOmitsAComparisonThatIsNotInEffect() throws Exception {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "crosstab1");
      crosstab.setPreConditionList(AssemblyStateFixtures.condition("Region", XCondition.EQUAL_TO, "East"));
      ((DateCompareAbleAssemblyInfo) crosstab.getVSAssemblyInfo()).setComparisonShareFrom("chart0");
      when(dateComparisonService.read(rvs, "crosstab1", null)).thenReturn(comparison("crosstab1", false));
      CreateViewsheetResult result = new CreateViewsheetResult();

      service.fillAssemblyState(rvs, crosstab, null, result);

      CreateViewsheetResult.AssemblyState state = result.getAssemblyState();
      assertNotNull(state);
      assertEquals("Region", state.getConditions().get(0).getField());
      assertNull(state.getDateComparison());
      assertNull(state.getTableColumns());
      verify(dateComparisonService).read(rvs, "crosstab1", null);
   }

   @Test
   void aChartWithoutADefinedComparisonNeverReadsOne() throws Exception {
      ChartVSAssembly chart = AssemblyStateFixtures.chart(vs, "chart1");
      CreateViewsheetResult result = new CreateViewsheetResult();

      service.fillAssemblyState(rvs, chart, null, result);

      assertNotNull(result.getAssemblyState());
      assertNull(result.getAssemblyState().getDateComparison());
      verify(dateComparisonService, never()).read(any(RuntimeViewsheet.class), anyString(), any());
   }

   @Test
   void tableStateListsVisibleColumnsAndNeverReadsAComparison() throws Exception {
      TableVSAssembly table = new TableVSAssembly(vs, "table1");
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(new ColumnRef(new AttributeRef(null, "Region")));
      columns.addAttribute(new ColumnRef(new AttributeRef(null, "Sales")));
      table.setColumnSelection(columns);
      CreateViewsheetResult result = new CreateViewsheetResult();

      service.fillAssemblyState(rvs, table, null, result);

      assertEquals(List.of("Region", "Sales"), result.getAssemblyState().getTableColumns().columns());
      verify(dateComparisonService, never()).read(any(RuntimeViewsheet.class), anyString(), any());
   }

   @Test
   void outputStateReadsTheGaugeCondition() {
      GaugeVSAssembly gauge = new GaugeVSAssembly(vs, "gauge1");
      gauge.setPreConditionList(AssemblyStateFixtures.condition("Category", XCondition.EQUAL_TO, "A"));
      CreateViewsheetResult result = new CreateViewsheetResult();

      service.fillAssemblyState(rvs, gauge, null, result);

      CreateViewsheetResult.AssemblyState state = result.getAssemblyState();
      assertEquals("Category", state.getConditions().get(0).getField());
      assertTrue(state.getHighlights().isEmpty());
      assertNull(state.getTableColumns());
   }

   @Test
   void aReaderFailureLeavesTheStateNullAndMarksItUnavailableWithoutThrowing() {
      ChartVSAssembly chart = AssemblyStateFixtures.chart(vs, "chart1");
      RuntimeViewsheet failing = mock(RuntimeViewsheet.class);
      when(failing.getViewsheet()).thenThrow(new IllegalStateException("reader failed"));
      CreateViewsheetResult result = new CreateViewsheetResult();
      result.setAssemblyName("chart1");

      assertDoesNotThrow(() -> service.fillAssemblyState(failing, chart, null, result));

      assertNull(result.getAssemblyState());
      assertEquals(Boolean.TRUE, result.getAssemblyStateUnavailable());
      assertEquals("chart1", result.getAssemblyName());
   }

   @Test
   void aFailedFillSerializesOnlyTheUnavailableFlag() {
      RuntimeViewsheet failing = mock(RuntimeViewsheet.class);
      when(failing.getViewsheet()).thenThrow(new IllegalStateException("reader failed"));
      CreateViewsheetResult result = new CreateViewsheetResult();

      service.fillAssemblyState(failing, AssemblyStateFixtures.chart(vs, "chart1"), null, result);

      JsonNode json =
         new ObjectMapper().valueToTree(result);
      assertFalse(json.has("assemblyState"));
      assertTrue(json.get("assemblyStateUnavailable").asBoolean());
   }

   @Test
   void aSuccessfulFillClearsAnEarlierUnavailableFlag() {
      CreateViewsheetResult result = new CreateViewsheetResult();
      result.setAssemblyStateUnavailable(Boolean.TRUE);

      service.fillAssemblyState(rvs, AssemblyStateFixtures.chart(vs, "chart1"), null, result);

      assertNotNull(result.getAssemblyState());
      assertNull(result.getAssemblyStateUnavailable());
      assertFalse(new ObjectMapper().valueToTree(result)
                     .has("assemblyStateUnavailable"));
   }

   @Test
   void withoutAComparisonServiceTheComparisonIsOmitted() {
      WizVsService plain = new WizVsService(viewsheetService, mock(AssetRepository.class),
                                            mock(SecurityEngine.class), null, null, null);
      CreateViewsheetResult result = new CreateViewsheetResult();

      plain.fillAssemblyState(rvs, AssemblyStateFixtures.chart(vs, "chart1"), null, result);

      assertNotNull(result.getAssemblyState());
      assertNull(result.getAssemblyState().getDateComparison());
   }

   @Test
   void byNameLooksUpTheLiveAssembly() {
      ChartVSAssembly chart = AssemblyStateFixtures.chart(vs, "chart1");
      chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Orders_3"));
      vs.addAssembly(chart);
      CreateViewsheetResult result = new CreateViewsheetResult();

      service.fillAssemblyState("rt-1", "chart1", (Principal) null, result);

      assertEquals("Orders_3", result.getAssemblyState().getSource().table());
   }

   @Test
   void byNameWithAnExpiredRuntimeLeavesTheStateNull() throws Exception {
      when(viewsheetService.getViewsheet("gone", null)).thenThrow(new ExpiredSheetException("gone", null));
      CreateViewsheetResult result = new CreateViewsheetResult();

      assertDoesNotThrow(() -> service.fillAssemblyState("gone", "chart1", (Principal) null, result));

      assertNull(result.getAssemblyState());
      assertEquals(Boolean.TRUE, result.getAssemblyStateUnavailable());
   }

   @Test
   void aNullAssemblyIsMarkedUnavailable() {
      CreateViewsheetResult result = new CreateViewsheetResult();

      service.fillAssemblyState(rvs, null, null, result);

      assertNull(result.getAssemblyState());
      assertEquals(Boolean.TRUE, result.getAssemblyStateUnavailable());
   }

   @Test
   void aNullRuntimeIsMarkedUnavailable() {
      CreateViewsheetResult result = new CreateViewsheetResult();

      service.fillAssemblyState((RuntimeViewsheet) null, AssemblyStateFixtures.chart(vs, "c1"), null, result);

      assertNull(result.getAssemblyState());
      assertEquals(Boolean.TRUE, result.getAssemblyStateUnavailable());
   }

   @Test
   void byNameWithAnAssemblyThatIsNotFoundIsMarkedUnavailable() {
      CreateViewsheetResult result = new CreateViewsheetResult();

      assertDoesNotThrow(() -> service.fillAssemblyState("rt-1", "missing", (Principal) null, result));

      assertNull(result.getAssemblyState());
      assertEquals(Boolean.TRUE, result.getAssemblyStateUnavailable());
      assertFalse(new ObjectMapper().valueToTree(result).has("assemblyState"));
   }

   @Test
   void byNameWithARuntimeThatIsNotFoundIsMarkedUnavailable() throws Exception {
      when(viewsheetService.getViewsheet("none", null)).thenReturn(null);
      CreateViewsheetResult result = new CreateViewsheetResult();

      service.fillAssemblyState("none", "chart1", (Principal) null, result);

      assertNull(result.getAssemblyState());
      assertEquals(Boolean.TRUE, result.getAssemblyStateUnavailable());
   }

   @Test
   void aMissingResultIsIgnored() {
      assertDoesNotThrow(() -> service.fillAssemblyState(rvs, AssemblyStateFixtures.chart(vs, "c1"), null, null));
   }
}
