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
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.script.viewsheet.VSAScriptable;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.vs.HyperlinkDialogModel;
import inetsoft.web.composer.model.vs.InputParameterDialogModel;
import inetsoft.web.composer.vs.dialog.HyperlinkDialogService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
      assertEquals(HyperlinkDialogService.NONE, posted.getLinkType());
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

   // ── alignment with the real hyperlink-dialog UI ─────────────────────────────

   /**
    * The dialog's "self" checkbox is UI sugar -- {@code HyperlinkDialog.submit()} rewrites
    * {@code targetFrame} to {@code "SELF"} right before sending, and
    * {@code HyperlinkDialogService.setHyperlinkDialogModel} only ever reads {@code targetFrame}.
    * Without the same rewrite here, {@code self:true} alone would be accepted and change nothing.
    */
   @Test
   void selfNormalizesToTargetFrameSelf() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.set("tok", principal(), "Chart1", null,
                    link("linkType", "web", "webLink", "https://example.com", "self", true), "");

      assertEquals("SELF", capture(h.links).getTargetFrame());
   }

   /**
    * Mirrors the dialog exactly: checking "self" overrides whatever the free-text target-frame
    * field held, rather than losing to it. An agent sending both in one call gets the same result
    * a human checking the box after typing a custom frame would.
    */
   @Test
   void selfOverridesAnExplicitTargetFrameInTheSameCall() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.set("tok", principal(), "Chart1", null,
                    link("linkType", "web", "webLink", "https://example.com",
                         "targetFrame", "_blank", "self", true), "");

      assertEquals("SELF", capture(h.links).getTargetFrame());
   }

   @Test
   void disableParameterPromptIsApplied() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.set("tok", principal(), "Chart1", null,
                    link("linkType", "viewsheet", "assetLinkPath", "Reports/Detail",
                         "disableParameterPrompt", true), "");

      assertTrue(capture(h.links).isDisableParameterPrompt());
   }

   @Test
   void readBackIncludesAssetLinkIdAndDisableParameterPrompt() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setLinkType(Hyperlink.VIEWSHEET_LINK);
      model.setAssetLinkId("1^128^__NULL__^Reports/Detail");
      model.setDisableParameterPrompt(true);

      Map<String, Object> read = harness(model).service.read("tok", principal(), "Chart1", null);

      assertEquals("1^128^__NULL__^Reports/Detail", read.get("assetLinkId"));
      assertEquals(true, read.get("disableParameterPrompt"));
   }

   @Test
   void paramListWithAConstantValueIsApplied() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.set("tok", principal(), "Chart1", null,
                    link("linkType", "web", "webLink", "https://example.com",
                         "paramList", List.of(Map.of(
                            "name", "region", "value", "West",
                            "valueSource", "constant", "type", "string"))),
                    "");

      List<InputParameterDialogModel> params = capture(h.links).getParamList();
      assertEquals(1, params.size());
      assertEquals("region", params.get(0).getName());
      assertEquals("West", params.get(0).getValue());
      assertEquals("constant", params.get(0).getValueSource());
      assertEquals("string", params.get(0).getType());
   }

   /** Omitting 'valueSource' means constant -- the common case shouldn't need it spelled out. */
   @Test
   void paramListEntryDefaultsToConstantValueSource() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.set("tok", principal(), "Chart1", null,
                    link("linkType", "web", "webLink", "https://example.com",
                         "paramList", List.of(Map.of("name", "region", "value", "West"))),
                    "");

      assertEquals("constant", capture(h.links).getParamList().get(0).getValueSource());
   }

   /**
    * A constant entry with no explicit type must still round-trip as constant.
    * {@code Hyperlink.getParameterType} returns whatever was stored -- including {@code null} --
    * and {@code HyperlinkDialogService.getHyperlinkDialogModel}'s read path infers
    * {@code valueSource} purely from whether that comes back null ({@code "field"}) or not
    * ({@code "constant"}). Leaving {@code type} null for an omitted-type constant entry would
    * make it read back as {@code "field"}, and this class's own field-name validation would then
    * refuse it on the very next {@code set_hyperlink} resubmission of that read-back state.
    */
   @Test
   void paramListEntryWithNoExplicitTypeDefaultsToStringSoItRoundTripsAsConstant()
      throws Exception
   {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.set("tok", principal(), "Chart1", null,
                    link("linkType", "web", "webLink", "https://example.com",
                         "paramList", List.of(Map.of("name", "region", "value", "West"))),
                    "");

      InputParameterDialogModel param = capture(h.links).getParamList().get(0);
      assertEquals("string", param.getType(),
                  "a null type is indistinguishable from a never-set one on read");
   }

   @Test
   void paramListWithAFieldValueMustNameARealBindableField() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      DataRefModel state = mock(DataRefModel.class);
      when(state.getName()).thenReturn("STATE");
      model.setFields(List.of(state));
      Harness h = harness(model);

      h.service.set("tok", principal(), "Chart1", null,
                    link("linkType", "web", "webLink", "https://example.com",
                         "paramList", List.of(Map.of(
                            "name", "region", "value", "STATE", "valueSource", "field"))),
                    "");

      assertEquals("STATE", capture(h.links).getParamList().get(0).getValue());
   }

   @Test
   void paramListRefusesAFieldValueThatIsNotABindableField() {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      DataRefModel state = mock(DataRefModel.class);
      when(state.getName()).thenReturn("STATE");
      model.setFields(List.of(state));
      Harness h = harness(model);

      Exception thrown = assertThrows(Exception.class, () -> h.service.set(
         "tok", principal(), "Chart1", null,
         link("linkType", "web", "webLink", "https://example.com",
              "paramList", List.of(Map.of(
                 "name", "region", "value", "NOT_A_FIELD", "valueSource", "field"))),
         ""));

      assertTrue(thrown.getMessage().contains("NOT_A_FIELD"));
   }

   @Test
   void paramListEntryNeedsAName() {
      Harness h = harness(new HyperlinkDialogModel());

      assertThrows(Exception.class, () -> h.service.set(
         "tok", principal(), "Chart1", null,
         link("linkType", "web", "webLink", "https://example.com",
              "paramList", List.of(Map.of("value", "West"))),
         ""));
   }

   @Test
   void paramListRefusesAnUnknownValueSource() {
      Harness h = harness(new HyperlinkDialogModel());

      assertThrows(Exception.class, () -> h.service.set(
         "tok", principal(), "Chart1", null,
         link("linkType", "web", "webLink", "https://example.com",
              "paramList", List.of(Map.of(
                 "name", "region", "value", "West", "valueSource", "expression"))),
         ""));
   }

   @Test
   void clearingALinkAlsoClearsTheParamList() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setParamList(new ArrayList<>(List.of(new InputParameterDialogModel())));
      Harness h = harness(model);

      h.service.set("tok", principal(), "Chart1", null, link("linkType", "none"), "");

      assertTrue(capture(h.links).getParamList().isEmpty());
   }

   @Test
   void readBackIncludesTheParamList() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      InputParameterDialogModel param = new InputParameterDialogModel();
      param.setName("region");
      param.setValueSource("constant");
      param.setValue("West");
      param.setType("string");
      model.setParamList(List.of(param));

      Map<String, Object> read = harness(model).service.read("tok", principal(), "Chart1", null);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> paramList = (List<Map<String, Object>>) read.get("paramList");
      assertEquals(1, paramList.size());
      assertEquals("region", paramList.get(0).get("name"));
      assertEquals("West", paramList.get(0).get("value"));
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

   // ── regressions for the four hyperlink defects ────────────────────────

   /**
    * The read half of the sentinel mismatch. {@code LINK_TYPES} mapped {@code "none"} to 0, but
    * the dialog service carries its own private {@code NONE = 9}, so a never-linked assembly --
    * which is every assembly until someone links it -- reported itself as {@code "unknown(9)"}.
    * That is the first thing a caller sees on any assembly, and it read as a bug in the tool.
    */
   @Test
   void aNeverLinkedAssemblyReadsBackAsNoneNotAnUnknownConstant() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setLinkType(HyperlinkDialogService.NONE);

      Map<String, Object> read = harness(model).service.read("tok", principal(), "Chart1", null);

      assertEquals("none", read.get("linkType"));
   }

   /** The token the read reports has to be the one {@link AssemblyHyperlinkService#set} accepts. */
   @Test
   void theNoneTokenRoundTripsBetweenReadAndWrite() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setLinkType(HyperlinkDialogService.NONE);
      Harness h = harness(model);

      Object token = h.service.read("tok", principal(), "Chart1", null).get("linkType");
      h.service.set("tok", principal(), "Chart1", null, link("linkType", token), "");

      assertEquals(HyperlinkDialogService.NONE, capture(h.links).getLinkType());
   }

   /**
    * A cleared link must not keep its destination fields. Tooltip and target frame surviving a
    * clear is what made a cleared link read differently from a never-linked one.
    */
   @Test
   void clearingDropsTheTooltipAndTargetFrameToo() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setWebLink("https://old.example.com");
      model.setTooltip("stale tooltip");
      model.setTargetFrame("_blank");
      Harness h = harness(model);

      h.service.set("tok", principal(), "Chart1", null, link("linkType", "none"), "");

      HyperlinkDialogModel posted = capture(h.links);
      assertNull(posted.getTooltip(), "a stale tooltip beside a cleared link reads wrong");
      assertNull(posted.getTargetFrame());
      assertNull(posted.getAssetLinkId());
      assertNull(posted.getAssetLinkPath());
   }

   /**
    * A viewsheet link is stored by {@code assetLinkId}, and nothing ever set it -- it reached
    * {@code VSUtil.getBookmarks} as null and surfaced as a raw NPE. Asserting the link type alone
    * would still pass with the id left null, which is exactly the state that crashed.
    */
   @Test
   void aViewsheetLinkCarriesTheResolvedAssetIdNotJustThePath() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.set("tok", principal(), "Chart1", null,
                    link("linkType", "viewsheet", "assetLinkPath", "Reports/Detail"), "");

      HyperlinkDialogModel posted = capture(h.links);
      assertNotNull(posted.getAssetLinkId(), "the id is the half getHyperlink actually stores");
      assertEquals("Reports/Detail", posted.getAssetLinkPath(), "the path is the display half");
   }

   /**
    * An explicit id short-circuits resolution, for a caller that already holds the identifier --
    * the Composer's own dialog works that way, from its asset tree. {@code assetLinkPath} is still
    * required, because it is the display half of the same link.
    */
   @Test
   void anExplicitAssetLinkIdIsUsedVerbatimWithoutConsultingTheRepository() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      h.service.set("tok", principal(), "Chart1", null,
                    link("linkType", "viewsheet", "assetLinkPath", "Reports/Detail",
                         "assetLinkId", "1^128^__NULL__^Reports/Detail"), "");

      assertEquals("1^128^__NULL__^Reports/Detail", capture(h.links).getAssetLinkId());
      verify(h.repository, never()).containsEntry(any());
   }

   /**
    * <b>The assertion that matters most in this file.</b> Resolving the target inside
    * {@code sessions.mutate} meant an unresolvable path threw with the model already half-written,
    * leaving the assembly carrying {@code linkType=viewsheet} and a null destination -- worse than
    * the refusal it was trying to report. Resolution now runs first, so nothing is mutated.
    */
   @Test
   void anUnresolvablePathIsRefusedWithoutMutatingAnything() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      when(h.repository.containsEntry(any())).thenReturn(false);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", null,
                             link("linkType", "viewsheet", "assetLinkPath", "Reports/Gone"), ""));

      verify(h.sessions, never()).mutate(anyString(), any(Principal.class), any());
      verifyNoInteractions(h.links);
      assertTrue(thrown.getMessage().contains("Reports/Gone"), "name the path that was not found");
   }

   /** The refusal names both scopes it looked in, so the caller knows where to put the sheet. */
   @Test
   void theRefusalNamesBothScopesItSearched() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      when(h.repository.containsEntry(any())).thenReturn(false);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", null,
                             link("linkType", "viewsheet", "assetLinkPath", "Reports/Gone"), ""));

      assertTrue(thrown.getMessage().contains("global"), "name the global scope");
      assertTrue(thrown.getMessage().contains("admin"), "name the caller's own scope");
   }

   /**
    * The dialog service does not echo {@code colName} back into the model, so a caller that
    * addressed a cell BY NAME read back null and could not confirm it had read the cell it wrote.
    * Falling back to what was asked for is the honest answer.
    */
   @Test
   void echoesBackTheColNameTheReadWasScopedTo() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      Map<String, Object> read = h.service.read(
         "tok", principal(), "Table1",
         new AssemblyHyperlinkService.Region(2, 1, "Sales", false, false, false, false));

      assertEquals("Sales", read.get("colName"),
                   "the model does not carry it; the name this read was scoped to is the answer");
   }

   /** The model's own value wins when it has one -- the fallback must not overwrite it. */
   @Test
   void theModelsOwnColNameTakesPrecedenceOverTheRequested() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setColName("Revenue");

      Map<String, Object> read = harness(model).service.read(
         "tok", principal(), "Table1",
         new AssemblyHyperlinkService.Region(2, 1, "Sales", false, false, false, false));

      assertEquals("Revenue", read.get("colName"));
   }

   /** No region, no name asked for, nothing to fall back to -- null, not a fabricated value. */
   @Test
   void reportsANullColNameWhenNoneWasAskedForOrCarried() throws Exception {
      Map<String, Object> read = harness(new HyperlinkDialogModel())
         .service.read("tok", principal(), "Chart1", null);

      assertNull(read.get("colName"));
   }

   // ── VHL-004: self:true reads back targetFrame:"SELF", not "" ───────────────

   /**
    * {@code HyperlinkDialogService.getHyperlinkDialogModel} blanks {@code targetFrame} to
    * {@code ""} and reports the fact via {@code self} whenever the persisted
    * {@code Hyperlink.targetFrame} was the literal {@code "SELF"} -- {@code describe()} must
    * reconstruct that literal for this tool's own contract to hold.
    */
   @Test
   void selfWithARealLinkReadsBackTargetFrameAsSelf() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setLinkType(Hyperlink.WEB_LINK);
      model.setWebLink("https://example.com");
      model.setSelf(true);
      model.setTargetFrame("");

      Map<String, Object> read = harness(model).service.read("tok", principal(), "Chart1", null);

      assertEquals("SELF", read.get("targetFrame"));
   }

   /**
    * A never-linked assembly also carries {@code self=true} by default
    * ({@code HyperlinkDialogService.java:182}, taken whenever {@code hyperlink == null}) -- not
    * because a "SELF" target frame was ever persisted. Reporting {@code targetFrame:"SELF"} here
    * would fabricate a link property for an assembly that has no link at all, so this must stay
    * as the model's own (unset) target frame, mirroring the real Angular dialog's own guard
    * ({@code hyperlink-dialog.component.ts:309-310}, which only rewrites for
    * {@code linkType !== NONE}).
    */
   @Test
   void selfDefaultOnANeverLinkedAssemblyDoesNotFabricateTargetFrameSelf() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setLinkType(HyperlinkDialogService.NONE);
      model.setSelf(true);
      model.setTargetFrame("");

      Map<String, Object> read = harness(model).service.read("tok", principal(), "Chart1", null);

      assertEquals("none", read.get("linkType"));
      assertNotEquals("SELF", read.get("targetFrame"));
   }

   // ── VHL-001: chart data-point and axis-label links share one storage slot ──

   /**
    * {@code HyperlinkDialogService.getHyperlinkDialogModel} only sets {@code model.colName} from
    * its chart branch, so a non-null model colName here (outside a title/empty-plot-link ask) is
    * exactly the shape where {@code axis:true} and a plain call resolve to the same
    * {@code ChartRef}'s single {@code Hyperlink} slot. The response must disclose that plainly.
    */
   @Test
   void disclosesTheSharedStorageForAnOrdinaryChartFieldAddressedByColName() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setColName("Revenue");

      Map<String, Object> read = harness(model).service.read(
         "tok", principal(), "Chart1",
         new AssemblyHyperlinkService.Region(null, null, "Revenue", true, false, false, false));

      assertEquals(true, read.get("axisAndDataPointShareLink"));
   }

   /** A title link lives on {@code ChartVSAssemblyInfo} itself, independent of any ChartRef. */
   @Test
   void doesNotDiscloseSharedStorageForATitleLink() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setColName("Revenue");

      Map<String, Object> read = harness(model).service.read(
         "tok", principal(), "Chart1",
         new AssemblyHyperlinkService.Region(null, null, null, false, false, true, false));

      assertEquals(false, read.get("axisAndDataPointShareLink"));
   }

   /** A table cell has no colName-addressed ChartRef at all -- nothing is shared. */
   @Test
   void doesNotDiscloseSharedStorageWhenTheModelCarriesNoColName() throws Exception {
      Map<String, Object> read = harness(new HyperlinkDialogModel())
         .service.read("tok", principal(), "Table1",
                       new AssemblyHyperlinkService.Region(2, 1, "Sales", false, false, false,
                                                           false));

      assertEquals(false, read.get("axisAndDataPointShareLink"));
   }

   // ── VHL-003: link.bookmark silently dropped ─────────────────────────────────

   @Test
   void bookmarkIsRefusedForAWebLink() {
      Harness h = harness(new HyperlinkDialogModel());

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Chart1", null,
                             link("linkType", "web", "webLink", "https://example.com",
                                  "bookmark", "Q3"),
                             ""));

      assertTrue(thrown.getMessage().contains("bookmark"));
      assertTrue(thrown.getMessage().contains("web"));
   }

   @Test
   void bookmarkIsRefusedForAMessageLink() {
      Harness h = harness(new HyperlinkDialogModel());

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Chart1", null,
                             link("linkType", "message", "webLink", "hello",
                                  "bookmark", "Q3"),
                             ""));

      assertTrue(thrown.getMessage().contains("bookmark"));
   }

   // The "matches an existing bookmark" / "matches none" cases call the real, unmocked
   // VSUtil.getBookmarks (via Mockito.mockStatic) -- that class's static initializer touches
   // Spring-context-dependent caching, so those two live in
   // AssemblyHyperlinkServiceBookmarkValidationTest, bootstrapped the same way
   // ComposerBindingControllerTest bootstraps it, rather than dragging that setup into this
   // file's fast, plain-Mockito harness for every other test in it.

   // ── harness ───────────────────────────────────────────────────────────────

   private record Harness(AssemblyHyperlinkService service, ViewsheetSessionService sessions,
                          HyperlinkDialogService links, AssetRepository repository,
                          RuntimeViewsheet rvs) {}

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
                         repository, rvs);
   }

   private static Principal principal() {
      return () -> "admin";
   }

   // ── VHL-005: webLink 'hyperlink:<field>'/'=<expr>' validation ──────────────

   /**
    * Wires {@code h.rvs} to resolve {@code assemblyName} to {@code assembly} and to hand back
    * {@code sandbox} from {@code getViewsheetSandbox()} -- the same two things
    * {@code runtimeColumnNames}/{@code requireValidWebLinkExpression} read.
    */
   private static ViewsheetSandbox wireRuntimeData(Harness h, String assemblyName,
                                                   VSAssembly assembly) throws Exception
   {
      Viewsheet vs = h.rvs().getViewsheet();
      when(vs.getAssembly(assemblyName)).thenReturn(assembly);
      ViewsheetSandbox sandbox = mock(ViewsheetSandbox.class);
      when(h.rvs().getViewsheetSandbox()).thenReturn(Optional.of(sandbox));
      return sandbox;
   }

   private static VSDataSet chartDataset(String... headers) {
      VSDataSet dataset = mock(VSDataSet.class);
      when(dataset.getColCount()).thenReturn(headers.length);

      for(int i = 0; i < headers.length; i++) {
         when(dataset.getHeader(i)).thenReturn(headers[i]);
      }

      return dataset;
   }

   private static VSTableLens tableLens(String... headers) {
      VSTableLens lens = mock(VSTableLens.class);
      when(lens.getColCount()).thenReturn(headers.length);

      for(int i = 0; i < headers.length; i++) {
         when(lens.getObject(0, i)).thenReturn(headers[i]);
      }

      return lens;
   }

   @Test
   void acceptsAHyperlinkFieldPrefixNamingARealChartColumn() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      ViewsheetSandbox sandbox = wireRuntimeData(h, "Chart1", chart);
      VSDataSet dataset = chartDataset("STATE", "REVENUE");
      when(sandbox.getData("Chart1")).thenReturn(dataset);

      assertDoesNotThrow(() -> h.service.set(
         "tok", principal(), "Chart1", null,
         link("linkType", "web", "webLink", "hyperlink:STATE"), ""));

      assertEquals("hyperlink:STATE", capture(h.links).getWebLink());
   }

   @Test
   void refusesAHyperlinkFieldPrefixNamingAColumnTheChartDataDoesNotReturn() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      ViewsheetSandbox sandbox = wireRuntimeData(h, "Chart1", chart);
      VSDataSet dataset = chartDataset("STATE", "REVENUE");
      when(sandbox.getData("Chart1")).thenReturn(dataset);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", null,
                             link("linkType", "web", "webLink", "hyperlink:TOTALLY_BOGUS"), ""));

      assertTrue(thrown.getMessage().contains("TOTALLY_BOGUS"));
      assertTrue(thrown.getMessage().contains("STATE"), "names the columns that do exist");
   }

   /**
    * The whole point of VHL-005's corrected fix shape: {@code model.getFields()} (what
    * {@code parseParamList} validates a 'field' paramList entry against) is narrower than the
    * runtime dataset -- here, {@code STATE} is not in the paramList-scoped field list at all, yet
    * it is a real column the chart's data actually returns and 'hyperlink:STATE' must be accepted.
    * A validator that reused {@code model.getFields()} (the diagnosis's own original, refuted
    * proposal) would have wrongly refused this.
    */
   @Test
   void acceptsAFieldTheNarrowParamListFieldSetDoesNotCarryButTheRuntimeDatasetDoes()
      throws Exception
   {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      DataRefModel revenueOnly = mock(DataRefModel.class);
      when(revenueOnly.getName()).thenReturn("REVENUE");
      model.setFields(List.of(revenueOnly));
      Harness h = harness(model);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      ViewsheetSandbox sandbox = wireRuntimeData(h, "Chart1", chart);
      VSDataSet dataset = chartDataset("STATE", "REVENUE");
      when(sandbox.getData("Chart1")).thenReturn(dataset);

      assertDoesNotThrow(() -> h.service.set(
         "tok", principal(), "Chart1", null,
         link("linkType", "web", "webLink", "hyperlink:STATE"), ""));
   }

   @Test
   void acceptsAHyperlinkFieldPrefixNamingARealTableColumn() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      TableVSAssembly table = mock(TableVSAssembly.class);
      ViewsheetSandbox sandbox = wireRuntimeData(h, "Table1", table);
      VSTableLens lens = tableLens("STATE", "REVENUE");
      when(sandbox.getVSTableLens("Table1", false)).thenReturn(lens);

      assertDoesNotThrow(() -> h.service.set(
         "tok", principal(), "Table1", null,
         link("linkType", "web", "webLink", "hyperlink:REVENUE"), ""));
   }

   @Test
   void refusesAHyperlinkFieldPrefixNamingAColumnTheTableLensDoesNotHave() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      TableVSAssembly table = mock(TableVSAssembly.class);
      ViewsheetSandbox sandbox = wireRuntimeData(h, "Table1", table);
      VSTableLens lens = tableLens("STATE", "REVENUE");
      when(sandbox.getVSTableLens("Table1", false)).thenReturn(lens);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Table1", null,
                             link("linkType", "web", "webLink", "hyperlink:NOT_A_COLUMN"), ""));

      assertTrue(thrown.getMessage().contains("NOT_A_COLUMN"));
   }

   /** No sandbox reachable (e.g. a sourceless assembly) -- nothing to check against, so no refusal. */
   @Test
   void doesNotRefuseAHyperlinkFieldPrefixWhenThereIsNoRuntimeDataYet() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      when(h.rvs().getViewsheetSandbox()).thenReturn(Optional.empty());

      assertDoesNotThrow(() -> h.service.set(
         "tok", principal(), "Chart1", null,
         link("linkType", "web", "webLink", "hyperlink:ANYTHING"), ""));
   }

   @Test
   void acceptsAValidExpressionWebLink() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      ViewsheetSandbox sandbox = wireRuntimeData(h, "Chart1", chart);
      ViewsheetScope scope = mock(ViewsheetScope.class);
      VSAScriptable scriptable = mock(VSAScriptable.class);
      when(sandbox.getScope()).thenReturn(scope);
      when(scope.getVSAScriptable("Chart1")).thenReturn(scriptable);
      when(scope.execute(eq("\"https://x/\" + STATE"), eq(scriptable), eq(false)))
         .thenReturn("https://x/NY");

      assertDoesNotThrow(() -> h.service.set(
         "tok", principal(), "Chart1", null,
         link("linkType", "web", "webLink", "=\"https://x/\" + STATE"), ""));

      assertEquals("=\"https://x/\" + STATE", capture(h.links).getWebLink());
   }

   /**
    * Otherwise this is only caught one layer downstream, at the next view refresh
    * ({@code ViewsheetSandbox.executeDynamicValue}), as a viewer message the plugin tool itself
    * never sees -- so a caller gets {@code ok:true} on a write that then silently fails to
    * render a working link. This must be refused at write time instead.
    */
   @Test
   void refusesASyntacticallyInvalidExpressionWebLinkAtWriteTime() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      ViewsheetSandbox sandbox = wireRuntimeData(h, "Chart1", chart);
      ViewsheetScope scope = mock(ViewsheetScope.class);
      VSAScriptable scriptable = mock(VSAScriptable.class);
      when(sandbox.getScope()).thenReturn(scope);
      when(scope.getVSAScriptable("Chart1")).thenReturn(scriptable);
      when(scope.execute(eq("STATE +"), eq(scriptable), eq(false)))
         .thenThrow(new RuntimeException("missing operand"));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", null,
                             link("linkType", "web", "webLink", "=STATE +"), ""));

      assertTrue(thrown.getMessage().contains("missing operand"));
   }

   @Test
   void doesNotRefuseAnExpressionWebLinkWhenThereIsNoRuntimeDataYet() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      when(h.rvs().getViewsheetSandbox()).thenReturn(Optional.empty());

      assertDoesNotThrow(() -> h.service.set(
         "tok", principal(), "Chart1", null,
         link("linkType", "web", "webLink", "=STATE +"), ""));
   }

   /** A plain URL is neither prefix -- neither validator ever reaches for the sandbox at all. */
   @Test
   void aPlainUrlWebLinkSkipsBothValidators() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());

      assertDoesNotThrow(() -> h.service.set(
         "tok", principal(), "Chart1", null,
         link("linkType", "web", "webLink", "https://example.com"), ""));

      verify(h.rvs(), never()).getViewsheetSandbox();
   }

   // ── bug #77031 ─────────────────────────────────────────────────────────────

   private static VSTableLens headedTableLens(String... headers) {
      VSTableLens lens = tableLens(headers);
      when(lens.getHeaderRowCount()).thenReturn(1);
      return lens;
   }

   /** A colName-only table call used to land on column 0 while the read echoed the name. */
   @Test
   void aTableColNameResolvesToItsColumn() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      ViewsheetSandbox sandbox = wireRuntimeData(h, "Table1", mock(TableVSAssembly.class));
      VSTableLens lens = headedTableLens("Region", "State", "Population");
      when(sandbox.getVSTableLens("Table1", false)).thenReturn(lens);

      h.service.set("tok", principal(), "Table1",
                    new AssemblyHyperlinkService.Region(1, null, "Population", false, false,
                                                        false, false),
                    link("linkType", "web", "webLink", "https://example.com"), "");

      verify(h.links).getHyperlinkDialogModel(eq("rt1"), eq("Table1"), eq(1), eq(2),
                                              eq("Population"), eq(false), eq(false), eq(false),
                                              eq(false), any(Principal.class));
   }

   @Test
   void aTableColNameAlsoResolvesOnRead() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      ViewsheetSandbox sandbox = wireRuntimeData(h, "Table1", mock(TableVSAssembly.class));
      VSTableLens lens = headedTableLens("Region", "State", "Population");
      when(sandbox.getVSTableLens("Table1", false)).thenReturn(lens);

      h.service.read("tok", principal(), "Table1",
                     new AssemblyHyperlinkService.Region(1, null, "State", false, false, false,
                                                         false));

      verify(h.links).getHyperlinkDialogModel(eq("rt1"), eq("Table1"), eq(1), eq(1),
                                              eq("State"), eq(false), eq(false), eq(false),
                                              eq(false), any(Principal.class));
   }

   @Test
   void aTableColNameThatMatchesNoColumnIsRefused() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      ViewsheetSandbox sandbox = wireRuntimeData(h, "Table1", mock(TableVSAssembly.class));
      VSTableLens lens = headedTableLens("Region", "State");
      when(sandbox.getVSTableLens("Table1", false)).thenReturn(lens);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.read("tok", principal(), "Table1",
                              new AssemblyHyperlinkService.Region(1, null, "Nope", false, false,
                                                                  false, false)));

      assertTrue(thrown.getMessage().contains("Nope"));
      verifyNoInteractions(h.links);
   }

   @Test
   void aTableColNameThatDisagreesWithColIsRefused() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      ViewsheetSandbox sandbox = wireRuntimeData(h, "Table1", mock(TableVSAssembly.class));
      VSTableLens lens = headedTableLens("Region", "State", "Population");
      when(sandbox.getVSTableLens("Table1", false)).thenReturn(lens);

      assertThrows(IllegalArgumentException.class,
                   () -> h.service.read("tok", principal(), "Table1",
                                        new AssemblyHyperlinkService.Region(
                                           1, 0, "Population", false, false, false, false)));
      verifyNoInteractions(h.links);
   }

   /** A crosstab has no one column per name, so colName is refused rather than ignored. */
   @Test
   void aCrosstabColNameIsRefused() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      wireRuntimeData(h, "Crosstab1", mock(CrosstabVSAssembly.class));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.read("tok", principal(), "Crosstab1",
                              new AssemblyHyperlinkService.Region(
                                 1, null, "Sum(Sales)", false, false, false, false)));

      assertTrue(thrown.getMessage().contains("'row' and 'col'"));
      verifyNoInteractions(h.links);
   }

   /** A read of a SELF link comes back as targetFrame "" + self=true; "" means a new tab. */
   @Test
   void aPartialUpdateOfASelfLinkKeepsSelf() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setLinkType(Hyperlink.WEB_LINK);
      model.setWebLink("https://example.com");
      model.setTargetFrame("");
      model.setSelf(true);
      Harness h = harness(model);

      h.service.set("tok", principal(), "Chart1", null,
                    link("linkType", "web", "webLink", "https://example.com", "tooltip", "tip"),
                    "");

      assertEquals("SELF", capture(h.links).getTargetFrame());
   }

   @Test
   void anExplicitTargetFrameStillWinsOverAReadSelf() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setSelf(true);
      Harness h = harness(model);

      h.service.set("tok", principal(), "Chart1", null,
                    link("linkType", "web", "webLink", "https://example.com",
                         "targetFrame", "_blank"), "");

      assertEquals("_blank", capture(h.links).getTargetFrame());
   }

   @Test
   void selfFalseStillOptsOutOfAReadSelf() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setSelf(true);
      model.setTargetFrame("");
      Harness h = harness(model);

      h.service.set("tok", principal(), "Chart1", null,
                    link("linkType", "web", "webLink", "https://example.com", "self", false), "");

      assertNotEquals("SELF", capture(h.links).getTargetFrame());
   }

   /** A row-linked table's data cell reads the row link; a plain cell set must not rewrite it. */
   @Test
   void aCellSetOnARowLinkedTableWithoutApplyToRowIsRefused() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setLinkType(Hyperlink.WEB_LINK);
      model.setApplyToRow(true);
      model.setShowRow(true);
      Harness h = harness(model);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Table1", null,
                             link("linkType", "web", "webLink", "https://example.com"), ""));

      assertTrue(thrown.getMessage().contains("applyToRow"));
      verify(h.links, never()).setHyperlinkDialogModel(anyString(), anyString(), any(),
                                                       anyString(), any(Principal.class), any());
   }

   @Test
   void applyToRowIsPassedThroughWhenExplicit() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setApplyToRow(true);
      model.setShowRow(true);
      Harness h = harness(model);

      h.service.set("tok", principal(), "Table1", null,
                    link("linkType", "web", "webLink", "https://example.com",
                         "applyToRow", false), "");

      assertFalse(capture(h.links).isApplyToRow());
   }

   @Test
   void applyToRowTrueIsRefusedWhereTheDialogDoesNotOfferIt() {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setShowRow(false);
      Harness h = harness(model);

      assertThrows(IllegalArgumentException.class,
                   () -> h.service.set("tok", principal(), "Crosstab1", null,
                                       link("linkType", "web", "webLink", "https://example.com",
                                            "applyToRow", true), ""));
   }

   @Test
   void readBackIncludesApplyToRow() throws Exception {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setLinkType(Hyperlink.WEB_LINK);
      model.setApplyToRow(true);

      Map<String, Object> read = harness(model).service.read("tok", principal(), "Table1", null);

      assertEquals(true, read.get("applyToRow"));
   }

   /** get_hyperlink reads the caller's own sheet back as "My Dashboards/<path>". */
   @Test
   void aMyDashboardsPathResolvesInTheCallersOwnScope() throws Exception {
      Harness h = harness(new HyperlinkDialogModel());
      when(h.repository.containsEntry(any())).thenReturn(false);
      AssetEntry mine = vsEntry(AssetRepository.USER_SCOPE, "Detail",
                                IdentityID.getIdentityIDFromKey("admin"));
      when(h.repository.containsEntry(mine)).thenReturn(true);

      h.service.set("tok", principal(), "Chart1", null,
                    link("linkType", "viewsheet", "assetLinkPath", "My Dashboards/Detail"), "");

      assertEquals(mine.toIdentifier(), capture(h.links).getAssetLinkId());
   }
}
