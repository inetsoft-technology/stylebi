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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.SliderVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.InputVSAssemblyInfo;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.viewsheet.service.VSInputService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78579, attempt 2: the wiz write-back pre-check must not depend on calling anything new on
 * the {@code VSInputService} bean.
 *
 * <p>Live, that bean is a Spring CGLIB proxy whose class Spring AOT pre-generates at build time.
 * A public method added after that class was generated is not overridden, so a call to it runs
 * on the proxy instance itself, whose fields are null -- the first fix's
 * {@code VSInputService.getWriteBackSandbox} NPE'd that way and turned every write-back
 * {@code set_input_value} into a 500. Unit tests that build a plain {@code VSInputService} cannot
 * see that, so here {@code VSInputService} is a bare mock: the pre-check has to refuse on its own,
 * and the only call it may make into the bean is the existing {@code singleApplySelection}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
               InputValueServiceWriteBackProxySafetyTest.Config.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class InputValueServiceWriteBackProxySafetyTest {
   @Configuration
   static class Config {
      @Bean
      AssetDataCache assetDataCache() {
         return mock(AssetDataCache.class);
      }
   }

   private static final String TABLE = "T78579";
   private static final String SLIDER = "Slider1";

   @Test
   void refusalNeedsNothingFromTheInputServiceBean() throws Exception {
      Fixture f = Fixture.topLevel();
      f.bind(5);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> f.setValue(SLIDER));
      assertTrue(e.getMessage().contains(TABLE) && e.getMessage().contains("row 5"),
                 e.getMessage());
      verifyNoInteractions(f.inputs);
   }

   @Test
   void embeddedRefusalNeedsNothingFromTheInputServiceBean() throws Exception {
      Fixture f = Fixture.embedded();
      f.bind(5);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> f.setValue("EVS." + SLIDER));
      assertTrue(e.getMessage().contains(TABLE) && e.getMessage().contains("row 5"),
                 e.getMessage());
      verifyNoInteractions(f.inputs);
   }

   @Test
   void validRowCallsOnlySingleApplySelection() throws Exception {
      Fixture f = Fixture.topLevel();
      f.bind(1);

      assertDoesNotThrow(() -> f.setValue(SLIDER));
      verify(f.inputs).singleApplySelection(eq("rt1"), eq(SLIDER), eq(40.0), any(), any(), any());
      verifyNoMoreInteractions(f.inputs);
   }

   /**
    * The resolution is the one {@code CoreLifecycleService.getSandbox} computed before it was made
    * to delegate here (the pre-change recursion is reproduced in {@link #legacyGetSandbox}).
    */
   @Test
   void sandboxForAssemblyMatchesTheLegacyResolution() throws Exception {
      Fixture f = Fixture.embedded();
      ViewsheetSandbox box = f.box;
      CoreLifecycleService lifecycle = mock(CoreLifecycleService.class);
      when(lifecycle.getSandbox(any(), anyString())).thenCallRealMethod();

      assertSame(box, box.getSandboxForAssembly(SLIDER));
      ViewsheetSandbox evs = box.getSandboxForAssembly("EVS." + SLIDER);
      assertNotNull(evs);
      assertNotSame(box, evs);
      assertSame(box.getSandbox("EVS"), evs);
      assertNull(box.getSandboxForAssembly("NOSUCH." + SLIDER));

      for(String name : List.of(SLIDER, "EVS." + SLIDER, "NOSUCH." + SLIDER)) {
         assertSame(legacyGetSandbox(box, name), box.getSandboxForAssembly(name), name);
         assertSame(legacyGetSandbox(box, name), lifecycle.getSandbox(box, name), name);
      }

      assertNull(lifecycle.getSandbox(null, SLIDER));
   }

   /** {@code CoreLifecycleService.getSandbox} as it was before #78579. */
   private static ViewsheetSandbox legacyGetSandbox(ViewsheetSandbox box, String name) {
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox box0 = box.getSandbox(name.substring(0, index));
         return legacyGetSandbox(box0, name.substring(index + 1));
      }

      return box;
   }

   private static final class Fixture {
      final Viewsheet owner;
      final SliderVSAssembly slider;
      final ViewsheetSandbox box;
      final VSInputService inputs = mock(VSInputService.class);
      final InputValueService service;
      final Principal principal = mock(Principal.class);

      static Fixture topLevel() throws Exception {
         Viewsheet root = new Viewsheet();
         setBaseWorksheet(root, worksheetWithTable());
         root.setEntry(entry("root"));
         return new Fixture(root, root);
      }

      /** Root VS whose own worksheet has NO table T78579; the embedded EVS's worksheet has it. */
      static Fixture embedded() throws Exception {
         Viewsheet root = new Viewsheet();
         setBaseWorksheet(root, new Worksheet());
         root.setEntry(entry("root"));
         Viewsheet evs = new Viewsheet();
         setBaseWorksheet(evs, worksheetWithTable());
         evs.setEntry(entry("evs"));
         evs.createVSAssembly("EVS");
         root.addAssembly(evs);
         return new Fixture(root, evs);
      }

      private Fixture(Viewsheet root, Viewsheet owner) throws Exception {
         this.owner = owner;
         slider = new SliderVSAssembly(owner, SLIDER);
         owner.addAssembly(slider);
         ((InputVSAssemblyInfo) slider.getVSAssemblyInfo()).setSelectedObject(30.0);

         box = new ViewsheetSandbox(root, AbstractSheet.SHEET_RUNTIME_MODE, null, false,
                                    root.getEntry());
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(root);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
         when(rvs.getID()).thenReturn("rt1");

         ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
         when(sessions.mutate(anyString(), any(), any())).thenAnswer(inv -> {
            ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
            return CapturingCommandDispatcher.withCapturingDispatcher(principal, d -> {
               mutation.run(rvs, "rt1", d);
               return d.getWarnings();
            });
         });
         service = new InputValueService(sessions, inputs, mock(CoreLifecycleService.class));
      }

      /** Binds the slider to TABLE.qty with writeBackDirectly and a static row. */
      void bind(int row) {
         InputVSAssemblyInfo info = (InputVSAssemblyInfo) slider.getVSAssemblyInfo();
         info.setTableName(TABLE);
         info.setColumnValue("qty");
         info.setRowValue(String.valueOf(row));
         info.setWriteBackValue(true);
         assertEquals(row, info.getRow());
      }

      void setValue(String assembly) throws Exception {
         service.setValue("tok", principal, assembly, List.of(40.0), null);
      }
   }

   private static Worksheet worksheetWithTable() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly embedded = new EmbeddedTableAssembly(ws, TABLE);
      embedded.setEmbeddedData(new XEmbeddedTable(
         new String[]{ XSchema.STRING, XSchema.INTEGER },
         new Object[][] {
            { "state", "qty" },
            { "CA", 10 },
            { "NY", 20 }
         }));
      ws.addAssembly(embedded);
      return ws;
   }

   private static void setBaseWorksheet(Viewsheet vs, Worksheet ws) throws Exception {
      Method m = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      m.setAccessible(true);
      m.invoke(vs, ws);
   }

   private static AssetEntry entry(String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                            "test/InputValueServiceWriteBackProxySafetyTest/" + name, null,
                            OrganizationManager.getInstance().getCurrentOrgID());
   }
}
