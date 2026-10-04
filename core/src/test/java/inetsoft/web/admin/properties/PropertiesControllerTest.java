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
 *     [log.provider=fluentd, non-enterprise] refused with a MessageException; nothing stored.
 *                      log.provider is deliberately not hidden by removeUnuseProperties -- "file"
 *                      is a valid community value -- so this page was the remaining route by which
 *                      a community build could be pointed at the enterprise-only forwarder, after
 *                      which logging silently continued to the file (Redmine #76045).
 *     [log.provider=fluentd, enterprise]     stored as normal.
 *     [log.provider=file, non-enterprise]    stored as normal; only the fluentd value is refused.
 *     [log.provider blank, non-enterprise]   the guard is checked against the submitted value,
 *                      before the blank-value branch substitutes the stored one, so a build
 *                      already carrying log.provider=fluentd can still submit that field.
 *     [org override of a global-only key]   inetsoft.org.<org>.<key> for a key in
 *                      PropertiesEngine.EXCLUDED_ORG_PROPERTIES is refused with a MessageException
 *                      and nothing is stored, also for a blank value; such an override is never
 *                      read, so storing it silently had no effect (Redmine #77694).
 *     [global-only key itself]              the global name is stored as normal.
 *     [delete of an org override]           still allowed, so an existing one can be cleaned up.
 */

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.MessageException;
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
   @Mock private Principal principal;

   private PropertiesController controller;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<LicenseManager> licenseManagerStatic;

   @BeforeEach
   void setUp() {
      controller = new PropertiesController(assetRepository);

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

   // [non-enterprise] the whole log.fluentd.* family is removed, not just a subset.
   // Regression: previously only 7 of the 12 keys were enumerated, so the shared key, password,
   // username, CA certificate path and log view URL stayed visible on community builds.
   @Test
   void getProperties_nonEnterprise_removesEntireFluentdFamily() {
      licenseManagerStatic.when(LicenseManager::isEnterprise).thenReturn(false);
      // names as stored by SreeEnv, which lower-cases property names when they are set
      String[] fluentdKeys = {
         "log.fluentd.host",
         "log.fluentd.port",
         "log.fluentd.connecttimeout",
         "log.fluentd.securityenabled",
         "log.fluentd.security.sharedkey",
         "log.fluentd.security.userauthenticationenabled",
         "log.fluentd.security.username",
         "log.fluentd.security.password",
         "log.fluentd.tlsenabled",
         "log.fluentd.tls.cacertificatefile",
         "log.fluentd.logviewurl",
         "log.fluentd.orgadminaccess"
      };

      Properties props = new Properties();

      for(String key : fluentdKeys) {
         props.setProperty(key, "value");
      }

      props.setProperty("app.name", "StyleBI");
      sreeEnvStatic.when(SreeEnv::getProperties).thenReturn(props);

      Properties result = controller.getProperties();

      for(String key : fluentdKeys) {
         assertFalse(result.containsKey(key), key + " should be hidden on a community build");
      }

      assertTrue(result.containsKey("app.name"));
   }

   // [non-enterprise] a key added to the family later is hidden without touching the filter
   @Test
   void getProperties_nonEnterprise_removesUnknownFluentdKeyByPrefix() {
      licenseManagerStatic.when(LicenseManager::isEnterprise).thenReturn(false);
      Properties props = new Properties();
      props.setProperty("log.fluentd.somefuturesetting", "value");
      // camel case, as a defaults listing could carry it before normalization
      props.setProperty("log.fluentd.tls.caCertificateFile", "/etc/ca.pem");
      props.setProperty("log.fluentdish.keep", "value");
      sreeEnvStatic.when(SreeEnv::getProperties).thenReturn(props);

      Properties result = controller.getProperties();

      assertFalse(result.containsKey("log.fluentd.somefuturesetting"));
      assertFalse(result.containsKey("log.fluentd.tls.caCertificateFile"));
      // only the log.fluentd. family is filtered; a similarly-named key is untouched
      assertTrue(result.containsKey("log.fluentdish.keep"));
   }

   // [enterprise] the full family is left intact
   @Test
   void getProperties_enterprise_keepsFluentdFamily() {
      licenseManagerStatic.when(LicenseManager::isEnterprise).thenReturn(true);
      Properties props = new Properties();
      props.setProperty("log.fluentd.host", "logs.example.com");
      props.setProperty("log.fluentd.security.sharedkey", "secret");
      props.setProperty("log.fluentd.logviewurl", "https://logs.example.com");
      sreeEnvStatic.when(SreeEnv::getProperties).thenReturn(props);

      Properties result = controller.getProperties();

      assertTrue(result.containsKey("log.fluentd.host"));
      assertTrue(result.containsKey("log.fluentd.security.sharedkey"));
      assertTrue(result.containsKey("log.fluentd.logviewurl"));
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

   // [non-enterprise] the caller's Properties must not be mutated. SreeEnv.getProperties()
   // returns the live internalProperties map, so filtering in place would delete the admin's real
   // fluentd configuration from the running server on a plain read-only GET.
   @Test
   void getProperties_nonEnterprise_doesNotMutateSourceProperties() {
      licenseManagerStatic.when(LicenseManager::isEnterprise).thenReturn(false);
      Properties live = new Properties();
      live.setProperty("log.fluentd.host", "logs.example.com");
      live.setProperty("log.fluentd.security.sharedkey", "secret");
      live.setProperty("log.level.inetsoft_audit", "INFO");
      live.setProperty("app.name", "StyleBI");
      sreeEnvStatic.when(SreeEnv::getProperties).thenReturn(live);

      Properties result = controller.getProperties();

      assertFalse(result.containsKey("log.fluentd.host"));
      assertNotSame(live, result, "must return a filtered copy, not the live map");
      assertEquals("logs.example.com", live.getProperty("log.fluentd.host"),
                   "live server configuration must survive a read-only GET");
      assertEquals("secret", live.getProperty("log.fluentd.security.sharedkey"));
      assertEquals("INFO", live.getProperty("log.level.inetsoft_audit"));
      assertEquals(4, live.size(), "no key may be removed from the caller's map");
   }

   // [non-enterprise] same contract for the defaults listing, which is a JVM-wide cached instance
   @Test
   void getDefaultProperties_nonEnterprise_doesNotMutateSourceProperties() {
      licenseManagerStatic.when(LicenseManager::isEnterprise).thenReturn(false);
      Properties cached = new Properties();
      cached.setProperty("log.fluentd.port", "24224");
      cached.setProperty("cache.size", "512");
      sreeEnvStatic.when(SreeEnv::getDefaultProperties).thenReturn(cached);

      Properties result = controller.getDefaultProperties();

      assertFalse(result.containsKey("log.fluentd.port"));
      assertNotSame(cached, result);
      assertEquals("24224", cached.getProperty("log.fluentd.port"),
                   "the shared defaults cache must not be stripped");
      assertEquals(2, cached.size());
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

   // [log.provider=fluentd, non-enterprise] refused, and nothing is written
   @Test
   void editProperty_fluentdProvider_nonEnterprise_isRefused() {
      licenseManagerStatic.when(LicenseManager::isEnterprise).thenReturn(false);

      PropertyModel property = PropertyModel.builder()
         .name("log.provider")
         .value("fluentd")
         .build();

      MessageException thrown =
         assertThrows(MessageException.class, () -> controller.editProperty(principal, property));

      assertNotNull(thrown.getMessage());
      sreeEnvStatic.verify(() -> SreeEnv.setProperty(anyString(), anyString()), never());
      sreeEnvStatic.verify(SreeEnv::save, never());
   }

   // [log.provider=fluentd, enterprise] the guard must not touch the licensed case
   @Test
   void editProperty_fluentdProvider_enterprise_isStored() throws Exception {
      licenseManagerStatic.when(LicenseManager::isEnterprise).thenReturn(true);

      PropertyModel property = PropertyModel.builder()
         .name("log.provider")
         .value("fluentd")
         .build();

      controller.editProperty(principal, property);

      sreeEnvStatic.verify(() -> SreeEnv.setProperty("log.provider", "fluentd"));
   }

   // [log.provider=file, non-enterprise] only the fluentd value is refused
   @Test
   void editProperty_fileProvider_nonEnterprise_isStored() throws Exception {
      licenseManagerStatic.when(LicenseManager::isEnterprise).thenReturn(false);

      PropertyModel property = PropertyModel.builder()
         .name("log.provider")
         .value("file")
         .build();

      controller.editProperty(principal, property);

      sreeEnvStatic.verify(() -> SreeEnv.setProperty("log.provider", "file"));
   }

   // [log.provider blank, non-enterprise] a blank submission means "keep what is there", so it
   // must not be refused just because the stored value happens to be fluentd
   @Test
   void editProperty_blankProvider_nonEnterprise_isNotRefused() throws Exception {
      licenseManagerStatic.when(LicenseManager::isEnterprise).thenReturn(false);
      sreeEnvStatic.when(() -> SreeEnv.getProperty("log.provider")).thenReturn("fluentd");

      PropertyModel property = PropertyModel.builder()
         .name("log.provider")
         .value("")
         .build();

      assertDoesNotThrow(() -> controller.editProperty(principal, property));

      sreeEnvStatic.verify(() -> SreeEnv.setProperty("log.provider", "fluentd"));
   }

   // [org override of a global-only key] refused, and nothing is written (Bug #77694)
   @Test
   void editProperty_orgOverrideOfExcludedKey_isRefused() {
      PropertyModel property = PropertyModel.builder()
         .name("  inetsoft.org.orga.replet.cache.directory  ")
         .value("/tmp/orga")
         .build();

      MessageException thrown =
         assertThrows(MessageException.class, () -> controller.editProperty(principal, property));

      assertNotNull(thrown.getMessage());
      assertTrue(thrown.getMessage().contains("inetsoft.org.orga.replet.cache.directory"),
                 thrown.getMessage());
      sreeEnvStatic.verify(() -> SreeEnv.setProperty(anyString(), anyString()), never());
      sreeEnvStatic.verify(SreeEnv::save, never());
   }

   // [org override of a global-only key, mixed case] the name is matched as it would be stored
   @Test
   void editProperty_mixedCaseOrgOverrideOfExcludedKey_isRefused() {
      PropertyModel property = PropertyModel.builder()
         .name("inetsoft.org.OrgA.SREE.HOME")
         .value("/opt/orga")
         .build();

      assertThrows(MessageException.class, () -> controller.editProperty(principal, property));
      sreeEnvStatic.verify(() -> SreeEnv.setProperty(anyString(), anyString()), never());
   }

   // [org override of a global-only key, blank value] refused before the blank-value branch
   // could store the existing value back under the same name
   @Test
   void editProperty_orgOverrideOfExcludedKey_blankValue_isRefused() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("inetsoft.org.orga.server.type"))
         .thenReturn("server");

      PropertyModel property = PropertyModel.builder()
         .name("inetsoft.org.orga.server.type")
         .value("")
         .build();

      assertThrows(MessageException.class, () -> controller.editProperty(principal, property));
      sreeEnvStatic.verify(() -> SreeEnv.setProperty(anyString(), anyString()), never());
      sreeEnvStatic.verify(SreeEnv::save, never());
   }

   // [global-only key itself] the global name stays writable
   @Test
   void editProperty_globalExcludedKey_isStored() throws Exception {
      PropertyModel property = PropertyModel.builder()
         .name("replet.cache.directory")
         .value("/var/cache/stylebi")
         .build();

      controller.editProperty(principal, property);

      sreeEnvStatic.verify(() -> SreeEnv.setProperty("replet.cache.directory", "/var/cache/stylebi"));
      sreeEnvStatic.verify(SreeEnv::save);
   }

   // [org override of another key] org overrides that reads honor are stored as normal
   @Test
   void editProperty_orgOverrideOfOtherKey_isStored() throws Exception {
      PropertyModel property = PropertyModel.builder()
         .name("inetsoft.org.orga.mail.smtp.host")
         .value("smtp.orga.example.com")
         .build();

      controller.editProperty(principal, property);

      sreeEnvStatic.verify(
         () -> SreeEnv.setProperty("inetsoft.org.orga.mail.smtp.host", "smtp.orga.example.com"));
   }

   // [delete of an org override] the only route in EM to clean up an existing dead override
   @Test
   void deleteProperty_orgOverrideOfExcludedKey_isRemoved() throws Exception {
      controller.deleteProperty(principal, "inetsoft.org.orga.sree.home");

      sreeEnvStatic.verify(() -> SreeEnv.remove("inetsoft.org.orga.sree.home"));
      sreeEnvStatic.verify(SreeEnv::save);
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
