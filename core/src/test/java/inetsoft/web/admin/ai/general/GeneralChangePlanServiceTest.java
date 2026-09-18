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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.general.model.*;
import inetsoft.web.admin.general.model.model.SMTPAuthType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pins the validations this area exists for: the three places where the merge-onto-current pattern
 * would otherwise produce a silent no-op, a spurious rollback, or a silent delete.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class GeneralChangePlanServiceTest {
   @Mock
   private GeneralSettingsAccess access;
   private GeneralChangePlanService service;
   private MockedStatic<Tool> tool;
   private MockedStatic<SreeEnv> sreeEnv;

   private static final Principal PRINCIPAL = () -> "admin";
   private static final ObjectMapper MAPPER = new ObjectMapper();

   @BeforeEach
   void setUp() {
      service = new GeneralChangePlanService(access);
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      sreeEnv = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      sreeEnv.when(() -> SreeEnv.getProperty("sree.home")).thenReturn("/opt/sree");
   }

   @AfterEach
   void tearDown() {
      tool.close();
      sreeEnv.close();
   }

   // ---------------------------------------------------------------- fixtures

   private static GeneralChangeRequest change(String subModel, ObjectNode spec) {
      GeneralChangeRequest r = new GeneralChangeRequest();
      r.setVerb("update");
      r.setSubModel(subModel);
      r.setSpec(spec);
      return r;
   }

   private static GeneralChangePlanRequest request(String task, GeneralChangeRequest... changes) {
      GeneralChangePlanRequest req = new GeneralChangePlanRequest();
      req.setTask(task);
      req.setChanges(List.of(changes));
      return req;
   }

   private static ObjectNode spec() {
      return MAPPER.createObjectNode();
   }

   private static CacheSettingsModel cache(String directory) {
      return CacheSettingsModel.builder().directory(directory).cleanUpStartup(false).build();
   }

   private static PerformanceSettingsModel performance(long timeout) {
      return PerformanceSettingsModel.builder()
         .queryTimeout(timeout).queryPreviewTimeout(30).maxQueryRowCount(1000)
         .maxQueryPreviewRowCount(100).maxTableRowCount(500).dataSetCaching(false)
         .dataCacheSize(10).dataCacheTimeout(60).build();
   }

   private static EmailSettingsModel email(SMTPAuthType authType) {
      return EmailSettingsModel.builder()
         .fromAddress("noreply@example.com").smtpAuthentication(authType)
         .ssl(false).tls(false).build();
   }

   private static MVSettingsModel mv(String defaultCycle, List<String> cycles) {
      MVSettingsModel.Builder builder = MVSettingsModel.builder()
         .onDemand(false).onDemandDefault(false).metadata(false).required(false)
         .defaultCycle(defaultCycle);

      if(cycles != null) {
         builder.cycles(cycles);
      }

      return builder.build();
   }

   private static LocalizationSettingsModel localization(String... languages) {
      LocalizationSettingsModel.Builder builder = LocalizationSettingsModel.builder();

      for(String language : languages) {
         builder.addLocales(LocalizationModel.builder()
            .language(language).country("US").label(language + "_US").build());
      }

      return builder.build();
   }

   private static ArrayNode localeArray(String... languages) {
      ArrayNode array = MAPPER.createArrayNode();

      for(String language : languages) {
         ObjectNode locale = MAPPER.createObjectNode();
         locale.put("language", language);
         locale.put("country", "US");
         locale.put("label", language + "_US");
         array.add(locale);
      }

      return array;
   }

   // ---------------------------------------------------------------- request shape

   @Test
   void blankTaskIsRefused() throws Exception {
      GeneralChangePlanRequest req = request("  ", change("cache", spec().put("cleanUpStartup", true)));
      assertTrue(assertThrows(IllegalArgumentException.class,
                              () -> service.resolve(req, PRINCIPAL)).getMessage().startsWith("task"));
   }

   @Test
   void emptyChangeListIsRefused() {
      GeneralChangePlanRequest req = new GeneralChangePlanRequest();
      req.setTask("t");
      req.setChanges(List.of());
      assertTrue(assertThrows(IllegalArgumentException.class,
                              () -> service.resolve(req, PRINCIPAL)).getMessage()
                    .startsWith("changes"));
   }

   @Test
   void onlyUpdateVerbIsAccepted() {
      GeneralChangeRequest raw = change("cache", spec().put("cleanUpStartup", true));
      raw.setVerb("delete");
      assertTrue(assertThrows(IllegalArgumentException.class,
                              () -> service.resolve(request("t", raw), PRINCIPAL)).getMessage()
                    .contains("verb"));
   }

   @Test
   void duplicateSubModelIsRefused() throws Exception {
      when(access.read(eq(GeneralSubModel.CACHE), any())).thenReturn(cache("/opt/sree/cache"));
      GeneralChangePlanRequest req = request(
         "t", change("cache", spec().put("cleanUpStartup", true)),
         change("cache", spec().put("cleanUpStartup", false)));

      assertTrue(assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL))
                    .getMessage().contains("more than once"));
   }

   @Test
   void unknownSpecFieldIsRefused() {
      GeneralChangePlanRequest req = request("t", change("cache", spec().put("nope", true)));
      assertTrue(assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL))
                    .getMessage().contains("not a field"));
   }

   @Test
   void emptySpecIsRefused() {
      GeneralChangePlanRequest req = request("t", change("cache", spec()));
      assertTrue(assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL))
                    .getMessage().contains("spec"));
   }

   // ---------------------------------------------------------------- scope refusal

   /**
    * The whole point of declaring scope/orgId rather than letting ignoreUnknown swallow them: a
    * caller who thinks they are confining a change to one tenant must be told otherwise, not
    * quietly given a global write.
    */
   @Test
   void scopeIsRefusedRatherThanIgnored() {
      GeneralChangeRequest raw = change("cache", spec().put("cleanUpStartup", true));
      raw.setScope("organization");
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> service.resolve(request("t", raw), PRINCIPAL));

      assertTrue(e.getMessage().contains("scope"), e.getMessage());
      assertTrue(e.getMessage().contains("deployment-global"), e.getMessage());
   }

   @Test
   void orgIdIsRefusedRatherThanIgnored() {
      GeneralChangeRequest raw = change("cache", spec().put("cleanUpStartup", true));
      raw.setOrgId("acme");
      assertTrue(assertThrows(IllegalArgumentException.class,
                              () -> service.resolve(request("t", raw), PRINCIPAL)).getMessage()
                    .contains("orgId"));
   }

   // ---------------------------------------------------------------- dataSpace read-only

   @Test
   void dataSpaceWriteIsRefusedAndPointsAtTheBackupTool() {
      GeneralChangePlanRequest req =
         request("t", change("dataSpace", spec().put("blobType", "s3")));
      IllegalArgumentException e =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));

      assertTrue(e.getMessage().contains("read-only"), e.getMessage());
      assertTrue(e.getMessage().contains("inetsoft.yaml"), e.getMessage());
      assertTrue(e.getMessage().contains("storage backup"), e.getMessage());
   }

   // ---------------------------------------------------------------- email

   @Test
   void everyEmailSecretFieldIsRefused() throws Exception {
      // No access.read stub: the secret refusal fires before the sub-model is ever read, which is
      // itself worth pinning -- a caller must not be able to probe a value by naming it.
      for(String secret : GeneralSubModel.EMAIL.secretFields()) {
         GeneralChangePlanRequest req =
            request("t", change("email", spec().put(secret, "value")));
         IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));

         assertTrue(e.getMessage().contains(secret), e.getMessage());
         assertTrue(e.getMessage().contains("Enterprise Manager"), e.getMessage());
      }
   }

   /** Unconditional fields are writable under any auth type, including none. */
   @Test
   void unconditionalEmailFieldsAreAcceptedUnderAnyAuthType() throws Exception {
      when(access.read(eq(GeneralSubModel.EMAIL), any())).thenReturn(email(SMTPAuthType.NONE));
      ResolvedPlan plan = service.resolve(
         request("t", change("email", spec().put("smtpHost", "smtp.example.com"))), PRINCIPAL);

      assertEquals(1, plan.changes().size());
      assertTrue(plan.changes().get(0).proposedValue().contains("smtp.example.com"));
   }

   @Test
   void oauthOnlyEmailFieldIsRefusedUnderSmtpAuth() throws Exception {
      when(access.read(eq(GeneralSubModel.EMAIL), any())).thenReturn(email(SMTPAuthType.SMTP_AUTH));
      GeneralChangePlanRequest req =
         request("t", change("email", spec().put("smtpClientId", "abc")));
      IllegalArgumentException e =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));

      assertTrue(e.getMessage().contains("smtpClientId"), e.getMessage());
      assertTrue(e.getMessage().contains(SMTPAuthType.SMTP_AUTH.name()), e.getMessage());
   }

   @Test
   void saslOnlyEmailFieldIsRefusedUnderGoogleAuth() throws Exception {
      when(access.read(eq(GeneralSubModel.EMAIL), any())).thenReturn(email(SMTPAuthType.GOOGLE_AUTH));
      GeneralChangePlanRequest req =
         request("t", change("email", spec().put("smtpOAuthScopes", "a b")));

      assertTrue(assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL))
                    .getMessage().contains("smtpOAuthScopes"));
   }

   @Test
   void smtpUserIsRefusedWhenAuthTypeIsNone() throws Exception {
      when(access.read(eq(GeneralSubModel.EMAIL), any())).thenReturn(email(SMTPAuthType.NONE));
      GeneralChangePlanRequest req = request("t", change("email", spec().put("smtpUser", "bob")));

      assertTrue(assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL))
                    .getMessage().contains("smtpUser"));
   }

   /** The merged auth type governs, so switching type and setting its fields in one change works
    * -- which it must, since that is the only way to switch. */
   @Test
   void switchingAuthTypeInTheSameChangeAllowsItsOwnFields() throws Exception {
      when(access.read(eq(GeneralSubModel.EMAIL), any())).thenReturn(email(SMTPAuthType.NONE));
      ObjectNode spec = spec();
      spec.put("smtpAuthentication", SMTPAuthType.SASL_XOAUTH2.name());
      spec.put("smtpOAuthScopes", "a b");
      spec.put("smtpUser", "bob");

      ResolvedPlan plan = service.resolve(request("t", change("email", spec)), PRINCIPAL);
      assertEquals(1, plan.changes().size());
   }

   @Test
   void displayOnlyEmailFieldsAreRefused() throws Exception {
      when(access.read(eq(GeneralSubModel.EMAIL), any())).thenReturn(email(SMTPAuthType.NONE));

      for(String field : List.of("secretIdVisible", "fromAddressEnabled")) {
         GeneralChangePlanRequest req = request("t", change("email", spec().put(field, true)));
         assertTrue(assertThrows(IllegalArgumentException.class,
                                 () -> service.resolve(req, PRINCIPAL)).getMessage()
                       .contains(field));
      }
   }

   /**
    * Off cloud secrets the field is never read or written by EmailSettingsService at all, so a
    * value here would be a silent no-op that surfaces only as an unexplained rollback.
    */
   @Test
   void smtpSecretIdIsRefusedWhenCloudSecretsAreOff() throws Exception {
      when(access.read(eq(GeneralSubModel.EMAIL), any())).thenReturn(email(SMTPAuthType.NONE));
      GeneralChangePlanRequest req =
         request("t", change("email", spec().put("smtpSecretId", "prod/mail")));
      IllegalArgumentException e =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));

      assertTrue(e.getMessage().contains("smtpSecretId"), e.getMessage());
      assertTrue(e.getMessage().contains("does not use cloud secrets"), e.getMessage());
   }

   @Test
   void smtpSecretIdIsRefusedUnderCloudSecrets() throws Exception {
      tool.when(Tool::isCloudSecrets).thenReturn(true);
      when(access.read(eq(GeneralSubModel.EMAIL), any())).thenReturn(email(SMTPAuthType.SMTP_AUTH));
      GeneralChangePlanRequest req =
         request("t", change("email", spec().put("smtpSecretId", "prod/mail")));
      IllegalArgumentException e =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));

      assertTrue(e.getMessage().contains("cloud secrets"), e.getMessage());
   }

   /**
    * Under cloud secrets the merged model must carry the value the service just returned, never a
    * caller-supplied one -- otherwise a literal password would be written where a secret reference
    * belongs. Guaranteed by the unconditional secret-field refusal; asserted rather than inferred.
    */
   @Test
   void mergedEmailModelNeverCarriesACallerSuppliedPassword() throws Exception {
      tool.when(Tool::isCloudSecrets).thenReturn(true);
      GeneralChangePlanRequest req =
         request("t", change("email", spec().put("smtpPassword", "hunter2")));

      assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));
      verify(access, never()).read(eq(GeneralSubModel.EMAIL), any());
   }

   /** Masked in the plan projection, not just in the GET response. */
   @Test
   void emailSecretsAreMaskedInThePlanProjection() throws Exception {
      EmailSettingsModel current = EmailSettingsModel.builder()
         .fromAddress("noreply@example.com").smtpAuthentication(SMTPAuthType.SMTP_AUTH)
         .ssl(false).tls(false).smtpPassword("hunter2").build();
      when(access.read(eq(GeneralSubModel.EMAIL), any())).thenReturn(current);

      ResolvedPlan plan = service.resolve(
         request("t", change("email", spec().put("smtpHost", "smtp.example.com"))), PRINCIPAL);

      assertFalse(plan.changes().get(0).currentValue().contains("hunter2"),
                  plan.changes().get(0).currentValue());
      assertTrue(plan.changes().get(0).currentValue().contains("********"));
   }

   // ------------------------------------------------- canonical projection (review finding 1)

   /**
    * The projection must be the canonical model, not the caller's raw JSON. A string-encoded
    * number deserializes fine but would otherwise project as "120" against a read-back of 120,
    * failing verification and rolling a correct write back.
    */
   @Test
   void stringEncodedNumberProjectsCanonically() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any())).thenReturn(performance(60));

      String projected = service.resolve(
         request("t", change("performance", spec().put("queryTimeout", "120"))), PRINCIPAL)
         .changes().get(0).proposedValue();

      assertTrue(projected.contains("\"queryTimeout\":120"), projected);
      assertFalse(projected.contains("\"queryTimeout\":\"120\""), projected);
   }

   @Test
   void stringEncodedBooleanProjectsCanonically() throws Exception {
      when(access.read(eq(GeneralSubModel.CACHE), any())).thenReturn(cache("$(sree.home)/cache"));

      String projected = service.resolve(
         request("t", change("cache", spec().put("cleanUpStartup", "true"))), PRINCIPAL)
         .changes().get(0).proposedValue();

      assertTrue(projected.contains("\"cleanUpStartup\":true"), projected);
   }

   /** Locale objects are the only nested values here, so caller key order is the live risk. */
   @Test
   void localeFieldOrderIsCanonicalRegardlessOfCallerKeyOrder() throws Exception {
      when(access.read(eq(GeneralSubModel.LOCALIZATION), any())).thenReturn(localization("en"));
      ObjectNode locale = MAPPER.createObjectNode();
      locale.put("label", "en_US");
      locale.put("country", "US");
      locale.put("language", "en");
      ObjectNode spec = spec();
      spec.set("locales", MAPPER.createArrayNode().add(locale));

      String projected = service.resolve(
         request("t", change("localization", spec)), PRINCIPAL).changes().get(0).proposedValue();

      assertTrue(projected.indexOf("language") < projected.indexOf("country"), projected);
      assertTrue(projected.indexOf("country") < projected.indexOf("label"), projected);
   }

   // ------------------------------------------------- cloud secrets clobber (finding 2)

   /**
    * Under cloud secrets getPassword returns the RESOLVED secret while setPassword stores its
    * argument literally, and setModel rewrites the whole OAuth credential group for these two auth
    * types on every write -- so any change would replace three secrets-manager references with
    * plaintext, irreversibly.
    */
   @Test
   void emailWriteIsRefusedUnderCloudSecretsWithOauthAuthTypes() throws Exception {
      tool.when(Tool::isCloudSecrets).thenReturn(true);

      for(SMTPAuthType authType : List.of(SMTPAuthType.SASL_XOAUTH2, SMTPAuthType.GOOGLE_AUTH)) {
         when(access.read(eq(GeneralSubModel.EMAIL), any())).thenReturn(email(authType));
         GeneralChangePlanRequest req =
            request("t", change("email", spec().put("smtpHost", "smtp.example.com")));

         IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));

         assertTrue(e.getMessage().contains("cloud secrets"), e.getMessage());
         assertTrue(e.getMessage().contains("irreversibly"), e.getMessage());
      }
   }

   @Test
   void emailWriteIsStillAllowedUnderCloudSecretsWithNonOauthAuthTypes() throws Exception {
      tool.when(Tool::isCloudSecrets).thenReturn(true);

      for(SMTPAuthType authType : List.of(SMTPAuthType.NONE, SMTPAuthType.SMTP_AUTH)) {
         when(access.read(eq(GeneralSubModel.EMAIL), any())).thenReturn(email(authType));

         assertEquals(1, service.resolve(
            request("t", change("email", spec().put("smtpHost", "smtp.example.com"))), PRINCIPAL)
            .changes().size());
      }
   }

   @Test
   void emailWriteIsUnaffectedWhenCloudSecretsAreOff() throws Exception {
      when(access.read(eq(GeneralSubModel.EMAIL), any()))
         .thenReturn(email(SMTPAuthType.SASL_XOAUTH2));

      assertEquals(1, service.resolve(
         request("t", change("email", spec().put("smtpHost", "smtp.example.com"))), PRINCIPAL)
         .changes().size());
   }

   // ------------------------------------------------- auth type naming (finding 6)

   /** forValue maps anything unrecognized to NONE, which would otherwise surface as a confusing
    * complaint about a different field. */
   @Test
   void unrecognizedAuthTypeIsRefusedByName() throws Exception {
      when(access.read(eq(GeneralSubModel.EMAIL), any())).thenReturn(email(SMTPAuthType.NONE));
      ObjectNode spec = spec();
      spec.put("smtpAuthentication", "smtp_auth");
      spec.put("smtpUser", "bob");

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> service.resolve(request("t", change("email", spec)), PRINCIPAL));

      assertTrue(e.getMessage().contains("smtpAuthentication"), e.getMessage());
      assertTrue(e.getMessage().contains("not a recognized"), e.getMessage());
   }

   /** The lowercase mail.smtp.auth spelling is not the JSON form either. */
   @Test
   void lowercaseAuthTypeValueIsRefusedByName() throws Exception {
      when(access.read(eq(GeneralSubModel.EMAIL), any())).thenReturn(email(SMTPAuthType.NONE));

      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> service.resolve(
            request("t", change("email", spec().put("smtpAuthentication", "saslXOauth2"))),
            PRINCIPAL)).getMessage().contains("not a recognized"));
   }

   // ------------------------------------------------- range check bypass (finding 3)

   @Test
   void stringEncodedNegativeCacheSizeIsStillRefused() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any())).thenReturn(performance(60));

      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> service.resolve(
            request("t", change("performance", spec().put("dataCacheSize", "-1"))), PRINCIPAL))
         .getMessage().contains("must not be negative"));
   }

   @Test
   void stringEncodedOverMaxCacheSizeIsStillRefused() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any())).thenReturn(performance(60));

      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> service.resolve(
            request("t", change("performance", spec().put("dataCacheSize", "5000000000"))),
            PRINCIPAL)).getMessage().contains("must not exceed"));
   }

   // ---------------------------------------------------------------- localization

   @Test
   void shorterLocaleListIsRefusedNamingWhatWouldBeDeleted() throws Exception {
      when(access.read(eq(GeneralSubModel.LOCALIZATION), any()))
         .thenReturn(localization("en", "fr", "de"));
      ObjectNode spec = spec();
      spec.set("locales", localeArray("en", "fr"));

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> service.resolve(request("t", change("localization", spec)), PRINCIPAL));

      assertTrue(e.getMessage().contains("de_US"), e.getMessage());
      assertTrue(e.getMessage().contains("deleted"), e.getMessage());
   }

   /**
    * The refusal has to be escapable, or it is a contradiction.
    *
    * <p>The message tells the caller to resend the same list with
    * {@code acknowledgeLocaleRemoval}; an earlier version instead said to "send the full list
    * without it and say so in the task description" while refusing exactly that, with no override
    * anywhere -- so removing a locale through this area was impossible. Caught in live testing by
    * following the message's own instruction and being refused again.
    */
   @Test
   void acknowledgedLocaleRemovalIsAccepted() throws Exception {
      when(access.read(eq(GeneralSubModel.LOCALIZATION), any()))
         .thenReturn(localization("en", "fr", "de"));
      ObjectNode spec = spec();
      spec.set("locales", localeArray("en", "fr"));
      GeneralChangeRequest raw = change("localization", spec);
      raw.setAcknowledgeLocaleRemoval(true);

      ResolvedPlan plan = service.resolve(request("drop the de locale on purpose", raw), PRINCIPAL);

      assertEquals(1, plan.changes().size());
      assertFalse(plan.changes().get(0).proposedValue().contains("de_US"),
                  plan.changes().get(0).proposedValue());
   }

   /** The refusal names the flag, so a caller can act on it without reading the source. */
   @Test
   void theLocaleRefusalNamesTheAcknowledgementFlag() throws Exception {
      when(access.read(eq(GeneralSubModel.LOCALIZATION), any()))
         .thenReturn(localization("en", "fr"));
      ObjectNode spec = spec();
      spec.set("locales", localeArray("en"));

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> service.resolve(request("t", change("localization", spec)), PRINCIPAL));

      assertTrue(e.getMessage().contains("acknowledgeLocaleRemoval"), e.getMessage());
      // And it must NOT still tell the caller to do the thing it just refused.
      assertFalse(e.getMessage().contains("say so in the task description"), e.getMessage());
   }

   /** Only false-y values leave the guard armed -- an explicit false is not an acknowledgement. */
   @Test
   void explicitlyFalseAcknowledgementStillRefuses() throws Exception {
      when(access.read(eq(GeneralSubModel.LOCALIZATION), any()))
         .thenReturn(localization("en", "fr"));
      ObjectNode spec = spec();
      spec.set("locales", localeArray("en"));
      GeneralChangeRequest raw = change("localization", spec);
      raw.setAcknowledgeLocaleRemoval(false);

      assertThrows(IllegalArgumentException.class,
                   () -> service.resolve(request("t", raw), PRINCIPAL));
   }

   /** A list that adds without dropping needs no acknowledgement. */
   @Test
   void acknowledgementIsNotRequiredWhenNothingIsDropped() throws Exception {
      when(access.read(eq(GeneralSubModel.LOCALIZATION), any())).thenReturn(localization("en"));
      ObjectNode spec = spec();
      spec.set("locales", localeArray("en", "fr"));

      assertEquals(1, service.resolve(request("add fr", change("localization", spec)), PRINCIPAL)
         .changes().size());
   }

   /** The flag authorizes nothing outside localization, so it is refused rather than ignored. */
   @Test
   void acknowledgementOnANonLocalizationChangeIsRefused() throws Exception {
      ObjectNode spec = spec();
      spec.put("metadata", true);
      GeneralChangeRequest raw = change("mv", spec);
      raw.setAcknowledgeLocaleRemoval(true);

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> service.resolve(request("t", raw), PRINCIPAL));

      assertTrue(e.getMessage().contains("acknowledgeLocaleRemoval"), e.getMessage());
      verify(access, never()).read(eq(GeneralSubModel.MV), any());
   }

   /**
    * The length check alone is not enough: swapping one locale for another keeps the count
    * identical while still deleting the one left out, which is exactly what this guard exists to
    * stop.
    */
   @Test
   void sameLengthLocaleListThatDropsALocaleIsRefused() throws Exception {
      when(access.read(eq(GeneralSubModel.LOCALIZATION), any())).thenReturn(localization("en", "fr"));
      ObjectNode spec = spec();
      spec.set("locales", localeArray("en", "es"));

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> service.resolve(request("t", change("localization", spec)), PRINCIPAL));

      assertTrue(e.getMessage().contains("fr_US"), e.getMessage());
   }

   @Test
   void sameLocaleSetInADifferentOrderIsAccepted() throws Exception {
      when(access.read(eq(GeneralSubModel.LOCALIZATION), any())).thenReturn(localization("en", "fr"));
      ObjectNode spec = spec();
      spec.set("locales", localeArray("fr", "en"));

      assertEquals(1, service.resolve(request("t", change("localization", spec)), PRINCIPAL)
         .changes().size());
   }

   @Test
   void addingALocaleIsAccepted() throws Exception {
      when(access.read(eq(GeneralSubModel.LOCALIZATION), any())).thenReturn(localization("en"));
      ObjectNode spec = spec();
      spec.set("locales", localeArray("en", "fr"));

      assertEquals(1, service.resolve(request("t", change("localization", spec)), PRINCIPAL)
         .changes().size());
   }

   /**
    * getModel sorts locale.available before rebuilding the list, while setModel writes the
    * caller's order. Without simulating that, the natural "read, append, send" edit projects
    * [en_US, de_US] but reads back [de_US, en_US], and the apply service rolls a correct write
    * back over as a failed verification -- the bug #76729 shape.
    */
   @Test
   void localeProjectionIsSortedTheWayAReadWouldReturnIt() throws Exception {
      when(access.read(eq(GeneralSubModel.LOCALIZATION), any())).thenReturn(localization("en"));
      ObjectNode spec = spec();
      spec.set("locales", localeArray("en", "de"));

      String projected = service.resolve(
         request("t", change("localization", spec)), PRINCIPAL).changes().get(0).proposedValue();

      assertTrue(projected.indexOf("de_US") < projected.indexOf("en_US"),
                 "projected locales should be sorted by language_country: " + projected);
   }

   // ---------------------------------------------------------------- mv

   @Test
   void mvCyclesIsRefusedAsDerived() throws Exception {
      when(access.read(eq(GeneralSubModel.MV), any())).thenReturn(mv("nightly", List.of("nightly")));
      ObjectNode spec = spec();
      spec.set("cycles", MAPPER.createArrayNode().add("weekly"));

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> service.resolve(request("t", change("mv", spec)), PRINCIPAL));

      assertTrue(e.getMessage().contains("cycles"), e.getMessage());
      assertTrue(e.getMessage().contains("defaultCycle"), e.getMessage());
   }

   @Test
   void unknownDefaultCycleIsRefusedAndExplainsTheGlobalPerOrgSplit() throws Exception {
      when(access.read(eq(GeneralSubModel.MV), any()))
         .thenReturn(mv("nightly", List.of("nightly", "weekly")));

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> service.resolve(request("t", change("mv", spec().put("defaultCycle", "hourly"))),
                               PRINCIPAL));

      assertTrue(e.getMessage().contains("hourly"), e.getMessage());
      assertTrue(e.getMessage().contains("per-organization"), e.getMessage());
   }

   @Test
   void knownDefaultCycleIsAccepted() throws Exception {
      when(access.read(eq(GeneralSubModel.MV), any()))
         .thenReturn(mv("nightly", List.of("nightly", "weekly")));

      assertEquals(1, service.resolve(
         request("t", change("mv", spec().put("defaultCycle", "weekly"))), PRINCIPAL)
         .changes().size());
   }

   /**
    * An empty cycles list means the product suppressed enumeration (multi-tenant enterprise), not
    * that no cycles exist -- so nothing can be validated against it and any value must pass.
    */
   @Test
   void suppressedCycleListDoesNotBlockADefaultCycle() throws Exception {
      when(access.read(eq(GeneralSubModel.MV), any())).thenReturn(mv("", List.of()));

      assertEquals(1, service.resolve(
         request("t", change("mv", spec().put("defaultCycle", "anything"))), PRINCIPAL)
         .changes().size());
   }

   // ---------------------------------------------------------------- cache readback

   /**
    * The false-mismatch guard. A path under sree.home reads back with the literal $(sree.home)
    * prefix, so the plan must project that, not the absolute path it was handed -- otherwise apply
    * rolls a correct write back over as a failed verification.
    */
   @Test
   void cacheDirectoryProjectionMatchesWhatAReadWouldReturn() throws Exception {
      when(access.read(eq(GeneralSubModel.CACHE), any())).thenReturn(cache("$(sree.home)/cache"));

      ResolvedPlan plan = service.resolve(
         request("t", change("cache", spec().put("directory", "/opt/sree/newcache"))), PRINCIPAL);

      assertTrue(plan.changes().get(0).proposedValue().contains("$(sree.home)/newcache"),
                 plan.changes().get(0).proposedValue());
   }

   @Test
   void cacheDirectoryOutsideSreeHomeIsProjectedVerbatim() throws Exception {
      when(access.read(eq(GeneralSubModel.CACHE), any())).thenReturn(cache("$(sree.home)/cache"));

      ResolvedPlan plan = service.resolve(
         request("t", change("cache", spec().put("directory", "/var/cache/stylebi"))), PRINCIPAL);

      assertTrue(plan.changes().get(0).proposedValue().contains("/var/cache/stylebi"),
                 plan.changes().get(0).proposedValue());
   }

   /**
    * getModel tests with contains() but substrings from index zero, so a path that merely mentions
    * sree.home mid-string loses its own leading characters. The simulation must reproduce that,
    * not improve on it -- a "better" simulation would resurrect the false-mismatch bug for exactly
    * the paths the product mishandles.
    */
   @Test
   void cacheReadbackReproducesTheProductsContainsBug() throws Exception {
      when(access.read(eq(GeneralSubModel.CACHE), any())).thenReturn(cache("$(sree.home)/cache"));

      ResolvedPlan plan = service.resolve(
         request("t", change("cache", spec().put("directory", "/mnt/opt/sree/cache"))), PRINCIPAL);

      // "/mnt/opt/sree/cache".substring("/opt/sree".length()) == "sree/cache" -- the leading
      // characters of the caller's own path are eaten, which is precisely the product behaviour
      // being reproduced rather than corrected.
      assertTrue(plan.changes().get(0).proposedValue().contains("$(sree.home)sree/cache"),
                 plan.changes().get(0).proposedValue());
   }

   // ---------------------------------------------------------------- hash

   @Test
   void hashIgnoresTheTaskDescription() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any())).thenReturn(performance(60));

      String first = service.resolve(
         request("raise the timeout", change("performance", spec().put("queryTimeout", 120))),
         PRINCIPAL).planHash();
      String second = service.resolve(
         request("bump query timeout to 120", change("performance", spec().put("queryTimeout", 120))),
         PRINCIPAL).planHash();

      assertEquals(first, second);
   }

   @Test
   void hashChangesWhenTheCurrentValueDrifts() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any())).thenReturn(performance(60));
      String first = service.resolve(
         request("t", change("performance", spec().put("queryTimeout", 120))), PRINCIPAL).planHash();

      when(access.read(eq(GeneralSubModel.PERFORMANCE), any())).thenReturn(performance(90));
      String second = service.resolve(
         request("t", change("performance", spec().put("queryTimeout", 120))), PRINCIPAL).planHash();

      assertNotEquals(first, second);
   }

   // ---------------------------------------------------------------- performance ranges

   /** QueryCacheSettings clamps to [0, MAX] on read, so a negative would be stored, read back as
    * 0, and reported as a failed write rather than applied. */
   @Test
   void negativeCacheSizeIsRefusedRatherThanSilentlyClamped() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any())).thenReturn(performance(60));
      GeneralChangePlanRequest req =
         request("t", change("performance", spec().put("dataCacheSize", -1)));
      IllegalArgumentException e =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL));

      assertTrue(e.getMessage().contains("dataCacheSize"), e.getMessage());
      assertTrue(e.getMessage().contains("negative"), e.getMessage());
   }

   @Test
   void negativeCacheTimeoutIsRefused() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any())).thenReturn(performance(60));
      GeneralChangePlanRequest req =
         request("t", change("performance", spec().put("dataCacheTimeout", -5)));

      assertTrue(assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL))
                    .getMessage().contains("dataCacheTimeout"));
   }

   @Test
   void cacheSizeAboveIntMaxIsRefused() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any())).thenReturn(performance(60));
      GeneralChangePlanRequest req = request(
         "t", change("performance", spec().put("dataCacheSize", (long) Integer.MAX_VALUE + 1)));

      assertTrue(assertThrows(IllegalArgumentException.class, () -> service.resolve(req, PRINCIPAL))
                    .getMessage().contains("dataCacheSize"));
   }

   @Test
   void zeroAndInRangeCacheValuesAreAccepted() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any())).thenReturn(performance(60));
      ObjectNode spec = spec();
      spec.put("dataCacheSize", 0);
      spec.put("dataCacheTimeout", 120);

      assertEquals(1, service.resolve(request("t", change("performance", spec)), PRINCIPAL)
         .changes().size());
   }

   @Test
   void planIsAlwaysSignedOffAndBackedUp() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any())).thenReturn(performance(60));
      ResolvedPlan plan = service.resolve(
         request("t", change("performance", spec().put("queryTimeout", 120))), PRINCIPAL);

      assertTrue(plan.requiresStorageBackup());
      assertTrue(plan.requiresAgentSignoff());
      assertNotNull(plan.taskToken());
      assertNull(plan.changes().get(0).orgId(), "general settings changes carry no organization");
      assertEquals("performance", plan.changes().get(0).property());
   }
}
