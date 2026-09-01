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
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetContent;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
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
import java.awt.Rectangle;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
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
   private AbstractTableAssembly table;
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

      table = mock(AbstractTableAssembly.class);
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

   // ── server-side dedupe when the caller gives no existingAssemblyName hint (06-review-r1.md
   // CRITICAL finding, item 2 of the fix -- the guarantee must not depend on wiz-services' own
   // tracked field->assemblyName state being accurate) ──────────────────────────────────────────

   @Test
   void noSessionHintButAMatchingLiveControlReusesItInsteadOfDuplicating() throws Exception {
      SelectionListVSAssembly liveControl = mock(SelectionListVSAssembly.class);
      when(liveControl.getName()).thenReturn("SelectionList1");
      when(liveControl.getTableNames()).thenReturn(List.of("SALES_FULL"));
      when(liveControl.getDataRefs()).thenReturn(new DataRef[] { new ColumnRef(new AttributeRef(null, "REGION")) });
      when(liveControl.getPixelOffset()).thenReturn(new Point(0, 250));
      when(liveControl.getPixelSize()).thenReturn(new Dimension(100, 120));
      when(vs.getAssemblies()).thenReturn(new Assembly[] { liveControl });

      AddFiltersResponse resp = service.addFilters(request("REGION"), user); // no existingAssemblyName

      assertEquals(1, resp.getApplied().size());
      assertEquals("SelectionList1", resp.getApplied().get(0).getAssemblyName());
      verify(vs, never()).removeAssembly(anyString());
      // The reused mock is reconfigured in place (title/table/position) -- not replaced.
      verify(liveControl).setTableNames(List.of("SALES_FULL"));
   }

   @Test
   void noSessionHintAndNoMatchingLiveControlStillCreatesAFreshOne() throws Exception {
      // A live control exists, but bound to a DIFFERENT field (CITY, not requested) -- must not be
      // mistaken for a match, and must not be disturbed.
      SelectionListVSAssembly unrelated = mock(SelectionListVSAssembly.class);
      when(unrelated.getName()).thenReturn("SelectionList0");
      when(unrelated.getTableNames()).thenReturn(List.of("SALES_FULL"));
      when(unrelated.getDataRefs()).thenReturn(new DataRef[] { new ColumnRef(new AttributeRef(null, "CITY")) });
      when(unrelated.getPixelOffset()).thenReturn(new Point(0, 250));
      when(unrelated.getPixelSize()).thenReturn(new Dimension(100, 120));
      when(vs.getAssemblies()).thenReturn(new Assembly[] { unrelated });

      AddFiltersResponse resp = service.addFilters(request("REGION"), user);

      assertEquals(1, resp.getApplied().size());
      assertNotEquals("SelectionList0", resp.getApplied().get(0).getAssemblyName());
      verify(vs, never()).removeAssembly(anyString());
      verify(vs, atLeastOnce()).addAssembly(any(SelectionListVSAssembly.class));
      verify(unrelated, never()).setTableNames(any());
   }

   // ── packing around already-placed controls across separate calls (06-review-r1.md Important
   // finding, item 3 of the fix) ────────────────────────────────────────────────────────────────

   @Test
   void aSecondSeparateCallWithDifferentFieldsDoesNotOverlapAnEarlierCallsControl() throws Exception {
      AddFiltersResponse first = service.addFilters(request("REGION"), user);
      assertEquals(1, first.getApplied().size());

      var firstCaptor = forClass(VSAssembly.class);
      verify(vs, atLeastOnce()).addAssembly(firstCaptor.capture());
      VSAssembly regionControl = firstCaptor.getValue();
      assertEquals(new Point(0, 250), regionControl.getPixelOffset());
      assertEquals(new Dimension(100, 120), regionControl.getPixelSize());

      // Call 2 is a SEPARATE, later call for a different field -- REGION's control is still live
      // (present in vs.getAssemblies()) but the session tracks no existingAssemblyName for either
      // field (simulating the exact staleness 06-review-r1.md's Critical finding described).
      ColumnSelection extendedCols = new ColumnSelection();
      extendedCols.addAttribute(new ColumnRef(new AttributeRef(null, "REGION")));
      extendedCols.addAttribute(new ColumnRef(new AttributeRef(null, "CITY")));
      when(table.getColumnSelection(false)).thenReturn(extendedCols);
      when(vs.getAssemblies()).thenReturn(new Assembly[] { regionControl });
      // A real Viewsheet's own by-name lookup would already reflect REGION's control (it's really
      // there) -- stub that explicitly too, so AssetUtil.getNextName's own by-name probe (a separate
      // path from getAssemblies()) doesn't hand out the same name again for CITY's control.
      when(vs.getAssembly(regionControl.getName())).thenReturn(regionControl);
      clearInvocations(vs); // keep stubbing, drop call-1's recorded invocations

      AddFiltersResponse second = service.addFilters(request("CITY"), user);
      assertEquals(1, second.getApplied().size());

      var secondCaptor = forClass(VSAssembly.class);
      verify(vs, atLeastOnce()).addAssembly(secondCaptor.capture());
      VSAssembly cityControl = secondCaptor.getValue();

      assertNotEquals(regionControl.getName(), cityControl.getName(), "a NEW control, not the reused REGION one");
      assertFalse(
         new Rectangle(cityControl.getPixelOffset(), cityControl.getPixelSize())
            .intersects(new Rectangle(regionControl.getPixelOffset(), regionControl.getPixelSize())),
         "call 2's control must not land on top of call 1's still-live control");
      // REGION's control was never moved -- pack() only ever returns rectangles for THIS call's
      // requested fields, and nothing in addFilters calls setPixelOffset/setPixelSize on anything
      // outside `resolvable` for this call.
      assertEquals(new Point(0, 250), regionControl.getPixelOffset());
      assertEquals(new Dimension(100, 120), regionControl.getPixelSize());
   }
}
