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

import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for bug #79532. The worksheet sandbox reads the viewsheet's own variables (the
 * variable a table condition references) through its additional variable provider. Replacing the
 * runtime viewsheet with {@link ViewsheetSandbox#setViewsheet(Viewsheet, boolean)} (what a save
 * does) left that provider on the replaced viewsheet, so parameter discovery kept reporting the
 * old viewsheet's variables until a render happened to call resetRuntime().
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetSandboxVariableProviderTest {
   private Viewsheet vs;
   private ViewsheetSandbox sandbox;

   @BeforeEach
   void setUp() throws Exception {
      vs = withBaseWorksheet(new Viewsheet());
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         "test/ViewsheetSandboxVariableProviderTest", null,
         OrganizationManager.getInstance().getCurrentOrgID());
      sandbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
   }

   @Test
   void replacingTheViewsheetRepointsTheVariableProvider() throws Exception {
      AssetQuerySandbox wbox = sandbox.getAssetQuerySandbox();
      assertNotNull(wbox, "precondition: the sandbox has a worksheet sandbox");
      assertSame(vs, wbox.getAdditionalVariableProvider());

      Viewsheet replacement = withBaseWorksheet(new Viewsheet());
      sandbox.setViewsheet(replacement, false);

      assertSame(replacement, sandbox.getViewsheet());
      assertSame(replacement, wbox.getAdditionalVariableProvider(),
                 "variables must be read from the current viewsheet, not the replaced one");
   }

   // Viewsheet.setBaseWorksheet() is private; wire the field directly (as the sibling tests do).
   private static Viewsheet withBaseWorksheet(Viewsheet sheet) throws Exception {
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(sheet, new Worksheet());
      return sheet;
   }
}
