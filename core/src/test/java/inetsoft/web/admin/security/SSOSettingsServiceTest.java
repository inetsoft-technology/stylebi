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
 * Test plan 2026-09-03, scenarios 9/11: SSOSettingsService.updateSSOSettings() -- previously
 * entirely zero-covered (SSOSettingsControllerTest only exercises the controller's delegation to
 * this service, never the service's own business logic).
 *
 * Scenario 9: the SAML branch validates settings BEFORE switching ("so we don't get locked out",
 * see validateSAMLAttributes()'s own comment) -- the OpenID and Custom branches do not, and switch
 * unconditionally even when the submitted attributes are entirely blank. These tests pin that
 * asymmetry as current behavior, not a claim that it is correct.
 *
 * Scenario 11: when SreeEnv.save() throws (e.g. disk write failure), the catch block downgrades
 * the local filter to NONE but re-sets "sso.protocol.type" to the NEW (just-failed-to-persist)
 * type rather than the previous one -- current, not necessarily intended, behavior.
 *
 * validateSAMLAttributes() itself is NOT mocked -- it calls the real com.onelogin.saml2
 * SettingsBuilder/Saml2Settings.checkSettings(), the same library production code uses, so the
 * "blank SAML fields fail validation" assertion below is a real library behavior, not an assumed
 * one.
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

   // ── scenario 9: protocol-switch validation is asymmetric ────────────────────────────────

   @Test
   void switchToOpenId_blankAttributes_succeedsWithoutAnyValidation() {
      SSOSettingsModel model = new SSOSettingsModel.Builder()
         .activeFilterType(SSOType.OPENID)
         .openIdAttributesModel(new OpenIdAttributesModel.Builder().build()) // every field blank
         .build();

      // the OPENID branch calls Tool.isCloudSecrets() (-> InetsoftConfig.getInstance()) to decide
      // between the secretId and clientId/clientSecret fields -- pinned explicitly to the LOCAL
      // (non-cloud-secrets) branch so this test doesn't depend on the ambient InetsoftConfig
      // default; either branch is a no-op for the assertion below, but only one is exercised.
      try(MockedStatic<Tool> toolMock = mockStatic(Tool.class)) {
         toolMock.when(Tool::isCloudSecrets).thenReturn(false);

         service.updateSSOSettings(model);

         verify(publisher).changeSSOFilterType(SSOType.OPENID);
      }
   }

   @Test
   void switchToCustom_blankAttributes_succeedsWithoutAnyValidation() {
      SSOSettingsModel model = new SSOSettingsModel.Builder()
         .activeFilterType(SSOType.CUSTOM)
         .customAttributesModel(CustomSSOAttributesModel.builder().build()) // no class/groovy set
         .build();

      service.updateSSOSettings(model);

      verify(publisher).changeSSOFilterType(SSOType.CUSTOM);
   }

   @Test
   void switchToSaml_missingRequiredFields_validationFailsAndSwitchIsAborted() {
      // positive control: proves the SAML branch's pre-switch validation actually rejects an
      // invalid configuration, unlike the two tests above.
      SSOSettingsModel model = new SSOSettingsModel.Builder()
         .activeFilterType(SSOType.SAML)
         .samlAttributesModel(new SAMLAttributesModel.Builder().build()) // every field blank
         .build();

      service.updateSSOSettings(model);

      verify(publisher, never()).changeSSOFilterType(SSOType.SAML);
      sreeEnvMock.verify(() -> SreeEnv.setProperty(eq("onelogin.saml2.sp.entityid"), anyString()),
         never());
   }

   // ── scenario 11: SreeEnv.save() failure leaves an inconsistent tri-state ────────────────

   @Test
   void saveFailure_downgradesLocalFilterToNone_butRePersistsTheNewTypeNotThePrevious()
      throws IOException
   {
      SSOSettingsModel model = new SSOSettingsModel.Builder()
         .activeFilterType(SSOType.OPENID)
         .openIdAttributesModel(new OpenIdAttributesModel.Builder().build())
         .build();
      sreeEnvMock.when(SreeEnv::save).thenThrow(new IOException("disk full"));

      service.updateSSOSettings(model);

      // the local filter is switched to OpenID once (the normal path), then downgraded to NONE
      // in the catch block once save() fails -- both calls happen, in that order.
      verify(publisher).changeSSOFilterType(SSOType.OPENID);
      verify(publisher).changeSSOFilterType(SSOType.NONE);
      // "sso.protocol.type" is re-set to "OpenID" (the type that just failed to persist) in the
      // catch block, not rolled back to the previous ("None") value -- current behavior.
      sreeEnvMock.verify(() -> SreeEnv.setProperty("sso.protocol.type", "OpenID"), times(2));
   }
}
