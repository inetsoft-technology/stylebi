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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.Hyperlink;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSModelTrapContext;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.erm.AbstractModelTrapContext;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.composer.model.vs.HyperlinkDialogModel;
import inetsoft.web.composer.vs.dialog.HyperlinkDialogService;
import inetsoft.test.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80476: the Composer asks before a plain Table link commits (checkVSTableTrap); the agent
 * path now compares clones before {@code setHyperlinkDialogModel} and returns a warning, and the
 * write is applied either way. Crosstab links have no native check and stay silent.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class AssemblyHyperlinkServiceTrapTest {
   @Test
   void aNewTrapOnATableLinkWarnsAndTheWriteStillHappens() throws Exception {
      Fixture f = new Fixture(mock(TableVSAssembly.class));

      try(MockedStatic<DataSourceRegistry> registry = Mockito.mockStatic(DataSourceRegistry.class);
          MockedConstruction<VSModelTrapContext> ctx = trapContext(true))
      {
         List<String> warnings = f.service.set(
            "tok", principal(), "Table1", null, link(), "");

         assertEquals(List.of("Data-model trap: Chasm Trap is on Orders, Returns. " +
                              "The write was applied; use undo to revert it."), warnings);
         verify(f.links).setHyperlinkDialogModel(eq("rt1"), eq("Table1"), any(), anyString(),
                                                 any(Principal.class), any());

         ArgumentCaptor<VSAssemblyInfo> oinfo = ArgumentCaptor.forClass(VSAssemblyInfo.class);
         ArgumentCaptor<VSAssemblyInfo> ninfo = ArgumentCaptor.forClass(VSAssemblyInfo.class);
         verify(ctx.constructed().get(0)).checkTrap(oinfo.capture(), ninfo.capture());
         TableVSAssemblyInfo before = (TableVSAssemblyInfo) oinfo.getValue();
         TableVSAssemblyInfo after = (TableVSAssemblyInfo) ninfo.getValue();
         assertNotSame(f.info, before, "the old side must be a clone");
         assertNotSame(f.info, after, "the new side must be a clone");
         assertNull(before.getHyperlinkAttr(), "the old side has no link");
         assertNotNull(after.getHyperlinkAttr().getHyperlink(f.path), "the new side has it");
         assertNull(f.info.getHyperlinkAttr(), "the live info is untouched by the check");
      }
   }

   @Test
   void noNewTrapIsSilent() throws Exception {
      Fixture f = new Fixture(mock(TableVSAssembly.class));

      try(MockedStatic<DataSourceRegistry> registry = Mockito.mockStatic(DataSourceRegistry.class);
          MockedConstruction<VSModelTrapContext> ctx = trapContext(false))
      {
         List<String> warnings = f.service.set(
            "tok", principal(), "Table1", null, link(), "");

         assertTrue(warnings.isEmpty(), warnings.toString());
         verify(f.links).setHyperlinkDialogModel(eq("rt1"), eq("Table1"), any(), anyString(),
                                                 any(Principal.class), any());
      }
   }

   /** A crosstab link has no native trap check: the detector is never even built. */
   @Test
   void aCrosstabLinkIsNotChecked() throws Exception {
      Fixture f = new Fixture(mock(CrosstabVSAssembly.class));

      try(MockedStatic<DataSourceRegistry> registry = Mockito.mockStatic(DataSourceRegistry.class);
          MockedConstruction<VSModelTrapContext> ctx = trapContext(true))
      {
         List<String> warnings = f.service.set(
            "tok", principal(), "Table1", null, link(), "");

         assertTrue(warnings.isEmpty(), warnings.toString());
         assertTrue(ctx.constructed().isEmpty());
      }
   }

   private static MockedConstruction<VSModelTrapContext> trapContext(boolean trap) {
      return Mockito.mockConstruction(VSModelTrapContext.class, (context, c) -> {
         AbstractModelTrapContext.TrapInfo info = mock(AbstractModelTrapContext.TrapInfo.class);
         when(info.showWarning()).thenReturn(trap);
         when(context.isCheckTrap()).thenReturn(true);
         when(context.checkTrap(any(), any())).thenReturn(info);
         when(context.getTrapCondition()).thenReturn("Chasm Trap is on Orders, Returns");
      });
   }

   private static Map<String, Object> link() {
      Map<String, Object> link = new LinkedHashMap<>();
      link.put("linkType", "web");
      link.put("webLink", "https://example.com");
      return link;
   }

   private static Principal principal() {
      return () -> "admin";
   }

   private static final class Fixture {
      Fixture(TableDataVSAssembly assembly) throws Exception {
         Viewsheet vs = mock(Viewsheet.class);
         when(vs.getAssembly("Table1")).thenReturn(assembly);
         info = new TableVSAssemblyInfo();
         when(assembly.getInfo()).thenReturn(info);
         when(assembly.getAbsoluteName()).thenReturn("Table1");
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);
         when(rvs.getID()).thenReturn("rt1");
         ViewsheetSandbox box = mock(ViewsheetSandbox.class);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

         VSTableLens lens = mock(VSTableLens.class);
         when(lens.getColCount()).thenReturn(3);
         when(lens.getRowCount()).thenReturn(10);
         when(lens.moreRows(intThat(r -> r >= 0 && r < 10))).thenReturn(true);
         when(lens.getTableDataPath(anyInt(), anyInt())).thenReturn(path);
         when(box.getVSTableLens("Table1", false)).thenReturn(lens);

         ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());

         links = mock(HyperlinkDialogService.class);
         when(links.getHyperlinkDialogModel(anyString(), anyString(), any(), any(), any(),
                                            anyBoolean(), anyBoolean(), anyBoolean(),
                                            anyBoolean(), any(Principal.class)))
            .thenReturn(new HyperlinkDialogModel());
         Hyperlink hyperlink = new Hyperlink();
         hyperlink.setLinkType(Hyperlink.WEB_LINK);
         hyperlink.setLink("https://example.com");
         when(links.getHyperlink(any(), any())).thenReturn(hyperlink);
         service = new AssemblyHyperlinkService(sessions, links);
      }

      final TableVSAssemblyInfo info;
      final TableDataPath path = new TableDataPath(-1, TableDataPath.DETAIL, "string",
                                                   new String[]{ "A" });
      final HyperlinkDialogService links;
      final AssemblyHyperlinkService service;
   }
}
