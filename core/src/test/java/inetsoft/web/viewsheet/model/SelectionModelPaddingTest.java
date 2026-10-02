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
package inetsoft.web.viewsheet.model;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionModelPaddingTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   /** A selection list carrying the modern mark, seeded, and attached to a real viewsheet. */
   private SelectionListVSAssembly markedSelectionList() {
      SelectionListVSAssembly assembly = new SelectionListVSAssembly(new Viewsheet(), "Sel1");
      assembly.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      assembly.getVSAssemblyInfo().initDefaultFormat();
      return assembly;
   }

   /** The model constructor reads the viewsheet back off the runtime, so it needs one to hand over. */
   private RuntimeViewsheet runtimeFor(SelectionListVSAssembly assembly) {
      RuntimeViewsheet rvs = Mockito.mock(RuntimeViewsheet.class);
      Mockito.when(rvs.getViewsheet()).thenReturn(assembly.getViewsheet());
      return rvs;
   }

   @Test
   void theModelCarriesTheSeededInset() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SelectionListVSAssembly assembly = markedSelectionList();

      VSSelectionListModel model = new VSSelectionListModel(assembly, runtimeFor(assembly));

      assertEquals(new Insets(16, 16, 16, 16), model.getPadding());
   }

   @Test
   void theModelDoesNotAliasTheAssemblysInset() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SelectionListVSAssembly assembly = markedSelectionList();

      VSSelectionListModel model = new VSSelectionListModel(assembly, runtimeFor(assembly));

      // the property dialog and the layout controller both mutate the assembly's Insets in place
      assembly.getVSAssemblyInfo().getPadding().top = 99;

      assertNotSame(assembly.getVSAssemblyInfo().getPadding(), model.getPadding());
      assertEquals(16, model.getPadding().top);
   }

   @Test
   void anUnmarkedSelectionCarriesNoInset() {
      SelectionListVSAssembly assembly = new SelectionListVSAssembly(new Viewsheet(), "Sel1");

      VSSelectionListModel model = new VSSelectionListModel(assembly, runtimeFor(assembly));

      assertEquals(new Insets(0, 0, 0, 0), model.getPadding());
   }
}
