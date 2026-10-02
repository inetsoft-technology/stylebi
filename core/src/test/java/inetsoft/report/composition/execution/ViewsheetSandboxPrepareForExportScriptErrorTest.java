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
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for Bug #77183: {@link ViewsheetSandbox#prepareForExport()} only recorded
 * {@code exportScriptError} from an exception escaping {@code processOnLoad()}, but the onLoad
 * script itself runs inside {@code executeVSScript()}, which swallows its own exception and never
 * rethrows -- so the "onLoad script failed" export warning never fired. The fix threads a
 * {@code Consumer<Exception>} from {@code prepareForExport()} through
 * {@code processOnLoad()}/{@code executeVSScript()} so that specific failure is captured, scoped
 * tightly enough that a *different* script failure reached through the same call graph (onInit,
 * re-run via {@code reset()} when the onLoad script changes a variable) is not misattributed to
 * it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetSandboxPrepareForExportScriptErrorTest {
   @Test
   void prepareForExport_capturesOnLoadScriptOwnException() throws Exception {
      ViewsheetSandbox box = createSandbox("undefinedFn();", null);

      try {
         box.prepareForExport();

         assertNotNull(box.getExportScriptError(),
            "executeVSScript() swallows the onLoad script's own exception without rethrowing, " +
            "so prepareForExport() must capture it through the handler it now passes down, or " +
            "the \"onLoad script failed\" export warning never fires (Bug #77183)");
      }
      finally {
         box.dispose();
      }
   }

   @Test
   void prepareForExport_doesNotAttributeOnInitFailureToOnLoad() throws Exception {
      // onLoad changes a variable, which makes processOnLoad() call reset(clist) -- the 7-arg
      // reset() unconditionally calls processOnInit() near its end. The sandbox is built with
      // reset=false, so onInit has never run yet and runs for the first time here, inside
      // prepareForExport()'s own call graph. Its failure must NOT surface as exportScriptError:
      // only the one call site inside processOnLoad() for the onLoad script itself is given the
      // handler; processOnInit()'s own call into executeVSScript() always passes null.
      ViewsheetSandbox box = createSandbox("parameter.x = 'set';", "undefinedFn();");

      try {
         box.prepareForExport();

         assertNull(box.getExportScriptError(),
            "the onInit script's own failure (reached via processOnLoad() -> reset(clist) -> " +
            "processOnInit(), since the onLoad script changed a variable) must not be " +
            "attributed to exportScriptError -- only the onLoad script's own executeVSScript() " +
            "call site receives the capture handler (Bug #77183's scoping fix)");
      }
      finally {
         box.dispose();
      }
   }

   private static ViewsheetSandbox createSandbox(String onLoad, String onInit) throws Exception {
      Worksheet ws = new Worksheet();
      Viewsheet vs = new Viewsheet();

      // Viewsheet.setBaseWorksheet() is private and only reachable through a full update()
      // against an asset repository, so wire the base worksheet directly -- same approach as
      // ViewsheetSandboxProcessOnInitTest/VSEmailServiceCreateSandboxTest.
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         "test/ViewsheetSandboxPrepareForExportScriptErrorTest", null);
      vs.setEntry(entry);

      vs.getViewsheetInfo().setScriptEnabled(true);
      vs.getViewsheetInfo().setOnLoad(onLoad);

      if(onInit != null) {
         vs.getViewsheetInfo().setOnInit(onInit);
      }

      return new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
   }
}
