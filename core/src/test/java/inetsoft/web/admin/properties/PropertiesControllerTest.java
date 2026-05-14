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
package inetsoft.web.admin.properties;

/*
 * Test strategy
 *
 * PropertiesController has three main behaviors with in-controller logic:
 *
 * --- getProperties / getDefaultProperties ---
 *   Both methods filter the returned Properties when not enterprise:
 *     [enterprise]     LicenseManager.isEnterprise() == true → properties returned as-is
 *     [non-enterprise] LicenseManager.isEnterprise() == false → fluentd and other
 *                      enterprise-specific keys removed before returning
 *
 * --- deleteProperty ---
 *     [normal property]             SreeEnv.remove() and SreeEnv.save() called
 *     [security.exposedefaultorgtoall] additionally fires assetRepository event
 *
 * --- editProperty ---
 *     [non-empty value]             SreeEnv.setProperty(name, trimmedValue) and save() called
 *     [empty value]                 reads existing value from SreeEnv; stores it back
 *                                   (keeps current value rather than overwriting with empty string)
 *     [security.exposedefaultorgtoall] additionally fires assetRepository event
 */

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.log.LogManager;
import inetsoft.web.admin.security.PropertyModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class PropertiesControllerTest {

   @Mock private AssetRepository assetRepository;
   @Mock private LogManager logManager;
   @Mock private SecurityEngine securityEngine;
   @Mock private Principal principal;

   private PropertiesController controller;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<LicenseManager> licenseManagerStatic;

   @BeforeEach
   void setUp() {
      controller = new PropertiesController(assetRepository, logManager, securityEngine);

      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
      licenseManagerStatic = mockStatic(LicenseManager.class, withSettings().lenient());
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
      licenseManagerStatic.close();
   }

   // -------------------------------------------------------------------------
   // getProperties
   // -------------------------------------------------------------------------

   // [enterprise] properties returned with no keys removed
   @Test
   void getProperties_enterprise_returnsUnfilteredProperties() {
      licenseManagerStatic.when(LicenseManager::isEnterprise).thenReturn(true);
      Properties props = new Properties();
      props.setProperty("log.fluentd.host", "logs.example.com");
      props.setProperty("app.name", "StyleBI");
      sreeEnvStatic.when(SreeEnv::getProperties).thenReturn(props);

      Properties result = controller.getProperties();

      assertTrue(result.containsKey("log.fluentd.host"));
      assertTrue(result.containsKey("app.name"));
   }

   // [non-enterprise] fluentd and other enterprise keys removed
   @Test
   void getProperties_nonEnterprise_removesEnterpriseOnlyKeys() {
      licenseManagerStatic.when(LicenseManager::isEnterprise).thenReturn(false);
      Properties props = new Properties();
      props.setProperty("log.fluentd.host", "logs.example.com");
      props.setProperty("log.fluentd.port", "24224");
      props.setProperty("log.level.inetsoft_audit", "INFO");
      props.setProperty("app.name", "StyleBI");
      sreeEnvStatic.when(SreeEnv::getProperties).thenReturn(props);

      Properties result = controller.getProperties();

      assertFalse(result.containsKey("log.fluentd.host"));
      assertFalse(result.containsKey("log.fluentd.port"));
      assertFalse(result.containsKey("log.level.inetsoft_audit"));
      assertTrue(result.containsKey("app.name"));
   }

   // [non-enterprise] getDefaultProperties applies same filter
   @Test
   void getDefaultProperties_nonEnterprise_removesEnterpriseOnlyKeys() {
      licenseManagerStatic.when(LicenseManager::isEnterprise).thenReturn(false);
      Properties props = new Properties();
      props.setProperty("log.fluentd.host", "logs.example.com");
      props.setProperty("cache.size", "512");
      sreeEnvStatic.when(SreeEnv::getDefaultProperties).thenReturn(props);

      Properties result = controller.getDefaultProperties();

      assertFalse(result.containsKey("log.fluentd.host"));
      assertTrue(result.containsKey("cache.size"));
   }

   // -------------------------------------------------------------------------
   // deleteProperty
   // -------------------------------------------------------------------------

   // [normal property] delegates remove and save to SreeEnv
   @Test
   void deleteProperty_normalProperty_removesAndSaves() throws Exception {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("some.property")).thenReturn(null);

      controller.deleteProperty(principal, "some.property");

      sreeEnvStatic.verify(() -> SreeEnv.remove("some.property"));
      sreeEnvStatic.verify(SreeEnv::save);
   }

   // [security.exposedefaultorgtoall] fires repository event after removal
   @Test
   void deleteProperty_exposeDefaultOrgProperty_firesRepositoryEvent() throws Exception {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("security.exposedefaultorgtoall"))
         .thenReturn(null);

      controller.deleteProperty(principal, "security.exposedefaultorgtoall");

      verify(assetRepository).fireExposeDefaultOrgPropertyChange();
   }

   // -------------------------------------------------------------------------
   // editProperty
   // -------------------------------------------------------------------------

   // [non-empty value] stores trimmed value and saves
   @Test
   void editProperty_nonEmptyValue_setsPropertyAndSaves() throws Exception {
      PropertyModel property = PropertyModel.builder()
         .name("my.setting")
         .value("  hello world  ")
         .build();

      controller.editProperty(principal, property);

      sreeEnvStatic.verify(() -> SreeEnv.setProperty("my.setting", "hello world"));
      sreeEnvStatic.verify(SreeEnv::save);
   }

   // [empty value] reads existing value from SreeEnv; stores it back unchanged
   @Test
   void editProperty_emptyValue_preservesExistingValue() throws Exception {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("my.setting")).thenReturn("existing-value");

      PropertyModel property = PropertyModel.builder()
         .name("my.setting")
         .value("")
         .build();

      controller.editProperty(principal, property);

      sreeEnvStatic.verify(() -> SreeEnv.setProperty("my.setting", "existing-value"));
   }

   // [empty value, no existing] no existing value → stores empty string
   @Test
   void editProperty_emptyValue_noExisting_storesEmptyString() throws Exception {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("new.setting")).thenReturn(null);

      PropertyModel property = PropertyModel.builder()
         .name("new.setting")
         .value("")
         .build();

      controller.editProperty(principal, property);

      sreeEnvStatic.verify(() -> SreeEnv.setProperty("new.setting", ""));
   }

   // [security.exposedefaultorgtoall] fires repository event after set
   @Test
   void editProperty_exposeDefaultOrgProperty_firesRepositoryEvent() throws Exception {
      PropertyModel property = PropertyModel.builder()
         .name("security.exposedefaultorgtoall")
         .value("true")
         .build();

      controller.editProperty(principal, property);

      verify(assetRepository).fireExposeDefaultOrgPropertyChange();
   }
}
