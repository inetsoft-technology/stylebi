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

import inetsoft.report.script.viewsheet.CheckBoxVSAScriptable;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CheckBoxVSAssemblyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Redmine #76699 / VFO-017. {@code CheckBoxVSAScriptable.getMember("value")} used to prefer a
 * {@code cellValue} snapshot taken by {@code InputVSAQuery.refreshListData} over a live read, so a
 * selection change applied directly to {@code CheckBoxVSAssemblyInfo} (as {@code set_input_value}
 * does) was invisible to a script's bare {@code CheckBox.value} read until some later call
 * happened to re-run {@code refreshListData} for that assembly -- unlike {@code selectedObjects}/
 * {@code selectedLabels}, which always read the live model directly and never went stale this way.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class InputVSAQueryCheckBoxValueLiveTest {
   private static final String CHECKBOX = "CheckBox1";

   private ViewsheetSandbox sandbox;
   private CheckBoxVSAssemblyInfo info;
   private InputVSAQuery query;

   @BeforeEach
   void setUp() {
      Viewsheet vs = new Viewsheet();
      CheckBoxVSAssembly checkBox = new CheckBoxVSAssembly(vs, CHECKBOX);
      info = (CheckBoxVSAssemblyInfo) checkBox.getVSAssemblyInfo();
      vs.addAssembly(checkBox);

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/InputVSAQueryCheckBoxValueLiveTest",
         null, OrganizationManager.getInstance().getCurrentOrgID());
      sandbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
      query = new InputVSAQuery(sandbox, CHECKBOX);
   }

   private Object value() {
      CheckBoxVSAScriptable scriptable =
         (CheckBoxVSAScriptable) sandbox.getScope().getVSAScriptable(CHECKBOX);
      return scriptable.getMember("value");
   }

   /** The confirmed VFO-017 mechanism: a live model change is visible without a re-run of refreshListData. */
   @Test
   void valueReflectsASelectionChangeMadeAfterTheLastRefresh() throws Exception {
      info.setSelectedObjects(new Object[]{ "itemA" });
      query.refreshView((ListData) new ListData().clone());

      assertArrayEquals(new Object[]{ "itemA" }, (Object[]) value(),
                        "value must match the selection that was current at the last refresh");

      // Simulate a clear applied directly to the model (as set_input_value does) with no
      // subsequent refreshListData call before the next script read -- the exact shape of the
      // render-interleaved staleness reported in Redmine #76699.
      info.setSelectedObjects(new Object[0]);

      assertArrayEquals(new Object[0], (Object[]) value(),
                        "value must track the live selection, not a stale pre-clear snapshot");
   }

   /** Symmetry with the sibling live getters this bug's asymmetry was measured against. */
   @Test
   void valueMatchesSelectedObjectsAtEveryPoint() throws Exception {
      CheckBoxVSAScriptable scriptable =
         (CheckBoxVSAScriptable) sandbox.getScope().getVSAScriptable(CHECKBOX);

      info.setSelectedObjects(new Object[]{ "itemA" });
      query.refreshView((ListData) new ListData().clone());
      assertArrayEquals((Object[]) scriptable.getSelectedObjectsArray(), (Object[]) value());

      info.setSelectedObjects(new Object[0]);
      assertArrayEquals((Object[]) scriptable.getSelectedObjectsArray(), (Object[]) value());
   }
}
