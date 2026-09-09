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
package inetsoft.web.admin.security;

/*
 * Test plan 2026-09-03, scenarios 9/11, updated 2026-09-08 for bug #76526:
 * SSOSettingsService.updateSSOSettings() -- previously entirely zero-covered
 * (SSOSettingsControllerTest only exercises the controller's delegation to this service, never
 * the service's own business logic).
 *
 * Scenario 9 (bug #76526): the SAML branch validated settings BEFORE switching ("so we don't get
 * locked out", see validateSAMLAttributes()'s own comment) -- the OpenID and Custom branches did
 * not, and would switch unconditionally even when the submitted attributes were entirely blank,
 * which permanently locks every user (including admin) out of the site since once SSO is active
 * StandardFilterChain bypasses all local authentication with no fallback unless double.sso=true.
 * validateOpenIdAttributes()/validateCustomAttributes() close that gap, mirroring
 * validateSAMLAttributes()'s existing pattern: validate first, only persist/switch on success,
 * return false (instead of throwing) on failure -- same contract SAML already used. The two tests
 * below that used to pin the buggy "succeeds without any validation" behavior as correct now
 * assert the fixed, rejecting behavior instead.
 *
 * Scenario 11: when SreeEnv.save() throws (e.g. disk write failure), the catch block downgrades
 * the local filter to NONE but re-sets "sso.protocol.type" to the NEW (just-failed-to-persist)
 * type rather than the previous one -- current, not necessarily intended, behavior. Updated to use
 * a fully valid OpenID config so it still reaches the save() call now that blank attributes are
 * rejected before that point.
 *
 * validateSAMLAttributes() itself is NOT mocked -- it calls the real com.onelogin.saml2
 * SettingsBuilder/Saml2Settings.checkSettings(), the same library production code uses, so the
 * "blank SAML fields fail validation" assertion below is a real library behavior, not an assumed
 * one. Tool.isCloudSecrets() is stubbed with mockStatic(..., CALLS_REAL_METHODS) so the OpenID
 * tests are deterministic regardless of the ambient InetsoftConfig, while Tool.isEmptyString()
 * and other Tool statics still run their real implementation.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class SSOSettingsServiceTest {
   @Mock
   private SecurityEngine engine;
   @Mock
   private OpenIDConfig openIDConfig;
   @Mock
   private CustomSSOConfig customConfig;
   @Mock
   private SSOFilterPublisher publisher;
   @Mock
   private Cluster cluster;

   private SSOSettingsService service;
   private MockedStatic<SreeEnv> sreeEnvMock;
   private MockedStatic<SUtil> sUtilMock;

   @BeforeEach
   void setUp() {
      service = new SSOSettingsService(engine, openIDConfig, customConfig, publisher, cluster);

      sreeEnvMock = mockStatic(SreeEnv.class);
      lenient().when(SreeEnv.getProperty("sso.protocol.type")).thenReturn(null); // getActiveFilterType() -> NONE

      sUtilMock = mockStatic(SUtil.class);
      lenient().when(SUtil.isMultiTenant()).thenReturn(false);
   }

   @AfterEach
   void tearDown() {
      sreeEnvMock.close();
      sUtilMock.close();
   }

   // ── scenario 9: protocol-switch validation, now symmetric across SAML/OpenID/Custom ─────

   @Test
   void switchToOpenId_blankAttributes_validationFailsAndSwitchIsAborted() {
      SSOSettingsModel model = new SSOSettingsModel.Builder()
         .activeFilterType(SSOType.OPENID)
         .openIdAttributesModel(new OpenIdAttributesModel.Builder().build()) // every field blank
         .build();

      // the OPENID branch calls Tool.isCloudSecrets() (-> InetsoftConfig.getInstance()) to decide
      // between the secretId and clientId/clientSecret fields -- pinned explicitly to the LOCAL
      // (non-cloud-secrets) branch so this test doesn't depend on the ambient InetsoftConfig
      // default. CALLS_REAL_METHODS keeps Tool.isEmptyString() (used by the new validator)
      // running its real implementation.
      try(MockedStatic<Tool> toolMock = mockStatic(Tool.class, CALLS_REAL_METHODS)) {
         toolMock.when(Tool::isCloudSecrets).thenReturn(false);

         boolean result = service.updateSSOSettings(model);

         assertFalse(result);
         verify(publisher, never()).changeSSOFilterType(SSOType.OPENID);
         sreeEnvMock.verify(() -> SreeEnv.setProperty(eq("openid.issuer"), anyString()), never());
         sreeEnvMock.verify(() -> SreeEnv.setProperty(eq("sso.protocol.type"), anyString()),
            never());
      }
   }

   @Test
   void switchToCustom_blankAttributes_validationFailsAndSwitchIsAborted() {
      SSOSettingsModel model = new SSOSettingsModel.Builder()
         .activeFilterType(SSOType.CUSTOM)
         .customAttributesModel(CustomSSOAttributesModel.builder().build()) // no class/groovy set
         .build();

      boolean result = service.updateSSOSettings(model);

      assertFalse(result);
      verify(publisher, never()).changeSSOFilterType(SSOType.CUSTOM);
      verify(customConfig, never()).setClassName(anyString());
      verify(customConfig, never()).setInlineGroovyClass(anyString());
   }

   @Test
   void switchToSaml_missingRequiredFields_validationFailsAndSwitchIsAborted() {
      // positive control: proves the SAML branch's pre-switch validation actually rejects an
      // invalid configuration, same contract the OpenID/Custom tests above now assert.
      SSOSettingsModel model = new SSOSettingsModel.Builder()
         .activeFilterType(SSOType.SAML)
         .samlAttributesModel(new SAMLAttributesModel.Builder().build()) // every field blank
         .build();

      boolean result = service.updateSSOSettings(model);

      assertFalse(result);
      verify(publisher, never()).changeSSOFilterType(SSOType.SAML);
      sreeEnvMock.verify(() -> SreeEnv.setProperty(eq("onelogin.saml2.sp.entityid"), anyString()),
         never());
   }

   // ── bug #76526 positive paths: a fully-specified config still switches successfully ─────

   @Test
   void switchToOpenId_validAttributes_succeeds() {
      SSOSettingsModel model = new SSOSettingsModel.Builder()
         .activeFilterType(SSOType.OPENID)
         .openIdAttributesModel(new OpenIdAttributesModel.Builder()
            .issuer("https://idp.example.com")
            .authorizationEndpoint("https://idp.example.com/authorize")
            .tokenEndpoint("https://idp.example.com/token")
            .clientId("client-123")
            .build())
         .build();

      try(MockedStatic<Tool> toolMock = mockStatic(Tool.class, CALLS_REAL_METHODS)) {
         toolMock.when(Tool::isCloudSecrets).thenReturn(false);

         boolean result = service.updateSSOSettings(model);

         assertTrue(result);
         verify(publisher).changeSSOFilterType(SSOType.OPENID);
      }
   }

   @Test
   void switchToOpenId_cloudSecrets_requiresSecretIdInsteadOfClientId() {
      SSOSettingsModel blankSecret = new SSOSettingsModel.Builder()
         .activeFilterType(SSOType.OPENID)
         .openIdAttributesModel(new OpenIdAttributesModel.Builder()
            .issuer("https://idp.example.com")
            .authorizationEndpoint("https://idp.example.com/authorize")
            .tokenEndpoint("https://idp.example.com/token")
            .clientId("client-123") // irrelevant in cloud-secrets mode
            .build())
         .build();

      try(MockedStatic<Tool> toolMock = mockStatic(Tool.class, CALLS_REAL_METHODS)) {
         toolMock.when(Tool::isCloudSecrets).thenReturn(true);

         assertFalse(service.updateSSOSettings(blankSecret));
         verify(publisher, never()).changeSSOFilterType(SSOType.OPENID);
      }

      SSOSettingsModel withSecret = new SSOSettingsModel.Builder()
         .activeFilterType(SSOType.OPENID)
         .openIdAttributesModel(new OpenIdAttributesModel.Builder()
            .issuer("https://idp.example.com")
            .authorizationEndpoint("https://idp.example.com/authorize")
            .tokenEndpoint("https://idp.example.com/token")
            .secretId("secret-123")
            .build())
         .build();

      try(MockedStatic<Tool> toolMock = mockStatic(Tool.class, CALLS_REAL_METHODS)) {
         toolMock.when(Tool::isCloudSecrets).thenReturn(true);

         assertTrue(service.updateSSOSettings(withSecret));
         verify(publisher).changeSSOFilterType(SSOType.OPENID);
      }
   }

   @Test
   void switchToOpenId_missingSingleRequiredField_validationFails() {
      try(MockedStatic<Tool> toolMock = mockStatic(Tool.class, CALLS_REAL_METHODS)) {
         toolMock.when(Tool::isCloudSecrets).thenReturn(false);

         assertFalse(service.updateSSOSettings(openIdModelMissing(
            b -> b.tokenEndpoint("https://idp.example.com/token")
                  .clientId("client-123")))); // missing authorizationEndpoint

         assertFalse(service.updateSSOSettings(openIdModelMissing(
            b -> b.authorizationEndpoint("https://idp.example.com/authorize")
                  .clientId("client-123")))); // missing tokenEndpoint

         assertFalse(service.updateSSOSettings(openIdModelMissing(
            b -> b.authorizationEndpoint("https://idp.example.com/authorize")
                  .tokenEndpoint("https://idp.example.com/token")))); // missing clientId

         verify(publisher, never()).changeSSOFilterType(SSOType.OPENID);
      }
   }

   @Test
   void switchToOpenId_blankIssuer_stillSucceeds_becauseIssuerIsOptional() {
      // issuer has no manual EM input (only OIDC Discovery populates it), and
      // OpenIDFilterBaseFilter treats it as optional at runtime -- requiring it here would break
      // legitimate manually-configured (non-Discovery) OpenID setups.
      try(MockedStatic<Tool> toolMock = mockStatic(Tool.class, CALLS_REAL_METHODS)) {
         toolMock.when(Tool::isCloudSecrets).thenReturn(false);

         boolean result = service.updateSSOSettings(openIdModelMissing(
            b -> b.authorizationEndpoint("https://idp.example.com/authorize")
                  .tokenEndpoint("https://idp.example.com/token")
                  .clientId("client-123"))); // issuer left blank

         assertTrue(result);
         verify(publisher).changeSSOFilterType(SSOType.OPENID);
      }
   }

   private SSOSettingsModel openIdModelMissing(
      java.util.function.UnaryOperator<OpenIdAttributesModel.Builder> customizer)
   {
      OpenIdAttributesModel attrs = customizer.apply(new OpenIdAttributesModel.Builder()).build();
      return new SSOSettingsModel.Builder()
         .activeFilterType(SSOType.OPENID)
         .openIdAttributesModel(attrs)
         .build();
   }

   @Test
   void switchToCustom_validJavaClass_succeeds() {
      SSOSettingsModel model = new SSOSettingsModel.Builder()
         .activeFilterType(SSOType.CUSTOM)
         .customAttributesModel(CustomSSOAttributesModel.builder()
            .useJavaClass(true)
            .javaClassName("com.example.MyCustomSSOFilter")
            .build())
         .build();

      boolean result = service.updateSSOSettings(model);

      assertTrue(result);
      verify(publisher).changeSSOFilterType(SSOType.CUSTOM);
      verify(customConfig).setClassName("com.example.MyCustomSSOFilter");
   }

   @Test
   void switchToCustom_validInlineGroovy_succeeds() {
      SSOSettingsModel model = new SSOSettingsModel.Builder()
         .activeFilterType(SSOType.CUSTOM)
         .customAttributesModel(CustomSSOAttributesModel.builder()
            .useInlineGroovy(true)
            .inlineGroovyClass("class Foo {}")
            .build())
         .build();

      boolean result = service.updateSSOSettings(model);

      assertTrue(result);
      verify(publisher).changeSSOFilterType(SSOType.CUSTOM);
      verify(customConfig).setInlineGroovyClass("class Foo {}");
   }

   @Test
   void switchToCustom_javaClassFlagSetButNameBlank_validationFails() {
      SSOSettingsModel model = new SSOSettingsModel.Builder()
         .activeFilterType(SSOType.CUSTOM)
         .customAttributesModel(CustomSSOAttributesModel.builder()
            .useJavaClass(true) // flag set, but no class name provided
            .build())
         .build();

      assertFalse(service.updateSSOSettings(model));
      verify(publisher, never()).changeSSOFilterType(SSOType.CUSTOM);
   }

   @Test
   void switchToCustom_inlineGroovyFlagSetButSourceBlank_validationFails() {
      SSOSettingsModel model = new SSOSettingsModel.Builder()
         .activeFilterType(SSOType.CUSTOM)
         .customAttributesModel(CustomSSOAttributesModel.builder()
            .useInlineGroovy(true) // flag set, but no Groovy source provided
            .build())
         .build();

      assertFalse(service.updateSSOSettings(model));
      verify(publisher, never()).changeSSOFilterType(SSOType.CUSTOM);
   }

   // ── scenario 11: SreeEnv.save() failure leaves an inconsistent tri-state ────────────────

   @Test
   void saveFailure_downgradesLocalFilterToNone_butRePersistsTheNewTypeNotThePrevious()
      throws IOException
   {
      // must be a fully valid config -- since bug #76526's fix, a blank config never reaches
      // this save() call at all (it is rejected by validateOpenIdAttributes() first).
      SSOSettingsModel model = new SSOSettingsModel.Builder()
         .activeFilterType(SSOType.OPENID)
         .openIdAttributesModel(new OpenIdAttributesModel.Builder()
            .issuer("https://idp.example.com")
            .authorizationEndpoint("https://idp.example.com/authorize")
            .tokenEndpoint("https://idp.example.com/token")
            .clientId("client-123")
            .build())
         .build();
      sreeEnvMock.when(SreeEnv::save).thenThrow(new IOException("disk full"));

      try(MockedStatic<Tool> toolMock = mockStatic(Tool.class, CALLS_REAL_METHODS)) {
         toolMock.when(Tool::isCloudSecrets).thenReturn(false);

         service.updateSSOSettings(model);
      }

      // the local filter is switched to OpenID once (the normal path), then downgraded to NONE
      // in the catch block once save() fails -- both calls happen, in that order.
      verify(publisher).changeSSOFilterType(SSOType.OPENID);
      verify(publisher).changeSSOFilterType(SSOType.NONE);
      // "sso.protocol.type" is re-set to "OpenID" (the type that just failed to persist) in the
      // catch block, not rolled back to the previous ("None") value -- current behavior.
      sreeEnvMock.verify(() -> SreeEnv.setProperty("sso.protocol.type", "OpenID"), times(2));
   }
}
