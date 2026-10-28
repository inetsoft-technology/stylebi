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
package inetsoft.web.admin.ai.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.general.model.WebMapSettingsModel;
import inetsoft.web.admin.presentation.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.withSettings;

/**
 * 01-spec.md section 0/2 (16-entry catalog, dead-field exclusion), section 4 (risk/scope split),
 * section 5 (partial-{@code spec} merge, {@code viewsheetToolbar}/{@code portalIntegration.tabs}
 * whole-list treatment), section 9 (webMap/share secret masking/refusal), section 11 (global-only refusal,
 * verb/scope/subModel validation); 03-reconcile.md Addition 1 (lookAndFeel file name sanitization)
 * and Addition 2 (portalIntegration.tabs whole-list enforcement).
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class PresentationChangePlanServiceTest {
   @Mock
   private PresentationSettingsAccess access;
   private PresentationChangePlanService service;
   private MockedStatic<Tool> tool;

   private static final Principal PRINCIPAL = () -> "admin";
   private static final ObjectMapper MAPPER = new ObjectMapper();

   @BeforeEach
   void setUp() {
      service = new PresentationChangePlanService(access);
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
   }

   @AfterEach
   void tearDown() {
      tool.close();
   }

   // ---------------------------------------------------------------- request builders

   private static PresentationChangeRequest change(String subModel, String scope, ObjectNode spec) {
      PresentationChangeRequest r = new PresentationChangeRequest();
      r.setVerb("update");
      r.setSubModel(subModel);
      r.setScope(scope);
      r.setSpec(spec);
      return r;
   }

   private static PresentationChangePlanRequest request(String task, PresentationChangeRequest... changes) {
      PresentationChangePlanRequest req = new PresentationChangePlanRequest();
      req.setTask(task);
      req.setChanges(Arrays.asList(changes));
      return req;
   }

   private static ObjectNode obj() {
      return MAPPER.createObjectNode();
   }

   private void stub(PresentationSubModel subModel, Object current) throws Exception {
      lenient().when(access.read(eq(subModel), any(), anyBoolean())).thenReturn(current);
   }

   // ---------------------------------------------------------------- minimal valid fixtures, one per sub-model

   private static Object currentFor(PresentationSubModel subModel) {
      switch(subModel) {
      case FORMATS:
         return PresentationFormatsSettingsModel.builder()
            .dateFormat("MM/dd/yyyy").timeFormat("HH:mm").dateTimeFormat("MM/dd/yyyy HH:mm").build();
      case DASHBOARD:
         return PresentationDashboardSettingsModel.builder()
            .enabled(true).tabsTop(false).drillTabsTop(false).build();
      case VIEWSHEET_TOOLBAR:
         return PresentationViewsheetToolbarOptionsModel.builder()
            .options(List.of(ToolbarOption.builder().id("save").visible(true).enabled(true).build()))
            .build();
      case LOOK_AND_FEEL:
         return LookAndFeelSettingsModel.builder()
            .ascending(true).repositoryTree(true).expand(false)
            .defaultLogo(true).logoName("")
            .defaultFavicon(true).faviconName("")
            .defaultViewsheet(true).viewsheetName("")
            .defaultFont(true)
            .viewsheetCSSEntries(List.of()).vsEnabled(true).build();
      case WELCOME_PAGE:
         return WelcomePageSettingsModel.builder().type(0).source("").build();
      case LOGIN_BANNER:
         return PresentationLoginBannerSettingsModel.builder().bannerType("0").loginBanner("").build();
      case PORTAL_INTEGRATION:
         return PortalIntegrationSettingsModel.builder()
            .tabs(List.of(
               PortalTabModel.builder().name("Dashboard").label("Dashboard").uri("/dashboard")
                  .visible(true).editable(false).originalIndex(0).build(),
               PortalTabModel.builder().name("Report").label("Repository").uri("/report")
                  .visible(true).editable(false).originalIndex(1).build()))
            .help(true).preference(true).logout(true).search(true).dashboardAvailable(true)
            .home(true).customLoadingText("").homeLink("").emHomeLink("").build();
      case PDF_GENERATION:
         return PresentationPdfGenerationSettingsModel.builder()
            .compressText(true).compressImage(true).asciiOnly(false).mapSymbols(false)
            .pdfEmbedCmap(false).pdfEmbedFont(false).browserEmbedPdf(false).pdfHyperlinks(true)
            .build();
      case EXPORT_MENU:
         return PresentationExportMenuSettingsModel.builder()
            .vsOptions(List.of(ExportMenuOption.builder().name("PDF").description("PDF").value(true)
                                  .build()))
            .vsEnabled(true).build();
      case FONT_MAPPING:
         return PresentationFontMappingSettingsModel.builder().fontMappings(List.of()).build();
      case SHARE:
         return PresentationShareSettingsModel.builder()
            .emailEnabled(true).facebookEnabled(false).googleChatEnabled(true)
            .linkedinEnabled(false).slackEnabled(true).twitterEnabled(false).linkEnabled(true)
            .slackUrl("https://hooks.slack.example/services/T0/B0/secret-slack-token")
            .googleChatUrl("https://chat.googleapis.com/v1/spaces/S0?key=secret-chat-key")
            .openGraphTitle("").openGraphDescription("").openGraphSiteName("").openGraphImageUrl("")
            .build();
      case COMPOSER_MESSAGE:
         return PresentationComposerMessageSettingsModel.builder()
            .worksheetCreateMessage("").worksheetEditMessage("").viewsheetCreateMessage("")
            .viewsheetEditMessage("").build();
      case TIME:
         return PresentationTimeSettingsModel.builder().weekStart("Sunday").scheduleTime12Hours(true)
            .build();
      case DATA_SOURCE_VISIBILITY:
         return PresentationDataSourceVisibilitySettingsModel.builder().build();
      case WEB_MAP:
         return WebMapSettingsModel.builder().service("mapbox").defaultOn(false)
            .mapboxUser("user").mapboxToken("secret-token").mapboxStyle("streets")
            .googleKey("secret-key").build();
      case AI:
         return PresentationAISettingsModel.builder().aiAssistantVisible(true)
            .chatAppServerUrl("https://example.com").build();
      default:
         throw new IllegalStateException("unhandled sub-model in test fixture: " + subModel);
      }
   }

   /** One valid, minimal partial {@code spec} per sub-model -- deliberately avoids the {@code tabs}/
    * secret fields the dedicated Addition-1/Addition-2/section-9 tests cover on their own. */
   private static ObjectNode specFor(PresentationSubModel subModel) {
      ObjectNode spec = obj();

      switch(subModel) {
      case FORMATS -> spec.put("dateFormat", "yyyy-MM-dd");
      case DASHBOARD -> spec.put("enabled", false);
      case VIEWSHEET_TOOLBAR -> {
         ArrayNode options = spec.putArray("options");
         options.addObject().put("id", "save").put("visible", false).put("enabled", true);
      }
      case LOOK_AND_FEEL -> spec.put("expand", true);
      case WELCOME_PAGE -> spec.put("type", 1).put("source", "http://x");
      case LOGIN_BANNER -> spec.put("bannerType", "1");
      // not a button: the buttons are global-only and refused at organization scope (Bug #78867)
      case PORTAL_INTEGRATION -> spec.put("customLoadingText", "Loading");
      case PDF_GENERATION -> spec.put("compressText", false);
      case EXPORT_MENU -> spec.put("vsEnabled", true);
      case FONT_MAPPING -> spec.putArray("fontMappings");
      case SHARE -> spec.put("emailEnabled", false);
      case COMPOSER_MESSAGE -> spec.put("viewsheetCreateMessage", "hello");
      case TIME -> spec.put("scheduleTime12Hours", false);
      case DATA_SOURCE_VISIBILITY -> spec.putArray("hiddenDataSources");
      case WEB_MAP -> spec.put("defaultOn", true);
      case AI -> spec.put("aiAssistantVisible", false);
      default -> throw new IllegalStateException("unhandled sub-model in test fixture: " + subModel);
      }

      return spec;
   }

   // ---------------------------------------------------------------- section 4: risk/scope, one per sub-model

   @Test
   void allSixteenSubModelsResolveToTheirDeclaredRiskAndScope() throws Exception {
      for(PresentationSubModel subModel : PresentationSubModel.values()) {
         stub(subModel, currentFor(subModel));
         String scope = subModel.globalOnly() ? "global" : "organization";
         PresentationChangePlanRequest req =
            request("update " + subModel.key(), change(subModel.key(), scope, specFor(subModel)));

         var plan = service.resolve(req, PRINCIPAL);

         assertEquals(1, plan.changes().size(), subModel.key());
         var planChange = plan.changes().get(0);
         assertEquals(subModel.risk(), planChange.risk(), subModel.key() + " risk");
         assertEquals(subModel.scope(), planChange.snapshotScope(), subModel.key() + " scope");
         assertEquals(subModel.key() + ":" + scope, planChange.property(), subModel.key() + " property");
         assertTrue(plan.requiresAgentSignoff());
         assertTrue(plan.requiresStorageBackup());
      }
   }

   @Test
   void storageScopeSetIsExactlyTheFourExpectedSubModels() {
      for(PresentationSubModel subModel : PresentationSubModel.values()) {
         boolean expectedStorage = subModel == PresentationSubModel.LOOK_AND_FEEL
            || subModel == PresentationSubModel.WELCOME_PAGE
            || subModel == PresentationSubModel.LOGIN_BANNER
            || subModel == PresentationSubModel.PORTAL_INTEGRATION;
         assertEquals(expectedStorage, subModel.isStorageScope(), subModel.key());
         assertEquals(expectedStorage ? AdminChangeRecord.RISK_HIGH : AdminChangeRecord.RISK_LOW,
                      subModel.risk(), subModel.key());
      }
   }

   // ---------------------------------------------------------------- section 2: dead fields / unrecognized names

   @Test
   void deadModelFieldsAreNotRecognizedSubModelNames() {
      assertThrows(IllegalArgumentException.class,
                  () -> PresentationSubModel.require("reportToolbarOptionsModel"));
      assertThrows(IllegalArgumentException.class,
                  () -> PresentationSubModel.require("reportViewerSettingsModel"));
   }

   @Test
   void unrecognizedSubModelIsRefusedNamingAllSixteen() {
      PresentationChangePlanRequest req =
         request("t", change("notARealSubModel", "global", obj().put("x", 1)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
      assertTrue(ex.getMessage().contains("notARealSubModel"));
      assertTrue(ex.getMessage().contains("lookAndFeel"));
      assertTrue(ex.getMessage().contains("ai"));
   }

   // ---------------------------------------------------------------- section 11: fontMapping/ai global-only

   @Test
   void fontMappingRejectsOrganizationScope() throws Exception {
      stub(PresentationSubModel.FONT_MAPPING, currentFor(PresentationSubModel.FONT_MAPPING));
      PresentationChangePlanRequest req = request("t",
         change("fontMapping", "organization", specFor(PresentationSubModel.FONT_MAPPING)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
      assertTrue(ex.getMessage().contains("global-only"));
   }

   @Test
   void aiRejectsOrganizationScope() throws Exception {
      stub(PresentationSubModel.AI, currentFor(PresentationSubModel.AI));
      PresentationChangePlanRequest req =
         request("t", change("ai", "organization", specFor(PresentationSubModel.AI)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
      assertTrue(ex.getMessage().contains("global-only"));
   }

   // ---------------------------------------------------------------- section 5: spec validation

   @Test
   void unknownSpecFieldIsRefusedNamingFieldAndSubModel() throws Exception {
      stub(PresentationSubModel.FORMATS, currentFor(PresentationSubModel.FORMATS));
      PresentationChangePlanRequest req = request("t",
         change("formats", "organization", obj().put("notARealField", "x")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
      assertTrue(ex.getMessage().contains("notARealField"));
      assertTrue(ex.getMessage().contains("formats"));
   }

   @Test
   void verbOtherThanUpdateIsRefused() {
      PresentationChangeRequest raw = change("formats", "organization", obj().put("dateFormat", "x"));
      raw.setVerb("create");
      PresentationChangePlanRequest req = request("t", raw);

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
      assertTrue(ex.getMessage().contains("update"));
   }

   @Test
   void duplicateSubModelAndScopePairIsRefused() throws Exception {
      stub(PresentationSubModel.FORMATS, currentFor(PresentationSubModel.FORMATS));
      PresentationChangePlanRequest req = request("t",
         change("formats", "organization", obj().put("dateFormat", "a")),
         change("formats", "organization", obj().put("timeFormat", "b")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
      assertTrue(ex.getMessage().contains("duplicate"));
   }

   @Test
   void sameSubModelDifferentScopeIsAllowed() throws Exception {
      stub(PresentationSubModel.FORMATS, currentFor(PresentationSubModel.FORMATS));
      PresentationChangePlanRequest req = request("t",
         change("formats", "global", obj().put("dateFormat", "a")),
         change("formats", "organization", obj().put("dateFormat", "b")));

      var plan = service.resolve(req, PRINCIPAL);
      assertEquals(2, plan.changes().size());
   }

   @Test
   void emptySpecIsRefused() {
      PresentationChangePlanRequest req = request("t", change("formats", "organization", obj()));
      assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
   }

   @Test
   void blankTaskIsRefused() {
      PresentationChangePlanRequest req =
         request("   ", change("formats", "organization", obj().put("dateFormat", "x")));
      assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
   }

   @Test
   void emptyChangesIsRefused() {
      PresentationChangePlanRequest req = new PresentationChangePlanRequest();
      req.setTask("t");
      req.setChanges(List.of());
      assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
   }

   // ---------------------------------------------------------------- 03-reconcile.md Addition 1: lookAndFeel file names

   @Test
   void lookAndFeelFileNameIsNormalizedToASafeBasename() throws Exception {
      stub(PresentationSubModel.LOOK_AND_FEEL, currentFor(PresentationSubModel.LOOK_AND_FEEL));
      ObjectNode spec = obj();
      spec.putObject("viewsheetFile").put("name", "../../etc/passwd").put("content", "YWJj");
      spec.put("defaultViewsheet", false);
      PresentationChangePlanRequest req = request("t", change("lookAndFeel", "organization", spec));

      var plan = service.resolve(req, PRINCIPAL);
      String proposed = plan.changes().get(0).proposedValue();
      String orgId = OrganizationManager.getInstance().getCurrentOrgID();
      // Bug #76729 fix: proposedValue now predicts the real post-write read-back, which never
      // round-trips viewsheetFile and derives viewsheetName as "<orgId>/<sanitized name>" for
      // organization scope (LookAndFeelService.setViewsheet) -- the sanitized name must still never
      // contain the caller's raw ".." path traversal.
      assertTrue(proposed.contains("\"viewsheetName\":\"" + orgId + "/passwd\""), proposed);
      assertTrue(proposed.contains("\"viewsheetFile\":null"), proposed);
      assertFalse(proposed.contains(".."), proposed);
   }

   @Test
   void lookAndFeelFileNameThatIsOnlyDotsIsRefused() throws Exception {
      stub(PresentationSubModel.LOOK_AND_FEEL, currentFor(PresentationSubModel.LOOK_AND_FEEL));
      ObjectNode spec = obj();
      spec.putObject("logoFile").put("name", "..").put("content", "YWJj");
      spec.put("defaultLogo", false);
      PresentationChangePlanRequest req = request("t", change("lookAndFeel", "organization", spec));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
      assertTrue(ex.getMessage().contains("logoFile"));
   }

   // ---------------------------------------------------------------- PR #5298 finding: derived lookAndFeel *Name fields

   @Test
   void lookAndFeelLogoNameCannotBeSetDirectlyGlobalScope() throws Exception {
      stub(PresentationSubModel.LOOK_AND_FEEL, currentFor(PresentationSubModel.LOOK_AND_FEEL));
      PresentationChangePlanRequest req = request("t",
         change("lookAndFeel", "global", obj().put("logoName", "portal/attacker-logo.png")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
      assertTrue(ex.getMessage().contains("logoName"), ex.getMessage());
      assertTrue(ex.getMessage().contains("logoFile"), ex.getMessage());
   }

   @Test
   void lookAndFeelLogoNameCannotBeSetDirectlyOrganizationScope() throws Exception {
      stub(PresentationSubModel.LOOK_AND_FEEL, currentFor(PresentationSubModel.LOOK_AND_FEEL));
      PresentationChangePlanRequest req = request("t",
         change("lookAndFeel", "organization", obj().put("logoName", "portal/org1/attacker-logo.png")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
      assertTrue(ex.getMessage().contains("logoName"), ex.getMessage());
   }

   @Test
   void lookAndFeelFaviconNameCannotBeSetDirectly() throws Exception {
      stub(PresentationSubModel.LOOK_AND_FEEL, currentFor(PresentationSubModel.LOOK_AND_FEEL));
      PresentationChangePlanRequest req = request("t",
         change("lookAndFeel", "global", obj().put("faviconName", "portal/attacker-favicon.ico")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
      assertTrue(ex.getMessage().contains("faviconName"), ex.getMessage());
      assertTrue(ex.getMessage().contains("faviconFile"), ex.getMessage());
   }

   @Test
   void lookAndFeelViewsheetNameCannotBeSetDirectly() throws Exception {
      stub(PresentationSubModel.LOOK_AND_FEEL, currentFor(PresentationSubModel.LOOK_AND_FEEL));
      PresentationChangePlanRequest req = request("t",
         change("lookAndFeel", "organization", obj().put("viewsheetName", "org1/attacker.css")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
      assertTrue(ex.getMessage().contains("viewsheetName"), ex.getMessage());
      assertTrue(ex.getMessage().contains("viewsheetFile"), ex.getMessage());
   }

   // ---------------------------------------------------------------- 03-reconcile.md Addition 2: portalIntegration.tabs

   @Test
   void portalIntegrationTabsMustIncludeEveryCurrentTab() throws Exception {
      stub(PresentationSubModel.PORTAL_INTEGRATION, currentFor(PresentationSubModel.PORTAL_INTEGRATION));
      ObjectNode spec = obj();
      ArrayNode tabs = spec.putArray("tabs");
      tabs.addObject().put("name", "Dashboard").put("label", "Dashboard").put("uri", "/dashboard")
         .put("visible", false).put("editable", false).put("originalIndex", 0);
      // current fixture has 2 tabs; this spec supplies only 1 -- must be refused.
      PresentationChangePlanRequest req =
         request("t", change("portalIntegration", "global", spec));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
      assertTrue(ex.getMessage().contains("tabs"));
   }

   @Test
   void portalIntegrationWholeTabListIsAccepted() throws Exception {
      stub(PresentationSubModel.PORTAL_INTEGRATION, currentFor(PresentationSubModel.PORTAL_INTEGRATION));
      ObjectNode spec = obj();
      ArrayNode tabs = spec.putArray("tabs");
      tabs.addObject().put("name", "Dashboard").put("label", "Dashboard").put("uri", "/dashboard")
         .put("visible", false).put("editable", false).put("originalIndex", 0);
      tabs.addObject().put("name", "Report").put("label", "Repository").put("uri", "/report")
         .put("visible", true).put("editable", false).put("originalIndex", 1);
      PresentationChangePlanRequest req =
         request("t", change("portalIntegration", "global", spec));

      var plan = service.resolve(req, PRINCIPAL);
      assertEquals(1, plan.changes().size());
   }

   // ---------------------------------------------------------------- Bug #78867: tabs keyed by originalIndex

   /** The reporter's live list: four built-in tabs, plus one editable custom tab where a test needs it. */
   private static PortalIntegrationSettingsModel portal(boolean withCustomTab) {
      List<PortalTabModel> tabs = new java.util.ArrayList<>(List.of(
         builtInTab("Dashboard", "/dashboard", 0),
         builtInTab("Report", "/report", 1),
         builtInTab("Schedule", "/schedule", 2),
         builtInTab("Data", "/data", 3)));

      if(withCustomTab) {
         tabs.add(PortalTabModel.builder().name("Custom").label("Custom").uri("https://example.com")
                     .visible(true).editable(true).originalIndex(4).build());
      }

      return PortalIntegrationSettingsModel.builder()
         .tabs(tabs)
         .help(true).preference(true).logout(true).search(true).dashboardAvailable(true)
         .home(true).customLoadingText("").homeLink("").emHomeLink("").build();
   }

   private static PortalTabModel builtInTab(String name, String uri, int originalIndex) {
      return PortalTabModel.builder().name(name).label(name).uri(uri)
         .visible(true).editable(false).originalIndex(originalIndex).build();
   }

   /** The live tab list exactly as the read projection returns it, which is how callers echo it. */
   private static ArrayNode liveTabs(PortalIntegrationSettingsModel model) {
      return (ArrayNode) PresentationJson.toNode(model).get("tabs").deepCopy();
   }

   private static ObjectNode tabsSpec(ArrayNode tabs) {
      ObjectNode spec = obj();
      spec.set("tabs", tabs);
      return spec;
   }

   private IllegalArgumentException refusedTabs(String scope, ArrayNode tabs) {
      PresentationChangePlanRequest req =
         request("t", change("portalIntegration", scope, tabsSpec(tabs)));
      return assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
   }

   private void acceptedTabs(String scope, ArrayNode tabs) throws Exception {
      PresentationChangePlanRequest req =
         request("t", change("portalIntegration", scope, tabsSpec(tabs)));
      assertEquals(1, service.resolve(req, PRINCIPAL).changes().size());
   }

   @Test
   void reportersDuplicateTabInPlaceOfDataIsRefusedNamingTheDeletedTab() throws Exception {
      PortalIntegrationSettingsModel live = portal(false);
      stub(PresentationSubModel.PORTAL_INTEGRATION, live);
      ArrayNode tabs = liveTabs(live);
      tabs.set(3, tabs.get(2).deepCopy()); // [Dashboard, Report, Schedule, Schedule(oi=2)]

      IllegalArgumentException ex = refusedTabs("global", tabs);

      assertTrue(ex.getMessage().contains("originalIndex [2] appears more than once"), ex.getMessage());
      assertTrue(ex.getMessage().contains("3 (\"Data\")"), ex.getMessage());
   }

   @Test
   void duplicateTabWithTheMissingTabsOwnNameIsStillRefused() throws Exception {
      PortalIntegrationSettingsModel live = portal(false);
      stub(PresentationSubModel.PORTAL_INTEGRATION, live);
      ArrayNode tabs = liveTabs(live);
      // name "Schedule" carrying Data's originalIndex: the built-in name check names it
      ((ObjectNode) tabs.get(3)).put("name", "Schedule");

      IllegalArgumentException ex = refusedTabs("global", tabs);

      assertTrue(ex.getMessage().contains("tabs[3].name"), ex.getMessage());
      assertTrue(ex.getMessage().contains("built-in tab \"Data\""), ex.getMessage());
   }

   @Test
   void tabEntryWithoutOriginalIndexIsRefusedPointingToEnterpriseManager() throws Exception {
      PortalIntegrationSettingsModel live = portal(false);
      stub(PresentationSubModel.PORTAL_INTEGRATION, live);
      ArrayNode tabs = liveTabs(live);
      ((ObjectNode) tabs.get(3)).remove("originalIndex");

      IllegalArgumentException ex = refusedTabs("global", tabs);

      assertTrue(ex.getMessage().contains("tabs[3].originalIndex: required"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Enterprise Manager"), ex.getMessage());
   }

   @Test
   void outOfRangeAndNonIntegerOriginalIndexAreRefused() throws Exception {
      PortalIntegrationSettingsModel live = portal(false);
      stub(PresentationSubModel.PORTAL_INTEGRATION, live);

      ArrayNode outOfRange = liveTabs(live);
      ((ObjectNode) outOfRange.get(3)).put("originalIndex", 4);
      assertTrue(refusedTabs("global", outOfRange).getMessage()
                    .contains("tabs[3].originalIndex: 4 is not the originalIndex of a current tab"));

      ArrayNode fractional = liveTabs(live);
      ((ObjectNode) fractional.get(3)).put("originalIndex", 2.5);
      assertTrue(refusedTabs("global", fractional).getMessage().contains("tabs[3].originalIndex"));

      ArrayNode text = liveTabs(live);
      ((ObjectNode) text.get(3)).put("originalIndex", "3");
      assertTrue(refusedTabs("global", text).getMessage().contains("tabs[3].originalIndex"));
   }

   @Test
   void builtInTabRenameIsRefusedIncludingACaseVariant() throws Exception {
      PortalIntegrationSettingsModel live = portal(false);
      stub(PresentationSubModel.PORTAL_INTEGRATION, live);

      ArrayNode renamed = liveTabs(live);
      ((ObjectNode) renamed.get(3)).put("name", "Datasets");
      assertTrue(refusedTabs("global", renamed).getMessage().contains("cannot be renamed"));

      ArrayNode caseVariant = liveTabs(live);
      ((ObjectNode) caseVariant.get(3)).put("name", "data");
      IllegalArgumentException ex = refusedTabs("global", caseVariant);
      assertTrue(ex.getMessage().contains("must be exactly \"Data\" (case-sensitive)"), ex.getMessage());
   }

   @Test
   void editableTabRenameIsAccepted() throws Exception {
      PortalIntegrationSettingsModel live = portal(true);
      stub(PresentationSubModel.PORTAL_INTEGRATION, live);
      ArrayNode tabs = liveTabs(live);
      ((ObjectNode) tabs.get(4)).put("name", "Renamed").put("label", "Renamed");

      acceptedTabs("global", tabs);
   }

   @Test
   void tabReorderIsAccepted() throws Exception {
      PortalIntegrationSettingsModel live = portal(true);
      stub(PresentationSubModel.PORTAL_INTEGRATION, live);
      ArrayNode tabs = liveTabs(live);
      ArrayNode reordered = MAPPER.createArrayNode();
      reordered.add(tabs.get(1)).add(tabs.get(0)).add(tabs.get(3)).add(tabs.get(2)).add(tabs.get(4));

      acceptedTabs("global", reordered);
   }

   @Test
   void longerAndShorterTabListsAreRefused() throws Exception {
      PortalIntegrationSettingsModel live = portal(false);
      stub(PresentationSubModel.PORTAL_INTEGRATION, live);

      ArrayNode longer = liveTabs(live);
      longer.addObject().put("name", "New").put("label", "New").put("uri", "https://example.com")
         .put("visible", true).put("editable", true);
      IllegalArgumentException ex = refusedTabs("global", longer);
      assertTrue(ex.getMessage().contains("must include every current tab (4)"), ex.getMessage());
      assertTrue(ex.getMessage().contains("got 5 entries"), ex.getMessage());

      ArrayNode shorter = liveTabs(live);
      shorter.remove(3);
      assertTrue(refusedTabs("global", shorter).getMessage().contains("got 3 entries"));
   }

   @Test
   void organizationScopeTabChangeIsRefusedButAnUnchangedResupplyIsAccepted() throws Exception {
      PortalIntegrationSettingsModel live = portal(true);
      stub(PresentationSubModel.PORTAL_INTEGRATION, live);

      ArrayNode renamed = liveTabs(live);
      ((ObjectNode) renamed.get(4)).put("name", "Renamed").put("label", "Renamed");
      IllegalArgumentException ex = refusedTabs("organization", renamed);
      assertTrue(ex.getMessage().contains("spec.tabs: portalIntegration.tabs is global-only"),
                 ex.getMessage());
      assertTrue(ex.getMessage().contains("Use scope \"global\""), ex.getMessage());

      // the identity rules still run first at organization scope
      ArrayNode duplicate = liveTabs(live);
      duplicate.set(3, duplicate.get(2).deepCopy());
      assertTrue(refusedTabs("organization", duplicate).getMessage().contains("more than once"));

      acceptedTabs("organization", liveTabs(live));
   }

   @Test
   void organizationScopePortalButtonChangeIsRefusedButNotOtherFields() throws Exception {
      stub(PresentationSubModel.PORTAL_INTEGRATION, portal(false));

      PresentationChangePlanRequest help =
         request("t", change("portalIntegration", "organization", obj().put("help", false)));
      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(help, PRINCIPAL));
      assertTrue(ex.getMessage().contains("spec.help: portalIntegration.help is global-only"),
                 ex.getMessage());

      // a string the backend coerces to the live value is unchanged, so it is accepted
      PresentationChangePlanRequest same =
         request("t", change("portalIntegration", "organization", obj().put("home", "true")));
      assertEquals(1, service.resolve(same, PRINCIPAL).changes().size());

      // customLoadingText has an organization layer and stays writable there
      PresentationChangePlanRequest text = request("t",
         change("portalIntegration", "organization", obj().put("customLoadingText", "Loading")));
      assertEquals(1, service.resolve(text, PRINCIPAL).changes().size());

      // and the button is writable at global scope
      PresentationChangePlanRequest global =
         request("t", change("portalIntegration", "global", obj().put("help", false)));
      assertEquals(1, service.resolve(global, PRINCIPAL).changes().size());
   }

   // ---------------------------------------------------------------- section 9: webMap secrets

   @Test
   void webMapMapboxTokenCannotBeSetThroughSpec() throws Exception {
      stub(PresentationSubModel.WEB_MAP, currentFor(PresentationSubModel.WEB_MAP));
      PresentationChangePlanRequest req = request("t",
         change("webMap", "organization", obj().put("mapboxToken", "new-secret")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
      assertTrue(ex.getMessage().contains("mapboxToken"));
   }

   @Test
   void webMapGoogleKeyCannotBeSetThroughSpec() throws Exception {
      stub(PresentationSubModel.WEB_MAP, currentFor(PresentationSubModel.WEB_MAP));
      PresentationChangePlanRequest req = request("t",
         change("webMap", "organization", obj().put("googleKey", "new-secret")));

      assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
   }

   @Test
   void webMapSecretsAreMaskedInThePlanProjection() throws Exception {
      stub(PresentationSubModel.WEB_MAP, currentFor(PresentationSubModel.WEB_MAP));
      PresentationChangePlanRequest req =
         request("t", change("webMap", "organization", specFor(PresentationSubModel.WEB_MAP)));

      var plan = service.resolve(req, PRINCIPAL);
      String current = plan.changes().get(0).currentValue();
      assertFalse(current.contains("secret-token"), current);
      assertFalse(current.contains("secret-key"), current);
      assertTrue(current.contains("********"), current);
   }

   // ---------------------------------------------------------------- section 9: share webhook URLs

   // The two share.* webhook URLs are secrets by possession: the token is in the path, so anything
   // holding the URL can post into that channel. AdminPropertyCatalog.CONFIRMED_SECRET stopped the
   // properties path returning them (Redmine #76170), but this area reached the same two values
   // through PresentationSubModel.SHARE and masked only webMap - so "show me the sharing settings"
   // still handed both to a caller that relays its responses to a model provider off-host, and an
   // update spec carrying slackUrl was still applied. These four tests pin both halves.

   @Test
   void shareSlackUrlCannotBeSetThroughSpec() throws Exception {
      stub(PresentationSubModel.SHARE, currentFor(PresentationSubModel.SHARE));
      PresentationChangePlanRequest req = request("t",
         change("share", "organization",
                obj().put("slackUrl", "https://hooks.slack.example/services/T1/B1/attacker")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
      assertTrue(ex.getMessage().contains("slackUrl"), ex.getMessage());
   }

   @Test
   void shareGoogleChatUrlCannotBeSetThroughSpec() throws Exception {
      stub(PresentationSubModel.SHARE, currentFor(PresentationSubModel.SHARE));
      PresentationChangePlanRequest req = request("t",
         change("share", "organization",
                obj().put("googleChatUrl", "https://chat.googleapis.com/v1/spaces/S1?key=attacker")));

      assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
   }

   @Test
   void shareWebhookUrlsAreMaskedInThePlanProjection() throws Exception {
      stub(PresentationSubModel.SHARE, currentFor(PresentationSubModel.SHARE));
      PresentationChangePlanRequest req =
         request("t", change("share", "organization", specFor(PresentationSubModel.SHARE)));

      var plan = service.resolve(req, PRINCIPAL);
      String current = plan.changes().get(0).currentValue();
      String proposed = plan.changes().get(0).proposedValue();

      for(String value : new String[] { current, proposed }) {
         assertFalse(value.contains("secret-slack-token"), value);
         assertFalse(value.contains("secret-chat-key"), value);
         assertTrue(value.contains("********"), value);
      }

      // The non-secret share.* fields beside them stay readable - masking must not cost the agent
      // the settings it legitimately needs to answer about.
      assertTrue(current.contains("emailEnabled"), current);
   }

   @Test
   void shareChangeStillWritesTheRealWebhookUrlsNotTheMask() throws Exception {
      // The mask is a projection for display only. resolveEntries merges the spec into the
      // UNMASKED current node, so the model that would be written keeps the real URLs - without
      // this, an ordinary "turn off email sharing" update would round-trip "********" into the
      // store and silently break both integrations.
      stub(PresentationSubModel.SHARE, currentFor(PresentationSubModel.SHARE));
      PresentationChangePlanRequest req =
         request("t", change("share", "organization", obj().put("emailEnabled", false)));

      var entries = service.resolveEntries(req, PRINCIPAL);
      PresentationShareSettingsModel proposed =
         (PresentationShareSettingsModel) entries.get(0).proposedModel();

      assertEquals("https://hooks.slack.example/services/T0/B0/secret-slack-token",
                   proposed.slackUrl());
      assertEquals("https://chat.googleapis.com/v1/spaces/S0?key=secret-chat-key",
                   proposed.googleChatUrl());
      assertFalse(proposed.emailEnabled());
   }

   // ---------------------------------------------------------------- hash: task is audit-only, not part of the plan identity

   @Test
   void hashIsUnaffectedByDifferentTaskStrings() throws Exception {
      stub(PresentationSubModel.FORMATS, currentFor(PresentationSubModel.FORMATS));
      PresentationChangeRequest raw = change("formats", "organization", specFor(PresentationSubModel.FORMATS));

      var planA = service.resolve(request("Update date format to ISO", raw), PRINCIPAL);
      var planB = service.resolve(request("Change the date format to ISO 8601", raw), PRINCIPAL);

      assertEquals(planA.planHash(), planB.planHash());
   }

   // ---------------------------------------------------------------- secretFields is per sub-model

   @Test
   void onlyTheTwoSubModelsWithCredentialsDeclareSecretFields() {
      // Pinned as a property OF the enum rather than through a call site. Every projection and the
      // write guard now ask the sub-model, so this list IS the area's secret classification; a name
      // added to a model without being added here is masked nowhere, which is the shape of the bug
      // that made share's URLs readable in the first place.
      for(PresentationSubModel subModel : PresentationSubModel.values()) {
         Set<String> secrets = subModel.secretFields();

         switch(subModel) {
         case WEB_MAP -> assertEquals(Set.of("mapboxToken", "googleKey"), secrets);
         case SHARE -> assertEquals(Set.of("slackUrl", "googleChatUrl"), secrets);
         default -> assertTrue(secrets.isEmpty(), subModel.key() + " " + secrets);
         }

         // A declared secret must be a field the sub-model actually has, or it masks nothing.
         assertTrue(subModel.fieldNames().containsAll(secrets),
                    subModel.key() + " declares a secret field it does not have: " + secrets);
      }
   }

   // ---------------------------------------------------------------- Bug #79429: file bytes are hashed

   private String lafHash(String scope, ObjectNode spec) throws Exception {
      stub(PresentationSubModel.LOOK_AND_FEEL, currentFor(PresentationSubModel.LOOK_AND_FEEL));
      return service.resolve(request("t", change("lookAndFeel", scope, spec)), PRINCIPAL).planHash();
   }

   private static ObjectNode lafFileSpec(String field, String defaultField, String name,
                                         String content)
   {
      ObjectNode spec = obj().put(defaultField, false);
      spec.putObject(field).put("name", name).put("content", content);
      return spec;
   }

   @Test
   void lookAndFeelPlanHashCoversLogoAndFaviconContentAtBothScopes() throws Exception {
      for(String scope : List.of("global", "organization")) {
         assertNotEquals(
            lafHash(scope, lafFileSpec("logoFile", "defaultLogo", "a.png", "QUJD")),
            lafHash(scope, lafFileSpec("logoFile", "defaultLogo", "a.png", "REVG")), scope + " logo");
         assertNotEquals(
            lafHash(scope, lafFileSpec("faviconFile", "defaultFavicon", "a.ico", "QUJD")),
            lafHash(scope, lafFileSpec("faviconFile", "defaultFavicon", "a.ico", "REVG")),
            scope + " favicon");
      }
   }

   @Test
   void lookAndFeelPlanHashCoversViewsheetFileContentAtBothScopes() throws Exception {
      for(String scope : List.of("global", "organization")) {
         assertNotEquals(
            lafHash(scope, lafFileSpec("viewsheetFile", "defaultViewsheet", "a.css", "QUJD")),
            lafHash(scope, lafFileSpec("viewsheetFile", "defaultViewsheet", "a.css", "REVG")),
            scope + " viewsheet");
      }
   }

   @Test
   void lookAndFeelPlanHashCoversUserformatContentAtGlobalScope() throws Exception {
      assertNotEquals(
         lafHash("global", obj().set("userformatFile",
            obj().put("name", "u.xml").put("content", "QUJD"))),
         lafHash("global", obj().set("userformatFile",
            obj().put("name", "u.xml").put("content", "REVG"))));
   }

   @Test
   void lookAndFeelUserformatIsNotWrittenAtOrganizationScopeSoItIsNotHashed() throws Exception {
      // Pinned decision (Bug #79429): the write gate is global-scope only, so at organization scope
      // the digest is "none" and two contents hash alike -- nothing would be written for either.
      assertEquals(
         lafHash("organization", obj().set("userformatFile",
            obj().put("name", "u.xml").put("content", "QUJD"))),
         lafHash("organization", obj().set("userformatFile",
            obj().put("name", "u.xml").put("content", "REVG"))));
   }

   @Test
   void lookAndFeelSameFileContentHashesTheSameTwice() throws Exception {
      for(String scope : List.of("global", "organization")) {
         assertEquals(
            lafHash(scope, lafFileSpec("logoFile", "defaultLogo", "a.png", "QUJD")),
            lafHash(scope, lafFileSpec("logoFile", "defaultLogo", "a.png", "QUJD")));
         assertEquals(
            lafHash(scope, lafFileSpec("viewsheetFile", "defaultViewsheet", "a.css", "QUJD")),
            lafHash(scope, lafFileSpec("viewsheetFile", "defaultViewsheet", "a.css", "QUJD")));
      }
   }

   @Test
   void lookAndFeelSwappedFileFieldsHashDifferently() throws Exception {
      // Same two byte strings, same projection (both names derive "portal/logo.png" and
      // "portal/favicon.png" either way); only the hidden digest tells them apart.
      ObjectNode a = obj().put("defaultLogo", false).put("defaultFavicon", false);
      a.putObject("logoFile").put("name", "x.png").put("content", "QUJD");
      a.putObject("faviconFile").put("name", "x.png").put("content", "REVG");
      ObjectNode b = obj().put("defaultLogo", false).put("defaultFavicon", false);
      b.putObject("logoFile").put("name", "x.png").put("content", "REVG");
      b.putObject("faviconFile").put("name", "x.png").put("content", "QUJD");

      assertNotEquals(lafHash("global", a), lafHash("global", b));
   }

   @Test
   void lookAndFeelEmptyContentHashesDifferentlyFromNoFile() throws Exception {
      // The write gate is "content != null", so "" is a real (empty) file that gets written.
      assertNotEquals(
         lafHash("global", lafFileSpec("logoFile", "defaultLogo", "a.png", "")),
         lafHash("global", obj().put("defaultLogo", false)));
   }

   @Test
   void lookAndFeelIneffectiveFilesAddNothingToTheHash() throws Exception {
      // defaultLogo:true makes the write ignore logoFile, so it must hash like the same plan without it.
      ObjectNode withFile = obj().put("defaultLogo", true);
      withFile.putObject("logoFile").put("name", "a.png").put("content", "QUJD");
      assertEquals(lafHash("global", withFile), lafHash("global", obj().put("defaultLogo", true)));

      // Viewsheet at global scope: only the content is written (fixed name), so the name is not hashed.
      assertEquals(
         lafHash("global", lafFileSpec("viewsheetFile", "defaultViewsheet", "a.css", "QUJD")),
         lafHash("global", lafFileSpec("viewsheetFile", "defaultViewsheet", "b.css", "QUJD")));
   }

   @Test
   void lookAndFeelFileWithoutANameDoesNotBreakThePlan() throws Exception {
      // A missing name is refused while the model is bound, as before; the digest must not turn it
      // into a different failure.
      ObjectNode spec = obj().put("defaultLogo", false);
      spec.putObject("logoFile").put("content", "QUJD");
      stub(PresentationSubModel.LOOK_AND_FEEL, currentFor(PresentationSubModel.LOOK_AND_FEEL));

      assertThrows(IllegalArgumentException.class, () -> service.resolve(
         request("t", change("lookAndFeel", "global", spec)), PRINCIPAL));
   }

   @Test
   void lookAndFeelNewFontFaceBytesAreAlreadyHashedViaTheProjection() throws Exception {
      // Pin: newFontFaces is not nulled by the read-back simulation, so its bytes are in the
      // projection. If a future change nulls it, this fails and reopens the Bug #79429 hole.
      assertNotEquals(lafHash("global", fontSpec("QUJD")), lafHash("global", fontSpec("REVG")));
   }

   private static ObjectNode fontSpec(String ttfContent) {
      ObjectNode spec = obj().put("defaultFont", false);
      ObjectNode font = spec.putArray("newFontFaces").addObject();
      font.put("name", "F").put("identifier", "f");
      font.putObject("ttf").put("name", "f.ttf").put("content", ttfContent);
      return spec;
   }

   @Test
   void nonFilePlanHashesAreUnchanged() throws Exception {
      // Control: golden values captured from the build before Bug #79429, so a plan with no
      // effective file hashes byte-identically.
      assertEquals(LAF_EXPAND_GLOBAL_HASH, lafHash("global", obj().put("expand", true)));
      stub(PresentationSubModel.FORMATS, currentFor(PresentationSubModel.FORMATS));
      assertEquals(FORMATS_ORG_HASH, service.resolve(request("t", change("formats", "organization",
         specFor(PresentationSubModel.FORMATS))), PRINCIPAL).planHash());
   }

   // ---------------------------------------------------------------- Bug #79430: merged value validation

   private IllegalArgumentException refuseValue(PresentationSubModel subModel, String scope,
                                                Object current, ObjectNode spec) throws Exception
   {
      stub(subModel, current);
      return assertThrows(IllegalArgumentException.class, () -> service.resolve(
         request("t", change(subModel.key(), scope, spec)), PRINCIPAL));
   }

   private void acceptValue(PresentationSubModel subModel, String scope, Object current,
                            ObjectNode spec) throws Exception
   {
      stub(subModel, current);
      assertEquals(1, service.resolve(
         request("t", change(subModel.key(), scope, spec)), PRINCIPAL).changes().size());
   }

   private static WelcomePageSettingsModel welcome(int type, String source) {
      return WelcomePageSettingsModel.builder().type(type).source(source).build();
   }

   private static PresentationTimeSettingsModel time(String weekStart) {
      return PresentationTimeSettingsModel.builder().weekStart(weekStart)
         .scheduleTime12Hours(true).build();
   }

   @Test
   void welcomePageUriWithBlankSourceIsRefusedAtBothScopes() throws Exception {
      for(String scope : new String[] {"global", "organization"}) {
         IllegalArgumentException e = refuseValue(PresentationSubModel.WELCOME_PAGE, scope,
            welcome(0, ""), obj().put("type", 1).put("source", ""));
         assertTrue(e.getMessage().contains("changes[0].spec.source"), e.getMessage());
      }
   }

   @Test
   void welcomePageBlankSourceVariantsAreRefused() throws Exception {
      IllegalArgumentException e = refuseValue(PresentationSubModel.WELCOME_PAGE, "global",
         welcome(1, "http://x"), obj().put("source", "   "));
      assertTrue(e.getMessage().contains("changes[0].spec.source"), e.getMessage());
      refuseValue(PresentationSubModel.WELCOME_PAGE, "global", welcome(0, ""),
                  obj().put("type", 1).putNull("source"));
      refuseValue(PresentationSubModel.WELCOME_PAGE, "global", welcome(0, ""),
                  obj().put("type", 2).put("source", " "));
   }

   @Test
   void welcomePageTypeOnlyAgainstBlankLiveSourceNamesTheTypeAndTheLiveSource() throws Exception {
      for(int type : new int[] {1, 2}) {
         IllegalArgumentException e = refuseValue(PresentationSubModel.WELCOME_PAGE, "global",
            welcome(0, ""), obj().put("type", type));
         assertTrue(e.getMessage().contains("changes[0].spec.type"), e.getMessage());
         assertTrue(e.getMessage().contains("source is blank"), e.getMessage());
      }
   }

   @Test
   void welcomePageOutOfRangeTypeIsRefusedOnlyWhenSent() throws Exception {
      IllegalArgumentException e = refuseValue(PresentationSubModel.WELCOME_PAGE, "global",
         welcome(0, ""), obj().put("type", 7).put("source", "http://x"));
      assertTrue(e.getMessage().contains("changes[0].spec.type"), e.getMessage());
      refuseValue(PresentationSubModel.WELCOME_PAGE, "global", welcome(0, ""), obj().put("type", -1));
      // a stale invalid stored type does not block an unrelated source edit
      acceptValue(PresentationSubModel.WELCOME_PAGE, "global", welcome(7, "a"),
                  obj().put("source", "b"));
   }

   @Test
   void welcomePageTypeCoercedFromStringIsJudgedOnTheTypedValue() throws Exception {
      refuseValue(PresentationSubModel.WELCOME_PAGE, "global", welcome(0, ""),
                  obj().put("type", "1").put("source", ""));
   }

   @Test
   void welcomePageTypeNullIsRefused() throws Exception {
      // type is a primitive int on the model; null must not silently become 0 (NONE).
      refuseValue(PresentationSubModel.WELCOME_PAGE, "global", welcome(1, "http://x"),
                  obj().putNull("type"));
   }

   @Test
   void welcomePageValidValuesStayAccepted() throws Exception {
      acceptValue(PresentationSubModel.WELCOME_PAGE, "global", welcome(0, ""),
                  obj().put("type", 1).put("source", "http://x"));
      acceptValue(PresentationSubModel.WELCOME_PAGE, "organization", welcome(0, ""),
                  obj().put("type", 2).put("source", "res"));
      acceptValue(PresentationSubModel.WELCOME_PAGE, "global", welcome(1, "http://x"),
                  obj().put("type", 0).put("source", ""));
      acceptValue(PresentationSubModel.WELCOME_PAGE, "global", welcome(0, "http://x"),
                  obj().put("type", 1));
      // type 0 with a null source is allowed
      acceptValue(PresentationSubModel.WELCOME_PAGE, "global", welcome(1, "http://x"),
                  obj().put("type", 0).putNull("source"));
   }

   @Test
   void welcomePageRepairAndUnchangedEchoPass() throws Exception {
      // repair path: {type:0} over a stored invalid (1, "")
      acceptValue(PresentationSubModel.WELCOME_PAGE, "global", welcome(1, ""), obj().put("type", 0));
      // an unchanged echo of the stored invalid pair is not a new edit (Bug #79430 O10)
      acceptValue(PresentationSubModel.WELCOME_PAGE, "global", welcome(1, ""),
                  obj().put("type", 1).put("source", ""));
      acceptValue(PresentationSubModel.WELCOME_PAGE, "organization", welcome(1, ""),
                  obj().put("type", 1).put("source", ""));
   }

   @Test
   void timeUnknownWeekStartIsRefused() throws Exception {
      for(String bad : new String[] {"Funday", " Monday", "Mon", "monday "}) {
         IllegalArgumentException e = refuseValue(PresentationSubModel.TIME, "global",
            time("Sunday"), obj().put("weekStart", bad));
         assertTrue(e.getMessage().contains("changes[0].spec.weekStart"), e.getMessage());
      }

      refuseValue(PresentationSubModel.TIME, "organization", time("Sunday"),
                  obj().put("weekStart", "Funday"));
      // null is a change when the live value is non-null, and it is refused
      refuseValue(PresentationSubModel.TIME, "global", time("Sunday"), obj().putNull("weekStart"));
   }

   @Test
   void timeValidWeekStartStaysAccepted() throws Exception {
      for(String ok : new String[] {"Monday", "monday", "SATURDAY", ""}) {
         acceptValue(PresentationSubModel.TIME, "global", time("Sunday"), obj().put("weekStart", ok));
      }
   }

   @Test
   void timeUnchangedEchoPasses() throws Exception {
      // live weekStart null (organization scope with no override), echoed with another change
      acceptValue(PresentationSubModel.TIME, "organization", time(null),
                  obj().putNull("weekStart").put("scheduleTime12Hours", false));
      // live stored invalid value echoed unchanged
      acceptValue(PresentationSubModel.TIME, "global", time("Funday"),
                  obj().put("weekStart", "Funday").put("scheduleTime12Hours", false));
      // an unrelated edit is not blocked by a stored invalid weekStart
      acceptValue(PresentationSubModel.TIME, "global", time("Funday"),
                  obj().put("scheduleTime12Hours", false));
   }

   @Test
   void otherSubModelsAreNotAffectedByValueValidation() throws Exception {
      acceptValue(PresentationSubModel.LOGIN_BANNER, "global",
         currentFor(PresentationSubModel.LOGIN_BANNER), obj().put("bannerType", "9"));
      acceptValue(PresentationSubModel.FORMATS, "global",
         currentFor(PresentationSubModel.FORMATS), obj().put("dateFormat", ""));
   }

   private static final String LAF_EXPAND_GLOBAL_HASH =
      "195df86f0835a3d4bde496f4529ce47b814a60588aaa386fef3382537c1a950c";
   private static final String FORMATS_ORG_HASH =
      "eff0bc0a6d3a7ff7c294aa2f0cc7fcba4b2d8e4ca0c415d742a8bb5d007b33d3";
}
