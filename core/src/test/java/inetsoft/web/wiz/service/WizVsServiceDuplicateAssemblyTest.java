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
import inetsoft.sree.security.SecurityEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * duplicateAssembly is the copy-then-apply entry point for the modificationOnly (in-place filter)
 * path, where the chart being copied is NOT necessarily the current primary: a wiz turn may name an
 * earlier chart explicitly. It must therefore duplicate whatever source it is given while demoting
 * whichever assembly actually holds primary, and report that demoted assembly back so the caller can
 * undo the promotion on rollback.
 *
 * <p>This is the difference from {@link WizVsServiceDuplicatePrimaryAssemblyTest}'s subject, which
 * additionally REFUSES a non-primary source (protecting callers whose assembly name comes from the
 * client and may be stale). The tests below deliberately cover the case that method rejects.
 *
 * <p>Real Viewsheet/ChartVSAssembly instances are used (not Mockito mocks) for the same reason as
 * that sibling test: rebindAssembly dispatches on src.getClass() against a Class-keyed factory map
 * (ASSEMBLY_FACTORIES), which a mock's synthetic subclass never matches.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServiceDuplicateAssemblyTest {
   private static WizVsService newService() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      return new WizVsService(vsService, engine, sec, null, null);
   }

   private static RuntimeViewsheet rvsOf(Viewsheet vs) {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   @Test
   void duplicatesANonPrimarySourceAndDemotesTheActualPrimary() throws Exception {
      // The scenario this method exists for: the user asked to filter an EARLIER chart, so the copy
      // must be taken from that chart while the newest chart is the one that loses primary status.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly earlier = new ChartVSAssembly(vs, "Chart1");
      earlier.setPrimary(false);
      vs.addAssembly(earlier);

      ChartVSAssembly newest = new ChartVSAssembly(vs, "Chart2");
      newest.setPrimary(true);
      vs.addAssembly(newest);

      WizVsService service = newService();
      WizVsService.AssemblyDuplication result = service.duplicateAssembly(rvsOf(vs), earlier);

      assertNotNull(result, "a non-primary source must still be duplicated");
      // The copy derives from the named source, not from whatever was primary.
      assertTrue(result.copy().getName().startsWith("Chart1"));
      assertTrue(result.copy().isPrimary());
      // The assembly that lost primary is the one that HELD it — not the source.
      assertSame(newest, result.demoted());
      assertFalse(newest.isPrimary());
   }

   @Test
   void leavesExactlyOnePrimaryBehind() throws Exception {
      // The specific corruption this method's demote-the-real-primary logic prevents: demoting the
      // source instead would be a no-op on an already-non-primary chart, leaving the old primary
      // promoted alongside the new copy.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly earlier = new ChartVSAssembly(vs, "Chart1");
      earlier.setPrimary(false);
      vs.addAssembly(earlier);

      ChartVSAssembly newest = new ChartVSAssembly(vs, "Chart2");
      newest.setPrimary(true);
      vs.addAssembly(newest);

      WizVsService service = newService();
      service.duplicateAssembly(rvsOf(vs), earlier);

      long primaries = Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof VSAssembly vsa && vsa.isPrimary())
         .count();
      assertEquals(1, primaries, "exactly one assembly may be primary");
   }

   @Test
   void keepsTheSourceInTheViewsheet() throws Exception {
      // Copy-then-apply's whole point: the chart the user named is left untouched, so its message in
      // the conversation still renders what it rendered before.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly earlier = new ChartVSAssembly(vs, "Chart1");
      earlier.setPrimary(false);
      vs.addAssembly(earlier);

      ChartVSAssembly newest = new ChartVSAssembly(vs, "Chart2");
      newest.setPrimary(true);
      vs.addAssembly(newest);

      WizVsService service = newService();
      service.duplicateAssembly(rvsOf(vs), earlier);

      Assembly source = vs.getAssembly("Chart1");
      assertNotNull(source, "the duplicated source must not be removed");
      assertFalse(((VSAssembly) source).isPrimary());
   }

   @Test
   void reportsANullDemotedAssemblyWhenNothingWasPrimary() throws Exception {
      // Nothing to restore on rollback in this case — the caller must tolerate a null rather than NPE
      // (see createViewsheetInternal's rollback branch).
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly source = new ChartVSAssembly(vs, "Chart1");
      source.setPrimary(false);
      vs.addAssembly(source);

      WizVsService service = newService();
      WizVsService.AssemblyDuplication result = service.duplicateAssembly(rvsOf(vs), source);

      assertNotNull(result);
      assertNull(result.demoted());
      assertTrue(result.copy().isPrimary());
   }

   @Test
   void duplicatesAPrimarySourceAndReportsItAsTheDemotedOne() throws Exception {
      // The ordinary case (source IS primary) must keep behaving exactly as duplicatePrimaryAssembly
      // did — that method now delegates here, so this pins the shared behaviour.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly source = new ChartVSAssembly(vs, "Chart1");
      source.setPrimary(true);
      vs.addAssembly(source);

      WizVsService service = newService();
      WizVsService.AssemblyDuplication result = service.duplicateAssembly(rvsOf(vs), source);

      assertNotNull(result);
      assertSame(source, result.demoted());
      assertFalse(source.isPrimary());
      assertTrue(result.copy().isPrimary());
      assertNotEquals("Chart1", result.copy().getName());
   }

   @Test
   void returnsNullForAnAssemblyTypeWithNoRebindFactory() throws Exception {
      // CalcTableVSAssembly has no ASSEMBLY_FACTORIES entry; the caller falls back to applying in
      // place rather than failing the request.
      Viewsheet vs = new Viewsheet();
      inetsoft.uql.viewsheet.CalcTableVSAssembly source =
         new inetsoft.uql.viewsheet.CalcTableVSAssembly(vs, "Calc1");
      source.setPrimary(true);
      vs.addAssembly(source);

      WizVsService service = newService();

      assertNull(service.duplicateAssembly(rvsOf(vs), source));
   }
}
