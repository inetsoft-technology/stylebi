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
import inetsoft.test.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.sree.security.SecurityEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * duplicatePrimaryAssembly is the copy-then-apply entry point for setChartFormat/setChartColors
 * (request.isCopy()): it must produce a NEW assembly carrying the source's binding/format state,
 * promote it to primary, and demote (never delete) the previous primary — mirroring the standard
 * create() path's own rebind sequence (uniqueAssemblyName + rebindAssembly + demote/promote). It
 * also enforces its documented precondition that {@code source} must already be the primary
 * assembly, refusing (returning null) rather than silently demoting an unrelated current-primary
 * chart if a caller passes a stale, no-longer-primary source.
 *
 * Real Viewsheet/ChartVSAssembly instances are used (not Mockito mocks) because rebindAssembly
 * dispatches on src.getClass() against a Class-keyed factory map (ASSEMBLY_FACTORIES) — a Mockito
 * mock's class would never match ChartVSAssembly.class, so the "real assembly duplicated" path could
 * never be exercised through mocks. Constructing a real Viewsheet/VSAssemblyInfo reads SreeEnv
 * properties, which requires the minimal Spring/@SreeHome test context (same setup as
 * ApplyParameterToInputTest / VSFormatTableLensTest).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServiceDuplicatePrimaryAssemblyTest {
   private static WizVsService newService() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      return new WizVsService(vsService, engine, sec);
   }

   private static RuntimeViewsheet rvsOf(Viewsheet vs) {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   @Test
   void duplicatesUnderAUniqueNameAndPromotesItToPrimary() throws Exception {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly source = new ChartVSAssembly(vs, "Chart1");
      source.setPrimary(true);
      vs.addAssembly(source);

      WizVsService service = newService();
      VSAssembly copy = service.duplicatePrimaryAssembly(rvsOf(vs), source);

      assertNotNull(copy);
      assertNotEquals("Chart1", copy.getName());
      assertTrue(copy.isPrimary());
      assertTrue(copy instanceof ChartVSAssembly);
   }

   @Test
   void demotesButDoesNotDeleteThePreviousPrimary() throws Exception {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly source = new ChartVSAssembly(vs, "Chart1");
      source.setPrimary(true);
      vs.addAssembly(source);

      WizVsService service = newService();
      service.duplicatePrimaryAssembly(rvsOf(vs), source);

      // The original is still present in the viewsheet (not removed) — just no longer primary.
      Assembly original = vs.getAssembly("Chart1");
      assertNotNull(original);
      assertFalse(((VSAssembly) original).isPrimary());
   }

   @Test
   void copiesTheSourcesBindingAndFormatState() throws Exception {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly source = new ChartVSAssembly(vs, "Chart1");
      source.setPrimary(true);

      if(source.getVSAssemblyInfo() instanceof ChartVSAssemblyInfo cinfo) {
         cinfo.setTitleValue("Contacts per Account");
         cinfo.setTitleVisible(true);
      }

      vs.addAssembly(source);

      WizVsService service = newService();
      VSAssembly copy = service.duplicatePrimaryAssembly(rvsOf(vs), source);

      assertTrue(copy.getVSAssemblyInfo() instanceof ChartVSAssemblyInfo);
      ChartVSAssemblyInfo copiedInfo = (ChartVSAssemblyInfo) copy.getVSAssemblyInfo();
      assertEquals("Contacts per Account", copiedInfo.getTitleValue());

      // Copy is independent — mutating the source afterward must not leak into the copy.
      if(source.getVSAssemblyInfo() instanceof ChartVSAssemblyInfo cinfo) {
         cinfo.setTitleValue("Changed after copy");
      }

      assertEquals("Contacts per Account", copiedInfo.getTitleValue());
   }

   @Test
   void allocatesASeparateUniqueNameOnASecondDuplicate() throws Exception {
      // Simulates a second copy-then-apply turn: the FIRST copy is now primary (the first copy is
      // still there, unsaved) — a second duplicate call resolves against the CURRENT primary (the
      // first copy), which must not collide with the first copy's own name.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly source = new ChartVSAssembly(vs, "Chart1");
      source.setPrimary(true);
      vs.addAssembly(source);

      WizVsService service = newService();
      VSAssembly firstCopy = service.duplicatePrimaryAssembly(rvsOf(vs), source);
      VSAssembly secondCopy = service.duplicatePrimaryAssembly(rvsOf(vs), firstCopy);

      assertNotNull(secondCopy);
      assertNotEquals(firstCopy.getName(), secondCopy.getName());
      assertTrue(secondCopy.isPrimary());
      assertFalse(firstCopy.isPrimary());
   }

   @Test
   void refusesToDuplicateWhenSourceIsNoLongerPrimary() throws Exception {
      // The documented precondition: source MUST already be primary. A caller (setChartFormat/
      // setChartColors) that resolves the source purely by a client-supplied, possibly-stale
      // assemblyName must not silently demote whatever chart currently IS primary — a completely
      // unrelated assembly — and duplicate the wrong (stale) source instead.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly source = new ChartVSAssembly(vs, "Chart1");
      source.setPrimary(false);
      vs.addAssembly(source);

      ChartVSAssembly actualPrimary = new ChartVSAssembly(vs, "Chart2");
      actualPrimary.setPrimary(true);
      vs.addAssembly(actualPrimary);

      WizVsService service = newService();
      VSAssembly copy = service.duplicatePrimaryAssembly(rvsOf(vs), source);

      assertNull(copy);
      // The unrelated current-primary assembly must be left completely untouched.
      assertTrue(actualPrimary.isPrimary());
      assertEquals(2, vs.getAssemblies().length, "no assembly should have been added");
   }

   @Test
   void returnsNullForAnAssemblyTypeWithNoRebindFactory() throws Exception {
      // TableVSAssembly IS in ASSEMBLY_FACTORIES; use a type that is not, to exercise the fallback.
      // GaugeVSAssembly/TextVSAssembly/CrosstabVSAssembly ARE registered too, so pick an assembly kind
      // that has no factory entry at all: a plain abstract-ish stand-in is not available, so instead
      // assert the documented contract directly against the registered set by using a type deliberately
      // NOT in ASSEMBLY_FACTORIES — inetsoft.uql.viewsheet.CalcTableVSAssembly.
      Viewsheet vs = new Viewsheet();
      inetsoft.uql.viewsheet.CalcTableVSAssembly source =
         new inetsoft.uql.viewsheet.CalcTableVSAssembly(vs, "Calc1");
      source.setPrimary(true);
      vs.addAssembly(source);

      WizVsService service = newService();
      VSAssembly copy = service.duplicatePrimaryAssembly(rvsOf(vs), source);

      assertNull(copy);
   }
}
