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

import inetsoft.report.BaseLayout;
import inetsoft.report.CellBinding;
import inetsoft.report.LibManager;
import inetsoft.report.TableCellBinding;
import inetsoft.report.TableLayout;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.lib.logical.NoopLibrarySecurity;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.CalculateRef;
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
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
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

   /** Bug #79718: the viewsheet's own script, onInit and onLoad are rewritten like assembly scripts. */
   @Test
   void viewsheetOwnScriptOnInitAndOnLoadAreRewrittenByIdentifier() throws Exception {
      Element doc = parse("<assembly><assemblyInfo><script><![CDATA[ab(0); abc(0);]]></script>" +
         "</assemblyInfo><viewsheetInfo><viewsheetInfo>" +
         "<initScript><![CDATA[ab(1); abc(1); x.ab(1);]]></initScript>" +
         "<loadScript><![CDATA[ab(2);]]></loadScript>" +
         "</viewsheetInfo></viewsheetInfo></assembly>");

      new AssetScriptDependencyTransformer(null).renameScript(doc, info());

      assertEquals("cd(0); abc(0);",
                   Tool.getValue(DependencyTransformer.getChildNodes(doc, "./assemblyInfo/script")
                                    .item(0)));
      assertEquals("cd(1); abc(1); x.ab(1);", Tool.getValue(DependencyTransformer.getChildNodes(
         doc, "./viewsheetInfo/viewsheetInfo/initScript").item(0)));
      assertEquals("cd(2);", Tool.getValue(DependencyTransformer.getChildNodes(
         doc, "./viewsheetInfo/viewsheetInfo/loadScript").item(0)));
   }

   private static TableCellBinding cell(int type, String value, String formula) {
      TableCellBinding cell = new TableCellBinding(type, value);
      cell.setFormula(formula);
      return cell;
   }

   /** Serialize a real calc table layout, one cell per column, in a stored viewsheet. */
   private static String calcTableXml(TableCellBinding... cells) {
      TableLayout layout = new TableLayout();
      layout.setColCount(cells.length);
      BaseLayout.Region region = layout.new Region();
      region.setRowCount(1);
      layout.addRegion(new DefaultTableLens(new Object[][] { { "A" }, { 1 } })
                          .getDescriptor().getRowDataPath(0), region);

      for(int i = 0; i < cells.length; i++) {
         layout.setCellBinding(0, i, cells[i]);
      }

      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      layout.writeXML(writer);
      writer.flush();
      return "<viewsheet><assemblies><oneAssembly><assembly><assemblyInfo>" + out +
         "</assemblyInfo></assembly></oneAssembly></assemblies></viewsheet>";
   }

   /** Bug #80071: a calc table formula cell is rewritten, no other kind of cell is. */
   @Test
   void calcTableFormulaCellIsRewrittenButOtherCellsAreNot() throws Exception {
      String xml = calcTableXml(
         cell(CellBinding.BIND_FORMULA, "ab(3300000)", null),
         cell(CellBinding.BIND_FORMULA, "ab(field['x']) + abc(1) + myab(2) + x.ab(3)", null),
         cell(CellBinding.BIND_TEXT, "ab(1)", null),
         cell(CellBinding.BIND_COLUMN, "ab(", "ab("));
      Element doc = parse(xml);

      new AssetScriptDependencyTransformer(null).renameScript(doc, info());

      NodeList values = DependencyTransformer.getChildNodes(doc, "//cellBinding/value");
      assertEquals(4, values.getLength());
      assertEquals("cd(3300000)", Tool.getValue(values.item(0)));
      assertEquals("cd(field['x']) + abc(1) + myab(2) + x.ab(3)", Tool.getValue(values.item(1)));
      assertEquals("ab(1)", Tool.getValue(values.item(2)));
      assertEquals("ab(", Tool.getValue(values.item(3)));
      NodeList formulas = DependencyTransformer.getChildNodes(doc, "//cellBinding/formula");
      assertEquals("ab(", Tool.getValue(formulas.item(formulas.getLength() - 1)));
   }

   /** A cell formula outside the root sheet's own assemblies is not visited. */
   @Test
   void calcTableFormulaOutsideAssembliesIsNotRewritten() throws Exception {
      Element doc = parse("<viewsheet><cellBinding type=\"3\"><value><![CDATA[ab(1)]]></value>" +
                          "</cellBinding></viewsheet>");

      new AssetScriptDependencyTransformer(null).renameScript(doc, info());

      assertEquals("ab(1)", Tool.getValue(
         DependencyTransformer.getChildNodes(doc, "//cellBinding/value").item(0)));
   }

   /** Serialize a column the way StyleBI does, so the fixture carries the real attributes. */
   private static String serialize(ColumnRef column, String script) {
      ExpressionRef expr = new ExpressionRef(null, "c");
      expr.setExpression(script);
      column.setDataRef(expr);
      StringWriter out = new StringWriter();
      column.writeXML(new PrintWriter(out));
      return out.toString();
   }

   @Test
   void calcFieldAndScriptExpressionColumnAreRewrittenButSqlColumnIsNot() throws Exception {
      ColumnRef scriptColumn = new ColumnRef();
      scriptColumn.setSQL(false);
      String xml = serialize(new CalculateRef(), "ab(1) + abX(2)") +
         serialize(scriptColumn, "ab(field['x'])") +
         serialize(new ColumnRef(), "ab(col)");
      Element doc = parse("<viewsheet>" + xml + "</viewsheet>");

      new AssetScriptDependencyTransformer(null).renameScript(doc, info());

      var refs = DependencyTransformer.getChildNodes(
         doc, ".//dataRef[@class='inetsoft.uql.erm.ExpressionRef']");
      assertEquals(3, refs.getLength());
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
