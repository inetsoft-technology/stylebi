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
package inetsoft.web.viewsheet.service;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.EmbeddedOnLoadFixture;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static inetsoft.report.composition.execution.EmbeddedOnLoadFixture.ONLOAD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #78222 through the real {@link VSExportService#writeViewsheetExport}: the export of the
 * current view reuses the viewer's sandbox, so the embedded viewsheet must show the wrapper
 * onLoad's parameter there as well as in the bookmark export. The exporter is a mock that
 * records what the embedded Text shows in the sandbox it is handed (after the
 * {@code prepareForExport()} that {@code AbstractVSExporter.prepareSheet} runs).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSExportServiceEmbeddedOnLoadParamTest {
   private Principal savedPrincipal;
   private final List<EmbeddedOnLoadFixture> fixtures = new ArrayList<>();

   @BeforeEach
   void savePrincipal() {
      savedPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void cleanup() {
      fixtures.forEach(EmbeddedOnLoadFixture::dispose);
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   @Test
   void currentViewExportShowsWrapperOnLoadInEmbeddedViewsheet() throws Exception {
      List<String> shown = export(true, false);
      assertEquals(List.of("P=ONLOADW"), shown);
   }

   @Test
   void bookmarkExportShowsWrapperOnLoadInEmbeddedViewsheet() throws Exception {
      List<String> shown = export(false, true);
      assertEquals(List.of("P=ONLOADW"), shown);
   }

   @Test
   void currentViewAndBookmarkExportShowTheSameValue() throws Exception {
      assertEquals(List.of("P=ONLOADW", "P=ONLOADW"), export(true, true));
   }

   @Test
   void controlWithoutEmbeddingIsUnaffected() throws Exception {
      // the wrapper's own Text reads the root table, which always had the value
      EmbeddedOnLoadFixture f = newFixture();
      ViewsheetSandbox live = f.canvas().wrapperOnLoad(ONLOAD).viewer();
      List<String> shown = new ArrayList<>();
      invoke(rvs(f, live), recorder(shown, box -> f.rootText(box)), true, new String[0]);
      assertEquals(List.of("R=ONLOADW"), shown);
   }

   /**
    * Exports the current view and/or one bookmark of a wrapper whose live viewer sandbox has
    * already run the wrapper's onLoad, and returns the embedded Text of each exported sandbox.
    */
   private List<String> export(boolean current, boolean bookmark) throws Exception {
      EmbeddedOnLoadFixture f = newFixture();
      ViewsheetSandbox live = f.wrapperOnLoad(ONLOAD).viewer();
      List<String> shown = new ArrayList<>();
      invoke(rvs(f, live), recorder(shown, box -> f.text(box, "Viewsheet1")), current,
             bookmark ? new String[] { "b1" } : new String[0]);
      return shown;
   }

   private EmbeddedOnLoadFixture newFixture() throws Exception {
      EmbeddedOnLoadFixture f = new EmbeddedOnLoadFixture();
      fixtures.add(f);
      return f;
   }

   private RuntimeViewsheet rvs(EmbeddedOnLoadFixture f, ViewsheetSandbox live) throws Exception {
      AssetEntry entry = f.wrapper.getEntry();
      f.wrapper.setRuntimeEntry(entry);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(f.wrapper);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(live));
      when(rvs.getEntry()).thenReturn(entry);
      when(rvs.getID()).thenReturn("rvs78222");

      // the bookmark is a fresh copy of the same wrapper
      EmbeddedOnLoadFixture g = newFixture().canvas().wrapperOnLoad(ONLOAD);
      when(rvs.getOriginalBookmark(anyString(), any())).thenAnswer(inv -> g.wrapper);
      return rvs;
   }

   private static VSExporter recorder(List<String> shown,
                                      java.util.function.Function<ViewsheetSandbox, String> read)
      throws Exception
   {
      VSExporter exporter = mock(VSExporter.class);
      org.mockito.stubbing.Answer<Object> answer = inv -> {
         ViewsheetSandbox box = inv.getArgument(0);
         box.prepareForExport();
         shown.add(read.apply(box));
         return null;
      };
      doAnswer(answer).when(exporter).export(any(ViewsheetSandbox.class), anyString(), any());
      doAnswer(answer).when(exporter)
         .export(any(ViewsheetSandbox.class), anyString(), org.mockito.ArgumentMatchers.anyInt(),
                 any());
      return exporter;
   }

   private static void invoke(RuntimeViewsheet rvs, VSExporter exporter, boolean current,
                              String[] bookmarks) throws Exception
   {
      Method method = VSExportService.class.getDeclaredMethod(
         "writeViewsheetExport", RuntimeViewsheet.class, VSExporter.class, Principal.class,
         boolean.class, boolean.class, boolean.class, String[].class, boolean.class,
         boolean.class);
      method.setAccessible(true);
      VSExportService service = new VSExportService(null, null, null, null, null, null);
      method.invoke(service, rvs, exporter, null, false, false, current, bookmarks, false,
                    false);
   }
}
