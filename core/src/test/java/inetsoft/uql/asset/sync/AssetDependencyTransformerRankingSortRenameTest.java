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
import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79840: renaming a worksheet column or a logical model attribute must rename the
 * sort-by-value column of a dimension whose Top-N ranking wraps the same column in an
 * aggregate. The ranking branches of {@code AssetDependencyTransformer.renameVSColumn} used to
 * overwrite the old/new names with their wrapped forms, so the sort-by block that follows no
 * longer matched. Runs real {@link Viewsheet#writeXML}, the real
 * {@link DependencyTransformer#transformAsset} and real {@link Viewsheet#parseXML}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  AssetDependencyTransformerRankingSortRenameTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AssetDependencyTransformerRankingSortRenameTest {
   private static final String ENTITY = "Customers";

   @TempDir
   Path tempDir;

   // ranking {0} (with Sales as the renamed column), sort by Sum(Sales), crosstab, WS rename
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "Sum(Sales)                 | Sum(Revenue)",
      "Correlation(Sales, Profit) | Correlation(Revenue, Profit)",
      "Correlation(Profit, Sales) | Correlation(Profit, Revenue)",
      "Sales                      | Revenue",
      "Sum(Profit)                | Sum(Profit)"
   })
   void crosstabWorksheetRename(String ranking, String expectedRanking) throws Exception {
      Viewsheet vs = crosstab(new SourceInfo(SourceInfo.ASSET, null, "T"), "Sales", ranking);
      Document doc = transform(vs, wsRename());

      assertDimension(doc, expectedRanking, "Sum(Revenue)");
      VSDimensionRef dim = reloadCrosstabRow(doc);
      assertEquals("Sum(Revenue)", dim.getSortByColValue());
      assertEquals(expectedRanking, dim.getRankingColValue());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "Sum(Customers:Sales)                           | Sum(Customers:Revenue)",
      "Correlation(Customers:Sales, Customers:Profit) | Correlation(Customers:Revenue, Customers:Profit)",
      "Correlation(Customers:Profit, Customers:Sales) | Correlation(Customers:Profit, Customers:Revenue)",
      "Customers:Sales                                | Customers:Revenue"
   })
   void crosstabLogicalModelRename(String ranking, String expectedRanking) throws Exception {
      Viewsheet vs = crosstab(new SourceInfo(SourceInfo.MODEL, "ds", "model"),
                              ENTITY + ":Sales", ranking);
      Document doc = transform(vs, lmRename());

      assertDimension(doc, expectedRanking, "Sum(Customers:Revenue)");
      VSDimensionRef dim = reloadCrosstabRow(doc);
      assertEquals("Sum(Customers:Revenue)", dim.getSortByColValue());
      assertEquals(expectedRanking, dim.getRankingColValue());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "Sum(Sales)                 | Sum(Revenue)",
      "Correlation(Sales, Profit) | Correlation(Revenue, Profit)",
      "Correlation(Profit, Sales) | Correlation(Profit, Revenue)"
   })
   void chartWorksheetRename(String ranking, String expectedRanking) throws Exception {
      Viewsheet vs = chart(new SourceInfo(SourceInfo.ASSET, null, "T"), "Sales", ranking);
      Document doc = transform(vs, wsRename());

      assertDimension(doc, expectedRanking, "Sum(Revenue)");
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "Sum(Customers:Sales)                           | Sum(Customers:Revenue)",
      "Correlation(Customers:Sales, Customers:Profit) | Correlation(Customers:Revenue, Customers:Profit)",
      "Correlation(Customers:Profit, Customers:Sales) | Correlation(Customers:Profit, Customers:Revenue)"
   })
   void chartLogicalModelRename(String ranking, String expectedRanking) throws Exception {
      Viewsheet vs = chart(new SourceInfo(SourceInfo.MODEL, "ds", "model"),
                           ENTITY + ":Sales", ranking);
      Document doc = transform(vs, lmRename());

      assertDimension(doc, expectedRanking, "Sum(Customers:Revenue)");
   }

   private static Viewsheet crosstab(SourceInfo source, String measure, String ranking) {
      Viewsheet vs = new Viewsheet();
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "Crosstab1");
      crosstab.setSourceInfo(source);
      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setDesignRowHeaders(new DataRef[] {
         dimension(new VSDimensionRef(), measure, ranking) });
      cinfo.setDesignAggregates(new DataRef[] { aggregate(new VSAggregateRef(), measure) });
      crosstab.setVSCrosstabInfo(cinfo);
      vs.addAssembly(crosstab);
      return vs;
   }

   private static Viewsheet chart(SourceInfo source, String measure, String ranking) {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.setSourceInfo(source);
      DefaultVSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.addXField((ChartRef) dimension(new VSChartDimensionRef(), measure, ranking));
      cinfo.addYField((ChartRef) aggregate(new VSChartAggregateRef(), measure));
      chart.setVSChartInfo(cinfo);
      vs.addAssembly(chart);
      return vs;
   }

   // dimension Region, sorted descending by Sum(measure), Top 3 by the given ranking column
   private static VSDimensionRef dimension(VSDimensionRef dim, String measure, String ranking) {
      dim.setGroupColumnValue("Region");
      dim.setOrder(XConstants.SORT_VALUE_DESC);
      dim.setSortByColValue("Sum(" + measure + ")");
      dim.setRankingOptionValue(String.valueOf(XCondition.TOP_N));
      dim.setRankingNValue("3");
      dim.setRankingColValue(ranking);
      return dim;
   }

   private static VSAggregateRef aggregate(VSAggregateRef agg, String measure) {
      agg.setColumnValue(measure);
      agg.setFormulaValue("Sum");
      return agg;
   }

   // the shape AssetDependencyTransformer emits for the viewsheets of a renamed ws column
   private static RenameInfo wsRename() {
      return new RenameInfo("Sales", "Revenue", RenameInfo.ASSET | RenameInfo.COLUMN,
                            "1^2^__NULL__^ws79840", "T");
   }

   // rename attribute Customers.Sales to Customers.Revenue as LogicalModelService does
   private static RenameInfo lmRename() {
      RenameInfo rinfo = new RenameInfo(ENTITY + ".Sales", ENTITY + ".Revenue",
                                        RenameInfo.LOGIC_MODEL | RenameInfo.COLUMN,
                                        "model", ENTITY);
      rinfo.setPrefix("ds");
      rinfo.setOldEntity(ENTITY);
      return rinfo;
   }

   private Document transform(Viewsheet vs, RenameInfo rinfo) throws Exception {
      File file = Files.createTempFile(tempDir, "vs", ".xml").toFile();

      // an imported viewsheet file holds the <assembly> under a root element
      try(PrintWriter writer = new PrintWriter(new OutputStreamWriter(
         new FileOutputStream(file), StandardCharsets.UTF_8)))
      {
         writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
         writer.println("<root>");
         vs.writeXML(writer);
         writer.println("</root>");
      }

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.VIEWSHEET, "vs79840", null);
      RenameDependencyInfo dinfo = new RenameDependencyInfo();
      dinfo.addRenameInfo(entry, rinfo);
      dinfo.setAssetFile(entry, file);
      DependencyTransformer.transformAsset(entry, dinfo);

      try(InputStream in = new FileInputStream(file)) {
         return Tool.parseXML(in, "UTF-8", false, false);
      }
   }

   private static void assertDimension(Document doc, String ranking, String sort) {
      NodeList groups = doc.getElementsByTagName("groupValue");
      int found = 0;

      for(int i = 0; i < groups.getLength(); i++) {
         Element dim = (Element) groups.item(i).getParentNode();

         if(!"Region".equals(Tool.getValue(groups.item(i)))) {
            continue;
         }

         found++;
         assertAll(
            () -> assertEquals(ranking, Tool.getChildValueByTagName(dim, "rankingColValue")),
            () -> assertEquals(ranking, Tool.getChildValueByTagName(dim, "rankingColRValue")),
            () -> assertEquals(sort, Tool.getChildValueByTagName(dim, "sortByColValue")),
            () -> assertEquals(sort, Tool.getChildValueByTagName(dim, "sortByColRValue")));
      }

      assertTrue(found > 0, "dimension Region not found");
   }

   private static VSDimensionRef reloadCrosstabRow(Document doc) throws Exception {
      Element assembly = Tool.getChildNodeByTagName(doc.getDocumentElement(), "assembly");
      Viewsheet vs = new Viewsheet();
      vs.parseXML(assembly);
      CrosstabVSAssembly crosstab = (CrosstabVSAssembly) vs.getAssembly("Crosstab1");
      return (VSDimensionRef) crosstab.getVSCrosstabInfo().getDesignRowHeaders()[0];
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
