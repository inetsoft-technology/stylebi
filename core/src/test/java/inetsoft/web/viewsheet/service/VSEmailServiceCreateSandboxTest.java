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
package inetsoft.web.viewsheet.service;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XValueNode;
import inetsoft.uql.viewsheet.TextInputVSAssembly;
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
 * Bug #77180: {@link VSEmailService#createSandbox} builds the sandbox used to email a bookmark.
 * It used the constructor reset, which never runs the root viewsheet's onLoad, so the onLoad only
 * ran later through {@code prepareSheet -> prepareForExport()} on the canvas path. The PDF
 * print-layout path (own or inherited from a thin wrapper's child) skips that, so emailed
 * bookmarks lost the root onLoad. createSandbox now mirrors bookmark export in
 * {@code VSExportService}: onInit once, onLoad in the reset, and input-assembly variables cleared
 * before the reset so bookmark-restored selections are not overwritten (Bug #74212).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSEmailServiceCreateSandboxTest {
   @Test
   void createSandbox_runsRootOnLoadAndOnInitOnce() throws Exception {
      Viewsheet bookmark = newBookmark(new Worksheet());
      bookmark.getViewsheetInfo().setScriptEnabled(true);
      bookmark.getViewsheetInfo().setOnInit(
         "parameter.onInit77180 = parameter.onInit77180 ? parameter.onInit77180 + 'x' : 'x';");
      bookmark.getViewsheetInfo().setOnLoad("parameter.onLoad77180 = 'ran';");

      ViewsheetSandbox box = createSandbox(bookmark);

      try {
         VariableTable vars = box.getVariableTable();
         assertEquals("ran", vars.get("onLoad77180"),
            "the root viewsheet's onLoad must run while building the email bookmark sandbox, " +
            "not only later in prepareForExport(), which the PDF print-layout path skips");
         assertEquals("x", vars.get("onInit77180"), "onInit must run exactly once");
      }
      finally {
         box.dispose();
      }
   }

   @Test
   void createSandbox_keepsBookmarkRestoredInputValueOverWorksheetDefault() throws Exception {
      // A worksheet variable with a default value feeds the sandbox variable table under the
      // same name as an input assembly. Without the 74212 clearing, applyParameterToInput()
      // would overwrite the value restored from the bookmark with the worksheet default.
      Worksheet ws = new Worksheet();
      DefaultVariableAssembly varAssembly = new DefaultVariableAssembly(ws, "TextInput1");
      AssetVariable var = new AssetVariable("TextInput1");
      // The Object cast selects createValueNode(Object value, String name); the
      // (String, String) overload means (name, type) and would leave the value null.
      var.setValueNode(XValueNode.createValueNode((Object) "wsDefault", "TextInput1"));
      varAssembly.setVariable(var);
      ws.addAssembly(varAssembly);

      Viewsheet bookmark = newBookmark(ws);
      TextInputVSAssembly input = new TextInputVSAssembly(bookmark, "TextInput1");
      input.setSelectedObject("bookmarked");
      bookmark.addAssembly(input);

      ViewsheetSandbox box = createSandbox(bookmark);

      try {
         TextInputVSAssembly restored =
            (TextInputVSAssembly) box.getViewsheet().getAssembly("TextInput1");
         assertEquals("bookmarked", restored.getSelectedObject(),
            "the bookmark-restored input value must win over the worksheet variable default, " +
            "the same as bookmark export (VSExportService, Bug #74212)");
      }
      finally {
         box.dispose();
      }
   }

   private static ViewsheetSandbox createSandbox(Viewsheet bookmark) throws Exception {
      AssetEntry entry = bookmark.getEntry();
      return new VSEmailService(null)
         .createSandbox(bookmark, AbstractSheet.SHEET_RUNTIME_MODE, null, entry);
   }

   private static Viewsheet newBookmark(Worksheet ws) throws Exception {
      Viewsheet vs = new Viewsheet();

      // Viewsheet.setBaseWorksheet() is private and only reachable through a full update()
      // against an asset repository, so wire the base worksheet directly -- same approach as
      // ViewsheetSandboxProcessOnInitTest.
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         "test/VSEmailServiceCreateSandboxTest", null);
      vs.setEntry(entry);
      return vs;
   }
}
