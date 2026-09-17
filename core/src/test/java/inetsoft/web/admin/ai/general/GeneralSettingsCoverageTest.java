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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;
import inetsoft.web.admin.general.model.*;
import inetsoft.web.admin.general.model.model.SMTPAuthType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pins the coverage claim this area exists to satisfy: every field of every "Settings &gt; General"
 * sub-model is reachable, and every field that is NOT writable is refused deliberately and by name
 * rather than being an accidental omission.
 *
 * <p>The gap report this area answers said these five sub-models had "no read, no write". This test
 * is what keeps that answered: it walks each model's own accessors, so a field added to a model
 * later fails here until someone decides -- explicitly -- whether it is settable.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class GeneralSettingsCoverageTest {
   @Mock
   private GeneralSettingsAccess access;
   private GeneralChangePlanService service;
   private MockedStatic<Tool> tool;
   private MockedStatic<SreeEnv> sreeEnv;

   private static final Principal PRINCIPAL = () -> "admin";
   private static final ObjectMapper MAPPER = new ObjectMapper();

   /**
    * Fields the area deliberately does not write, mapped to why. Anything here must be refused;
    * anything not here must be settable. Both directions are asserted.
    */
   private static final Map<GeneralSubModel, Set<String>> EXPECTED_REFUSED = Map.of(
      GeneralSubModel.LOCALIZATION, Set.of(),
      GeneralSubModel.CACHE, Set.of(),
      GeneralSubModel.PERFORMANCE, Set.of(),
      // cycles is derived from the deployment's data cycles; defaultCycle selects among them.
      GeneralSubModel.MV, Set.of("cycles"),
      // Four encrypted credentials (withheld on read too), the cloud-secrets reference, and two
      // display flags EmailSettingsService never persists.
      GeneralSubModel.EMAIL, Set.of("smtpPassword", "smtpClientSecret", "smtpAccessToken",
                                    "smtpRefreshToken", "smtpSecretId", "secretIdVisible",
                                    "fromAddressEnabled"));

   @BeforeEach
   void setUp() {
      service = new GeneralChangePlanService(access);
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(any())).thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      sreeEnv = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      sreeEnv.when(() -> SreeEnv.getProperty("sree.home")).thenReturn("/opt/sree");
   }

   @AfterEach
   void tearDown() {
      tool.close();
      sreeEnv.close();
   }

   // ---------------------------------------------------------------- the five reported gaps

   /**
    * Gap 1-5 of the report, as one assertion: each named sub-model is in the catalog, has a model
    * class, and exposes the fields the report enumerated.
    */
   @Test
   void everyReportedGapSubModelIsCatalogued() {
      assertEquals(Set.of("localization", "mv", "cache", "email", "performance", "dataSpace"),
                   Arrays.stream(GeneralSubModel.values()).map(GeneralSubModel::key)
                      .collect(java.util.stream.Collectors.toSet()));

      // The exact fields the gap report named for each sub-model.
      assertTrue(GeneralSubModel.LOCALIZATION.fieldNames().contains("locales"));
      assertTrue(GeneralSubModel.MV.fieldNames().containsAll(
         Set.of("onDemand", "onDemandDefault", "metadata", "required", "cycles")));
      assertTrue(GeneralSubModel.CACHE.fieldNames().containsAll(
         Set.of("directory", "cleanUpStartup")));
      assertTrue(GeneralSubModel.EMAIL.fieldNames().containsAll(
         Set.of("smtpHost", "ssl", "tls", "jndiUrl", "smtpUser", "smtpPassword", "smtpClientId",
                "smtpClientSecret", "smtpAuthUri", "smtpTokenUri", "smtpOAuthScopes",
                "smtpOAuthFlags", "smtpAccessToken")));
      assertTrue(GeneralSubModel.PERFORMANCE.fieldNames().containsAll(
         Set.of("queryTimeout", "queryPreviewTimeout", "maxQueryRowCount",
                "maxQueryPreviewRowCount", "maxTableRowCount", "dataSetCaching", "dataCacheSize",
                "dataCacheTimeout")));
      // The half-covered storage gap: readable, and honestly read-only.
      assertTrue(GeneralSubModel.DATA_SPACE.fieldNames().containsAll(
         Set.of("keyValueType", "blobType", "assetBackupTaskName")));
      assertFalse(GeneralSubModel.DATA_SPACE.writable());
   }

   /**
    * Every field of every writable sub-model is either settable or deliberately refused -- no
    * field falls through unclassified. A model gaining a field breaks this until someone decides.
    */
   @Test
   void everyFieldIsEitherSettableOrDeliberatelyRefused() throws Exception {
      for(GeneralSubModel subModel : GeneralSubModel.values()) {
         if(!subModel.writable()) {
            continue;
         }

         Set<String> refused = EXPECTED_REFUSED.get(subModel);
         assertNotNull(refused, subModel.key() + " has no declared refusal set");

         for(String field : subModel.fieldNames()) {
            boolean accepted = resolves(subModel, field);

            if(refused.contains(field)) {
               assertFalse(accepted,
                           subModel.key() + "." + field + " is declared non-settable but a write " +
                           "of it was accepted");
            }
            else {
               assertTrue(accepted,
                          subModel.key() + "." + field + " is neither settable nor in the " +
                          "declared refusal set -- decide which it is and say so explicitly");
            }
         }
      }
   }

   /** The read side of the gap: every sub-model, including the read-only one, is readable. */
   @Test
   void everySubModelIsReadable() throws Exception {
      for(GeneralSubModel subModel : GeneralSubModel.values()) {
         stubRead(subModel);
         assertNotNull(GeneralJson.toNode(access.read(subModel, PRINCIPAL)),
                       subModel.key() + " is not readable");
      }
   }

   /** Reachable for reading, but withheld -- the read closes the gap without leaking the value. */
   @Test
   void emailCredentialsAreReadableAsMaskedNotAbsent() {
      EmailSettingsModel model = EmailSettingsModel.builder()
         .fromAddress("a@b.c").smtpAuthentication(SMTPAuthType.SMTP_AUTH).ssl(false).tls(false)
         .smtpPassword("hunter2").build();
      JsonNode masked = GeneralJson.maskSecrets(GeneralSubModel.EMAIL, GeneralJson.toNode(model));

      assertTrue(masked.has("smtpPassword"), "the field must still be present, not dropped");
      assertEquals("********", masked.get("smtpPassword").asText());
   }

   // ---------------------------------------------------------------- helpers

   /** True when a single-field write of {@code field} resolves into a plan. */
   private boolean resolves(GeneralSubModel subModel, String field) throws Exception {
      stubRead(subModel);
      ObjectNode spec = MAPPER.createObjectNode();
      spec.set(field, sampleValue(subModel, field));

      GeneralChangeRequest raw = new GeneralChangeRequest();
      raw.setVerb("update");
      raw.setSubModel(subModel.key());
      raw.setSpec(spec);

      GeneralChangePlanRequest req = new GeneralChangePlanRequest();
      req.setTask("coverage probe");
      req.setChanges(List.of(raw));

      try {
         service.resolve(req, PRINCIPAL);
         return true;
      }
      catch(IllegalArgumentException e) {
         return false;
      }
   }

   /** A type-appropriate JSON value for one model accessor, derived from its return type. */
   private static JsonNode sampleValue(GeneralSubModel subModel, String field) throws Exception {
      Method method = subModel.modelClass().getMethod(field);
      Class<?> type = method.getReturnType();

      if(type == boolean.class || type == Boolean.class) {
         return MAPPER.getNodeFactory().booleanNode(true);
      }

      if(type == long.class || type == Long.class || type == int.class || type == Integer.class) {
         return MAPPER.getNodeFactory().numberNode(7);
      }

      if(type == SMTPAuthType.class) {
         // The auth type in force for the probe, so conditional fields stay writable.
         return MAPPER.getNodeFactory().textNode(SMTPAuthType.SASL_XOAUTH2.name());
      }

      if(List.class.isAssignableFrom(type)) {
         if(subModel == GeneralSubModel.LOCALIZATION) {
            ObjectNode locale = MAPPER.createObjectNode();
            locale.put("language", "en");
            locale.put("country", "US");
            locale.put("label", "en_US");
            return MAPPER.createArrayNode().add(locale);
         }

         return MAPPER.createArrayNode().add("sample");
      }

      return MAPPER.getNodeFactory().textNode("sample");
   }

   private void stubRead(GeneralSubModel subModel) throws Exception {
      lenient().when(access.read(eq(subModel), any())).thenReturn(current(subModel));
   }

   private static Object current(GeneralSubModel subModel) {
      return switch(subModel) {
         case LOCALIZATION -> LocalizationSettingsModel.builder().build();
         case MV -> MVSettingsModel.builder()
            .onDemand(false).onDemandDefault(false).metadata(false).required(false).build();
         case CACHE -> CacheSettingsModel.builder()
            .directory("$(sree.home)/cache").cleanUpStartup(false).build();
         // SASL_XOAUTH2 so the conditional OAuth fields are writable during the probe; cloud
         // secrets are off here, so the clobber guard does not fire.
         case EMAIL -> EmailSettingsModel.builder()
            .fromAddress("a@b.c").smtpAuthentication(SMTPAuthType.SASL_XOAUTH2)
            .ssl(false).tls(false).build();
         case PERFORMANCE -> PerformanceSettingsModel.builder()
            .queryTimeout(60).queryPreviewTimeout(30).maxQueryRowCount(1000)
            .maxQueryPreviewRowCount(100).maxTableRowCount(500).dataSetCaching(false)
            .dataCacheSize(10).dataCacheTimeout(60).build();
         case DATA_SPACE -> DataSpaceSettingsModel.builder()
            .keyValueType("mapdb").blobType("local").assetBackupTaskName("").build();
      };
   }
}
