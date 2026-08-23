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

import inetsoft.report.Hyperlink;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.model.vs.HyperlinkDialogModel;
import inetsoft.web.composer.vs.dialog.HyperlinkDialogService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class AssemblyHyperlinkServiceTest {
   private static Map<String, Object> link(Object... pairs) {
      Map<String, Object> link = new LinkedHashMap<>();

      for(int i = 0; i < pairs.length; i += 2) {
         link.put((String) pairs[i], pairs[i + 1]);
      }

      return link;
   }

   @Test
   void setsAWebLink() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.set("tok", principal(), "Chart1", null,
                    link("linkType", "web", "webLink", "https://example.com"), "");

      HyperlinkDialogModel posted = capture(h.links);
      assertEquals(Hyperlink.WEB_LINK, posted.getLinkType());
      assertEquals("https://example.com", posted.getWebLink());
   }

   @Test
   void setsAViewsheetLink() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.set("tok", principal(), "Chart1", null,
                    link("linkType", "viewsheet", "assetLinkPath", "Reports/Detail"), "");

      assertEquals(Hyperlink.VIEWSHEET_LINK, capture(h.links).getLinkType());
   }

   @Test
   void eachWriteIsExactlyOneCheckpoint() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.set("tok", principal(), "Chart1", null,
                    link("linkType", "web", "webLink", "https://example.com"), "");

      verify(h.sessions, times(1)).mutate(anyString(), any(Principal.class), any());
   }

   /** Clearing is its own type rather than an empty value, so intent is never inferred. */
   @Test
   void clearingALinkNeedsNoDestination() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setWebLink("https://old.example.com");
      Harness h = harness(model);

      h.service.set("tok", principal(), "Chart1", null, link("linkType", "none"), "");

      HyperlinkDialogModel posted = capture(h.links);
      assertEquals(0, posted.getLinkType());
      assertNull(posted.getWebLink(), "clearing the type must clear the destination with it");
   }

   /**
    * A link with a type but no matching destination is accepted by the dialog and then does
    * nothing when clicked, which reads as a broken report rather than a bad call.
    */
   @Test
   void refusesAWebLinkWithNoUrl() {
      Harness h = harness(new HyperlinkDialogModel());

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Chart1", null, link("linkType", "web"), ""));

      assertTrue(thrown.getMessage().contains("webLink"));
   }

   @Test
   void refusesAViewsheetLinkWithNoPath() {
      Harness h = harness(new HyperlinkDialogModel());

      assertThrows(Exception.class,
                   () -> h.service.set("tok", principal(), "Chart1", null,
                                       link("linkType", "viewsheet"), ""));
   }

   @Test
   void validatesBeforeTouchingTheRuntime() {
      Harness h = harness(new HyperlinkDialogModel());

      assertThrows(Exception.class,
                   () -> h.service.set("tok", principal(), "Chart1", null,
                                       link("linkType", "web"), ""));

      verifyNoInteractions(h.sessions);
   }

   @Test
   void refusesAnIntegerLinkTypeListingTheTokens() {
      Harness h = harness(new HyperlinkDialogModel());

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Chart1", null,
                             link("linkType", 1, "webLink", "https://x"), ""));

      assertTrue(thrown.getMessage().contains("web"));
   }

   @Test
   void refusesAMissingLinkType() {
      Harness h = harness(new HyperlinkDialogModel());

      assertThrows(Exception.class,
                   () -> h.service.set("tok", principal(), "Chart1", null,
                                       link("webLink", "https://x"), ""));
   }

   // ── region addressing ─────────────────────────────────────────────────────

   /**
    * Addressing the whole assembly means row/col <b>0</b>, not null.
    *
    * <p>{@code HyperlinkDialogService.getHyperlinkDialogModel} dereferences row as an int
    * (via {@code getFields}), so nulls threw
    * {@code NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "row" is
    * null} for every assembly type — set_hyperlink was unusable at assembly level. The Composer
    * never sends null: its controller declares
    * {@code @RequestParam(value = "row", required = false, defaultValue = "0")}.
    *
    * <p>This test previously asserted {@code isNull(), isNull()} and so certified the crash.
    */
   @Test
   void addressesTheWholeAssemblyWithZerosBecauseNullRowNPEs() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.read("tok", principal(), "Chart1", null);

      verify(h.links).getHyperlinkDialogModel(eq("rt1"), eq("Chart1"), eq(0), eq(0),
                                              isNull(), eq(false), eq(false), eq(false),
                                              eq(false), any(Principal.class));
   }

   /**
    * The controller builds a Region straight from its nullable {@code @RequestParam}s rather than
    * calling {@code Region.whole()}, so normalizing only in the factory left the live path still
    * passing nulls — and still NPEing. The record itself must normalize.
    */
   @Test
   void aRegionBuiltDirectlyWithNullsStillAddressesRowAndColZero() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.read("tok", principal(), "Chart1",
                     new AssemblyHyperlinkService.Region(null, null, null, false, false, false,
                                                         false));

      verify(h.links).getHyperlinkDialogModel(eq("rt1"), eq("Chart1"), eq(0), eq(0),
                                              isNull(), eq(false), eq(false), eq(false),
                                              eq(false), any(Principal.class));
   }

   @Test
   void passesARegionThrough() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.read("tok", principal(), "Table1",
                     new AssemblyHyperlinkService.Region(2, 1, "Sales", false, false, false,
                                                         false));

      verify(h.links).getHyperlinkDialogModel(eq("rt1"), eq("Table1"), eq(2), eq(1),
                                              eq("Sales"), eq(false), eq(false), eq(false),
                                              eq(false), any(Principal.class));
   }

   @Test
   void addressesATitleLink() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.read("tok", principal(), "Chart1",
                     new AssemblyHyperlinkService.Region(null, null, null, false, false, true,
                                                         false));

      // Row/col normalize to 0 here too: a title link addresses the assembly, and the dialog
      // service dereferences them as ints whatever the region flags say. This previously
      // asserted isNull(), isNull() — the values that NPE live.
      verify(h.links).getHyperlinkDialogModel(anyString(), anyString(), eq(0), eq(0),
                                              isNull(), eq(false), eq(false), eq(true),
                                              eq(false), any(Principal.class));
   }

   // ── the read direction ────────────────────────────────────────────────────

   @Test
   void readsBackATokenNeverAnInteger() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setLinkType(Hyperlink.WEB_LINK);
      model.setWebLink("https://example.com");

      Map<String, Object> read = harness(model).service.read("tok", principal(), "Chart1", null);

      assertEquals("web", read.get("linkType"));
      assertEquals("https://example.com", read.get("webLink"));
   }

   @Test
   void reportsAnUnrecognizedConstantAsItself() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setLinkType(99);

      Map<String, Object> read = harness(model).service.read("tok", principal(), "Chart1", null);

      assertEquals("unknown(99)", read.get("linkType"));
   }

   @Test
   void listsTheLinkTypesForDiscovery() {
      Map<String, Object> types = harness(new HyperlinkDialogModel()).service.linkTypes();

      assertTrue(String.valueOf(types.get("linkTypes")).contains("viewsheet"));
   }

   // ── list_hyperlink_targets ───────────────────────────────────────────────

   private static final IdentityID OWNER = IdentityID.getIdentityIDFromKey("admin");

   private static AssetEntry folderEntry(int scope, String path, IdentityID owner) {
      return new AssetEntry(scope, AssetEntry.Type.REPOSITORY_FOLDER, path, owner);
   }

   private static AssetEntry vsEntry(int scope, String path, IdentityID owner) {
      return new AssetEntry(scope, AssetEntry.Type.VIEWSHEET, path, owner);
   }

   private static AssetEntry globalRoot() {
      return folderEntry(AssetRepository.GLOBAL_SCOPE, "/", null);
   }

   private static AssetEntry userRoot() {
      return folderEntry(AssetRepository.USER_SCOPE, "/", OWNER);
   }

   @SuppressWarnings("unchecked")
   private static List<Map<String, Object>> targetsOf(Map<String, Object> result) {
      return (List<Map<String, Object>>) result.get("targets");
   }

   @Test
   void returnsGlobalScopeViewsheetsUnderTheRoot() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      when(h.repository.containsEntry(globalRoot())).thenReturn(true);
      when(h.repository.containsEntry(userRoot())).thenReturn(false);
      AssetEntry detail = vsEntry(AssetRepository.GLOBAL_SCOPE, "Detail", null);
      when(h.repository.getEntries(eq(globalRoot()), any(Principal.class),
                                   eq(ResourceAction.READ), any()))
         .thenReturn(new AssetEntry[]{ detail });

      Map<String, Object> result = h.service.listLinkTargets("tok", principal(), null, null, null);

      List<Map<String, Object>> targets = targetsOf(result);
      assertEquals(1, targets.size());
      assertEquals("Detail", targets.get(0).get("path"));
      assertEquals(detail.toIdentifier(), targets.get(0).get("assetLinkId"));
      assertEquals("global", targets.get(0).get("scope"));
      assertEquals(false, result.get("truncated"));
   }

   @Test
   void returnsUserScopeViewsheetsUnderTheRoot() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      when(h.repository.containsEntry(globalRoot())).thenReturn(false);
      when(h.repository.containsEntry(userRoot())).thenReturn(true);
      AssetEntry mine = vsEntry(AssetRepository.USER_SCOPE, "MyReport", OWNER);
      when(h.repository.getEntries(eq(userRoot()), any(Principal.class),
                                   eq(ResourceAction.READ), any()))
         .thenReturn(new AssetEntry[]{ mine });

      Map<String, Object> result = h.service.listLinkTargets("tok", principal(), null, null, null);

      List<Map<String, Object>> targets = targetsOf(result);
      assertEquals(1, targets.size());
      assertEquals("MyReport", targets.get(0).get("path"));
      assertEquals("user", targets.get(0).get("scope"));
   }

   /**
    * Deduping by name would hide exactly the ambiguity {@code assetLinkId} exists to resolve, so
    * a name present in both scopes comes back twice.
    */
   @Test
   void aNameInBothScopesIsReturnedOncePerScope() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      when(h.repository.containsEntry(globalRoot())).thenReturn(true);
      when(h.repository.containsEntry(userRoot())).thenReturn(true);
      when(h.repository.getEntries(eq(globalRoot()), any(Principal.class),
                                   eq(ResourceAction.READ), any()))
         .thenReturn(new AssetEntry[]{ vsEntry(AssetRepository.GLOBAL_SCOPE, "Shared", null) });
      when(h.repository.getEntries(eq(userRoot()), any(Principal.class),
                                   eq(ResourceAction.READ), any()))
         .thenReturn(new AssetEntry[]{ vsEntry(AssetRepository.USER_SCOPE, "Shared", OWNER) });

      Map<String, Object> result = h.service.listLinkTargets("tok", principal(), null, null, null);

      List<Map<String, Object>> targets = targetsOf(result);
      assertEquals(2, targets.size());
      assertTrue(targets.stream().anyMatch(t -> "global".equals(t.get("scope"))));
      assertTrue(targets.stream().anyMatch(t -> "user".equals(t.get("scope"))));
      assertTrue(targets.stream().allMatch(t -> "Shared".equals(t.get("path"))));
   }

   /** A folder that exists in only one scope is normal, so the other scope is skipped, not refused. */
   @Test
   void folderRestrictsToTheSubtreeAndIsSkippedInTheScopeThatLacksIt() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      AssetEntry globalFolder = folderEntry(AssetRepository.GLOBAL_SCOPE, "Reports", null);
      AssetEntry userFolder = folderEntry(AssetRepository.USER_SCOPE, "Reports", OWNER);
      when(h.repository.containsEntry(globalFolder)).thenReturn(true);
      when(h.repository.containsEntry(userFolder)).thenReturn(false);
      when(h.repository.getEntries(eq(globalFolder), any(Principal.class),
                                   eq(ResourceAction.READ), any()))
         .thenReturn(new AssetEntry[]{
            vsEntry(AssetRepository.GLOBAL_SCOPE, "Reports/Detail", null) });

      Map<String, Object> result =
         h.service.listLinkTargets("tok", principal(), "Reports", null, null);

      List<Map<String, Object>> targets = targetsOf(result);
      assertEquals(1, targets.size());
      assertEquals("Reports/Detail", targets.get(0).get("path"));
      verify(h.repository, never()).getEntries(eq(userFolder), any(Principal.class), any(), any());
   }

   /** A match several levels deep is still found even though its containing folder's name does not match. */
   @Test
   void queryMatchesCaseInsensitivelyEvenNestedUnderANonMatchingFolder() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      AssetEntry misc = folderEntry(AssetRepository.GLOBAL_SCOPE, "Misc", null);
      when(h.repository.containsEntry(globalRoot())).thenReturn(true);
      when(h.repository.containsEntry(userRoot())).thenReturn(false);
      when(h.repository.getEntries(eq(globalRoot()), any(Principal.class),
                                   eq(ResourceAction.READ), any()))
         .thenReturn(new AssetEntry[]{ misc });
      when(h.repository.getEntries(eq(misc), any(Principal.class),
                                   eq(ResourceAction.READ), any()))
         .thenReturn(new AssetEntry[]{
            vsEntry(AssetRepository.GLOBAL_SCOPE, "Misc/TargetVS", null) });

      Map<String, Object> result =
         h.service.listLinkTargets("tok", principal(), null, "target", null);

      List<Map<String, Object>> targets = targetsOf(result);
      assertEquals(1, targets.size());
      assertEquals("Misc/TargetVS", targets.get(0).get("path"));
   }

   @Test
   void neverDescendsIntoAFolderNamedRecycleBin() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      AssetEntry recycleBin = folderEntry(AssetRepository.GLOBAL_SCOPE, "Recycle Bin", null);
      when(h.repository.containsEntry(globalRoot())).thenReturn(true);
      when(h.repository.containsEntry(userRoot())).thenReturn(false);
      when(h.repository.getEntries(eq(globalRoot()), any(Principal.class),
                                   eq(ResourceAction.READ), any()))
         .thenReturn(new AssetEntry[]{
            recycleBin, vsEntry(AssetRepository.GLOBAL_SCOPE, "Keep", null) });

      Map<String, Object> result = h.service.listLinkTargets("tok", principal(), null, null, null);

      List<Map<String, Object>> targets = targetsOf(result);
      assertEquals(1, targets.size());
      assertEquals("Keep", targets.get(0).get("path"));
      verify(h.repository, never()).getEntries(eq(recycleBin), any(Principal.class), any(), any());
   }

   @Test
   void limitTruncatesAndReportsTruncatedTrue() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      when(h.repository.containsEntry(globalRoot())).thenReturn(true);
      when(h.repository.containsEntry(userRoot())).thenReturn(false);
      when(h.repository.getEntries(eq(globalRoot()), any(Principal.class),
                                   eq(ResourceAction.READ), any()))
         .thenReturn(new AssetEntry[]{
            vsEntry(AssetRepository.GLOBAL_SCOPE, "A", null),
            vsEntry(AssetRepository.GLOBAL_SCOPE, "B", null) });

      Map<String, Object> result = h.service.listLinkTargets("tok", principal(), null, null, 1);

      assertEquals(1, targetsOf(result).size());
      assertEquals(true, result.get("truncated"));
   }

   @Test
   void aResultSetUnderTheLimitReportsTruncatedFalse() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      when(h.repository.containsEntry(globalRoot())).thenReturn(true);
      when(h.repository.containsEntry(userRoot())).thenReturn(false);
      when(h.repository.getEntries(eq(globalRoot()), any(Principal.class),
                                   eq(ResourceAction.READ), any()))
         .thenReturn(new AssetEntry[]{ vsEntry(AssetRepository.GLOBAL_SCOPE, "A", null) });

      Map<String, Object> result = h.service.listLinkTargets("tok", principal(), null, null, 200);

      assertEquals(false, result.get("truncated"));
   }

   /**
    * This is the test that actually enforces "this tool's output is a contract with the write
    * path" -- every path this method returns must round-trip through {@code set} without hitting
    * the "No viewsheet at ..." refusal {@code resolveViewsheetTarget} raises on a miss.
    */
   @Test
   void everyReturnedPathRoundTripsThroughSetHyperlink() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      reset(h.repository);
      AssetEntry viewsheet = vsEntry(AssetRepository.GLOBAL_SCOPE, "Reports/Detail", null);
      when(h.repository.containsEntry(globalRoot())).thenReturn(true);
      when(h.repository.getEntries(eq(globalRoot()), any(Principal.class),
                                   eq(ResourceAction.READ), any()))
         .thenReturn(new AssetEntry[]{ viewsheet });
      // What resolveViewsheetTarget itself checks to accept the path.
      when(h.repository.containsEntry(viewsheet)).thenReturn(true);

      Map<String, Object> result = h.service.listLinkTargets("tok", principal(), null, null, null);
      String path = (String) targetsOf(result).get(0).get("path");

      assertDoesNotThrow(() -> h.service.set(
         "tok", principal(), "Chart1", null,
         link("linkType", "viewsheet", "assetLinkPath", path), ""));
   }

   /**
    * A mock repository could hand back an entry of a type the selector was never meant to admit
    * (a {@code WORKSHEET}, say); this confirms such an entry is excluded, and that the correct
    * selector -- {@code REPOSITORY_FOLDER}/{@code VIEWSHEET} only -- is what was actually passed
    * to {@code getEntries}, rather than a client-side type check standing in for it.
    */
   @Test
   void nonFolderNonViewsheetEntriesAreExcludedAndTheSelectorIsPassedThrough() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      when(h.repository.containsEntry(globalRoot())).thenReturn(true);
      when(h.repository.containsEntry(userRoot())).thenReturn(false);
      AssetEntry worksheet = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                            "SomeWorksheet", null);
      when(h.repository.getEntries(eq(globalRoot()), any(Principal.class),
                                   eq(ResourceAction.READ), any()))
         .thenReturn(new AssetEntry[]{ worksheet,
                                       vsEntry(AssetRepository.GLOBAL_SCOPE, "Keep", null) });

      Map<String, Object> result = h.service.listLinkTargets("tok", principal(), null, null, null);

      List<Map<String, Object>> targets = targetsOf(result);
      assertEquals(1, targets.size());
      assertEquals("Keep", targets.get(0).get("path"));
      verify(h.repository).getEntries(eq(globalRoot()), any(Principal.class),
                                      eq(ResourceAction.READ),
                                      argThat(sel -> sel.matches(AssetEntry.Type.VIEWSHEET) &&
                                                    sel.matches(AssetEntry.Type.REPOSITORY_FOLDER) &&
                                                    !sel.matches(AssetEntry.Type.WORKSHEET)));
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private record Harness(AssemblyHyperlinkService service, ViewsheetSessionService sessions,
                          HyperlinkDialogService links, AssetRepository repository) {}

   private static HyperlinkDialogModel capture(HyperlinkDialogService links) throws Exception {
      ArgumentCaptor<HyperlinkDialogModel> captor =
         ArgumentCaptor.forClass(HyperlinkDialogModel.class);
      verify(links).setHyperlinkDialogModel(eq("rt1"), anyString(), captor.capture(),
                                            anyString(), any(Principal.class), any());
      return captor.getValue();
   }

   private static Harness harness(HyperlinkDialogModel model) {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      when(rvs.getID()).thenReturn("rt1");

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      HyperlinkDialogService links = mock(HyperlinkDialogService.class);
      AssetRepository repository = mock(AssetRepository.class);

      try {
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         when(rvs.getAssetRepository()).thenReturn(repository);
         // A lenient default -- resolveViewsheetTarget's own tests below stub containsEntry
         // precisely, but the write-path tests above (setsAViewsheetLink) only need resolution
         // to succeed, not to exercise scope precedence, so any entry matches.
         when(repository.containsEntry(any())).thenReturn(true);
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
         when(links.getHyperlinkDialogModel(anyString(), anyString(), any(), any(), any(),
                                            anyBoolean(), anyBoolean(), anyBoolean(),
                                            anyBoolean(), any(Principal.class)))
            .thenReturn(model);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new Harness(new AssemblyHyperlinkService(sessions, links), sessions, links,
                         repository);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
