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
package inetsoft.uql.onedrive;

import inetsoft.test.*;
import inetsoft.uql.tabular.TabularColumn;
import inetsoft.uql.tabular.TabularDatasetSchema;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.CredentialType;
import inetsoft.util.credential.LocalPasswordCredential;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * B4 -- {@code describeDataset}'s Excel-specific cases. Needs the REAL Spring test context
 * (unlike {@code OneDriveCatalogDescribeTest}'s lightweight mocked one): {@code
 * OneDriveFileUtil.getColumnDefinition}'s Excel branch reaches {@code ExcelFileSupport.getInstance()}
 * -> {@code XLSXFileReader}, which unconditionally calls {@code FileSystemService.getInstance()},
 * {@code Config.getConfig()} (via {@code TabularSchemaExtractor.extract} -> {@code LayoutCreator}),
 * and {@code PropertiesEngine.getInstance()} -- none of which resolve against a hand-mocked
 * {@code ApplicationContext} (confirmed by trying that first: a {@code NullPointerException} out of
 * {@code ConfigurationContext.getSpringBean}'s own bean cache, the exact trap the predecessor
 * ServerFile catalog SPI round's retro recorded). Mirrors {@code OneDriveRuntimeTests}' own context
 * shape, which already proves this combination resolves correctly for {@code runQuery}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
               OneDriveCatalogExcelTest.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class OneDriveCatalogExcelTest {
   private static InputStream readFixture(String file) {
      InputStream input = OneDriveCatalogExcelTest.class.getResourceAsStream(file);
      assertNotNull(input, "fixture not found: " + file);
      return input;
   }

   private static Function<OneDriveDataSource, OneDriveQuery> fixtureFactory(String fixture) {
      return ds -> {
         OneDriveQuery query = spy(new OneDriveQuery());
         doAnswer(invocation -> readFixture(fixture)).when(query).getFile();
         return query;
      };
   }

   private static OneDriveDataSource dataSource() {
      OneDriveDataSource ds = new OneDriveDataSource();
      ds.setName("OneDrive Test");
      ds.setAccessToken("AnyAccessToken");
      return ds;
   }

   private static List<String> columnNames(TabularDatasetSchema schema) {
      return schema.columns().stream().map(TabularColumn::name).toList();
   }

   // ----- B4: single-sheet workbook -> one id, columns from its own (non-"Sheet1") sheet -----

   @Test
   void singleSheetWorkbookDescribesWithExactlyOneId_paramsPinItsRealSheetName() throws Exception {
      TabularDatasetSchema schema = OneDriveCatalog.describeDataset(
         dataSource(), "Test/TestExcelSingleSheet.xlsx", fixtureFactory("TestExcelSingleSheet.xlsx"));

      assertEquals(List.of("Name", "Amount"), columnNames(schema));
      assertFalse(schema.columnsMayBeIncomplete());
      // Deliberately named "Data", NOT "Sheet1": describeDataset's own default
      // (getExcelSheetNames()[0], the workbook's REAL first sheet) must be what is pinned, not the
      // literal string "Sheet1" that ONLY runQuery/readExcel default to on a miss (the design doc's
      // §4 describe/execute default-sheet divergence) -- a fixture literally named "Sheet1" could
      // not discriminate the two.
      assertEquals("Data", schema.params().get(OneDriveCatalog.PARAM_EXCEL_SHEET_NAME));
      assertEquals("Test/TestExcelSingleSheet.xlsx", schema.params().get(OneDriveCatalog.PARAM_PATH));
      assertTrue(schema.sampleable());
   }

   // ----- B4: multi-sheet workbook -> loud, named refusal, never a silent bind to sheet 1 -----

   @Test
   void multiSheetWorkbookThrowsANamedRefusal_ratherThanBindingToTheFirstSheet() {
      Exception ex = assertThrows(Exception.class, () -> OneDriveCatalog.describeDataset(
         dataSource(), "Test/TestExcel.xlsx", fixtureFactory("TestExcel.xlsx")));

      // TestExcel.xlsx is genuinely [Sheet1, Sheet2] with DIFFERENT headers each (confirmed via
      // openpyxl while building this test) -- both names must appear so an operator can act on the
      // message, not just be told "ambiguous."
      assertTrue(ex.getMessage().contains("Sheet1"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Sheet2"), ex.getMessage());
      assertTrue(ex.getMessage().contains("2 sheets"), ex.getMessage());
   }

   // ----- describeDataset's sheet-list guard must be null-safe against BOTH of
   //       getExcelSheetNames()'s sentinel returns, not just the not-yet-downloaded one -----

   @Test
   void excelSheetNamesSwallowedExceptionSentinelThrowsNamedRefusal_notNPE() {
      // getExcelSheetNames() has two sentinels: [""] when nothing was ever downloaded (covered by
      // singleSheetWorkbookDescribesWithExactlyOneId... never hitting this branch at all), and
      // [null] -- its own uninitialised `new String[1]`, returned as-is when the sheet-name read
      // itself throws and is swallowed by that method's own catch(Exception). A corrupt-workbook
      // fixture can't reach this branch: a failed download/parse already makes
      // getColumnDefinition() return null columns, which describeDataset refuses earlier, before
      // ever calling getExcelSheetNames(). So the query is stubbed directly here -- a real,
      // successful download/parse (the same fixture as the passing single-sheet case above),
      // with only getExcelSheetNames() overridden to return the swallowed-exception sentinel.
      Function<OneDriveDataSource, OneDriveQuery> factory = ds -> {
         OneDriveQuery query = spy(new OneDriveQuery());
         doAnswer(invocation -> readFixture("TestExcelSingleSheet.xlsx")).when(query).getFile();
         doReturn(new String[]{ null }).when(query).getExcelSheetNames();
         return query;
      };

      Exception ex = assertThrows(Exception.class, () -> OneDriveCatalog.describeDataset(
         dataSource(), "Test/TestExcelSingleSheet.xlsx", factory));

      assertInstanceOf(IOException.class, ex);
      assertTrue(ex.getMessage().contains("could not read the sheet"), ex.getMessage());
   }

   @Configuration
   static class TestConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService credentialService = mock(CredentialService.class);
         when(credentialService.createCredential(CredentialType.CLIENT))
            .thenReturn(mock(inetsoft.util.credential.ClientCredentials.class));
         when(credentialService.createCredential(CredentialType.CLIENT, false))
            .thenReturn(mock(inetsoft.util.credential.ClientCredentials.class));
         when(credentialService.createCredential(CredentialType.PASSWORD))
            .thenReturn(mock(LocalPasswordCredential.class));
         when(credentialService.createCredential(CredentialType.PASSWORD, false))
            .thenReturn(mock(LocalPasswordCredential.class));
         return credentialService;
      }
   }
}
