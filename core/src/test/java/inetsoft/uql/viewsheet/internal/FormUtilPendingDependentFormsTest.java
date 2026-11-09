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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.composition.FormTableLens;
import inetsoft.report.composition.FormTableRow;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
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
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Bug #80482: names the Form tables that depend on a selection and hold pending edits, using the
 * same dependency test as {@link FormUtil#checkFormData}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class FormUtilPendingDependentFormsTest {
   private Viewsheet vs;
   private ViewsheetSandbox box;
   private RuntimeViewsheet rvs;
   private TableVSAssembly form;
   private SelectionListVSAssembly filter;

   @BeforeEach
   void setUp() {
      vs = new Viewsheet();
      filter = new SelectionListVSAssembly(vs, "Filter");
      vs.addAssembly(filter);
      form = formTable("AdminTable");
      box = mock(ViewsheetSandbox.class);
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
   }

   private TableVSAssembly formTable(String name) {
      TableVSAssembly table = new TableVSAssembly(vs, name);
      ((TableVSAssemblyInfo) table.getInfo()).setForm(true);
      vs.addAssembly(table);
      return table;
   }

   /** A lens whose pending rows are as given; formDataChanged() reads the three row states. */
   private static FormTableLens lens(int added, int changed, int deleted) {
      FormTableLens lens = mock(FormTableLens.class);
      when(lens.rows(FormTableRow.ADDED)).thenReturn(new FormTableRow[added]);
      when(lens.rows(FormTableRow.CHANGED)).thenReturn(new FormTableRow[changed]);
      when(lens.rows(FormTableRow.DELETED)).thenReturn(new FormTableRow[deleted]);
      return lens;
   }

   private List<String> pending(boolean dependsOnFilter) {
      try(MockedStatic<AssetUtil> util = mockStatic(AssetUtil.class)) {
         util.when(() -> AssetUtil.getDependedAssemblies(
            any(Viewsheet.class), any(Assembly.class), anyBoolean(), anyBoolean(), anyBoolean()))
            .thenReturn(dependsOnFilter ? new Assembly[] { filter } : new Assembly[0]);
         return FormUtil.pendingDependentForms(rvs, "Filter");
      }
   }

   @Test
   void namesADependentFormWithPendingEdits() {
      FormTableLens lens = lens(0, 1, 0);
      when(box.getCachedFormTableLens("AdminTable")).thenReturn(lens);

      assertEquals(List.of("AdminTable"), pending(true));
   }

   @Test
   void ignoresADependentFormWithNoPendingEdits() {
      FormTableLens lens = lens(0, 0, 0);
      when(box.getCachedFormTableLens("AdminTable")).thenReturn(lens);

      assertEquals(List.of(), pending(true));
   }

   @Test
   void ignoresAFormThatDoesNotDependOnTheSelection() {
      FormTableLens lens = lens(1, 0, 0);
      when(box.getCachedFormTableLens("AdminTable")).thenReturn(lens);

      assertEquals(List.of(), pending(false));
   }

   @Test
   void neverBuildsAFormTableLens() throws Exception {
      pending(true);

      verify(box, never()).getFormTableLens(any());
   }

   @Test
   void unknownSelectionNameYieldsNothing() {
      assertEquals(List.of(), FormUtil.pendingDependentForms(rvs, "Missing"));
   }
}
