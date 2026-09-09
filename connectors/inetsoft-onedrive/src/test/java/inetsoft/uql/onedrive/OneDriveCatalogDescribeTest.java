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

import inetsoft.uql.tabular.BrowsableQuery;
import inetsoft.uql.tabular.BrowsableQuery.BrowseEntry;
import inetsoft.uql.tabular.BrowsableQuery.BrowseListing;
import inetsoft.uql.tabular.TabularCatalog;
import inetsoft.uql.tabular.TabularColumn;
import inetsoft.uql.tabular.TabularDatasetSchema;
import inetsoft.util.ConfigurationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

/**
 * B4/B5/B5b/B6/B8/B17 -- {@code OneDriveCatalog.describeDataset}, driven through the package-private
 * query-factory seam so every case runs against a real fixture file with NO live Graph client:
 * {@code getFile()} is stubbed to read the fixture straight off the classpath, exactly the way
 * {@code OneDriveRuntimeTests}' own {@code shouldReadCSV}/{@code shouldReadExcel} stub it, one layer
 * up from a real {@code GraphServiceClient}.
 */
@Tag("core")
class OneDriveCatalogDescribeTest {
   @BeforeAll
   static void installContext() {
      previous = OneDriveTestSupport.installMockContext();
   }

   @AfterAll
   static void clearContext() {
      OneDriveTestSupport.clearContext(previous);
   }

   private static ConfigurationContext previous;

   private static InputStream readFixture(String file) {
      InputStream input = OneDriveCatalogDescribeTest.class.getResourceAsStream(file);
      assertNotNull(input, "fixture not found: " + file);
      return input;
   }

   /** A query-factory that hands back a spy whose {@code getFile()} reads a fixture off disk. */
   private static Function<OneDriveDataSource, OneDriveQuery> fixtureFactory(String fixture) {
      return ds -> {
         OneDriveQuery query = spy(new OneDriveQuery());
         doAnswer(invocation -> readFixture(fixture)).when(query).getFile();
         return query;
      };
   }

   // ----- B5: columns come from the declared header, not from sampling -----

   @Test
   void csvColumnsComeFromTheDeclaredHeader_columnsMayBeIncompleteIsFalse() throws Exception {
      OneDriveDataSource ds = OneDriveTestSupport.fakeDataSource("OneDrive Test");

      TabularDatasetSchema schema = OneDriveCatalog.describeDataset(
         ds, "Test/TestCSV.csv", fixtureFactory("TestCSV.csv"));

      // TestCSV.csv's actual first row -- the declared header getColumnDefinition reads -- is an
      // InfluxDB-style annotation line; "m"/"host"/... is the SECOND row, which describeDataset
      // must NOT read as the header (that would be sampling the first data row's shape, not the
      // declared header, per B5's own "what would make this fail" case).
      assertEquals(List.of("#datatype measurement", "tag", "double", "dateTime:RFC3339"),
         columnNames(schema));
      assertFalse(schema.columnsMayBeIncomplete());
      assertTrue(schema.sampleable(), "G2: OneDrive declares sampleable=true");
      assertEquals("Test/TestCSV.csv", schema.params().get(OneDriveCatalog.PARAM_PATH));
      assertFalse(schema.params().containsKey(OneDriveCatalog.PARAM_EXCEL_SHEET_NAME),
         "a non-workbook target must not carry a sheet param at all");
   }

   // B4's Excel-specific cases (single-sheet, multi-sheet refusal) live in
   // OneDriveCatalogExcelTest, which needs the REAL Spring test context ExcelFileSupport reaches
   // through (FileSystemService/PropertiesEngine/XSwapper) -- the lightweight mocked
   // ApplicationContext this file uses is enough for a plain-text CSV header read (via
   // getTextHeader) but NOT for POI's own Excel path, confirmed by running it first and hitting a
   // bean-cache NullPointerException from ConfigurationContext.getSpringBean for an unstubbed bean
   // type, exactly the trap the predecessor round's own retro recorded.

   // ----- B5b/D2: a download/header failure throws, never an empty/null schema -----

   @Test
   void aDownloadFailureThrowsRatherThanReturningAnEmptyOrNullSchema() {
      OneDriveDataSource ds = OneDriveTestSupport.fakeDataSource("OneDrive Test");
      Function<OneDriveDataSource, OneDriveQuery> failingFactory = d -> {
         OneDriveQuery query = spy(new OneDriveQuery());
         doAnswer(invocation -> {
            throw new IOException("simulated Graph failure");
         }).when(query).getFile();
         return query;
      };

      assertThrows(Exception.class,
         () -> OneDriveCatalog.describeDataset(ds, "Test/broken.csv", failingFactory));
   }

   // ----- B6: id grammar bijection, exercised as a GENUINE encode -> decode round trip -----
   //
   // R1-1 (06-review-r1.md): every case here used to hand describeDataset a manually
   // pre-computed, already-escaped literal (e.g. "sales%232026.csv") and assert only that
   // unescapeId recovered the right path -- so encodeId itself (its one production call site is
   // OneDriveCatalog.listDatasets, line ~126) had ZERO test coverage; a regression there (escaping
   // only "#" and not "%", reversing the escape order, or dropping the escape entirely) would have
   // gone undetected. Each case below now DERIVES the id by actually enumerating a stub
   // BrowsableQuery whose entry is literally named the raw filename, through the real
   // OneDriveCatalog.listDatasets -- the same production encodeId call listDatasets always makes --
   // and only THEN feeds that derived id into describeDataset. The literal escaped string still
   // appears, but as an ASSERTION on what listDatasets produced, never as an input.

   /** Enumerates exactly one file, literally named {@code fileName}, and returns its emitted id. */
   private static String encodedIdFor(String fileName) throws Exception {
      BrowsableQuery stub = new BrowsableQuery() {
         @Override
         public String getBrowsablePropertyName() {
            return "path";
         }

         @Override
         public List<String> getAcceptedExtensions() {
            return List.of(".txt", ".csv", ".xls", ".xlsx");
         }

         @Override
         public BrowseListing browseChildren(String path, boolean recursive,
                                              List<String> acceptTypes, int maxEntries)
         {
            return new BrowseListing(List.of(new BrowseEntry(fileName, fileName, false)), false);
         }
      };

      TabularCatalog catalog = OneDriveCatalog.listDatasets(stub);
      assertEquals(1, catalog.datasets().size(), "expected exactly one enumerated dataset");
      return catalog.datasets().get(0).id();
   }

   @Test
   void aFileNamedWithAHashEncodesThroughListDatasets_andRoundTripsThroughDescribe() throws Exception {
      String id = encodedIdFor("sales#2026.csv");
      assertEquals("sales%232026.csv", id, "encodeId must escape a literal '#' as '%23'");

      OneDriveDataSource ds = OneDriveTestSupport.fakeDataSource("OneDrive Test");
      TabularDatasetSchema schema = OneDriveCatalog.describeDataset(
         ds, id, fixtureFactory("TestCSV.csv"));

      assertEquals("sales#2026.csv", schema.params().get(OneDriveCatalog.PARAM_PATH),
         "the DECODED path (with its real '#') must be what is sent, not the escaped id itself");
   }

   @Test
   void aFileNamedWithAnAlreadyEscapedSequenceEncodesThroughListDatasets_andRoundTrips()
      throws Exception
   {
      // A file literally named "100%25.csv" (a name that already, literally, contains the escape
      // sequence "%25") must encode to "100%2525.csv" -- escaping "%" first is what keeps this
      // correct (a "#"-first order would corrupt it).
      String id = encodedIdFor("100%25.csv");
      assertEquals("100%2525.csv", id, "encodeId must escape '%' as '%25' BEFORE escaping '#'");

      OneDriveDataSource ds = OneDriveTestSupport.fakeDataSource("OneDrive Test");
      TabularDatasetSchema schema = OneDriveCatalog.describeDataset(
         ds, id, fixtureFactory("TestCSV.csv"));

      assertEquals("100%25.csv", schema.params().get(OneDriveCatalog.PARAM_PATH));
   }

   @Test
   void aFileNamedOnlyTheHashCharacterEncodesThroughListDatasets_andRoundTrips() throws Exception {
      String id = encodedIdFor("#.csv");
      assertEquals("%23.csv", id);

      OneDriveDataSource ds = OneDriveTestSupport.fakeDataSource("OneDrive Test");
      TabularDatasetSchema schema = OneDriveCatalog.describeDataset(
         ds, id, fixtureFactory("TestCSV.csv"));

      assertEquals("#.csv", schema.params().get(OneDriveCatalog.PARAM_PATH));
   }

   @Test
   void aFileNamedOnlyThePercentCharacterEncodesThroughListDatasets_andRoundTrips() throws Exception {
      String id = encodedIdFor("%.csv");
      assertEquals("%25.csv", id);

      OneDriveDataSource ds = OneDriveTestSupport.fakeDataSource("OneDrive Test");
      TabularDatasetSchema schema = OneDriveCatalog.describeDataset(
         ds, id, fixtureFactory("TestCSV.csv"));

      assertEquals("%.csv", schema.params().get(OneDriveCatalog.PARAM_PATH));
   }

   /**
    * R2-1 (06-review-r1.md's own follow-on question, "which wrong {@code encodeId} would still pass
    * all five [prior cases]?"): every prior case contains its escape character AT MOST ONCE, so a
    * mutation that downgrades {@code String#replace} (replaces EVERY occurrence) to {@code
    * replaceFirst} (replaces only the first) would leave a second {@code #}/{@code %} raw and still
    * pass all of them. A filename containing the SAME escape character TWICE is the one shape that
    * closes that gap.
    */
   @Test
   void aFileNamedWithTheHashCharacterTwiceEncodesThroughListDatasets_andRoundTrips() throws Exception {
      String id = encodedIdFor("a#b#c.csv");
      assertEquals("a%23b%23c.csv", id, "BOTH '#' occurrences must be escaped, not just the first");

      OneDriveDataSource ds = OneDriveTestSupport.fakeDataSource("OneDrive Test");
      TabularDatasetSchema schema = OneDriveCatalog.describeDataset(
         ds, id, fixtureFactory("TestCSV.csv"));

      assertEquals("a#b#c.csv", schema.params().get(OneDriveCatalog.PARAM_PATH));
   }

   @Test
   void aFileNamedWithThePercentCharacterTwiceEncodesThroughListDatasets_andRoundTrips() throws Exception {
      String id = encodedIdFor("a%b%c.csv");
      assertEquals("a%25b%25c.csv", id, "BOTH '%' occurrences must be escaped, not just the first");

      OneDriveDataSource ds = OneDriveTestSupport.fakeDataSource("OneDrive Test");
      TabularDatasetSchema schema = OneDriveCatalog.describeDataset(
         ds, id, fixtureFactory("TestCSV.csv"));

      assertEquals("a%b%c.csv", schema.params().get(OneDriveCatalog.PARAM_PATH));
   }

   /**
    * Non-ASCII characters are outside the escape alphabet ({@code %}/{@code #} only), so a Chinese
    * (or any other Unicode) filename passes through {@code encodeId}/{@code unescapeId} completely
    * untouched -- safe by construction, not by luck, but nothing said so explicitly before this case
    * (06-review-r1.md's adversarial-attempts section).
    */
   @Test
   void aNonAsciiFilenameEncodesUnchangedThroughListDatasets_andRoundTrips() throws Exception {
      String id = encodedIdFor("销售报表.csv");
      assertEquals("销售报表.csv", id, "no escape character appears in a non-ASCII name");

      OneDriveDataSource ds = OneDriveTestSupport.fakeDataSource("OneDrive Test");
      TabularDatasetSchema schema = OneDriveCatalog.describeDataset(
         ds, id, fixtureFactory("TestCSV.csv"));

      assertEquals("销售报表.csv", schema.params().get(OneDriveCatalog.PARAM_PATH));
   }

   // ----- B8: ids requireEmittableId must refuse, all before any Graph call -----

   @Test
   void blankIdIsRefused() {
      OneDriveDataSource ds = OneDriveTestSupport.fakeDataSource("OneDrive Test");
      assertThrows(Exception.class,
         () -> OneDriveCatalog.describeDataset(ds, "", fixtureFactory("TestCSV.csv")));
   }

   @Test
   void aLeadingSlashIsRefused() {
      OneDriveDataSource ds = OneDriveTestSupport.fakeDataSource("OneDrive Test");
      assertThrows(IllegalArgumentException.class, () -> OneDriveCatalog.describeDataset(
         ds, "/etc/passwd", fixtureFactory("TestCSV.csv")));
   }

   @Test
   void aDriveLetterShapedIdIsRefused() {
      OneDriveDataSource ds = OneDriveTestSupport.fakeDataSource("OneDrive Test");
      assertThrows(IllegalArgumentException.class, () -> OneDriveCatalog.describeDataset(
         ds, "C:\\Windows\\win.ini", fixtureFactory("TestCSV.csv")));
   }

   @Test
   void aDotDotSegmentIsRefused() {
      OneDriveDataSource ds = OneDriveTestSupport.fakeDataSource("OneDrive Test");
      assertThrows(IllegalArgumentException.class, () -> OneDriveCatalog.describeDataset(
         ds, "../../secret.csv", fixtureFactory("TestCSV.csv")));
   }

   // ----- B17: describeDataset refuses a non-whitelisted extension too, before any Graph call -----

   @Test
   void aNonWhitelistedExtensionIsRefused() {
      OneDriveDataSource ds = OneDriveTestSupport.fakeDataSource("OneDrive Test");
      assertThrows(IllegalArgumentException.class, () -> OneDriveCatalog.describeDataset(
         ds, "secrets/private-key.pem", fixtureFactory("TestCSV.csv")));
   }

   private static java.util.List<String> columnNames(TabularDatasetSchema schema) {
      return schema.columns().stream().map(inetsoft.uql.tabular.TabularColumn::name).toList();
   }

}
