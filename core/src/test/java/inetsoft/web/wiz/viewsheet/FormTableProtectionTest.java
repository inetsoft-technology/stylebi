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

import inetsoft.report.composition.FormTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #80482: the whole-sheet refresh protects a Form table's pending edits the way a script run
 * does (Bug #80034).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class FormTableProtectionTest {
   private Viewsheet vs;
   private ViewsheetSandbox box;

   @BeforeEach
   void setUp() {
      vs = new Viewsheet();
      box = mock(ViewsheetSandbox.class);
   }

   private void table(String name, boolean form, Boolean changed) throws Exception {
      TableVSAssembly table = new TableVSAssembly(vs, name);
      ((TableVSAssemblyInfo) table.getInfo()).setForm(form);
      vs.addAssembly(table);

      if(changed != null) {
         FormTableLens lens = mock(FormTableLens.class);
         when(lens.isChanged()).thenReturn(changed);
         when(box.getFormTableLens(name)).thenReturn(lens);
      }
   }

   @Test
   void protectsAFormTableWithPendingEdits() throws Exception {
      table("AdminTable", true, true);

      FormTableProtection.protectChangedFormTables(box, vs);

      verify(box).addScriptChangedForm("AdminTable");
   }

   @Test
   void leavesAFormTableWithNoPendingEditsAlone() throws Exception {
      table("AdminTable", true, false);

      FormTableProtection.protectChangedFormTables(box, vs);

      verify(box, never()).addScriptChangedForm(any());
   }

   @Test
   void neverAsksANonFormTable() throws Exception {
      table("Plain", false, null);

      FormTableProtection.protectChangedFormTables(box, vs);

      verify(box, never()).getFormTableLens(any());
      verify(box, never()).addScriptChangedForm(any());
   }
}
