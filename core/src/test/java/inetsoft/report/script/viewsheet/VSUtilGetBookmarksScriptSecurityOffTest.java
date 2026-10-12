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
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
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
 * With security off, a sheet script's own {@code VSUtil.getBookmarks} call still lists the
 * anonymous session's own view, and a script call with no context principal lists nothing.
 * (Bug #77822)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class VSUtilGetBookmarksScriptSecurityOffTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private static final String FOLDER = "R77822Off";
   private static final String SHEET = FOLDER + "/Sheet";

   private static final String FUNCS =
      "var AE = Java.type('inetsoft.uql.asset.AssetEntry');" +
      "var V = Java.type('inetsoft.uql.viewsheet.internal.VSUtil');" +
      "function id(n, o) { return AE.createAssetEntry('4^128^' + n + '~;~' + o + '^x^' + o).getUser(); }" +
      "function list(b) { var s = [];" +
      "  for(var i = 0; i < b.length; i++) { s.push(b[i].getName() + '|' + b[i].getOwner().getName()); }" +
      "  return s.join(','); }";

   private ViewsheetScope viewsheetScope;
   private AssetEntry entry;
   private String org;
   private Principal savedContextPrincipal;
   private Principal savedPrincipal;

   @BeforeEach
   void setUp() throws Exception {
      assertFalse(SecurityEngine.getSecurity().isSecurityEnabled(), "security must be off");
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox().orElseThrow();
      viewsheetScope = new ViewsheetScope(sandbox, false);
      savedContextPrincipal = ThreadContext.getContextPrincipal();
      savedPrincipal = ThreadContext.getPrincipal();

      org = Organization.getDefaultOrganizationID();
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      AssetEntry folder = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.REPOSITORY_FOLDER, FOLDER, null, org);

      if(!repository.containsEntry(folder)) {
         repository.addFolder(folder, null);
      }

      entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, SHEET,
                             null, org);
      repository.setSheet(entry, new Viewsheet(), null, true);
      saveBookmarks(new IdentityID(XPrincipal.ANONYMOUS, org), "AnonPrivate", "AnonShared");
      saveBookmarks(new IdentityID("admin", org), "AdminPrivate", "AdminShared");
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedContextPrincipal);
      ThreadContext.setPrincipal(savedPrincipal);
   }

   /** The anonymous session still gets its own view, whatever user the script passes. */
   @Test
   void anonymousScriptListsOwnView() throws Exception {
      setPrincipal(new SRPrincipal(new IdentityID(XPrincipal.ANONYMOUS, org)));

      for(String user : new String[] { XPrincipal.ANONYMOUS, "admin" }) {
         Object result = run("list(V.getBookmarks('" + entry.toIdentifier() + "', id('" + user +
                                "', '" + org + "')))");
         assertInstanceOf(String.class, result, String.valueOf(result));
         List<String> bookmarks = Arrays.asList(((String) result).split(","));

         assertTrue(bookmarks.contains("AnonPrivate|" + XPrincipal.ANONYMOUS), bookmarks.toString());
         assertTrue(bookmarks.contains("AdminShared|admin"), bookmarks.toString());
         assertTrue(bookmarks.contains(VSBookmark.HOME_BOOKMARK + "|" + XPrincipal.ANONYMOUS),
                    bookmarks.toString());
         assertFalse(bookmarks.contains("AdminPrivate|admin"), bookmarks.toString());
      }
   }

   /** A script call without a context principal lists nothing; a Java call is unaffected. */
   @Test
   void noContextPrincipalListsNothingForScript() throws Exception {
      setPrincipal(null);

      assertEquals("", run("list(V.getBookmarks('" + entry.toIdentifier() + "', id('admin', '" +
                              org + "')))"));
      List<String> java = Arrays.stream(VSUtil.getBookmarks(entry, new IdentityID("admin", org)))
         .map(VSBookmarkInfo::getName).toList();
      assertTrue(java.contains("AdminPrivate"), java.toString());
   }

   private static void setPrincipal(Principal principal) {
      ThreadContext.setContextPrincipal(principal);
      ThreadContext.setPrincipal(principal);
   }

   private void saveBookmarks(IdentityID user, String privateName, String sharedName)
      throws Exception
   {
      Viewsheet vs = new Viewsheet();
      VSBookmark bookmark = new VSBookmark();
      bookmark.setUser(user);
      bookmark.addBookmark(privateName, vs, VSBookmarkInfo.PRIVATE, false, false);
      bookmark.addBookmark(sharedName, vs, VSBookmarkInfo.ALLSHARE, false, false);
      AssetUtil.getAssetRepository(false).setVSBookmark(entry, bookmark, new XPrincipal(user));
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
