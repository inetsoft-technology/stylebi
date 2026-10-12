/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.asset.delete;

import inetsoft.uql.asset.sync.RenameInfo;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77893: {@link DependencyChecker#checkDataRef} put the deleted column name into XPath
 * string literals, so a name with an apostrophe matched nothing. No Spring context: the
 * end-to-end rows are in {@link DependencyCheckerQuoteTest}.
 */
@Tag("core")
class DependencyCheckerDataRefTest {
   // every alternative the old XPath matched is still matched, also with an apostrophe
   @ParameterizedTest
   @ValueSource(strings = { "Customers", "Customer's", "Mix'\"" })
   void everyDataRefFormIsChecked(String name) throws Exception {
      String cdata = "<![CDATA[" + name + "]]>";
      String attr = Tool.escape(name);
      String[] matches = {
         "<dataRef class=\"inetsoft.uql.erm.ExpressionRef\" name=\"" + attr + "\"/>",
         "<dataRef class=\"inetsoft.uql.erm.AttributeRef\" attribute=\"" + attr + "\"/>",
         "<dataRef class=\"inetsoft.uql.asset.ColumnRef\"><dataRef class=\"x\">" +
            "<dataRef class=\"inetsoft.uql.erm.AttributeRef\" attribute=\"" + attr + "\"/>" +
            "</dataRef></dataRef>",
         "<dataRef class=\"inetsoft.uql.asset.NumericRangeRef\"><attribute>" + cdata +
            "</attribute></dataRef>",
         "<dataRef class=\"inetsoft.uql.asset.DateRangeRef\"><attribute>" + cdata +
            "</attribute></dataRef>",
         "<dataRef class=\"inetsoft.uql.erm.AggregateRef\"><refValue>" + cdata +
            "</refValue></dataRef>",
      };
      String[] misses = {
         // only an expression is matched by its name
         "<dataRef class=\"inetsoft.uql.erm.AttributeRef\" name=\"" + attr + "\"/>",
         // only a range ref is matched by its attribute child
         "<dataRef class=\"inetsoft.uql.erm.AttributeRef\"><attribute>" + cdata +
            "</attribute></dataRef>",
         "<dataRef class=\"inetsoft.uql.erm.AttributeRef\" attribute=\"" + attr + "x\"/>",
         "<dataRef class=\"inetsoft.uql.erm.AggregateRef\"><refValue>" + cdata +
            "x</refValue></dataRef>",
         "<other attribute=\"" + attr + "\"/>",
      };

      for(String xml : matches) {
         assertTrue(check(xml, name), xml);
      }

      for(String xml : misses) {
         assertFalse(check(xml, name), xml);
      }
   }

   private static boolean check(String xml, String name) throws Exception {
      String doc = "<assemblyInfo><ColumnSelection>" + xml + "</ColumnSelection></assemblyInfo>";
      Element elem = Tool.parseXML(new ByteArrayInputStream(doc.getBytes(StandardCharsets.UTF_8)),
                                   "UTF-8", false, false).getDocumentElement();
      DependencyChecker checker = new DependencyChecker() {
         @Override
         protected boolean isSameSource(Element elem, DeleteInfo info) {
            return true;
         }
      };

      return checker.checkDataRef(elem, new DeleteInfo(name, RenameInfo.ASSET | RenameInfo.COLUMN,
                                                       "src", "T"));
   }
}
