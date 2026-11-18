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
package inetsoft.web.binding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81635: the script editor must not list names the script engine does not define.
 */
@Tag("core")
class ScriptEditorUndefinedNamesTest {
   private static final List<String> RHINO_ERA = Arrays.asList(
      "Call", "Continuation", "Iterator", "Namespace", "QName", "Script", "StopIteration",
      "StyleReport", "TOC", "With", "XML", "XMLList", "XType", "dataBinding", "net",
      "registerPackage", "reprint", "setChanged", "showReport", "uneval", "getClass");

   @Test
   void globalClassesTreeHasNoUndefinedNames() {
      for(String entry : VSScriptableController.CHART_GLOBAL_CLASSES) {
         String name = entry.split("[(\\[]")[0].trim();
         assertFalse(RHINO_ERA.contains(name), name + " is not defined by the script engine");
      }
   }

   @Test
   void jsFunctionsHasNoUndefinedNames() throws Exception {
      try(InputStream in = getClass().getResourceAsStream("/inetsoft/web/binding/js-functions.json")) {
         JsonNode root = new ObjectMapper().readTree(in);

         for(String name : RHINO_ERA) {
            assertFalse(root.has(name), name + " is not defined by the script engine");
         }

         JsonNode style = root.get("StyleConstant");
         assertNotNull(style);

         for(String name : Arrays.asList("ARROW", "BOOLEAN", "ROLE", "TIME_INSTANT", "XSHAPE",
                                         "DESIGN_MODE", "LIVE_MODE", "CLASSIC", "USER_DEFINED"))
         {
            assertFalse(style.has(name), "StyleConstant." + name + " is not defined");
         }
      }
   }

   @Test
   void crosstabAndCalcTableDoNotListFormTableMethods() throws Exception {
      Properties props = new Properties();

      try(InputStream in = getClass().getResourceAsStream("/inetsoft/web/binding/assembly.properties")) {
         props.load(in);
      }

      for(String m : Arrays.asList("appendRow", "commit", "deleteRow", "getFormRow",
                                   "getFormRows", "getHiddenColumnValue", "insertRow",
                                   "setObject"))
      {
         assertNull(props.getProperty("Crosstab." + m), "Crosstab." + m);
      }

      assertNull(props.getProperty("CalcTable.setObject"));
      assertNotNull(props.getProperty("Table.appendRow"));
   }
}
