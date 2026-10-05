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
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.Identity;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
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
 * A sheet script that calls {@code VSUtil.getBookmarks} itself gets only the bookmarks the
 * context principal sees, and only on a viewsheet the context principal may read, whatever
 * user and entry the script passes. Java callers keep trusting their arguments. (Bug #77822)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class VSUtilGetBookmarksScriptTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private static final String ORG_A = "orgA77822";
   private static final String ORG_B = "orgB77822";
   private static final String FOLDER = "R77822";
   private static final String SHEET = FOLDER + "/Sheet";
   // a sheet of aliceA's own organization that she has no READ grant on
   private static final String HIDDEN_SHEET = FOLDER + "/Hidden";

   // lists the bookmarks the script gets as name|owner, and mints an IdentityID for any
   // user without Java.type('...IdentityID') through a USER-scope asset identifier
   private static final String FUNCS =
      "var AE = Java.type('inetsoft.uql.asset.AssetEntry');" +
      "var V = Java.type('inetsoft.uql.viewsheet.internal.VSUtil');" +
      "function id(n, o) { return AE.createAssetEntry('4^128^' + n + '~;~' + o + '^x^' + o).getUser(); }" +
      "function list(b) { var s = [];" +
      "  for(var i = 0; i < b.length; i++) {" +
      "    s.push(b[i].getName() + '|' + b[i].getOwner().getName() + '@' + b[i].getOwner().getOrgID()); }" +
      "  return s.join(','); }";

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
      // aliceA may read her own organization's sheet; org B's is denied by the org rule
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
      saveBookmarks(entryA, new IdentityID("aliceA", ORG_A), "AlicePrivate", "AliceShared");
      saveBookmarks(entryA, new IdentityID("annA", ORG_A), "AnnPrivate", "AnnShared");
      saveBookmarks(entryB, new IdentityID("bobB", ORG_B), "BobPrivate", "BobShared");
      saveBookmarks(hiddenEntryA, new IdentityID("annA", ORG_A), "AnnHiddenPrivate",
                    "AnnHiddenShared");

      SRPrincipal alice = builder.principalOf("aliceA", ORG_A);
      ThreadContext.setContextPrincipal(alice);
      ThreadContext.setPrincipal(alice);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedContextPrincipal);
      ThreadContext.setPrincipal(savedPrincipal);
      OrganizationContextHolder.setCurrentOrgId(null);
      SreeEnv.setProperty("security.provider", oldProvider == null ? "" : oldProvider);

      if(builder != null) {
         builder.teardown();
      }
   }

   /** The context user still gets her own view of a sheet she may read. */
   @Test
   void ownIdOnReadableSheetListsOwnView() throws Exception {
      assertAliceView(run("list(V.getBookmarks(" + q(entryA) + ", id('aliceA', '" + ORG_A + "')))"));
   }

   /** Another same-org user's id gets the context user's view, not that user's private ones. */
   @Test
   void sameOrgOtherUserIdIsIgnored() throws Exception {
      assertAliceView(run("list(V.getBookmarks(" + q(entryA) + ", id('annA', '" + ORG_A + "')))"));
   }

   /** The owner of a listed shared bookmark, passed back in, does not open its private ones. */
   @Test
   void chainedOwnerOfSameOrgSheetIsIgnored() throws Exception {
      Object result = run(
         "var own = V.getBookmarks(" + q(entryA) + ", id('aliceA', '" + ORG_A + "'));" +
         "var owner = null;" +
         "for(var i = 0; i < own.length; i++) {" +
         "  if(own[i].getName() == 'AnnShared') { owner = own[i].getOwner(); } }" +
         "owner == null ? 'no owner' : list(V.getBookmarks(" + q(entryA) + ", owner))");

      assertAliceView(result);
   }

   /** The context user's own id on another org's sheet lists nothing of that org. */
   @Test
   void ownIdOnOtherOrgSheetListsNothing() throws Exception {
      assertEquals("", run("list(V.getBookmarks(" + q(entryB) + ", id('aliceA', '" + ORG_A + "')))"));
   }

   /**
    * The context user's own id on a sheet of her own organization that she may not read
    * lists nothing, so the check is READ and not only the organization.
    */
   @Test
   void ownIdOnUnreadableSameOrgSheetListsNothing() throws Exception {
      assertEquals("", run("list(V.getBookmarks(" + q(hiddenEntryA) + ", id('aliceA', '" +
                              ORG_A + "')))"));
      assertEquals("", run("list(V.getBookmarks(" + q(hiddenEntryA) + ", id('annA', '" +
                              ORG_A + "')))"));
   }

   /** A minted other-org id on that org's sheet lists nothing. */
   @Test
   void forgedOtherOrgIdListsNothing() throws Exception {
      assertEquals("", run("list(V.getBookmarks(" + q(entryB) + ", id('bobB', '" + ORG_B + "')))"));
   }

   /**
    * The chain the refuter showed: own id on the other org's sheet, then each owner it
    * returned. The first call returns no owner, and reusing any owner lists nothing.
    */
   @Test
   void chainedOwnerOfOtherOrgSheetListsNothing() throws Exception {
      Object result = run(
         "var first = V.getBookmarks(" + q(entryB) + ", id('aliceA', '" + ORG_A + "'));" +
         "var out = [list(first)];" +
         "for(var i = 0; i < first.length; i++) {" +
         "  out.push(list(V.getBookmarks(" + q(entryB) + ", first[i].getOwner()))); }" +
         "out.push(list(V.getBookmarks(" + q(entryB) + ", id('bobB', '" + ORG_B + "'))));" +
         "out.join(';')");

      assertInstanceOf(String.class, result, String.valueOf(result));
      assertFalse(((String) result).contains("Bob"), String.valueOf(result));
      assertFalse(((String) result).contains("@" + ORG_B), String.valueOf(result));
   }

   /** The AssetEntry overload is covered as well as the identifier one. */
   @Test
   void entryOverloadOnOtherOrgSheetListsNothing() throws Exception {
      assertEquals("", run("list(V.getBookmarks(AE.createAssetEntry(" + q(entryB) +
                              "), id('bobB', '" + ORG_B + "')))"));
   }

   /** The legacy package route is the script's own call too. */
   @Test
   void legacyRouteIsChecked() throws Exception {
      String vsutil = "inetsoft.uql.viewsheet.internal.VSUtil";

      assertEquals("", run("list(" + vsutil + ".getBookmarks(" + q(entryB) +
                              ", id('bobB', '" + ORG_B + "')))"));
      assertAliceView(run("list(" + vsutil + ".getBookmarks(" + q(entryA) +
                             ", id('annA', '" + ORG_A + "')))"));
   }

   /** Java callers check permission themselves and still get the passed user's view. */
   @Test
   void javaCallerKeepsPassedUser() {
      List<String> ann = names(VSUtil.getBookmarks(entryA, new IdentityID("annA", ORG_A)));
      List<String> bob = names(VSUtil.getBookmarks(entryB, new IdentityID("bobB", ORG_B)));

      assertTrue(ann.contains("AnnPrivate"), ann.toString());
      assertFalse(ann.contains("AlicePrivate"), ann.toString());
      assertTrue(bob.contains("BobPrivate"), bob.toString());

      // the unreadable same-org sheet has a shared bookmark, so its empty script result
      // comes from the READ check
      List<String> hidden = names(VSUtil.getBookmarks(hiddenEntryA, new IdentityID("aliceA", ORG_A)));
      assertTrue(hidden.contains("AnnHiddenShared"), hidden.toString());
   }

   private static void assertAliceView(Object result) {
      assertInstanceOf(String.class, result, String.valueOf(result));
      List<String> bookmarks = Arrays.asList(((String) result).split(","));

      assertTrue(bookmarks.contains("AlicePrivate|aliceA@" + ORG_A), bookmarks.toString());
      assertTrue(bookmarks.contains("AnnShared|annA@" + ORG_A), bookmarks.toString());
      assertTrue(bookmarks.contains(VSBookmark.HOME_BOOKMARK + "|aliceA@" + ORG_A),
                 bookmarks.toString());
      assertFalse(bookmarks.contains("AnnPrivate|annA@" + ORG_A), bookmarks.toString());
      assertFalse(bookmarks.contains(VSBookmark.HOME_BOOKMARK + "|annA@" + ORG_A),
                  bookmarks.toString());
   }

   private static List<String> names(VSBookmarkInfo[] bookmarks) {
      return Arrays.stream(bookmarks).map(VSBookmarkInfo::getName).toList();
   }

   private static String q(AssetEntry entry) {
      return "'" + entry.toIdentifier() + "'";
   }

   private static AssetEntry saveSheet(String path, String orgId) throws Exception {
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        path, null, orgId);
      OrganizationContextHolder.setCurrentOrgId(orgId);

      try {
         AssetEntry folder = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
            AssetEntry.Type.REPOSITORY_FOLDER, FOLDER, null, orgId);

         if(!repository.containsEntry(folder)) {
            repository.addFolder(folder, null);
         }

         repository.setSheet(entry, new Viewsheet(), null, true);
      }
      finally {
         OrganizationContextHolder.setCurrentOrgId(null);
      }

      return entry;
   }

   private static void saveBookmarks(AssetEntry entry, IdentityID user, String privateName,
                                     String sharedName)
      throws Exception
   {
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      Viewsheet vs = new Viewsheet();
      VSBookmark bookmark = new VSBookmark();
      bookmark.setUser(user);
      bookmark.addBookmark(privateName, vs, VSBookmarkInfo.PRIVATE, false, false);
      bookmark.addBookmark(sharedName, vs, VSBookmarkInfo.ALLSHARE, false, false);
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
