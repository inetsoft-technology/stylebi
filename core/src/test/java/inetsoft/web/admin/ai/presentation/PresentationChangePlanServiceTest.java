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
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.general.model.WebMapSettingsModel;
import inetsoft.web.admin.presentation.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

/**
 * 01-spec.md section 0/2 (16-entry catalog, dead-field exclusion), section 4 (risk/scope split),
 * section 5 (partial-{@code spec} merge, {@code viewsheetToolbar}/{@code portalIntegration.tabs}
 * whole-list treatment), section 9 (webMap secret masking/refusal), section 11 (global-only refusal,
 * verb/scope/subModel validation); 03-reconcile.md Addition 1 (lookAndFeel file name sanitization)
 * and Addition 2 (portalIntegration.tabs whole-list enforcement).
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class PresentationChangePlanServiceTest {
   @Mock
   private PresentationSettingsAccess access;
   private PresentationChangePlanService service;

   private static final Principal PRINCIPAL = () -> "admin";
   private static final ObjectMapper MAPPER = new ObjectMapper();

   @BeforeEach
   void setUp() {
      service = new PresentationChangePlanService(access);
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
            .defaultLogo(true).defaultFavicon(true).defaultViewsheet(true).defaultFont(true)
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
            .emailEnabled(true).facebookEnabled(false).googleChatEnabled(false)
            .linkedinEnabled(false).slackEnabled(false).twitterEnabled(false).linkEnabled(true)
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
      case WELCOME_PAGE -> spec.put("type", 1);
      case LOGIN_BANNER -> spec.put("bannerType", "1");
      case PORTAL_INTEGRATION -> spec.put("help", false);
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
      assertTrue(proposed.contains("\"name\":\"passwd\""), proposed);
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
}
