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
package inetsoft.uql.asset.sync;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.lib.logical.NoopLibrarySecurity;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78759: renaming a library function rewrites the text of every dependent that calls it,
 * by identifier and only in code: viewsheet and worksheet scripts and expressions, and other
 * library scripts.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  AssetScriptDependencyTransformerRenameTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AssetScriptDependencyTransformerRenameTest {
   @Configuration
   static class Config {
      @Bean
      RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }

      @Bean
      LibManagerProvider libManagerProvider() {
         return mock(LibManagerProvider.class);
      }
   }

   @Autowired private BlobStorageManager blobStorageManager;
   @Autowired private LibManagerProvider libManagerProvider;

   private static RenameInfo info() {
      return new RenameInfo("ab", "cd", RenameInfo.SCRIPT_FUNCTION);
   }

   private static Element parse(String xml) throws Exception {
      return Tool.parseXML(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                           "UTF-8", false, false).getDocumentElement();
   }

   @Test
   void viewsheetScriptIsRewrittenByIdentifier() throws Exception {
      Element doc = parse("<viewsheet><assemblies><oneAssembly><assembly><assemblyInfo>" +
         "<script><![CDATA[T.text = ab(1) + \"|\" + abX(2) + \"|label-ab\";]]></script>" +
         "</assemblyInfo></assembly></oneAssembly></assemblies></viewsheet>");

      new AssetScriptDependencyTransformer(null).renameScript(doc, info());

      assertEquals("T.text = cd(1) + \"|\" + abX(2) + \"|label-ab\";",
                   Tool.getValue(DependencyTransformer.getChildNodes(doc, "//script").item(0)));
   }

   @Test
   void calcFieldAndExpressionColumnAreRewritten() throws Exception {
      Element doc = parse("<viewsheet>" +
         "<dataRef class=\"inetsoft.uql.viewsheet.CalculateRef\">" +
         "<dataRef class=\"inetsoft.uql.erm.ExpressionRef\"><![CDATA[ab(1) + abX(2)]]></dataRef>" +
         "</dataRef>" +
         "<dataRef class=\"inetsoft.uql.asset.ColumnRef\" sql=\"false\">" +
         "<dataRef class=\"inetsoft.uql.erm.ExpressionRef\"><![CDATA[ab(field['x'])]]></dataRef>" +
         "</dataRef>" +
         "<dataRef class=\"inetsoft.uql.asset.ColumnRef\" sql=\"true\">" +
         "<dataRef class=\"inetsoft.uql.erm.ExpressionRef\"><![CDATA[ab(col)]]></dataRef>" +
         "</dataRef></viewsheet>");

      new AssetScriptDependencyTransformer(null).renameScript(doc, info());

      var refs = DependencyTransformer.getChildNodes(
         doc, ".//dataRef[@class='inetsoft.uql.erm.ExpressionRef']");
      assertEquals("cd(1) + abX(2)", Tool.getValue(refs.item(0)));
      assertEquals("cd(field['x'])", Tool.getValue(refs.item(1)));
      assertEquals("ab(col)", Tool.getValue(refs.item(2)));
   }

   @Test
   void libraryCallerIsRewrittenAndNothingElseIsTouched() throws Exception {
      LibManager lib = new LibManager(new NoopLibrarySecurity(), "r78759-org", mock(Cluster.class),
                                      blobStorageManager) { };
      when(libManagerProvider.getManager(anyString())).thenReturn(lib);
      String caller = "function H(v) {\n   // old ab(1) stays in this comment\n" +
         "   return ab(v) + abX(v) + other(v);\n}";
      lib.setScript("H", caller);
      lib.setScript("other", "function other(v) { return ab(v); }");
      AssetEntry entry = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
                                        AssetEntry.Type.SCRIPT, "H", null);

      new AssetScriptDependencyTransformer(entry).process(List.of(info()));

      assertEquals("function H(v) {\n   // old ab(1) stays in this comment\n" +
                      "   return cd(v) + abX(v) + other(v);\n}",
                   lib.getScript("H"));
      // a function that is not the dependent is left alone
      assertEquals("function other(v) { return ab(v); }", lib.getScript("other"));
   }
}
