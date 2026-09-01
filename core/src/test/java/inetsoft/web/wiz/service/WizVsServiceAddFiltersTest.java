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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AbstractTableAssembly;
import inetsoft.uql.asset.AssetContent;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.model.AddFiltersRequest;
import inetsoft.web.wiz.model.AddFiltersResponse;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import inetsoft.web.wiz.model.FilterFieldSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Point;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WizVsService.addFilters — the one piece of section 4's Java sketch flagged (01-design.md
 * section 6's risk list) as worth a dedicated unit test beyond WizFilterLayoutTest: column
 * resolution (a genuinely-missing field lands in skipped, a genuinely-present one resolves) and
 * the upsert-by-field reuse path (assertion A3 — same assemblyName across a repeat call, not a
 * duplicate). executeAndExtract/persistViewsheet are stubbed via a Mockito spy, same pattern as
 * {@link WizVsServiceFilterCopyTest} — this isolates addFilters' own wiring from the pre-existing,
 * separately-tested sandbox-execution machinery.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServiceAddFiltersTest {
   private WizVsService service;
   private Viewsheet vs;
   private ChartVSAssembly chart;
   private Principal user;

   @BeforeEach
   void setUp() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      user = mock(Principal.class);

      WizVsService real = new WizVsService(viewsheetService, engine, securityEngine, null, null, null);
      service = spy(real);
      doReturn(new CreateViewsheetResult()).when(service).executeAndExtract(any(), any(), anyInt());
      doReturn("vs-identifier").when(service).persistViewsheet(any(), any(), any());

      chart = mock(ChartVSAssembly.class);
      when(chart.getName()).thenReturn("Chart1");
      when(chart.getTableName()).thenReturn("SALES_FULL");
      when(chart.getPixelOffset()).thenReturn(new Point(0, 0));
      when(chart.getPixelSize()).thenReturn(new Dimension(400, 240));

      vs = mock(Viewsheet.class);
      when(vs.getAssembly("Chart1")).thenReturn(chart);
      when(vs.getWizInfo()).thenReturn(new Viewsheet.WizInfo(true, null, null));

      AssetEntry wsEntry = mock(AssetEntry.class);
      when(vs.getBaseEntry()).thenReturn(wsEntry);

      AbstractTableAssembly table = mock(AbstractTableAssembly.class);
      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssembly(anyString())).thenReturn(table);
      when(engine.getSheet(eq(wsEntry), eq(user), eq(false), any(AssetContent.class))).thenReturn(ws);

      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "REGION")));
      when(table.getColumnSelection(false)).thenReturn(cols);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt-1");
      when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);
   }

   private static AddFiltersRequest request(String... fields) {
      AddFiltersRequest req = new AddFiltersRequest();
      req.setRuntimeId("rt-1");
      req.setAssemblyName("Chart1");
      List<FilterFieldSpec> specs = new ArrayList<>();

      for(String f : fields) {
         FilterFieldSpec spec = new FilterFieldSpec();
         spec.setField(f);
         spec.setControlType("selection_list");
         specs.add(spec);
      }

      req.setFields(specs);
      return req;
   }

   @Test
   void createsANewControlBoundToTheChartsTable() throws Exception {
      AddFiltersResponse resp = service.addFilters(request("REGION"), user);

      assertEquals(1, resp.getApplied().size());
      assertEquals("REGION", resp.getApplied().get(0).getField());
      // VSEventUtil.createVSAssembly already adds the assembly once internally, then addFilters
      // re-adds it for z-index-at-final-position (01-design.md section 2.1) -- atLeastOnce, not an
      // exact count, since that internal call count is VSEventUtil's own implementation detail.
      verify(vs, atLeastOnce()).addAssembly(any(SelectionListVSAssembly.class));
   }

   @Test
   void aFieldNotOnTheBoundTableIsSkippedNotFailed() throws Exception {
      AddFiltersResponse resp = service.addFilters(request("NOPE"), user);

      assertEquals(0, resp.getApplied().size());
      assertEquals(1, resp.getSkipped().size());
      assertEquals("NOPE", resp.getSkipped().get(0).getField());
      assertEquals("not found on bound table", resp.getSkipped().get(0).getReason());
   }

   @Test
   void aPartialFailureStillAppliesTheResolvableFields() throws Exception {
      AddFiltersResponse resp = service.addFilters(request("REGION", "NOPE"), user);

      assertEquals(1, resp.getApplied().size());
      assertEquals("REGION", resp.getApplied().get(0).getField());
      assertEquals(1, resp.getSkipped().size());
      assertEquals("NOPE", resp.getSkipped().get(0).getField());
   }

   @Test
   void repeatCallForAnAlreadyTrackedFieldReusesTheSameAssemblyInsteadOfCreatingADuplicate() throws Exception {
      AddFiltersResponse first = service.addFilters(request("REGION"), user);
      String assemblyName = first.getApplied().get(0).getAssemblyName();

      var captor = forClass(VSAssembly.class);
      verify(vs, atLeastOnce()).addAssembly(captor.capture());
      VSAssembly created = captor.getValue();
      when(vs.getAssembly(assemblyName)).thenReturn(created);

      AddFiltersRequest req = request("REGION");
      req.getFields().get(0).setLabel("Region (relabeled)");
      req.getFields().get(0).setExistingAssemblyName(assemblyName);

      AddFiltersResponse second = service.addFilters(req, user);

      // Same assemblyName reused -- not a duplicate control (assertion A3).
      assertEquals(assemblyName, second.getApplied().get(0).getAssemblyName());
      assertEquals("Region (relabeled)", second.getApplied().get(0).getLabel());
      // The SAME real assembly object was mutated in place, not a fresh one created alongside it --
      // the strongest possible proof of reuse (an object identity check), independent of however
      // many times VSEventUtil.createVSAssembly's own internals happen to call addAssembly.
      assertEquals("Region (relabeled)", ((SelectionListVSAssembly) created).getTitleValue());
      verify(vs, never()).removeAssembly(anyString());
   }

   @Test
   void anExistingAssemblyNameThatNoLongerExistsFallsBackToCreatingFresh() throws Exception {
      AddFiltersRequest req = request("REGION");
      req.getFields().get(0).setExistingAssemblyName("SelectionListGone");
      // vs.getAssembly("SelectionListGone") defaults to null (stale name, already removed).

      AddFiltersResponse resp = service.addFilters(req, user);

      assertEquals(1, resp.getApplied().size());
      verify(vs, never()).removeAssembly("SelectionListGone");
   }
}
