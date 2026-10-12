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

package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.Identity;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.ThreadContext;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A sheet script can't get the asset engine from {@code AssetUtil.getAssetRepository}, and
 * the engine and its storage expose none of their members to a script that holds them, so a
 * script can't list, clear or delete another user's or org's assets and bookmarks. Java
 * callers, including the Java helpers a script calls, still get the engine. (Bug #77827)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class AssetRepositoryScriptAccessTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private static final String ORG_A = "orgA77827";
   private static final String ORG_B = "orgB77827";
   private static final String FOLDER = "R77827";
   private static final String SHEET = FOLDER + "/Sheet";
   // a sheet of aliceA's own organization that she has no READ grant on
   private static final String HIDDEN_SHEET = FOLDER + "/Hidden";
   private static final String WORKSHEET = FOLDER + "/Data";
   private static final String REFUSED = "A script may not get the asset repository";

   private static final String FUNCS =
      "var AE = Java.type('inetsoft.uql.asset.AssetEntry');" +
      "var AU = Java.type('inetsoft.uql.asset.internal.AssetUtil');" +
      "var R = Java.type('inetsoft.report.script.viewsheet.AssetRepositoryRelay');" +
      "var V = Java.type('inetsoft.uql.viewsheet.internal.VSUtil');" +
      "function id(n, o) { return AE.createAssetEntry('4^128^' + n + '~;~' + o + '^x^' + o).getUser(); }";

   private SecurityTestDataBuilder builder;
   private ViewsheetScope viewsheetScope;
   private AssetEntry entryA;
   private AssetEntry entryB;
   private AssetEntry hiddenEntryA;
   private String oldProvider;
   private Principal savedContextPrincipal;
   private Principal savedPrincipal;

   @BeforeEach
   void setUp() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox().orElseThrow();
      viewsheetScope = new ViewsheetScope(sandbox, false);
      savedContextPrincipal = ThreadContext.getContextPrincipal();
      savedPrincipal = ThreadContext.getPrincipal();

      oldProvider = SreeEnv.getProperty("security.provider");
      SreeEnv.setProperty("security.provider", "file");
      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG_A, ORG_A)
         .addOrg(ORG_B, ORG_B)
         .addUser("aliceA", ORG_A, "password")
         .addUser("annA", ORG_A, "password")
         .addUser("bobB", ORG_B, "password")
         .grantPermission(ResourceType.REPORT, SHEET, ResourceAction.READ,
                          "aliceA", Identity.USER, ORG_A)
         .markPermissionEdited(ResourceType.REPORT, SHEET, ORG_A)
         .setup();

      entryA = saveSheet(SHEET, ORG_A);
      entryB = saveSheet(SHEET, ORG_B);
      hiddenEntryA = saveSheet(HIDDEN_SHEET, ORG_A);
      saveBookmark(entryA, new IdentityID("aliceA", ORG_A), "AlicePrivate");
      saveBookmark(entryB, new IdentityID("bobB", ORG_B), "BobPrivate");
      saveBookmark(hiddenEntryA, new IdentityID("annA", ORG_A), "AnnHidden");

      SRPrincipal alice = builder.principalOf("aliceA", ORG_A);
      ThreadContext.setContextPrincipal(alice);
      ThreadContext.setPrincipal(alice);
   }

   @AfterEach
   void tearDown() {
      AssetRepositoryRelay.assembly = null;
      AssetRepositoryRelay.columns = null;
      ThreadContext.setContextPrincipal(savedContextPrincipal);
      ThreadContext.setPrincipal(savedPrincipal);
      OrganizationContextHolder.setCurrentOrgId(null);
      SreeEnv.setProperty("security.provider", oldProvider == null ? "" : oldProvider);

      if(builder != null) {
         builder.teardown();
      }
   }

   /** The reported attack: clear another org's bookmarks through the Java.type route. */
   @Test
   void javaTypeRouteRefused() throws Exception {
      assertRefused(run("AU.getAssetRepository(false).clearVSBookmark(AE.createAssetEntry(" +
                           q(entryB) + ")); 'cleared'"));
      assertEquals(List.of("BobPrivate"), bookmarkNames(entryB, "bobB", ORG_B));
   }

   @Test
   void designRouteRefused() throws Exception {
      assertRefused(run("AU.getAssetRepository(true).clearVSBookmark(AE.createAssetEntry(" +
                           q(entryB) + ")); 'cleared'"));
      assertEquals(List.of("BobPrivate"), bookmarkNames(entryB, "bobB", ORG_B));
   }

   @Test
   void legacyRouteRefused() throws Exception {
      assertRefused(run("inetsoft.uql.asset.internal.AssetUtil.getAssetRepository(false)" +
                           ".clearVSBookmark(AE.createAssetEntry(" + q(hiddenEntryA) +
                           ")); 'cleared'"));
      assertEquals(List.of("AnnHidden"), bookmarkNames(hiddenEntryA, "annA", ORG_A));
   }

   /** Listing another org's bookmark owners is refused. */
   @Test
   void bookmarkUsersRefused() throws Exception {
      Object result = run("'' + AU.getAssetRepository(false).getBookmarkUsers(" +
                             "AE.createAssetEntry(" + q(entryB) + "))");

      assertRefused(result);
      assertFalse(String.valueOf(result).contains("bobB"), String.valueOf(result));
   }

   @Test
   void removeSheetRefused() throws Exception {
      assertRefused(run("var r = AU.getAssetRepository(false);" +
                           "r.removeSheet(AE.createAssetEntry(" + q(entryB) + "), null, true);" +
                           "r.removeSheet(AE.createAssetEntry(" + q(hiddenEntryA) +
                           "), null, true); 'removed'"));
      assertTrue(containsEntry(entryB));
      assertTrue(containsEntry(hiddenEntryA));
   }

   @Test
   void storageRemoveRefused() throws Exception {
      assertRefused(run("AU.getAssetRepository(false).getStorage(AE.createAssetEntry(" +
                           q(entryA) + ")).remove(" + q(entryA) + "); 'removed'"));
      assertTrue(containsEntry(entryA));
   }

   /**
    * Defence in depth: an engine or storage a script holds through some other route exposes
    * none of its members. Members Graal attributes to an undenied JDK interface (close() of
    * AutoCloseable, propertyChange() of PropertyChangeListener) stay callable on a held
    * engine; no product route hands one out, which is why getAssetRepository refuses a direct
    * script call. Not asserted here, so that a later hardening doesn't fail this test.
    */
   @Test
   void relayedEngineHasNoDestructiveMembers() throws Exception {
      String[] members = { "getBookmarkUsers", "clearVSBookmark", "removeSheet", "getSheet",
                           "setSheet", "getStorage", "getReportStorage", "setVSBookmark",
                           "removeRepositoryEntry", "setPermission", "changePassword",
                           "removeScheduleTask" };
      StringBuilder script = new StringBuilder("var r = R.repo(); var out = [];");

      for(String member : members) {
         script.append("out.push('").append(member).append("=' + typeof r.")
            .append(member).append(");");
      }

      script.append("var s = R.storage(AE.createAssetEntry(").append(q(entryA)).append("));")
         .append("out.push('remove=' + typeof s.remove);")
         .append("out.push('getKeys=' + typeof s.getKeys);")
         .append("out.push('getDocument=' + typeof s.getDocument);")
         .append("out.join(',')");
      Object result = run(script.toString());

      assertInstanceOf(String.class, result, String.valueOf(result));

      for(String typeOf : ((String) result).split(",")) {
         assertTrue(typeOf.endsWith("=undefined"), String.valueOf(result));
      }

      assertTrue(containsEntry(entryA));
   }

   /** Java callers still get the live engine. */
   @Test
   void javaCallerStillGetsEngine() {
      assertNotNull(AssetUtil.getAssetRepository(false));
      assertNotNull(AssetUtil.getAssetRepository(true));
   }

   /** VSUtil.getBookmarks fetches the engine Java-side and still lists the caller's own view. */
   @Test
   void ownBookmarksStillListed() throws Exception {
      Object result = run(
         "var b = V.getBookmarks(" + q(entryA) + ", id('aliceA', '" + ORG_A + "')); var s = [];" +
         "for(var i = 0; i < b.length; i++) { s.push(b[i].getName()); } s.join(',')");

      assertInstanceOf(String.class, result, String.valueOf(result));
      assertTrue(Arrays.asList(((String) result).split(",")).contains("AlicePrivate"),
                 String.valueOf(result));
   }

   /**
    * An XUtil static a script calls is a Java caller of getAssetRepository:
    * addDescriptionsFromSource reads the bound worksheet through the engine and copies its
    * column description.
    */
   @Test
   void xutilHelperStillGetsEngine() throws Exception {
      AssetEntry wsEntry = saveWorksheet(ORG_A);
      Viewsheet vs = new Viewsheet(wsEntry);
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "T"));
      vs.addAssembly(table);
      ColumnSelection columns = new ColumnSelection();
      ColumnRef column = new ColumnRef(new AttributeRef(null, "col"));
      columns.addAttribute(column);
      AssetRepositoryRelay.assembly = table;
      AssetRepositoryRelay.columns = columns;

      Object result = run("Java.type('inetsoft.uql.util.XUtil')" +
                             ".addDescriptionsFromSource(R.assembly(), R.columns()); 'ok'");

      assertEquals("ok", result);
      assertEquals("DESC77827", column.getDescription());
   }

   /**
    * StyleConstant keeps working. The asset engine deny hides the constants of the engine
    * types themselves, ReportSheet and the asset scope constants of AssetRepository.
    */
   @Test
   void styleConstantStillReadable() throws Exception {
      assertEquals(1, ((Number) run("StyleConstant.TABLE_FIT_PAGE")).intValue());
      assertEquals("undefined", run(
         "typeof Java.type('inetsoft.report.ReportSheet').TABLE_FIT_PAGE"));
      assertEquals("undefined", run(
         "typeof Java.type('inetsoft.uql.asset.AssetRepository').GLOBAL_SCOPE"));
   }

   private static void assertRefused(Object result) {
      assertInstanceOf(String.class, result, String.valueOf(result));
      assertTrue(((String) result).startsWith("error: "), String.valueOf(result));
      assertTrue(((String) result).contains(REFUSED), String.valueOf(result));
   }

   private static boolean containsEntry(AssetEntry entry) throws Exception {
      OrganizationContextHolder.setCurrentOrgId(entry.getOrgID());

      try {
         return AssetUtil.getAssetRepository(false).containsEntry(entry);
      }
      finally {
         OrganizationContextHolder.setCurrentOrgId(null);
      }
   }

   private static List<String> bookmarkNames(AssetEntry entry, String user, String org)
      throws Exception
   {
      OrganizationContextHolder.setCurrentOrgId(org);

      try {
         VSBookmark bookmark = AssetUtil.getAssetRepository(false)
            .getVSBookmark(entry, new XPrincipal(new IdentityID(user, org)), true);
         List<String> names = new ArrayList<>(bookmark == null ?
            List.of() : Arrays.asList(bookmark.getBookmarks()));
         names.remove(VSBookmark.HOME_BOOKMARK);
         return names;
      }
      finally {
         OrganizationContextHolder.setCurrentOrgId(null);
      }
   }

   private static String q(AssetEntry entry) {
      return "'" + entry.toIdentifier() + "'";
   }

   private static AssetEntry saveSheet(String path, String orgId) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        path, null, orgId);
      save(entry, new Viewsheet(), orgId);
      return entry;
   }

   // a worksheet whose table T has a column with a description
   private static AssetEntry saveWorksheet(String orgId) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        WORKSHEET, null, orgId);
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "T");
      ColumnSelection columns = new ColumnSelection();
      ColumnRef column = new ColumnRef(new AttributeRef(null, "col"));
      column.setDescription("DESC77827");
      columns.addAttribute(column);
      table.setColumnSelection(columns, false);
      table.setColumnSelection(columns, true);
      ws.addAssembly(table);
      ws.setPrimaryAssembly(table);
      save(entry, ws, orgId);
      return entry;
   }

   private static void save(AssetEntry entry, AbstractSheet sheet, String orgId)
      throws Exception
   {
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      OrganizationContextHolder.setCurrentOrgId(orgId);

      try {
         AssetEntry folder = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
            AssetEntry.Type.REPOSITORY_FOLDER, FOLDER, null, orgId);

         if(!repository.containsEntry(folder)) {
            repository.addFolder(folder, null);
         }

         repository.setSheet(entry, sheet, null, true);
      }
      finally {
         OrganizationContextHolder.setCurrentOrgId(null);
      }
   }

   private static void saveBookmark(AssetEntry entry, IdentityID user, String name)
      throws Exception
   {
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      VSBookmark bookmark = new VSBookmark();
      bookmark.setUser(user);
      bookmark.addBookmark(name, new Viewsheet(), VSBookmarkInfo.PRIVATE, false, false);
      OrganizationContextHolder.setCurrentOrgId(entry.getOrgID());

      try {
         repository.setVSBookmark(entry, bookmark, new XPrincipal(user));
      }
      finally {
         OrganizationContextHolder.setCurrentOrgId(null);
      }
   }

   private Object run(String script) throws Exception {
      return viewsheetScope.execute(
         "try { " + FUNCS + script + " } catch(e) { 'error: ' + e }",
         viewsheetScope.getVSAScriptable(ViewsheetScope.VIEWSHEET_SCRIPTABLE), false);
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ViewsheetScopeTest.ASSET_ID);
      event.setViewer(true);
      return event;
   }
}
