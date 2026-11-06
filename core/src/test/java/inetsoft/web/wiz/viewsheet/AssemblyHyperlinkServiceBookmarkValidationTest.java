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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.composer.model.vs.HyperlinkDialogModel;
import inetsoft.web.composer.vs.dialog.HyperlinkDialogService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The "matches an existing bookmark" / "matches none" halves of VHL-004's sibling, VHL-003
 * (bug #76580): {@code AssemblyHyperlinkService.requireValidBookmark} calls the real, static
 * {@code VSUtil.getBookmarks} -- the same call {@code HyperlinkDialogService.getHyperlink}
 * already makes at persist time -- and that class's static initializer touches
 * Spring-context-dependent caching ({@code DataCacheSweeper}/{@code ConfigurationContext}), so
 * mocking it needs the same Spring bootstrap {@code ComposerBindingControllerTest} already uses
 * for the identical {@code Mockito.mockStatic(VSUtil.class)} call, rather than the plain-Mockito
 * harness the rest of {@code AssemblyHyperlinkServiceTest} uses. The "wrong linkType" bookmark
 * refusal (web/message) needs none of this -- it short-circuits before ever calling
 * {@code VSUtil.getBookmarks} -- and stays in that faster file.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("core")
class AssemblyHyperlinkServiceBookmarkValidationTest {
   private static Map<String, Object> link(Object... pairs) {
      Map<String, Object> link = new LinkedHashMap<>();

      for(int i = 0; i < pairs.length; i += 2) {
         link.put((String) pairs[i], pairs[i + 1]);
      }

      return link;
   }

   private static final IdentityID OWNER = IdentityID.getIdentityIDFromKey("admin");

   private static Principal principal() {
      return () -> "admin";
   }

   private record Harness(AssemblyHyperlinkService service, HyperlinkDialogService links,
                          AssetRepository repository) {}

   private static Harness harness() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      when(rvs.getID()).thenReturn("rt1");

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      HyperlinkDialogService links = mock(HyperlinkDialogService.class);
      AssetRepository repository = mock(AssetRepository.class);

      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
      when(rvs.getAssetRepository()).thenReturn(repository);
      when(repository.containsEntry(any())).thenReturn(true);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());
      when(links.getHyperlinkDialogModel(anyString(), anyString(), any(), any(), any(),
                                         anyBoolean(), anyBoolean(), anyBoolean(),
                                         anyBoolean(), any(Principal.class)))
         .thenReturn(new HyperlinkDialogModel());

      return new Harness(new AssemblyHyperlinkService(sessions, links), links, repository);
   }

   private static HyperlinkDialogModel capture(HyperlinkDialogService links) throws Exception {
      ArgumentCaptor<HyperlinkDialogModel> captor =
         ArgumentCaptor.forClass(HyperlinkDialogModel.class);
      verify(links).setHyperlinkDialogModel(eq("rt1"), anyString(), captor.capture(),
                                            anyString(), any(Principal.class), any());
      return captor.getValue();
   }

   /**
    * {@code HyperlinkDialogService.getHyperlink} silently no-ops -- no exception, no signal --
    * when the supplied bookmark name matches no {@code VSBookmarkInfo} for the target viewsheet.
    * This must be refused instead, naming the bookmarks that do exist.
    */
   @Test
   void bookmarkIsRefusedWhenItMatchesNoExistingBookmark() throws Exception {
      Harness h = harness();

      try(MockedStatic<VSUtil> vsutil = Mockito.mockStatic(VSUtil.class)) {
         vsutil.when(() -> VSUtil.getBookmarks(anyString(), any(IdentityID.class)))
            .thenReturn(new VSBookmarkInfo[]{
               new VSBookmarkInfo("Q3", VSBookmarkInfo.PRIVATE, OWNER, false, 0L) });

         Exception thrown = assertThrows(
            Exception.class,
            () -> h.service().set("tok", principal(), "Chart1", null,
                                  link("linkType", "viewsheet", "assetLinkPath", "Reports/Detail",
                                       "bookmark", "Nope"),
                                  ""));

         assertTrue(thrown.getMessage().contains("Nope"));
         assertTrue(thrown.getMessage().contains("Q3"), "name the bookmarks that do exist");
      }
   }

   @Test
   void bookmarkSucceedsWhenItMatchesAnExistingBookmark() throws Exception {
      Harness h = harness();

      try(MockedStatic<VSUtil> vsutil = Mockito.mockStatic(VSUtil.class)) {
         vsutil.when(() -> VSUtil.getBookmarks(anyString(), any(IdentityID.class)))
            .thenReturn(new VSBookmarkInfo[]{
               new VSBookmarkInfo("Q3", VSBookmarkInfo.PRIVATE, OWNER, false, 0L) });

         h.service().set("tok", principal(), "Chart1", null,
                        link("linkType", "viewsheet", "assetLinkPath", "Reports/Detail",
                             "bookmark", "Q3"),
                        "");

         assertEquals("Q3", capture(h.links()).getBookmark());
      }
   }

   // ── bug #80474: describeTarget ───────────────────────────────────────────────

   private static void wireVariablesAndSheet(Harness h, UserVariable... variables)
      throws Exception
   {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAllVariables()).thenReturn(variables);
      when(h.repository().getSheet(any(AssetEntry.class), any(Principal.class), eq(true),
                                   any())).thenReturn(vs);
   }

   @Test
   void describeTargetReturnsRealBookmarksAndParametersForAReadableTarget() throws Exception {
      Harness h = harness();
      UserVariable customer = new UserVariable("customer");
      customer.setTypeNode(XSchema.createPrimitiveType(XSchema.STRING));
      wireVariablesAndSheet(h, customer);

      try(MockedStatic<VSUtil> vsutil = Mockito.mockStatic(VSUtil.class)) {
         vsutil.when(() -> VSUtil.getBookmarks(anyString(), any(IdentityID.class)))
            .thenReturn(new VSBookmarkInfo[]{
               new VSBookmarkInfo("Q1", VSBookmarkInfo.PRIVATE, OWNER, false, 0L) });
         vsutil.when(() -> VSUtil.getUserAlias(any(IdentityID.class))).thenReturn("admin");

         Map<String, Object> result =
            h.service().describeTarget("tok", principal(), "Reports/Detail", null);

         @SuppressWarnings("unchecked")
         List<Map<String, Object>> parameters = (List<Map<String, Object>>) result.get("parameters");
         @SuppressWarnings("unchecked")
         List<Map<String, Object>> bookmarks = (List<Map<String, Object>>) result.get("bookmarks");

         assertEquals(1, parameters.size());
         assertEquals("customer", parameters.get(0).get("name"));
         assertEquals(XSchema.STRING, parameters.get(0).get("type"));
         assertEquals(1, bookmarks.size());
         assertEquals("Q1", bookmarks.get(0).get("name"));
         assertEquals("admin", bookmarks.get(0).get("owner"));
      }
   }

   /**
    * Deliberate policy choice (bug #80474): unlike {@code set}, which refuses a recycled target
    * outright, {@code describeTarget} answers with the target's real data -- a recycled-but-
    * readable asset's bookmarks/parameters are still real, and this read-only method has none of
    * {@code set}'s "don't create a link that goes nowhere" concern.
    */
   @Test
   void describeTargetAnswersForARecycledButReadableTarget() throws Exception {
      Harness h = harness();
      UserVariable customer = new UserVariable("customer");
      customer.setTypeNode(XSchema.createPrimitiveType(XSchema.STRING));
      wireVariablesAndSheet(h, customer);

      try(MockedStatic<VSUtil> vsutil = Mockito.mockStatic(VSUtil.class)) {
         vsutil.when(() -> VSUtil.getBookmarks(anyString(), any(IdentityID.class)))
            .thenReturn(new VSBookmarkInfo[]{
               new VSBookmarkInfo("Q1", VSBookmarkInfo.PRIVATE, OWNER, false, 0L) });

         Map<String, Object> result = h.service().describeTarget(
            "tok", principal(), "Recycle Bin/Reports-Detail-20261101", null);

         @SuppressWarnings("unchecked")
         List<Map<String, Object>> parameters = (List<Map<String, Object>>) result.get("parameters");
         @SuppressWarnings("unchecked")
         List<Map<String, Object>> bookmarks = (List<Map<String, Object>>) result.get("bookmarks");

         assertEquals(1, parameters.size());
         assertEquals(1, bookmarks.size());
      }
   }
}
