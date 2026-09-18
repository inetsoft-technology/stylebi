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
package inetsoft.web.admin.ai.general;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import com.fasterxml.jackson.databind.JsonNode;
import inetsoft.web.admin.general.CacheSettingsService;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.general.model.*;
import inetsoft.web.admin.general.model.model.SMTPAuthType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the controller surface: the site-admin gate, the read (including its masking and its
 * partial-read contract), the action-request validation, the conflict handlers, and the endpoint
 * mapping contract. The bearer-token half of the gate is exercised through
 * {@code AdminAiCallerGuard}'s own tests, as in the sibling areas; the {@code isSiteAdmin} half is
 * this controller's own code and is asserted here.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminGeneralSettingsControllerTest {
   @Mock private GeneralSettingsAccess access;
   @Mock private GeneralChangePlanService planService;
   @Mock private GeneralChangesetApplyService applyService;
   @Mock private CacheSettingsService cacheSettingsService;
   @Mock private LocalizationSettingsService localizationSettingsService;

   private static final java.security.Principal PRINCIPAL = () -> "admin";

   private MockedStatic<AdminAiCallerGuard> guard;
   private MockedStatic<OrganizationManager> orgManager;

   /**
    * Admits the caller past the site-admin gate so the tests below can reach the validation they
    * are actually about. {@link #everyEndpointRefusesANonSiteAdmin} re-stubs {@code isSiteAdmin}
    * to false to cover the deny side.
    */
   @BeforeEach
   void allowSiteAdmin() {
      guard = mockStatic(AdminAiCallerGuard.class,
                         withSettings().strictness(Strictness.LENIENT));
      guard.when(AdminAiCallerGuard::requireBearerAuthenticatedRequest).thenAnswer(inv -> null);
      OrganizationManager manager = mock(OrganizationManager.class);
      lenient().when(manager.isSiteAdmin(any(java.security.Principal.class))).thenReturn(true);
      orgManager = mockStatic(OrganizationManager.class,
                              withSettings().strictness(Strictness.LENIENT));
      orgManager.when(OrganizationManager::getInstance).thenReturn(manager);
   }

   @AfterEach
   void tearDown() {
      guard.close();
      orgManager.close();
   }

   private AdminGeneralSettingsController controller() {
      return new AdminGeneralSettingsController(access, planService, applyService,
                                                cacheSettingsService, localizationSettingsService);
   }

   // ---------------------------------------------------------------- mapping contract

   /**
    * Every endpoint must live under /api/wiz/v1/admin/general/**. In particular none may be added
    * under /api/wiz/v1/admin/mv/**, which belongs to the per-viewsheet materialized-view area.
    */
   @Test
   void everyEndpointIsUnderTheGeneralPath() {
      int mapped = 0;

      for(Method method : AdminGeneralSettingsController.class.getDeclaredMethods()) {
         GetMapping get = method.getAnnotation(GetMapping.class);
         PostMapping post = method.getAnnotation(PostMapping.class);

         for(String path : get != null ? get.value() : new String[0]) {
            assertTrue(path.startsWith("/api/wiz/v1/admin/general/"), path);
            mapped++;
         }

         for(String path : post != null ? post.value() : new String[0]) {
            assertTrue(path.startsWith("/api/wiz/v1/admin/general/"), path);
            mapped++;
         }
      }

      assertEquals(5, mapped, "expected settings, preview, apply and the two actions");
   }

   /** The EM page exposes these two as GET; a mutating GET would be wrong and, under the
    * /api/wiz/** CSRF exemption, risky. */
   @Test
   void theTwoActionsArePostNotGet() throws NoSuchMethodException {
      assertNotNull(AdminGeneralSettingsController.class
         .getMethod("cleanUpCache", GeneralActionRequest.class, java.security.Principal.class)
         .getAnnotation(PostMapping.class));
      assertNotNull(AdminGeneralSettingsController.class
         .getMethod("reloadLocales", GeneralActionRequest.class, java.security.Principal.class)
         .getAnnotation(PostMapping.class));
   }

   // ---------------------------------------------------------------- action validation

   @Test
   void cacheCleanupRequiresTheIrreversibleAcknowledgement() {
      GeneralActionRequest req = new GeneralActionRequest();
      req.setTask("clear stale cache");

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> controller().cleanUpCache(req, PRINCIPAL));

      assertTrue(e.getMessage().contains("acknowledgeIrreversibleAction"), e.getMessage());
      verifyNoInteractionsWithCache();
   }

   @Test
   void cacheCleanupRequiresATask() {
      GeneralActionRequest req = new GeneralActionRequest();
      req.setAcknowledgeIrreversibleAction(true);

      assertTrue(assertThrows(IllegalArgumentException.class,
                              () -> controller().cleanUpCache(req, PRINCIPAL)).getMessage()
                    .startsWith("task"));
      verifyNoInteractionsWithCache();
   }

   @Test
   void localeReloadRequiresATask() {
      GeneralActionRequest req = new GeneralActionRequest();

      assertTrue(assertThrows(IllegalArgumentException.class,
                              () -> controller().reloadLocales(req, PRINCIPAL)).getMessage()
                    .startsWith("task"));
   }

   /**
    * AdminChangeRecord.validate() rejects a record with no transactionId and the changeset tools
    * filter on it, so an action audited without one is silently discarded -- which would lose the
    * trail for the single irreversible operation in this area.
    */
   @Test
   void cacheCleanupReturnsATransactionIdForTheAuditTrail() {
      GeneralActionRequest req = new GeneralActionRequest();
      req.setTask("clear stale cache");
      req.setAcknowledgeIrreversibleAction(true);

      Map<String, String> result = controller().cleanUpCache(req, PRINCIPAL);

      assertEquals("applied", result.get("status"));
      assertNotNull(result.get("transactionId"));
      assertTrue(result.get("transactionId").startsWith("gen-"), result.get("transactionId"));
      assertNotNull(result.get("advisory"));
      verify(cacheSettingsService).cleanUpCache();
   }

   @Test
   void localeReloadReturnsATransactionIdForTheAuditTrail() throws Exception {
      GeneralActionRequest req = new GeneralActionRequest();
      req.setTask("pick up new labels");

      Map<String, String> result = controller().reloadLocales(req, PRINCIPAL);

      assertEquals("applied", result.get("status"));
      assertTrue(result.get("transactionId").startsWith("gen-"), result.get("transactionId"));
      verify(localizationSettingsService).reloadLocales();
   }

   /**
    * cleanUpCache fans out across cluster nodes and can throw partway through, having already
    * cleared the nodes it reached. Auditing only the success path would leave the most destructive
    * operation in this area with no trail in exactly the case an operator most needs one.
    */
   @Test
   void cacheCleanupStillAuditsWhenTheCleanupThrows() {
      doThrow(new IllegalStateException("node 3 unreachable"))
         .when(cacheSettingsService).cleanUpCache();
      GeneralActionRequest req = new GeneralActionRequest();
      req.setTask("clear stale cache");
      req.setAcknowledgeIrreversibleAction(true);

      assertThrows(IllegalStateException.class, () -> controller().cleanUpCache(req, PRINCIPAL));
      verify(cacheSettingsService).cleanUpCache();
   }

   private void verifyNoInteractionsWithCache() {
      org.mockito.Mockito.verifyNoInteractions(cacheSettingsService);
   }

   // ---------------------------------------------------------------- conflict handlers

   @Test
   void planHashMismatchIsAConflictCarryingNoTaskToken() {
      ResolvedPlan current =
         new ResolvedPlan("raise the query timeout", List.of(), true, true, "hash123", "token123");
      AdminChangesetApplyService.PlanHashMismatchException ex =
         new AdminChangesetApplyService.PlanHashMismatchException(current);

      Map<String, Object> actual = controller().handlePlanHashMismatch(ex);

      assertEquals("conflict", actual.get("status"));
      assertNull(((ResolvedPlan) actual.get("plan")).taskToken());
   }

   @Test
   void taskTokenMismatchIsAConflictCarryingNoTaskToken() {
      ResolvedPlan current =
         new ResolvedPlan("raise the query timeout", List.of(), true, true, "hash456", "token456");
      AdminChangesetApplyService.TaskTokenMismatchException ex =
         new AdminChangesetApplyService.TaskTokenMismatchException(current, "stale token");

      Map<String, Object> actual = controller().handleTaskTokenMismatch(ex);

      assertEquals("conflict", actual.get("status"));
      assertEquals(ex.getMessage(), actual.get("error"));
      assertNull(((ResolvedPlan) actual.get("plan")).taskToken());
   }

   @Test
   void conflictHandlersAreAnnotatedConflict() throws NoSuchMethodException {
      assertEquals(HttpStatus.CONFLICT, AdminGeneralSettingsController.class
         .getMethod("handlePlanHashMismatch",
                    AdminChangesetApplyService.PlanHashMismatchException.class)
         .getAnnotation(ResponseStatus.class).value());
      assertEquals(HttpStatus.CONFLICT, AdminGeneralSettingsController.class
         .getMethod("handleTaskTokenMismatch",
                    AdminChangesetApplyService.TaskTokenMismatchException.class)
         .getAnnotation(ResponseStatus.class).value());
   }

   @Test
   void illegalArgumentIsABadRequest() throws NoSuchMethodException {
      assertEquals(HttpStatus.BAD_REQUEST, AdminGeneralSettingsController.class
         .getMethod("handleIllegalArgument", IllegalArgumentException.class)
         .getAnnotation(ResponseStatus.class).value());
   }

   // ---------------------------------------------------------------- the site-admin gate

   /**
    * Every endpoint in this area, including the plain read, refuses a caller who is not a site
    * administrator -- the deliberate narrowing this area documents, where Enterprise Manager would
    * let an organization administrator edit {@code mvSettingsModel} alone on a multi-tenant
    * deployment. Written per endpoint rather than on one of them, because the gate is a call the
    * next endpoint added here could simply forget.
    */
   @Test
   void everyEndpointRefusesANonSiteAdmin() {
      OrganizationManager manager = mock(OrganizationManager.class);
      when(manager.isSiteAdmin(any(java.security.Principal.class))).thenReturn(false);
      orgManager.when(OrganizationManager::getInstance).thenReturn(manager);

      AdminGeneralSettingsController controller = controller();
      GeneralActionRequest action = new GeneralActionRequest();
      action.setTask("a task");
      action.setAcknowledgeIrreversibleAction(true);

      assertForbidden(() -> controller.getSettings(null, PRINCIPAL));
      assertForbidden(() -> controller.getSettings("email", PRINCIPAL));
      assertForbidden(() -> controller.preview(new GeneralChangePlanRequest(), PRINCIPAL));
      assertForbidden(() -> controller.apply(new GeneralApplyRequest(), PRINCIPAL));
      assertForbidden(() -> controller.cleanUpCache(action, PRINCIPAL));
      assertForbidden(() -> controller.reloadLocales(action, PRINCIPAL));

      // Refused before anything is read, planned, applied or cleared -- a 403 that still ran the
      // work would leak or change exactly what the gate exists to withhold.
      verifyNoInteractions(access, planService, applyService, cacheSettingsService,
                           localizationSettingsService);
   }

   private static void assertForbidden(org.junit.jupiter.api.function.Executable call) {
      ResponseStatusException e = assertThrows(ResponseStatusException.class, call);
      assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
   }

   // ---------------------------------------------------------------- the read

   /**
    * A sub-model that cannot be read is reported under {@code readErrors} and the other five still
    * come back. {@code PerformanceSettingsService.getModel} parses four properties with no
    * default, so an unset {@code query.runtime.timeout} makes that one sub-model throw -- and an
    * operator asking about mail settings must not get nothing because of it.
    */
   @Test
   void aFailedSubModelReadIsReportedWithoutTakingTheOthersDown() throws Exception {
      stubEveryRead();
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any()))
         .thenThrow(new NumberFormatException("null"));

      GeneralGetResult result = controller().getSettings(null, PRINCIPAL);

      assertEquals(GeneralSubModel.values().length - 1, result.subModels().size());
      assertFalse(result.subModels().containsKey("performance"));
      assertEquals(Map.of("performance", "null"), result.readErrors());
      // The point of the whole exercise: the unrelated sub-model is still answerable.
      assertTrue(result.subModels().containsKey("email"));
   }

   /** A clean read carries no {@code readErrors} at all rather than an empty map. */
   @Test
   void aCleanReadReportsNoReadErrors() throws Exception {
      stubEveryRead();

      GeneralGetResult result = controller().getSettings(null, PRINCIPAL);

      assertEquals(GeneralSubModel.values().length, result.subModels().size());
      assertNull(result.readErrors());
   }

   /**
    * A single named sub-model that fails still throws -- there is no partial answer to give, and
    * folding it into {@code readErrors} would return a 200 that answered nothing.
    */
   @Test
   void aNamedSubModelThatFailsStillThrows() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any()))
         .thenThrow(new NumberFormatException("null"));

      assertThrows(NumberFormatException.class,
                   () -> controller().getSettings("performance", PRINCIPAL));
   }

   /** An unrecognized sub-model name is refused, not silently read as all six. */
   @Test
   void anUnknownSubModelNameIsRefused() {
      assertTrue(assertThrows(IllegalArgumentException.class,
                              () -> controller().getSettings("smtp", PRINCIPAL))
                    .getMessage().contains("smtp"));
   }

   /**
    * The email credentials are masked on the no-{@code subModel} form too -- the shortest path a
    * caller has to all four at once, and therefore the form it would matter most on if the masking
    * were ever applied only to the single-sub-model path.
    */
   @Test
   void emailCredentialsAreMaskedOnBothReadForms() throws Exception {
      stubEveryRead();

      for(String path : new String[] { null, "email" }) {
         String form = path == null ? "all-sub-models" : "single sub-model";
         JsonNode email = controller().getSettings(path, PRINCIPAL).subModels().get("email");

         for(String field : GeneralJson.EMAIL_SECRET_FIELDS) {
            assertEquals("********", email.path(field).asText(),
                         field + " is not masked on the " + form + " read");
            // Masking withholds the value; it must not drop the field, or a caller cannot tell a
            // configured credential from an unset one.
            assertTrue(email.has(field), field + " was dropped rather than masked");
         }

         // An ordinary field beside them stays readable -- see GeneralJson.EMAIL_SECRET_FIELDS for
         // why smtpTokenUri is deliberately not classified as a secret.
         assertEquals("https://oauth.example/token", email.path("smtpTokenUri").asText(), form);
      }
   }

   /** A readable value for every sub-model, so a read test fails on what it is actually about. */
   private void stubEveryRead() throws Exception {
      lenient().when(access.read(eq(GeneralSubModel.LOCALIZATION), any()))
         .thenReturn(LocalizationSettingsModel.builder().build());
      lenient().when(access.read(eq(GeneralSubModel.MV), any()))
         .thenReturn(MVSettingsModel.builder().onDemand(false).onDemandDefault(false)
                        .metadata(false).required(false).build());
      lenient().when(access.read(eq(GeneralSubModel.CACHE), any()))
         .thenReturn(CacheSettingsModel.builder()
                        .directory("$(sree.home)/cache").cleanUpStartup(false).build());
      lenient().when(access.read(eq(GeneralSubModel.EMAIL), any()))
         .thenReturn(EmailSettingsModel.builder()
                        .smtpAuthentication(SMTPAuthType.SASL_XOAUTH2)
                        .ssl(false).tls(false).fromAddress("noreply@example.com")
                        .smtpPassword("hunter2").smtpClientSecret("cs")
                        .smtpAccessToken("at").smtpRefreshToken("rt")
                        .smtpTokenUri("https://oauth.example/token").build());
      lenient().when(access.read(eq(GeneralSubModel.PERFORMANCE), any()))
         .thenReturn(PerformanceSettingsModel.builder()
                        .queryTimeout(60).queryPreviewTimeout(30).maxQueryRowCount(1000)
                        .maxQueryPreviewRowCount(100).maxTableRowCount(500).dataSetCaching(false)
                        .dataCacheSize(10).dataCacheTimeout(60).build());
      lenient().when(access.read(eq(GeneralSubModel.DATA_SPACE), any()))
         .thenReturn(DataSpaceSettingsModel.builder()
                        .keyValueType("mapdb").blobType("local")
                        .assetBackupTaskName("").build());
   }
}
