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
import inetsoft.report.io.viewsheet.AbstractVSExporter;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.sree.internal.Mailer;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.util.FileSystemService;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;

import static inetsoft.report.composition.execution.EmbeddedOnLoadFixture.ONLOAD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78222 through the real {@link VSEmailService#emailViewsheet}: the email of the current
 * view reuses the viewer's sandbox, and an emailed bookmark is built by the real
 * {@code createSandbox(..., liveVars)}; in both the embedded viewsheet must show the wrapper
 * onLoad's parameter. The exporter is a mock that records what the embedded Text shows in the
 * sandbox it is handed (after the {@code prepareForExport()} of {@code prepareSheet}); the
 * mailer stops the email right after the export.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSEmailServiceEmbeddedOnLoadParamTest {
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
   void currentViewEmailShowsWrapperOnLoadInEmbeddedViewsheet(@TempDir Path dir)
      throws Exception
   {
      assertEquals(List.of("P=ONLOADW"), email(dir, true, new String[0], false));
   }

   @Test
   void bookmarkEmailShowsWrapperOnLoadInEmbeddedViewsheet(@TempDir Path dir) throws Exception {
      assertEquals(List.of("P=ONLOADW"), email(dir, false, new String[] { "b1" }, false));
   }

   @Test
   void currentViewAndBookmarkEmailShowTheSameValue(@TempDir Path dir) throws Exception {
      assertEquals(List.of("P=ONLOADW", "P=ONLOADW"),
                   email(dir, true, new String[] { "b1" }, false));
   }

   @Test
   void controlWithoutEmbeddingIsUnaffected(@TempDir Path dir) throws Exception {
      assertEquals(List.of("R=ONLOADW"), email(dir, true, new String[0], true));
   }

   /**
    * Emails the current view and/or one bookmark of a wrapper whose live viewer sandbox has
    * already run the wrapper's onLoad, and returns what each exported sandbox shows: the
    * embedded Text, or the wrapper's own Text when {@code control} is set.
    */
   private List<String> email(Path dir, boolean current, String[] bookmarks, boolean control)
      throws Exception
   {
      EmbeddedOnLoadFixture f = newFixture().canvas().wrapperOnLoad(ONLOAD);
      EmbeddedOnLoadFixture g = newFixture().canvas().wrapperOnLoad(ONLOAD);
      ViewsheetSandbox live = f.viewer();
      List<String> shown = new ArrayList<>();
      VSExporter exporter = mock(VSExporter.class);
      Answer<Object> answer = inv -> {
         ViewsheetSandbox box = inv.getArgument(0);
         box.prepareForExport();
         shown.add(control ? f.rootText(box) : f.text(box, "Viewsheet1"));
         return null;
      };
      doAnswer(answer).when(exporter).export(any(ViewsheetSandbox.class), anyString(), any());
      doAnswer(answer).when(exporter)
         .export(any(ViewsheetSandbox.class), anyString(), anyInt(), any());

      VSEmailService service = new VSEmailService(cacheIn(dir)) {
         @Override
         protected Mailer createMailer() {
            throw new IllegalStateException("stop78222");
         }
      };

      AssetEntry entry = f.wrapper.getEntry();
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(f.wrapper);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(live));
      when(rvs.getEntry()).thenReturn(entry);
      when(rvs.getOriginalBookmark(anyString())).thenAnswer(inv -> g.wrapper);

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
          MockedStatic<PortalThemesManager> themes = mockStatic(PortalThemesManager.class);
          MockedStatic<AbstractVSExporter> exporters =
             mockStatic(AbstractVSExporter.class, CALLS_REAL_METHODS))
      {
         sutil.when(() -> SUtil.localize(anyString(), any(), anyBoolean(), any()))
            .thenReturn("vs78222");
         themes.when(PortalThemesManager::getColorTheme).thenReturn(null);
         exporters.when(() -> AbstractVSExporter.getVSExporter(
               anyInt(), any(), any(), anyBoolean(), any()))
            .thenReturn(exporter);

         Exception ex = assertThrows(IllegalStateException.class, () ->
            service.emailViewsheet(rvs, FileFormatInfo.EXPORT_TYPE_PDF, bookmarks, false, false,
                                   current, "a@b.c", null, null, "s", "b", false, null, null));
         assertEquals("stop78222", ex.getMessage());
      }

      return shown;
   }

   private EmbeddedOnLoadFixture newFixture() throws Exception {
      EmbeddedOnLoadFixture f = new EmbeddedOnLoadFixture();
      fixtures.add(f);
      return f;
   }

   private static FileSystemService cacheIn(Path dir) {
      FileSystemService fs = mock(FileSystemService.class);
      when(fs.getCacheFile(anyString()))
         .thenAnswer(inv -> dir.resolve((String) inv.getArgument(0)).toFile());

      try {
         when(fs.getCacheDirectory()).thenReturn(dir.toString());
      }
      catch(IOException e) {
         throw new RuntimeException(e);
      }

      when(fs.getFile(anyString())).thenAnswer(inv -> new File((String) inv.getArgument(0)));
      return fs;
   }
}
