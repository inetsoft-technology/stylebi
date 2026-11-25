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
package inetsoft.mv;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82323: an MV must not be built on a rotated table or on a table whose data comes
 * through one. A rotated column holds the values of a source row, so it mixes types (a date,
 * a number, a boolean); {@code RotatedTableLens.getColType} reports it as a string, and the MV
 * dictionary would turn every value into text. The analyzer must build the MV on the rotated
 * table's source table instead, and rotate at query time.
 * <p>
 * The viewsheet is built the way the server builds it: {@code Viewsheet.createMirrorTables}
 * renames the bound rotated table {@code Query2} to {@code Query2_O} and puts a mirror named
 * {@code Query2} on top of it, so the analyzer's root MV table is that mirror.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  VSMVAnalyzerRotatedTableTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSMVAnalyzerRotatedTableTest {
   @Test
   void viewsheetMirrorOverARotatedTableGetsASubMVOnTheSourceTable() throws Exception {
      Fixture fx = new Fixture();
      fx.rotatedOverGrouped();
      fx.selection("SelectionList1", "Query2", "2005");
      fx.table("Table1", "Query2");
      fx.createMirrorTables();

      // the server's shape: mirror Query2 -> rotated Query2_O -> grouped Query1
      assertInstanceOf(MirrorTableAssembly.class, fx.ws.getAssembly("Query2"));
      assertInstanceOf(RotatedTableAssembly.class, fx.ws.getAssembly("Query2_O"));

      MVDef[] defs = fx.analyze();
      Set<String> tables = mvTables(defs);

      assertFalse(tables.contains("Query2"), "MV on the mirror of a rotated table: " + tables);
      assertFalse(tables.contains("Query2_O"), "MV on a rotated table: " + tables);
      assertFalse(tables.contains("ASSOCIATION_Query2"), "association MV: " + tables);

      MVDef sub = Arrays.stream(defs)
         .filter(def -> def != null && "Query1".equals(def.getMVTable()))
         .findFirst().orElse(null);
      assertNotNull(sub, "no sub MV on the rotated table's source table: " + tables);
      assertTrue(sub.isSub());
      assertEquals("Query2", sub.getBoundTable());
      String plan = fx.plan();
      assertTrue(plan.contains("based on a rotated table"), "no rotated table fault: " + plan);
   }

   @Test
   void rotatedTableBoundDirectlyGetsASubMVOnTheSourceTable() throws Exception {
      // the plain variant, without the viewsheet mirror
      Fixture fx = new Fixture();
      fx.rotatedOverGrouped();
      fx.selection("SelectionList1", "Query2", "2005");
      fx.table("Table1", "Query2");

      Set<String> tables = mvTables(fx.analyze());

      assertFalse(tables.contains("Query2"), "MV on a rotated table: " + tables);
      assertTrue(tables.contains("Query1"), "no sub MV on the source table: " + tables);
   }

   @Test
   void tableWithoutARotatedTableStillGetsItsOwnMV() throws Exception {
      Fixture fx = new Fixture();
      fx.grouped("Query1");
      fx.selection("SelectionList1", "Query1", "Region");
      fx.table("Table1", "Query1");
      fx.createMirrorTables();

      Set<String> tables = mvTables(fx.analyze());

      assertTrue(tables.contains("Query1"), "no MV on the bound table: " + tables);
   }

   private static Set<String> mvTables(MVDef[] defs) {
      Set<String> tables = new HashSet<>();

      for(MVDef def : defs) {
         if(def != null) {
            tables.add(def.getMVTable());
         }
      }

      return tables;
   }

   private static ColumnRef column(String name, String type) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
      ref.setDataType(type);
      return ref;
   }

   @Configuration
   static class Config {
      @Bean
      MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   /** A worksheet, a viewsheet on it and the analyzer of the viewsheet. */
   private static final class Fixture {
      final Worksheet ws = new Worksheet();
      final Viewsheet vs = new Viewsheet();

      Fixture() throws Exception {
         Method method = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
         method.setAccessible(true);
         method.invoke(vs, ws);
         vs.setBaseEntry(new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.WORKSHEET, "Orders", null));
      }

      /** A physical table grouped on Region with the sum of Amount. */
      TableAssembly grouped(String name) {
         PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, name);
         table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds1", "ORDERS"));
         ColumnRef region = column("Region", XSchema.STRING);
         ColumnRef amount = column("Amount", XSchema.DOUBLE);
         ColumnSelection cols = new ColumnSelection();
         cols.addAttribute(region);
         cols.addAttribute(amount);
         table.setColumnSelection(cols);
         AggregateInfo ainfo = new AggregateInfo();
         ainfo.addGroup(new GroupRef(region));
         ainfo.addAggregate(new AggregateRef(amount, AggregateFormula.SUM));
         table.setAggregateInfo(ainfo);
         ws.addAssembly(table);
         return table;
      }

      /** Query2 rotates the grouped Query1: its columns are the values of Region. */
      void rotatedOverGrouped() {
         TableAssembly query1 = grouped("Query1");
         RotatedTableAssembly query2 = new RotatedTableAssembly(ws, "Query2", query1);
         // RotatedQuery.fixColumnSelection declares a mixed rotated column as a string
         ColumnSelection cols = new ColumnSelection();
         cols.addAttribute(column("Region", XSchema.STRING));
         cols.addAttribute(column("2005", XSchema.STRING));
         cols.addAttribute(column("2006", XSchema.STRING));
         query2.setColumnSelection(cols);
         ws.addAssembly(query2);
      }

      void selection(String name, String table, String col) {
         SelectionListVSAssembly assembly = new SelectionListVSAssembly(vs, name);
         assembly.setTableName(table);
         assembly.setDataRef(column(col, XSchema.STRING));
         vs.addAssembly(assembly);
      }

      void table(String name, String table) {
         TableVSAssembly assembly = new TableVSAssembly(vs, name);
         assembly.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, table));
         vs.addAssembly(assembly);
      }

      /** What Viewsheet.update does for every table a viewsheet assembly is bound to. */
      void createMirrorTables() throws Exception {
         Method method = Viewsheet.class.getDeclaredMethod("createMirrorTables");
         method.setAccessible(true);
         method.invoke(vs);
      }

      MVDef[] analyze() throws Exception {
         ViewsheetSandbox box = mock(ViewsheetSandbox.class);
         when(box.getAllVariables()).thenReturn(new VariableTable());
         analyzer = new VSMVAnalyzer("1^128^__NULL__^vs", vs, null, box, false, null);
         return analyzer.analyze();
      }

      /** The analysis plan the user sees. */
      String plan() {
         return analyzer.getDescriptor().getInfo();
      }

      private VSMVAnalyzer analyzer;
   }
}
