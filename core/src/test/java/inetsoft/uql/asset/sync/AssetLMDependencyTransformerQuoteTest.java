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

import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.*;

import java.io.*;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #77893: renaming a logical model attribute must rename the short-form
 * {@code field['Total']} expressions of the worksheet tables built on the renamed column. The
 * worksheet table name was put into an XPath string literal, so a name with an apostrophe made
 * the XPath fail silently and the expressions kept the old name. Run through the public
 * {@link DependencyTransformer#transformAsset} on the import-time asset-file path.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  AssetLMDependencyTransformerQuoteTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // about 10 s alone: the Spring context with the real dependency storage
class AssetLMDependencyTransformerQuoteTest {
   private static final String ENTITY = "Customers";

   @TempDir
   Path tempDir;

   // the table is named after the entity, its own expression uses the short form
   @ParameterizedTest
   @ValueSource(strings = { "Customers", "Customer's", "Say \"hi\"", "Mix'\"" })
   void selfExpressionFollowsAttributeRename(String entity) throws Exception {
      Worksheet ws = new Worksheet();
      BoundTableAssembly table = boundTable(ws, entity, entity);
      addExpression(table, "SelfCalc", "field['Total'] * 2");
      ws.addAssembly(table);

      File file = transform(ws, entity);

      assertEquals("field['Revenue'] * 2", expression(file, "SelfCalc"));
   }

   // a mirror of the table refers to the column by the table name
   @ParameterizedTest
   @ValueSource(strings = { "Sales", "Sale's", "Sa\"les", "Mix'\"" })
   void mirrorExpressionFollowsAttributeRename(String tableName) throws Exception {
      Worksheet ws = new Worksheet();
      BoundTableAssembly table = boundTable(ws, tableName, ENTITY);
      ws.addAssembly(table);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", table);
      addExpression(mirror, "MirrorCalc", "field['Total'] + 1");
      ws.addAssembly(mirror);

      File file = transform(ws, ENTITY);

      assertEquals("field['Revenue'] + 1", expression(file, "MirrorCalc"));
   }

   // a mirror whose columns don't include the renamed one keeps its expression
   @ParameterizedTest
   @ValueSource(strings = { "Sales", "Sale's" })
   void unrelatedMirrorExpressionIsNotRenamed(String tableName) throws Exception {
      Worksheet ws = new Worksheet();
      BoundTableAssembly table = boundTable(ws, tableName, ENTITY);
      ws.addAssembly(table);
      BoundTableAssembly other = boundTable(ws, "Other's", "Other");
      ws.addAssembly(other);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", other);
      addExpression(mirror, "MirrorCalc", "field['Total'] + 1");
      ws.addAssembly(mirror);

      File file = transform(ws, ENTITY);

      assertEquals("field['Total'] + 1", expression(file, "MirrorCalc"));
   }

   private static BoundTableAssembly boundTable(Worksheet ws, String name, String entity) {
      BoundTableAssembly table = new BoundTableAssembly(ws, name);
      table.setSourceInfo(new SourceInfo(SourceInfo.MODEL, "ds", "model"));
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(new ColumnRef(new AttributeRef(entity, "Total")));
      table.setColumnSelection(cols, false);
      return table;
   }

   private static void addExpression(AbstractTableAssembly table, String name, String exp) {
      ExpressionRef ref = new ExpressionRef(null, name);
      ref.setExpression(exp);
      ColumnSelection cols = table.getColumnSelection(false);
      cols.addAttribute(new ColumnRef(ref));
      table.setColumnSelection(cols, false);
   }

   // rename attribute <entity>.Total to <entity>.Revenue as LogicalModelService does
   private File transform(Worksheet ws, String entity) throws Exception {
      File file = Files.createTempFile(tempDir, "ws", ".xml").toFile();

      try(PrintWriter writer = new PrintWriter(new OutputStreamWriter(
         new FileOutputStream(file), StandardCharsets.UTF_8)))
      {
         writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
         ws.writeXML(writer);
      }

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.WORKSHEET, "ws77893", null);
      RenameInfo rinfo = new RenameInfo(entity + ".Total", entity + ".Revenue",
                                        RenameInfo.LOGIC_MODEL | RenameInfo.COLUMN,
                                        "model", entity);
      rinfo.setPrefix("ds");
      rinfo.setOldEntity(entity);
      RenameDependencyInfo dinfo = new RenameDependencyInfo();
      dinfo.addRenameInfo(entry, rinfo);
      dinfo.setAssetFile(entry, file);
      DependencyTransformer.transformAsset(entry, dinfo);
      return file;
   }

   private static String expression(File file, String name) throws Exception {
      try(InputStream in = new FileInputStream(file)) {
         Document doc = Tool.parseXML(in, "UTF-8", false, false);
         NodeList refs = doc.getElementsByTagName("dataRef");

         for(int i = 0; i < refs.getLength(); i++) {
            Element ref = (Element) refs.item(i);

            if("inetsoft.uql.erm.ExpressionRef".equals(ref.getAttribute("class")) &&
               name.equals(ref.getAttribute("name")))
            {
               return Tool.getValue(ref);
            }
         }
      }

      throw new AssertionError("expression " + name + " not found");
   }

   @Configuration
   static class Beans {
      // the constructor is package private; the transformer reads the dependency storage
      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager storage)
         throws Exception
      {
         Constructor<DependencyStorageService> ctor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         ctor.setAccessible(true);
         return ctor.newInstance(storage);
      }
   }
}
