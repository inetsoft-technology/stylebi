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

import inetsoft.report.script.viewsheet.RadioButtonVSAScriptable;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.RadioButtonVSAssemblyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redmine #78292. {@code RadioButtonVSAScriptable.getMember("value")} read a {@code cellValue}
 * snapshot that is lost whenever the scope recreates its scriptables (render/export reset), so
 * {@code RadioButton.value} read null while {@code selectedObject} stayed correct.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class InputVSAQueryRadioButtonValueLiveTest {
   private static final String RADIO = "RadioButton1";

   private ViewsheetSandbox sandbox;
   private RadioButtonVSAssemblyInfo info;
   private InputVSAQuery query;

   @BeforeEach
   void setUp() {
      Viewsheet vs = new Viewsheet();
      RadioButtonVSAssembly radio = new RadioButtonVSAssembly(vs, RADIO);
      info = (RadioButtonVSAssemblyInfo) radio.getVSAssemblyInfo();
      vs.addAssembly(radio);

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/InputVSAQueryRadioButtonValueLiveTest",
         null, OrganizationManager.getInstance().getCurrentOrgID());
      sandbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
      query = new InputVSAQuery(sandbox, RADIO);
   }

   private Object value() {
      RadioButtonVSAScriptable scriptable =
         (RadioButtonVSAScriptable) sandbox.getScope().getVSAScriptable(RADIO);
      return scriptable.getMember("value");
   }

   @Test
   void valueReflectsSelectionWithoutRefresh() throws Exception {
      info.setSelectedObject("a");
      assertEquals("a", value());
      query.refreshView((ListData) new ListData().clone());
      assertEquals("a", value());
      info.setSelectedObject("b");
      assertEquals("b", value());
   }

   @Test
   void valueSurvivesScriptableRecreation() throws Exception {
      info.setSelectedObject("a");
      query.refreshView((ListData) new ListData().clone());
      sandbox.getScope().refreshScriptable();
      assertEquals("a", value());
   }

   @Test
   void valueSurvivesScopeClone() throws Exception {
      info.setSelectedObject("a");
      query.refreshView((ListData) new ListData().clone());
      ViewsheetScope clone = (ViewsheetScope) sandbox.getScope().clone();
      RadioButtonVSAScriptable scriptable = (RadioButtonVSAScriptable) clone.getVSAScriptable(RADIO);
      assertEquals("a", scriptable.getMember("value"));
   }

   @Test
   void noSelectionReadsNull() throws Exception {
      query.refreshView((ListData) new ListData().clone());
      sandbox.getScope().refreshScriptable();
      assertNull(value());
   }
}
