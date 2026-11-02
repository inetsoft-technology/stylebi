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
package inetsoft.report.composition.execution;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79738: applying a shared filter between two cube-bound selection lists must not spin
 * forever in {@code ViewsheetSandbox.noSelectionValues} when the target already has a selected
 * value that differs from the source's first value.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetSandboxSharedCubeSelectionTest {
   @Test
   void sharedCubeListsWithDifferentSelectionTerminate() {
      SelectionListVSAssembly source = list("Source", "A", "B");
      source.setStateSelectionList(selected("A"));
      SelectionListVSAssembly target = list("Target", "C", "D");
      target.setStateSelectionList(selected("C"));

      int hint = assertTimeoutPreemptively(Duration.ofSeconds(20),
         () -> ViewsheetSandbox.processSharedSelection(target, source));

      assertEquals(0, hint);
   }

   @Test
   void sharedCubeListsTargetSelectionInSourceListTerminate() {
      SelectionListVSAssembly source = list("Source", "A", "B");
      source.setStateSelectionList(selected("A"));
      SelectionListVSAssembly target = list("Target", "A", "D");
      target.setStateSelectionList(selected("D"));

      assertTimeoutPreemptively(Duration.ofSeconds(20),
         () -> ViewsheetSandbox.processSharedSelection(target, source));
   }

   private static SelectionListVSAssembly list(String name, String... vals) {
      SelectionListVSAssembly assembly = new SelectionListVSAssembly(new Viewsheet(), name);
      assembly.setTableName("___inetsoft_cube_cube1");
      assembly.setSelectionList(values(vals));
      return assembly;
   }

   private static SelectionList values(String... vals) {
      SelectionList list = new SelectionList();

      for(String val : vals) {
         list.addSelectionValue(new SelectionValue(val, val));
      }

      return list;
   }

   /** A state list holds only the selected values. */
   private static SelectionList selected(String... vals) {
      SelectionList list = values(vals);

      for(SelectionValue sv : list.getSelectionValues()) {
         sv.setSelected(true);
      }

      return list;
   }
}
