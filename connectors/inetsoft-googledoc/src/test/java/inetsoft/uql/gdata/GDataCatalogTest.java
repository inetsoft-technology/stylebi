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
package inetsoft.uql.gdata;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.PropertyMeta;
import inetsoft.uql.tabular.TabularCatalog;
import inetsoft.uql.tabular.TabularColumn;
import inetsoft.uql.tabular.TabularDatasetRef;
import inetsoft.uql.tabular.TabularDatasetSchema;
import inetsoft.uql.tabular.TabularUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers {@link GDataCatalog}, driven off {@link GDataRuntime}'s three extracted static fetch
 * methods ({@code listSpreadsheetFiles}/{@code listSheetProperties}/{@code fetchSampleRows})
 * mocked via {@code MockedStatic} -- the same pattern {@code AnalyticsCatalogTest} uses for
 * {@code AnalyticsRuntime}. There is no live Google account in this environment; the honest
 * ceiling this suite holds to is contract correct, unit tests discriminating, degradations
 * recorded -- the first two are what this file provides, and the degradations are recorded in
 * stylebi-wiz {@code docs/tabular/stylebi-tabular-wiz-integration.md} §5.3 nos. 37-47.
 */
class GDataCatalogTest {
   private MockedStatic<GDataRuntime> runtimeStatic;

   @AfterEach
   void closeStaticMock() {
      if(runtimeStatic != null) {
         runtimeStatic.close();
      }
   }

   /**
    * A mocked, not a real, {@code GDataDataSource}: {@code GDataCatalog} never calls a method on
    * it directly (every network-facing call goes through the mocked {@code GDataRuntime} static
    * methods below, matched with {@code any()}), and {@code new GDataDataSource()} would run
    * {@code TabularDataSource}'s constructor, which reaches {@code CredentialService.getInstance()}.
    */
   private GDataDataSource fakeDataSource() {
      return mock(GDataDataSource.class);
   }

   // ----- shape builders -----

   private static File file(String id) {
      return new File().setId(id);
   }

   private static SheetProperties sheetProps(int sheetId, String title) {
      return new SheetProperties().setSheetId(sheetId).setTitle(title);
   }

   private static SheetProperties sheetProps(int sheetId, String title, String sheetType) {
      return new SheetProperties().setSheetId(sheetId).setTitle(title).setSheetType(sheetType);
   }

   private static RowData row(CellData... cells) {
      return new RowData().setValues(Arrays.asList(cells));
   }

   private static CellData stringCell(String s) {
      return new CellData().setEffectiveValue(new ExtendedValue().setStringValue(s));
   }

   private static CellData emptyCell() {
      return new CellData();
   }

   private static CellData errorCell() {
      return new CellData().setEffectiveValue(new ExtendedValue().setErrorValue(new ErrorValue()));
   }

   private static CellData numberCell(double value, String formatType) {
      ExtendedValue ev = new ExtendedValue().setNumberValue(value);
      CellData cell = new CellData().setEffectiveValue(ev);

      if(formatType != null) {
         cell.setEffectiveFormat(
            new CellFormat().setNumberFormat(new NumberFormat().setType(formatType)));
      }

      return cell;
   }

   private static CellData boolCell(boolean b) {
      return new CellData().setEffectiveValue(new ExtendedValue().setBoolValue(b));
   }

   private void stubListSheetProperties(List<SheetProperties> properties) {
      runtimeStatic = mockStatic(GDataRuntime.class);
      runtimeStatic.when(() -> GDataRuntime.listSheetProperties(any(), any()))
         .thenReturn(properties);
   }

   // =====================================================================================
   // listDatasets
   // =====================================================================================

   @Test
   void listDatasets_composesOneIdPerSpreadsheetSheetPair() throws Exception {
      runtimeStatic = mockStatic(GDataRuntime.class);
      runtimeStatic.when(() -> GDataRuntime.listSpreadsheetFiles(any()))
         .thenReturn(List.of(file("F1"), file("F2")));
      runtimeStatic.when(() -> GDataRuntime.listSheetProperties(any(), eq("F1")))
         .thenReturn(List.of(sheetProps(0, "Sheet1")));
      runtimeStatic.when(() -> GDataRuntime.listSheetProperties(any(), eq("F2")))
         .thenReturn(List.of(sheetProps(10, "A"), sheetProps(20, "B")));

      TabularCatalog catalog = GDataCatalog.listDatasets(fakeDataSource());

      assertEquals(List.of(
         new TabularDatasetRef("F1~0"),
         new TabularDatasetRef("F2~10"),
         new TabularDatasetRef("F2~20")), catalog.datasets());
      // N5: Sheets declares no edges between one sheet and another.
      assertEquals(List.of(), catalog.relationships());
   }

   @Test
   void listDatasets_pagesDriveToExhaustion() throws Exception {
      // A3's two-page test: paging lives INSIDE listSpreadsheetFiles, i.e. behind the seam every
      // other test in this class mocks, so this one test mocks one level lower -- CALLS_REAL_METHODS
      // for listSpreadsheetFiles itself, with getDrive() stubbed to return a Mockito-mocked Drive
      // whose files().list() chain returns two FileLists in sequence. A single-page test would pass
      // against a non-paging implementation, which is exactly what this must not be.
      FileList page1 = new FileList().setFiles(List.of(file("F1"))).setNextPageToken("TOKEN2");
      FileList page2 = new FileList().setFiles(List.of(file("F2"))).setNextPageToken(null);

      Drive.Files.List listRequest = mock(Drive.Files.List.class, RETURNS_SELF);
      when(listRequest.execute()).thenReturn(page1, page2);
      Drive.Files filesMock = mock(Drive.Files.class);
      when(filesMock.list()).thenReturn(listRequest);
      Drive driveMock = mock(Drive.class);
      when(driveMock.files()).thenReturn(filesMock);

      runtimeStatic = mockStatic(GDataRuntime.class, CALLS_REAL_METHODS);
      runtimeStatic.when(() -> GDataRuntime.getDrive(any())).thenReturn(driveMock);
      runtimeStatic.when(() -> GDataRuntime.listSheetProperties(any(), any()))
         .thenReturn(List.of(sheetProps(0, "Sheet1")));

      TabularCatalog catalog = GDataCatalog.listDatasets(fakeDataSource());

      Set<String> ids = catalog.datasets().stream().map(TabularDatasetRef::id)
         .collect(java.util.stream.Collectors.toSet());
      assertEquals(Set.of("F1~0", "F2~0"), ids);
      verify(listRequest, times(2)).execute();
   }

   @Test
   void listDatasets_drivePageTwoEmptyButPresent_noException() throws Exception {
      // A page can carry no nextPageToken-terminating files of its own yet still legitimately
      // exist in the sequence (e.g. Drive returned a page with zero matches before signalling the
      // end) -- must not NPE or otherwise fail.
      FileList page1 = new FileList().setFiles(List.of(file("F1"))).setNextPageToken("TOKEN2");
      FileList page2 = new FileList().setFiles(List.of()).setNextPageToken(null);

      Drive.Files.List listRequest = mock(Drive.Files.List.class, RETURNS_SELF);
      when(listRequest.execute()).thenReturn(page1, page2);
      Drive.Files filesMock = mock(Drive.Files.class);
      when(filesMock.list()).thenReturn(listRequest);
      Drive driveMock = mock(Drive.class);
      when(driveMock.files()).thenReturn(filesMock);

      runtimeStatic = mockStatic(GDataRuntime.class, CALLS_REAL_METHODS);
      runtimeStatic.when(() -> GDataRuntime.getDrive(any())).thenReturn(driveMock);
      runtimeStatic.when(() -> GDataRuntime.listSheetProperties(any(), any()))
         .thenReturn(List.of(sheetProps(0, "Sheet1")));

      TabularCatalog catalog = GDataCatalog.listDatasets(fakeDataSource());

      assertEquals(List.of(new TabularDatasetRef("F1~0")), catalog.datasets());
   }

   @Test
   void listSpreadsheetFiles_realBody_fieldMaskRequestsIdAndPageTokenButNotName() throws Exception {
      // P6 review round 2, item 1: listSpreadsheetFiles' own field mask -- unlike
      // listSheetProperties' and fetchSampleRows' (P6 round 1) -- had no real-body test at all,
      // which is exactly why the mask requesting `name` (a field File.getName() is never read
      // anywhere in this connector) drifted past four readings unnoticed. Runs the real body via
      // CALLS_REAL_METHODS with only getDrive stubbed (the same wiring
      // listDatasets_pagesDriveToExhaustion above uses), and captures the string passed to
      // setFields.
      //
      // Rejects two different wrong implementations: one that DROPS "nextPageToken" (without it
      // in the mask the field filter strips it from the response, and the paging loop terminates
      // after page one -- the exact silent truncation X3 forbids), and one that ADDS "name" back
      // (dead weight nothing reads -- verified by hand: both mutations turn this test RED, see
      // 07-fix-r2.md §3).
      FileList page = new FileList().setFiles(List.of(file("F1"))).setNextPageToken(null);
      Drive.Files.List listRequest = mock(Drive.Files.List.class, RETURNS_SELF);
      when(listRequest.execute()).thenReturn(page);
      Drive.Files filesMock = mock(Drive.Files.class);
      when(filesMock.list()).thenReturn(listRequest);
      Drive driveMock = mock(Drive.class);
      when(driveMock.files()).thenReturn(filesMock);

      runtimeStatic = mockStatic(GDataRuntime.class, CALLS_REAL_METHODS);
      runtimeStatic.when(() -> GDataRuntime.getDrive(any())).thenReturn(driveMock);

      GDataRuntime.listSpreadsheetFiles(fakeDataSource());

      ArgumentCaptor<String> fieldsCaptor = ArgumentCaptor.forClass(String.class);
      verify(listRequest).setFields(fieldsCaptor.capture());
      String mask = fieldsCaptor.getValue();

      assertTrue(mask.contains("nextPageToken"), mask);
      assertTrue(mask.contains("id"), mask);
      assertFalse(mask.contains("name"), mask);
   }

   @Test
   void listDatasets_dropsNonGridSheets() throws Exception {
      // F4/A18: a chart-only OBJECT sheet has no grid and can only ever fail describeDataset.
      runtimeStatic = mockStatic(GDataRuntime.class);
      runtimeStatic.when(() -> GDataRuntime.listSpreadsheetFiles(any()))
         .thenReturn(List.of(file("F1")));
      runtimeStatic.when(() -> GDataRuntime.listSheetProperties(any(), eq("F1")))
         .thenReturn(List.of(
            sheetProps(0, "Sheet1", "GRID"),
            sheetProps(1, "Chart1", "OBJECT"),
            sheetProps(2, "DataSrc", "DATA_SOURCE")));

      TabularCatalog catalog = GDataCatalog.listDatasets(fakeDataSource());

      assertEquals(List.of(new TabularDatasetRef("F1~0")), catalog.datasets());
   }

   @Test
   void listDatasets_nullSheetTypeIsTreatedAsGrid() throws Exception {
      runtimeStatic = mockStatic(GDataRuntime.class);
      runtimeStatic.when(() -> GDataRuntime.listSpreadsheetFiles(any()))
         .thenReturn(List.of(file("F1")));
      runtimeStatic.when(() -> GDataRuntime.listSheetProperties(any(), eq("F1")))
         .thenReturn(List.of(sheetProps(0, "Sheet1", null)));

      TabularCatalog catalog = GDataCatalog.listDatasets(fakeDataSource());

      assertEquals(1, catalog.datasets().size());
   }

   @Test
   void listDatasets_sheetIdZero_isIncludedNotTreatedAsAbsent() throws Exception {
      // sheetId 0 is Sheets' real default first sheet -- the guard must be `== null`, never a
      // truthiness/isEmpty()-style check that would treat 0 as absent.
      runtimeStatic = mockStatic(GDataRuntime.class);
      runtimeStatic.when(() -> GDataRuntime.listSpreadsheetFiles(any()))
         .thenReturn(List.of(file("F1")));
      runtimeStatic.when(() -> GDataRuntime.listSheetProperties(any(), eq("F1")))
         .thenReturn(List.of(sheetProps(0, "Sheet1")));

      TabularCatalog catalog = GDataCatalog.listDatasets(fakeDataSource());

      assertEquals(List.of(new TabularDatasetRef("F1~0")), catalog.datasets());
   }

   @Test
   void listDatasets_idsAreUniqueEvenWhenSheetIdCollidesAcrossSpreadsheets() throws Exception {
      // A4(b): two different spreadsheets can each have a sheetId 0 (their own default first
      // sheet) -- the composite id must not collide.
      runtimeStatic = mockStatic(GDataRuntime.class);
      runtimeStatic.when(() -> GDataRuntime.listSpreadsheetFiles(any()))
         .thenReturn(List.of(file("F1"), file("F2")));
      runtimeStatic.when(() -> GDataRuntime.listSheetProperties(any(), eq("F1")))
         .thenReturn(List.of(sheetProps(0, "Sheet1")));
      runtimeStatic.when(() -> GDataRuntime.listSheetProperties(any(), eq("F2")))
         .thenReturn(List.of(sheetProps(0, "Sheet1")));

      TabularCatalog catalog = GDataCatalog.listDatasets(fakeDataSource());

      Set<String> ids = catalog.datasets().stream().map(TabularDatasetRef::id)
         .collect(java.util.stream.Collectors.toSet());
      assertEquals(2, ids.size());
   }

   @Test
   void listDatasets_spreadsheetWithZeroSheets_contributesNoDatasets() throws Exception {
      runtimeStatic = mockStatic(GDataRuntime.class);
      runtimeStatic.when(() -> GDataRuntime.listSpreadsheetFiles(any()))
         .thenReturn(List.of(file("F1")));
      runtimeStatic.when(() -> GDataRuntime.listSheetProperties(any(), eq("F1")))
         .thenReturn(List.of());

      TabularCatalog catalog = GDataCatalog.listDatasets(fakeDataSource());

      assertTrue(catalog.datasets().isEmpty());
   }

   @Test
   void listDatasets_emptyDrive_returnsEmptyWithoutThrowing() throws Exception {
      // The "no datasets to annotate" error belongs to TabularCatalogService, not this connector.
      runtimeStatic = mockStatic(GDataRuntime.class);
      runtimeStatic.when(() -> GDataRuntime.listSpreadsheetFiles(any())).thenReturn(List.of());

      TabularCatalog catalog = GDataCatalog.listDatasets(fakeDataSource());

      assertTrue(catalog.datasets().isEmpty());
      assertTrue(catalog.relationships().isEmpty());
   }

   @Test
   void listDatasets_driveCallFails_propagatesRatherThanReturningEmptyCatalog() {
      runtimeStatic = mockStatic(GDataRuntime.class);
      runtimeStatic.when(() -> GDataRuntime.listSpreadsheetFiles(any()))
         .thenThrow(new RuntimeException("credential revoked"));

      assertThrows(RuntimeException.class, () -> GDataCatalog.listDatasets(fakeDataSource()));
   }

   // =====================================================================================
   // describeDataset
   // =====================================================================================

   private void stubDescribe(int sheetId, String title, List<RowData> rows) {
      runtimeStatic = mockStatic(GDataRuntime.class);
      runtimeStatic.when(() -> GDataRuntime.listSheetProperties(any(), any()))
         .thenReturn(List.of(sheetProps(sheetId, title)));
      runtimeStatic.when(() -> GDataRuntime.fetchSampleRows(any(), any(), any()))
         .thenReturn(rows);
   }

   private String id(int sheetId) {
      return SheetsDatasetId.compose("SS1", Integer.toString(sheetId));
   }

   @Test
   void describeDataset_columnCountWiderThanData_reportsDataWidth() throws Exception {
      // A5/T1, but read this docstring before trusting the test name: this test alone does NOT
      // structurally pin "gridProperties.columnCount is never trusted". P6 fix round 1's mutation
      // test proved that -- widening `width` to `Math.max(width, 26)` after the real
      // width-from-sampled-rows loop leaves this test GREEN, because the resulting 23 phantom
      // columns fall through cellAt's bounds check (headerCell == null for c >= 3), headerName()
      // returns null for a null cell, and the drop logic removes all 23 of them -- the column
      // count "self-heals" back to 3 through the header-drop mechanism, a completely different
      // code path than the one this test's name claims to guard.
      //
      // What this test DOES pin: three header cells, however that number reaches describeDataset,
      // produce three columns -- not some other count derived from the row data's shape (e.g. the
      // longest data row, or a hardcoded constant).
      //
      // The STRUCTURAL guarantee -- that GDataRuntime.listSheetProperties' field mask never even
      // requests gridProperties, so there is no columnCount available to trust in the first place
      // -- is pinned by listSheetProperties_realBody_fieldMaskRequestsPropertiesButNotGridProperties
      // below, which executes the real method body and asserts on the actual field-mask string.
      stubDescribe(0, "Sheet1", List.of(
         row(stringCell("A"), stringCell("B"), stringCell("C")),
         row(stringCell("a1"), stringCell("b1"), stringCell("c1"))));

      TabularDatasetSchema schema = GDataCatalog.describeDataset(fakeDataSource(), id(0));

      assertEquals(3, schema.columns().size());
   }

   @Test
   void listSheetProperties_realBody_fieldMaskRequestsPropertiesButNotGridProperties()
      throws Exception
   {
      // A5/T1, the structural half. Every other test in this class mocks listSheetProperties
      // itself, so nothing until now has executed its REAL body or its own field-mask string --
      // that gap is exactly what let the mutation above pass undetected (see the docstring on
      // describeDataset_columnCountWiderThanData_reportsDataWidth). This test runs the real body
      // via CALLS_REAL_METHODS, mocking only getSheets (the seam, widened package-private for
      // this test the same way getDrive already was for listSpreadsheetFiles' A3 test), and
      // captures the exact string passed to setFields.
      //
      // Rejects two different wrong implementations: one that ADDS "gridProperties" to the mask
      // (T1's trap made reachable again -- verified by hand: adding
      // ".gridProperties(columnCount)" to the mask string in listSheetProperties turns this RED),
      // and one that DROPS "sheetId"/"title"/"sheetType" (the fields describeDataset actually
      // needs -- X6's title resolution and A18's non-GRID filter would silently stop working).
      Sheets.Spreadsheets.Get getRequest = mock(Sheets.Spreadsheets.Get.class, RETURNS_SELF);
      Spreadsheet response = new Spreadsheet().setSheets(
         List.of(new Sheet().setProperties(sheetProps(0, "Sheet1"))));
      when(getRequest.execute()).thenReturn(response);

      Sheets.Spreadsheets spreadsheetsResource = mock(Sheets.Spreadsheets.class);
      when(spreadsheetsResource.get(any())).thenReturn(getRequest);

      Sheets sheetsMock = mock(Sheets.class);
      when(sheetsMock.spreadsheets()).thenReturn(spreadsheetsResource);

      runtimeStatic = mockStatic(GDataRuntime.class, CALLS_REAL_METHODS);
      runtimeStatic.when(() -> GDataRuntime.getSheets(any(), anyBoolean())).thenReturn(sheetsMock);

      GDataRuntime.listSheetProperties(fakeDataSource(), "SS1");

      ArgumentCaptor<String> fieldsCaptor = ArgumentCaptor.forClass(String.class);
      verify(getRequest).setFields(fieldsCaptor.capture());
      String mask = fieldsCaptor.getValue();

      assertTrue(mask.contains("sheetId"), mask);
      assertTrue(mask.contains("title"), mask);
      assertTrue(mask.contains("sheetType"), mask);
      assertFalse(mask.contains("gridProperties"), mask);
   }

   @Test
   void fetchSampleRows_realBody_fieldMaskRequestsEffectiveValueAndNumberFormat() throws Exception {
      // Reviewer nit 3's other half, added on judgement (not instructed): fetchSampleRows' own
      // real body and field mask were equally unexecuted by any test before this -- every other
      // test mocks it out entirely, and A17 only verifies the a1Range ARGUMENT passed to the
      // mocked method, never the field mask inside the real implementation. Unlike
      // listSheetProperties there is no "must never request X" trap here -- this pins the ordinary
      // completeness guarantee that the sample actually asks for cell values and their number
      // format, not that it must exclude something.
      Sheets.Spreadsheets.Get getRequest = mock(Sheets.Spreadsheets.Get.class, RETURNS_SELF);
      Spreadsheet response = new Spreadsheet().setSheets(List.of(new Sheet()
         .setData(List.of(new GridData().setRowData(List.of(row(stringCell("A"))))))));
      when(getRequest.execute()).thenReturn(response);

      Sheets.Spreadsheets spreadsheetsResource = mock(Sheets.Spreadsheets.class);
      when(spreadsheetsResource.get(any())).thenReturn(getRequest);

      Sheets sheetsMock = mock(Sheets.class);
      when(sheetsMock.spreadsheets()).thenReturn(spreadsheetsResource);

      runtimeStatic = mockStatic(GDataRuntime.class, CALLS_REAL_METHODS);
      runtimeStatic.when(() -> GDataRuntime.getSheets(any(), anyBoolean())).thenReturn(sheetsMock);

      GDataRuntime.fetchSampleRows(fakeDataSource(), "SS1", "'Sheet1'!1:4");

      verify(getRequest).setRanges(List.of("'Sheet1'!1:4"));
      ArgumentCaptor<String> fieldsCaptor = ArgumentCaptor.forClass(String.class);
      verify(getRequest).setFields(fieldsCaptor.capture());
      String mask = fieldsCaptor.getValue();

      assertTrue(mask.contains("effectiveValue"), mask);
      assertTrue(mask.contains("numberFormat"), mask);
   }

   @Test
   void describeDataset_headerRowHasInteriorGap_dropsThatColumnAndFlagsIncomplete() throws Exception {
      stubDescribe(0, "Sheet1", List.of(
         row(stringCell("Name"), emptyCell(), stringCell("Age")),
         row(stringCell("Alice"), stringCell("x"), numberCell(30, null))));

      TabularDatasetSchema schema = GDataCatalog.describeDataset(fakeDataSource(), id(0));

      assertEquals(List.of("Name", "Age"),
         schema.columns().stream().map(TabularColumn::name).toList());
      assertTrue(schema.columnsMayBeIncomplete());
   }

   @Test
   void describeDataset_tidySheet_flagIsFalse() throws Exception {
      stubDescribe(0, "Sheet1", List.of(
         row(stringCell("Name"), stringCell("Age")),
         row(stringCell("Alice"), numberCell(30, null))));

      TabularDatasetSchema schema = GDataCatalog.describeDataset(fakeDataSource(), id(0));

      assertFalse(schema.columnsMayBeIncomplete());
   }

   @Test
   void describeDataset_whitespaceOnlyHeader_dropsThatColumnAtTheConnectorLayer() throws Exception {
      // A6/C3: a whitespace-only header is a THIRD case, distinct from empty and error-valued.
      // The assertion must land on the connector's own return value, not on
      // TabularCatalogService.validateColumnNames rejecting it.
      stubDescribe(0, "Sheet1", List.of(
         row(stringCell("Name"), stringCell("   "), stringCell("Age"))));

      TabularDatasetSchema schema = GDataCatalog.describeDataset(fakeDataSource(), id(0));

      assertEquals(List.of("Name", "Age"),
         schema.columns().stream().map(TabularColumn::name).toList());
      assertTrue(schema.columnsMayBeIncomplete());
   }

   @Test
   void describeDataset_duplicateHeaders_keepsFirstDropsRestAndFlagsIncomplete() throws Exception {
      // F1/Q3: the licensed deviation -- keep first, drop the rest, flag incomplete.
      stubDescribe(0, "Sheet1", List.of(
         row(stringCell("Notes"), stringCell("Age"), stringCell("Notes")),
         row(stringCell("n1"), numberCell(1, null), stringCell("n2"))));

      TabularDatasetSchema schema = GDataCatalog.describeDataset(fakeDataSource(), id(0));

      assertEquals(List.of("Notes", "Age"),
         schema.columns().stream().map(TabularColumn::name).toList());
      assertTrue(schema.columnsMayBeIncomplete());
   }

   @Test
   void describeDataset_headerCellHoldsAnError_dropsThatColumn() throws Exception {
      // F5: a second route to a null header name that T2 does not name.
      stubDescribe(0, "Sheet1", List.of(
         row(stringCell("Name"), errorCell(), stringCell("Age"))));

      TabularDatasetSchema schema = GDataCatalog.describeDataset(fakeDataSource(), id(0));

      assertEquals(List.of("Name", "Age"),
         schema.columns().stream().map(TabularColumn::name).toList());
      assertTrue(schema.columnsMayBeIncomplete());
   }

   @Test
   void describeDataset_headerCellIsNonString_dropsThatColumn() throws Exception {
      // D2: a numeric header cell (e.g. 2024) -- runQuery's getValidHeaderName is never reached
      // for it either (guarded by `data[c] instanceof String`), so there is no honest string name
      // to report. [unverified -- do not assert either way] what runQuery ultimately names it.
      stubDescribe(0, "Sheet1", List.of(
         row(stringCell("Name"), numberCell(2024, null), stringCell("Age"))));

      TabularDatasetSchema schema = GDataCatalog.describeDataset(fakeDataSource(), id(0));

      assertEquals(List.of("Name", "Age"),
         schema.columns().stream().map(TabularColumn::name).toList());
      assertTrue(schema.columnsMayBeIncomplete());
   }

   @Test
   void describeDataset_allSampledCellsEmpty_columnIsString() throws Exception {
      // X1: a column whose sampled cells are all empty must still get a legal type, not a null
      // one or an NPE.
      stubDescribe(0, "Sheet1", List.of(
         row(stringCell("Name"), stringCell("Notes")),
         row(stringCell("Alice"), emptyCell()),
         row(stringCell("Bob"), emptyCell()),
         row(stringCell("Cara"), emptyCell())));

      TabularDatasetSchema schema = GDataCatalog.describeDataset(fakeDataSource(), id(0));

      TabularColumn notes = schema.columns().stream()
         .filter(c -> c.name().equals("Notes")).findFirst().orElseThrow();
      assertEquals(XSchema.STRING, notes.type());
   }

   @Test
   void describeDataset_errorValuedDataCell_contributesNoTypeSignal_fallsThroughToLaterRow()
      throws Exception
   {
      // A20: an error-valued cell in a DATA row (as opposed to the header) contributes no type
      // signal and must not itself become the resolved type; the next sampled row's real value
      // should win.
      stubDescribe(0, "Sheet1", List.of(
         row(stringCell("Score")),
         row(errorCell()),
         row(numberCell(5, null))));

      TabularDatasetSchema schema = GDataCatalog.describeDataset(fakeDataSource(), id(0));

      assertEquals(XSchema.DOUBLE, schema.columns().get(0).type());
   }

   @Test
   void describeDataset_allSampledCellsAreErrors_fallsBackToString() throws Exception {
      stubDescribe(0, "Sheet1", List.of(
         row(stringCell("Score")),
         row(errorCell()),
         row(errorCell()),
         row(errorCell())));

      TabularDatasetSchema schema = GDataCatalog.describeDataset(fakeDataSource(), id(0));

      assertEquals(XSchema.STRING, schema.columns().get(0).type());
   }

   @Test
   void describeDataset_sampledRowShorterThanWidth_noIndexError() throws Exception {
      // The bounds check in GDataCatalog.cellAt is what prevents this: a data row can legitimately
      // be shorter than the reported width (Sheets truncates trailing empty cells from
      // rowData.values), so a blind row.getValues().get(c) would throw
      // IndexOutOfBoundsException the first time a real spreadsheet has a short row -- the single
      // most likely defect in an implementation that reads sampled cells by column index.
      stubDescribe(0, "Sheet1", List.of(
         row(stringCell("A"), stringCell("B"), stringCell("C")),
         row(stringCell("onlyOneCell"))));

      assertDoesNotThrow(() -> GDataCatalog.describeDataset(fakeDataSource(), id(0)));
   }

   @Test
   void describeDataset_requestsFourRowsInOneSampleCall() throws Exception {
      // A17 (as amended, C2): ONE sample call (fetchSampleRows), plus the separate
      // listSheetProperties call that resolves the title -- two calls total, never a call per
      // column and never the whole sheet.
      stubDescribe(0, "Sheet1", List.of(row(stringCell("A"))));

      GDataCatalog.describeDataset(fakeDataSource(), id(0));

      runtimeStatic.verify(
         () -> GDataRuntime.fetchSampleRows(any(), eq("SS1"), eq("'Sheet1'!1:4")), times(1));
      runtimeStatic.verify(
         () -> GDataRuntime.listSheetProperties(any(), eq("SS1")), times(1));
   }

   @Test
   void describeDataset_sheetTitleWithSpaceNeedsQuoting_rangeIsQuoted() throws Exception {
      // F6/A19: describeDataset quotes; runQuery (GDataRuntime.java:75) does not -- an X4
      // mismatch recorded, not levelled (N2).
      stubDescribe(0, "Sales 2024", List.of(row(stringCell("A"))));

      GDataCatalog.describeDataset(fakeDataSource(), id(0));

      runtimeStatic.verify(
         () -> GDataRuntime.fetchSampleRows(any(), any(), eq("'Sales 2024'!1:4")));
   }

   @Test
   void describeDataset_sheetTitleWithEmbeddedQuote_isDoubled() throws Exception {
      stubDescribe(0, "Bob's data", List.of(row(stringCell("A"))));

      GDataCatalog.describeDataset(fakeDataSource(), id(0));

      runtimeStatic.verify(
         () -> GDataRuntime.fetchSampleRows(any(), any(), eq("'Bob''s data'!1:4")));
   }

   @Test
   void describeDataset_nonAsciiHeaderText_isPreservedVerbatim() throws Exception {
      stubDescribe(0, "Sheet1", List.of(
         row(stringCell("姓名"), stringCell("Age")),
         row(stringCell("张三"), numberCell(30, null))));

      TabularDatasetSchema schema = GDataCatalog.describeDataset(fakeDataSource(), id(0));

      assertEquals(List.of("姓名", "Age"),
         schema.columns().stream().map(TabularColumn::name).toList());
   }

   @Test
   void describeDataset_everyColumnIsDimensionNull() throws Exception {
      // A7: the contract forbids inferring dimension/measure from the type.
      stubDescribe(0, "Sheet1", List.of(
         row(stringCell("Name"), stringCell("Age")),
         row(stringCell("Alice"), numberCell(30, null))));

      TabularDatasetSchema schema = GDataCatalog.describeDataset(fakeDataSource(), id(0));

      assertTrue(schema.columns().stream().allMatch(c -> c.isDimension() == null));
   }

   @Test
   void describeDataset_dateFormattedColumn_isDate() throws Exception {
      stubDescribe(0, "Sheet1", List.of(
         row(stringCell("Signup")),
         row(numberCell(45000, "DATE"))));

      TabularDatasetSchema schema = GDataCatalog.describeDataset(fakeDataSource(), id(0));

      assertEquals(XSchema.DATE, schema.columns().get(0).type());
   }

   @Test
   void describeDataset_unknownNumberFormatType_isDouble() throws Exception {
      // A8's live case at the catalog level: an unrecognised numberFormat.getType() string is a
      // real value a live server can send, not a theoretical one.
      stubDescribe(0, "Sheet1", List.of(
         row(stringCell("X")),
         row(numberCell(1, "SOME_FUTURE_FORMAT_TYPE"))));

      TabularDatasetSchema schema = GDataCatalog.describeDataset(fakeDataSource(), id(0));

      assertEquals(XSchema.DOUBLE, schema.columns().get(0).type());
   }

   @Test
   void describeDataset_paramsIsExactlySpreadsheetIdWorksheetIdFirstRowAsHeader() throws Exception {
      stubDescribe(7, "Sheet1", List.of(row(stringCell("A"))));

      TabularDatasetSchema schema = GDataCatalog.describeDataset(fakeDataSource(), id(7));

      Map<String, String> params = schema.params();
      // A9: the exact key SET, not merely "does not contain some other key" -- stays green only if
      // the alias names themselves have not silently drifted.
      assertEquals(Set.of("spreadsheetId", "worksheetId", "firstRowAsHeader"), params.keySet());
      assertEquals("SS1", params.get("spreadsheetId"));
      assertEquals("7", params.get("worksheetId"));
      assertEquals("true", params.get("firstRowAsHeader"));

      // The keys must be the query bean's OWN real property names, derived by reflection, not
      // asserted as a bare literal -- guards the case where GDataQuery's getters get renamed and
      // GDataCatalog's literals are not updated to match.
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(GDataQuery.class);
      assertTrue(pmap.containsKey("spreadsheetId"));
      assertTrue(pmap.containsKey("worksheetId"));
      assertTrue(pmap.containsKey("firstRowAsHeader"));
   }

   @Test
   void describeDataset_datasetIdEchoedVerbatim() throws Exception {
      stubDescribe(0, "Sheet1", List.of(row(stringCell("A"))));

      String requestedId = id(0);
      TabularDatasetSchema schema = GDataCatalog.describeDataset(fakeDataSource(), requestedId);

      assertEquals(requestedId, schema.datasetId());
   }

   @Test
   void describeDataset_unknownDatasetId_throws() {
      // X6: an id this data source never emitted must throw, not answer with some other sheet's
      // schema.
      stubListSheetProperties(List.of(sheetProps(0, "Sheet1")));

      String unknownId = SheetsDatasetId.compose("SS1", "999");
      assertThrows(Exception.class,
         () -> GDataCatalog.describeDataset(fakeDataSource(), unknownId));
   }

   @Test
   void describeDataset_sheetsCallFails_propagates() {
      runtimeStatic = mockStatic(GDataRuntime.class);
      runtimeStatic.when(() -> GDataRuntime.listSheetProperties(any(), any()))
         .thenThrow(new RuntimeException("credential revoked"));

      assertThrows(RuntimeException.class,
         () -> GDataCatalog.describeDataset(fakeDataSource(), id(0)));
   }

   @Test
   void describeDataset_sampleFetchFails_propagates() {
      runtimeStatic = mockStatic(GDataRuntime.class);
      runtimeStatic.when(() -> GDataRuntime.listSheetProperties(any(), any()))
         .thenReturn(List.of(sheetProps(0, "Sheet1")));
      runtimeStatic.when(() -> GDataRuntime.fetchSampleRows(any(), any(), any()))
         .thenThrow(new RuntimeException("credential revoked"));

      assertThrows(RuntimeException.class,
         () -> GDataCatalog.describeDataset(fakeDataSource(), id(0)));
   }

   @Test
   void describeDataset_noRowsAtAll_throws() {
      // A11: returning an empty column list is forbidden -- an empty/all-blank sample throws.
      stubDescribe(0, "Sheet1", List.of());

      assertThrows(Exception.class, () -> GDataCatalog.describeDataset(fakeDataSource(), id(0)));
   }

   @Test
   void describeDataset_singleRowSheet_headerOnly_allColumnsAreString() throws Exception {
      // A sheet with exactly one row: the header itself, no data rows at all -- every column's
      // whole type sample is empty, and X1 still applies (STRING, not null, not a throw).
      stubDescribe(0, "Sheet1", List.of(row(stringCell("Name"), stringCell("Age"))));

      TabularDatasetSchema schema = GDataCatalog.describeDataset(fakeDataSource(), id(0));

      assertEquals(2, schema.columns().size());
      assertTrue(schema.columns().stream().allMatch(c -> XSchema.STRING.equals(c.type())));
   }

   @Test
   void describeDataset_sheetIdZero_resolvesCorrectly() throws Exception {
      stubDescribe(0, "Sheet1", List.of(row(stringCell("A"))));

      TabularDatasetSchema schema = GDataCatalog.describeDataset(fakeDataSource(), id(0));

      assertEquals(id(0), schema.datasetId());
   }

   @Test
   void describeDataset_labelIsRawHeaderOnlyWhenItDiffersFromTrimmedName() throws Exception {
      // Q7: label == null when header and trimmed name agree; label == raw header when they
      // differ (e.g. a header with padding whitespace that gets trimmed into the name).
      stubDescribe(0, "Sheet1", List.of(row(stringCell("Name"), stringCell(" Age "))));

      TabularDatasetSchema schema = GDataCatalog.describeDataset(fakeDataSource(), id(0));

      TabularColumn name = schema.columns().get(0);
      TabularColumn age = schema.columns().get(1);
      assertNull(name.label());
      assertEquals(" Age ", age.label());
      assertEquals("Age", age.name());
   }
}
