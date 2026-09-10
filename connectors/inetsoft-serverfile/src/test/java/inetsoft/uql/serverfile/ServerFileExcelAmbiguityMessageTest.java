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
package inetsoft.uql.serverfile;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.tabular.*;
import inetsoft.web.wiz.service.TabularQueryContractSupport;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers charter A7 (the ambiguity guard fires through the SPI path) and C1 -- restated per
 * 03-reconcile.md D-1: the Java message text must not change, AND wiz's CURRENT regex does not
 * match it today, independent of this round (found during P1/P2, see 03-reconcile.md §1 and
 * follow-up F1). This test pins BOTH facts in one place: it is GREEN today, and goes red the
 * moment EITHER side changes -- which is exactly when someone needs to look at the other side. It
 * is a self-deleting pin, not a permanent blessing of the cross-repo mismatch.
 *
 * <p>Full Spring test context (see {@link ServerFileSpringTestSupport}'s class javadoc): reaching
 * the ambiguity guard needs a real workbook's sheet list read through the same machinery the
 * row-reading tests do.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  ServerFileSpringTestSupport.ConfigBeanConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class ServerFileExcelAmbiguityMessageTest {
   @TempDir
   File root;

   private ServerFileDataSource ds;

   @BeforeEach
   void setUp() {
      ds = new ServerFileDataSource();
      ds.setName("ambiguity-test-ds");
      ds.setFile(root);
   }

   @Test
   void aMultiSheetWorkbookBoundWithNoSheetStillThrowsTheAmbiguityRefusal_andWizsRegexDoesNotMatchItToday()
      throws Exception
   {
      try(XSSFWorkbook wb = new XSSFWorkbook()) {
         Sheet a = wb.createSheet("A");
         a.createRow(0).createCell(0).setCellValue("x");
         Sheet b = wb.createSheet("B");
         b.createRow(0).createCell(0).setCellValue("y");

         try(FileOutputStream out = new FileOutputStream(new File(root, "book.xlsx"))) {
            wb.write(out);
         }
      }

      ServerFileQuery query = new ServerFileQuery();
      query.setDataSource(ds);
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(ServerFileQuery.class);
      TabularQuerySchema schema =
         new TabularSchemaExtractor().extract(query, ServerFileDataSource.TYPE);
      // No excelSheet -- exactly the case listDatasets/describeDataset never produce (a
      // multi-sheet id always names its sheet), but a caller may hand-edit params before calling
      // applyQueryContract, so the guard is exercised here directly (defense in depth, A7-4).
      Map<String, Object> queryParams =
         new HashMap<>(Map.of(ServerFileCatalog.PARAM_FILE_FOLDER, "book.xlsx"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> TabularQueryContractSupport.applyQueryContract(
            query, pmap, schema, queryParams, ds.getName()));

      // TabularQueryContractSupport:491-495, verbatim -- this assertion fails the moment that
      // text changes, which is exactly what C1 forbids doing in this round.
      String expected = "'book.xlsx' of 'ambiguity-test-ds' has 2 sheets, so one has to be " +
         "named: A, B. Supply it as queryParams.excelSheet.";
      assertEquals(expected, ex.getMessage());

      // wiz's CURRENT regex (wiz-services/src/services/tabular/tabularFileClient.ts:276),
      // reproduced verbatim. It requires ". Put it in tabularSource.params.excelSheet" -- the
      // Java message above ends ". Supply it as queryParams.excelSheet." instead, so it does NOT
      // match. This is follow-up F1: wiz's regex is stale (it pins a method,
      // WorksheetTableService.resolveExcelSheet, deleted in community#24c8ee7e8), not fixed by
      // this round (C6 -- the file pipeline this regex serves is out of scope here; ServerFile is
      // leaving it via the classification flip, but OneDrive still depends on it).
      Pattern wizRegex = Pattern.compile(
         "\\bhas (\\d+) sheets, so one has to be named: (.+?)\\. Put it in tabularSource\\.params\\.excelSheet");
      Matcher matcher = wizRegex.matcher(ex.getMessage());
      assertFalse(matcher.find(),
         "wiz's parseExcelSheetAmbiguity regex now matches the Java ambiguity message -- either " +
         "the Java text or the wiz regex changed since this test was written. That means " +
         "follow-up F1 has been resolved: update tabularFileClient.test.ts's stale fixture and " +
         "this test together, do not just relax this assertion.");
   }
}
