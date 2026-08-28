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
package inetsoft.uql.onedrive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.File;
import com.microsoft.graph.models.Folder;
import com.microsoft.graph.requests.DriveItemCollectionPage;
import com.microsoft.graph.requests.DriveItemCollectionRequest;
import com.microsoft.graph.requests.DriveItemCollectionRequestBuilder;
import com.microsoft.graph.requests.DriveItemRequestBuilder;
import com.microsoft.graph.requests.GraphServiceClient;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.tabular.BrowsableQuery;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, OneDriveRuntimeTests.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
public class OneDriveRuntimeTests {
   private OneDriveDataSource dataSource;
   private OneDriveRuntime runtime;

   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

   @BeforeEach
   void setupDataSource() {
      dataSource = new OneDriveDataSource();
      dataSource.setName("OneDrive Test");
      dataSource.setAccessToken("AnyAccessToken");
      runtime = new OneDriveRuntime();
   }

   @Test
   void shouldReadCSV() throws Exception {
      Object[][] expected = readExpected("shouldReadCSV.expected.json");

      OneDriveQuery query0 = new OneDriveQuery();
      OneDriveQuery query = spy(query0);
      query.setDataSource(dataSource);
      query.setName("OneDrive Test Query");
      query.setPath("Test/TestCSV.csv");

      doAnswer((invocation) -> readFile("TestCSV.csv")).when(query).getFile();
      assert OneDriveRuntime.getFileURL(query)
         .equals("https://graph.microsoft.com/v1.0/me/drive/root:/Test%2FTestCSV.csv:/content");
      XNodeTableLens table = getTable(query);

      try {
         Object[][] actual = getData(table);
         assertArrayEquals(expected, actual);
      }
      finally {
         table.dispose();
      }
   }

   @Test
   void shouldReadExcel() throws Exception {
      Object[][] expected = readExpected("shouldReadExcel.expected.json");

      OneDriveQuery query0 = new OneDriveQuery();
      OneDriveQuery query = spy(query0);
      query.setDataSource(dataSource);
      query.setName("OneDrive Test Query");
      query.setPath("Test/TestExcel.xlsx");

      doAnswer((invocation) -> readFile("TestExcel.xlsx")).when(query).getFile();
      query.setExcelSheet("Sheet2");
      assert OneDriveRuntime.getFileURL(query)
         .equals("https://graph.microsoft.com/v1.0/me/drive/root:/Test%2FTestExcel.xlsx:/content");
      XNodeTableLens table = getTable(query);

      try {
         Object[][] actual = getData(table);
         assertArrayEquals(expected, actual);
      }
      finally {
         table.dispose();
      }
   }

   // ─── BrowsableQuery.browseChildren / OneDriveRuntime.collectChildren ──────
   //
   // Exercised directly against collectChildren (package-private for exactly this reason) with a
   // fully mocked GraphServiceClient, rather than through listChildren -- listChildren's own
   // getClient(ds) builds a REAL client from a real OneDriveAuthenticator, which this file's
   // existing shouldReadCSV/shouldReadExcel tests never exercise either (they stub query.getFile()
   // one layer up instead).

   private DriveItem folderItem(String name) {
      DriveItem item = new DriveItem();
      item.name = name;
      item.folder = new Folder();
      return item;
   }

   private DriveItem fileItem(String name) {
      DriveItem item = new DriveItem();
      item.name = name;
      item.file = new File();
      return item;
   }

   /** A page whose getNextPage() is null -- the last (or only) page of a listing. */
   private DriveItemCollectionPage page(DriveItem... items) {
      DriveItemCollectionPage page = mock(DriveItemCollectionPage.class);
      when(page.getCurrentPage()).thenReturn(List.of(items));
      return page;
   }

   /**
    * A client whose {@code me().drive().root()} resolves to {@code root} -- built by hand, one
    * concretely-typed mock per hop, rather than {@code RETURNS_DEEP_STUBS}: several of this SDK's
    * builder methods ({@code buildRequest()}, {@code get()}, {@code getNextPage()}) return a
    * generic type parameter that erases to a common base class
    * ({@code BaseCollectionRequestBuilder}/{@code BaseCollectionPage}) in the compiled bytecode,
    * and Mockito's deep-stub auto-mocking picks that ERASED type -- producing an object the calling
    * code's own implicit (compiler-inserted, covariant-generics) cast then rejects with a
    * ClassCastException. Hand-built, concretely-typed mocks at every hop sidestep this entirely.
    */
   private DriveItemRequestBuilder mockClient(GraphServiceClient client) {
      com.microsoft.graph.requests.UserRequestBuilder user =
         mock(com.microsoft.graph.requests.UserRequestBuilder.class);
      com.microsoft.graph.requests.DriveRequestBuilder drive =
         mock(com.microsoft.graph.requests.DriveRequestBuilder.class);
      DriveItemRequestBuilder root = mock(DriveItemRequestBuilder.class);
      when(client.me()).thenReturn(user);
      when(user.drive()).thenReturn(drive);
      when(drive.root()).thenReturn(root);
      return root;
   }

   /** Wires {@code builder.children().buildRequest().get()} to answer with {@code firstPage}. */
   private void stubChildren(DriveItemRequestBuilder builder, DriveItemCollectionPage firstPage) {
      DriveItemCollectionRequestBuilder childrenBuilder =
         mock(DriveItemCollectionRequestBuilder.class);
      DriveItemCollectionRequest request = mock(DriveItemCollectionRequest.class);
      when(builder.children()).thenReturn(childrenBuilder);
      when(childrenBuilder.buildRequest()).thenReturn(request);
      when(request.get()).thenReturn(firstPage);
   }

   private void stubChildrenThrows(DriveItemRequestBuilder builder, RuntimeException failure) {
      DriveItemCollectionRequestBuilder childrenBuilder =
         mock(DriveItemCollectionRequestBuilder.class);
      DriveItemCollectionRequest request = mock(DriveItemCollectionRequest.class);
      when(builder.children()).thenReturn(childrenBuilder);
      when(childrenBuilder.buildRequest()).thenReturn(request);
      when(request.get()).thenThrow(failure);
   }

   /** Wires {@code page.getNextPage()} so following it re-fetches {@code nextPage}. */
   private void stubNextPage(DriveItemCollectionPage page, DriveItemCollectionPage nextPage) {
      DriveItemCollectionRequestBuilder nextBuilder = mock(DriveItemCollectionRequestBuilder.class);
      DriveItemCollectionRequest nextRequest = mock(DriveItemCollectionRequest.class);
      when(page.getNextPage()).thenReturn(nextBuilder);
      when(nextBuilder.buildRequest()).thenReturn(nextRequest);
      when(nextRequest.get()).thenReturn(nextPage);
   }

   @Test
   void rootListingDistinguishesFoldersFromFiles() {
      GraphServiceClient client = mock(GraphServiceClient.class);
      DriveItemRequestBuilder root = mockClient(client);
      stubChildren(root, page(folderItem("Test"), fileItem("a.csv")));

      List<BrowsableQuery.BrowseEntry> entries = new ArrayList<>();
      boolean truncated = OneDriveRuntime.collectChildren(
         client, "", false, List.of(), 2000, entries, new int[] { 0 });

      assertFalse(truncated);
      assertEquals(2, entries.size());
      assertTrue(entries.stream().anyMatch(e -> e.name().equals("Test") && e.folder()));
      assertTrue(entries.stream().anyMatch(e -> e.name().equals("a.csv") && !e.folder()));
   }

   @Test
   void paginationIsFollowedToCompletionRatherThanStoppingAtTheFirstPage() {
      GraphServiceClient client = mock(GraphServiceClient.class);
      DriveItemRequestBuilder root = mockClient(client);
      DriveItemCollectionPage page1 = page(fileItem("page1.csv"));
      DriveItemCollectionPage page2 = page(fileItem("page2.csv"));
      stubNextPage(page1, page2);
      stubChildren(root, page1);

      List<BrowsableQuery.BrowseEntry> entries = new ArrayList<>();
      boolean truncated = OneDriveRuntime.collectChildren(
         client, "", false, List.of(), 2000, entries, new int[] { 0 });

      assertFalse(truncated);
      assertEquals(
         List.of("page1.csv", "page2.csv"),
         entries.stream().map(BrowsableQuery.BrowseEntry::name).toList());
   }

   @Test
   void recursiveWalksIntoAReturnedSubFolderWithJoinedPaths() {
      GraphServiceClient client = mock(GraphServiceClient.class);
      DriveItemRequestBuilder root = mockClient(client);
      stubChildren(root, page(folderItem("Test")));

      DriveItemRequestBuilder sub = mock(DriveItemRequestBuilder.class);
      when(root.itemWithPath("Test")).thenReturn(sub);
      stubChildren(sub, page(fileItem("a.csv")));

      List<BrowsableQuery.BrowseEntry> entries = new ArrayList<>();
      boolean truncated = OneDriveRuntime.collectChildren(
         client, "", true, List.of(), 2000, entries, new int[] { 0 });

      assertFalse(truncated);
      assertTrue(entries.stream().anyMatch(e -> e.path().equals("Test") && e.folder()));
      assertTrue(entries.stream().anyMatch(e -> e.path().equals("Test/a.csv") && !e.folder()));
   }

   @Test
   void maxEntriesReachedMidWalkStopsEarlyAndReportsTruncated() {
      GraphServiceClient client = mock(GraphServiceClient.class);
      DriveItemRequestBuilder root = mockClient(client);
      stubChildren(root, page(fileItem("a.csv"), fileItem("b.csv"), fileItem("c.csv")));

      List<BrowsableQuery.BrowseEntry> entries = new ArrayList<>();
      boolean truncated = OneDriveRuntime.collectChildren(
         client, "", false, List.of(), 2, entries, new int[] { 0 });

      assertTrue(truncated);
      assertEquals(2, entries.size());
   }

   /**
    * A folder tree exceeding MAX_GRAPH_REQUESTS stops and reports truncated -- independent of
    * maxEntries, which this case never comes close to (each folder holds only one item).
    */
   @Test
   void graphRequestCapStopsTheWalkIndependentlyOfTheEntriesCap() {
      // A chain of single-subfolder folders: "f", "f/f", "f/f/f", ... -- each fetch finds exactly
      // one more folder to recurse into, so the walk never comes close to the entries cap and is
      // stopped purely by MAX_GRAPH_REQUESTS.
      GraphServiceClient client = mock(GraphServiceClient.class);
      DriveItemRequestBuilder root = mockClient(client);
      stubChildren(root, page(folderItem("f")));

      // Every recursive call re-derives its builder from root().itemWithPath(fullPath) directly
      // (see collectChildren's own ternary) -- never by chaining off the previous itemWithPath
      // result -- so each stub below is keyed off root, not off the previous iteration's builder.
      String path = "f";

      for(int i = 0; i < OneDriveRuntime.MAX_GRAPH_REQUESTS + 5; i++) {
         DriveItemRequestBuilder next = mock(DriveItemRequestBuilder.class);
         when(root.itemWithPath(path)).thenReturn(next);
         stubChildren(next, page(folderItem("f")));
         path = path + "/f";
      }

      List<BrowsableQuery.BrowseEntry> entries = new ArrayList<>();
      boolean truncated = OneDriveRuntime.collectChildren(
         client, "", true, List.of(), 100_000, entries, new int[] { 0 });

      assertTrue(truncated);
      assertTrue(entries.size() < 100_000, "must have stopped well short of the entries cap");
   }

   @Test
   void aGraphFailurePropagatesUncaughtRatherThanReturningAnEmptyListing() {
      GraphServiceClient client = mock(GraphServiceClient.class);
      DriveItemRequestBuilder root = mockClient(client);
      stubChildrenThrows(root, new ClientException("throttled", null));

      assertThrows(ClientException.class, () -> OneDriveRuntime.collectChildren(
         client, "", false, List.of(), 2000, new ArrayList<>(), new int[] { 0 }));
   }

   @Test
   void acceptTypesFiltersFilesButNeverFolders() {
      GraphServiceClient client = mock(GraphServiceClient.class);
      DriveItemRequestBuilder root = mockClient(client);
      stubChildren(root, page(folderItem("Test"), fileItem("a.csv"), fileItem("b.txt")));

      List<BrowsableQuery.BrowseEntry> entries = new ArrayList<>();
      OneDriveRuntime.collectChildren(
         client, "", false, List.of(".csv"), 2000, entries, new int[] { 0 });

      assertEquals(
         List.of("Test", "a.csv"),
         entries.stream().map(BrowsableQuery.BrowseEntry::name).toList());
   }

   @Test
   void emptyAcceptTypesAcceptsEveryFile() {
      GraphServiceClient client = mock(GraphServiceClient.class);
      DriveItemRequestBuilder root = mockClient(client);
      stubChildren(root, page(fileItem("a.csv"), fileItem("b.txt"), fileItem("c.dat")));

      List<BrowsableQuery.BrowseEntry> entries = new ArrayList<>();
      OneDriveRuntime.collectChildren(
         client, "", false, List.of(), 2000, entries, new int[] { 0 });

      assertEquals(3, entries.size());
   }

   /**
    * P5 review round 1 finding: accepts() lowercased only the filename side of the comparison,
    * never acceptTypes -- so an uppercase extension in acceptTypes (a future BrowsableQuery
    * implementor's constant, or an edit to this one) would silently drop every matching file
    * from the listing, the same "quietly wrong" failure shape the charter's counter-assertions
    * exist to rule out for the Graph-error case, just via filtering instead of error handling.
    */
   @Test
   void acceptTypesMatchesRegardlessOfCaseOnEitherSide() {
      GraphServiceClient client = mock(GraphServiceClient.class);
      DriveItemRequestBuilder root = mockClient(client);
      stubChildren(root, page(fileItem("Report.CSV"), fileItem("b.txt")));

      List<BrowsableQuery.BrowseEntry> entries = new ArrayList<>();
      OneDriveRuntime.collectChildren(
         client, "", false, List.of(".CSV"), 2000, entries, new int[] { 0 });

      assertEquals(
         List.of("Report.CSV"),
         entries.stream().map(BrowsableQuery.BrowseEntry::name).toList());
   }

   private Object[][] readExpected(String file) throws IOException {
      try(InputStream input = getClass().getResourceAsStream(file)) {
         ObjectMapper mapper = new ObjectMapper();
         ArrayNode root = (ArrayNode) mapper.readTree(input);
         Object[][] expected = new Object[root.size()][];

         for(int i = 0; i < expected.length; i++) {
            ArrayNode array = (ArrayNode) root.get(i);
            expected[i] = mapper.treeToValue(array, Object[].class);
         }

         return expected;
      }
   }

   private InputStream readFile(String file) {
      InputStream input = getClass().getResourceAsStream(file);
      assert input != null;
      return input;
   }

   private XNodeTableLens getTable(OneDriveQuery query) {
      XTableNode node = runtime.runQuery(query, new VariableTable());
      assertNotNull(node);

      XNodeTableLens table = new XNodeTableLens(node);

      return table;
   }

   private Object[][] getData(XNodeTableLens table) {
      table.moreRows(XTable.EOT);
      Object[][] actual = new Object[table.getRowCount()][];

      for(int i = 0; table.moreRows(i); i++) {
         actual[i] = new Object[table.getColCount()];

         for(int j = 0; j < table.getColCount(); j++) {
            actual[i][j] = table.getObject(i, j);
         }
      }

      return actual;
   }

   @Configuration
   static class TestConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService credentialService = mock(CredentialService.class);
         when(credentialService.createCredential(CredentialType.PASSWORD)).thenReturn(mock(LocalPasswordCredential.class));
         when(credentialService.createCredential(CredentialType.PASSWORD, false)).thenReturn(mock(LocalPasswordCredential.class));
         return credentialService;
      }
   }

}
