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
package inetsoft.uql.asset.sync;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #77893: the short-form {@code field['Total']} expressions of a table built on the renamed
 * table are found by matching the inner table name of its column refs. The name was put into
 * an XPath string literal, so a name with an apostrophe matched nothing. No Spring context: the
 * end-to-end rows are in {@link AssetLMDependencyTransformerQuoteTest}.
 */
@Tag("core")
class AssetLMDependencyTransformerEntityMatchTest {
   @ParameterizedTest
   @ValueSource(strings = { "Sales", "Sale's", "Sa\"les", "Mix'\"" })
   void expressionOfTableOnRenamedTableIsRenamed(String table) throws Exception {
      Element exp = rename(table, table);

      assertEquals("field['Revenue'] + 1", Tool.getValue(exp));
   }

   @ParameterizedTest
   @ValueSource(strings = { "Sales", "Sale's" })
   void expressionOfTableOnOtherTableIsNotRenamed(String table) throws Exception {
      Element exp = rename(table, "Other's");

      assertEquals("field['Total'] + 1", Tool.getValue(exp));
   }

   // a mirror whose column refers to innerTable.Total, after Customers.Total -> Revenue in table
   private static Element rename(String table, String innerTable) throws Exception {
      String xml = "<worksheet><assemblies><oneAssembly><assembly><assemblyInfo>" +
         "<ColumnSelection>" +
         "<dataRef class=\"inetsoft.uql.asset.ColumnRef\">" +
         "<dataRef class=\"inetsoft.uql.erm.AttributeRef\" entity=\"" + Tool.escape(innerTable) +
         "\" attribute=\"Total\"/></dataRef>" +
         "<dataRef class=\"inetsoft.uql.asset.ColumnRef\">" +
         "<dataRef class=\"inetsoft.uql.erm.ExpressionRef\" name=\"MirrorCalc\">" +
         "<![CDATA[field['Total'] + 1]]></dataRef></dataRef>" +
         "</ColumnSelection></assemblyInfo></assembly></oneAssembly></assemblies></worksheet>";
      Element root = Tool.parseXML(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                                   "UTF-8", false, false).getDocumentElement();
      AssetLMDependencyTransformer transformer = new AssetLMDependencyTransformer(
         new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws", null));
      RenameInfo info = new RenameInfo("Customers.Total", "Customers.Revenue",
                                       RenameInfo.LOGIC_MODEL | RenameInfo.COLUMN,
                                       "model", "Customers");
      info.setPrefix("ds");
      info.setOldEntity("Customers");
      NodeList assemblies = DependencyTransformer.getChildNodes(
         root, AssetDependencyTransformer.ASSET_DEPEND_ELEMENTS);
      transformer.renameDependExpressionRef(assemblies, info, table);

      NodeList refs = root.getElementsByTagName("dataRef");

      for(int i = 0; i < refs.getLength(); i++) {
         Element ref = (Element) refs.item(i);

         if("MirrorCalc".equals(ref.getAttribute("name"))) {
            return ref;
         }
      }

      throw new AssertionError("expression not found");
   }
}
