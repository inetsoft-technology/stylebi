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
package inetsoft.web.admin.ai.licensing;

import inetsoft.report.internal.license.License;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.report.internal.license.LicenseType;
import inetsoft.sree.security.OrganizationManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Regression test for the fix in 07-fix-r1-java.md: {@code GET .../licensing/key} takes {@code key}
 * as a query parameter, not a {@code {key}} path-variable segment -- this repo's embedded Tomcat
 * 10.1.x rejects a literal {@code %2F} in a request URI by default, so the old path-variable shape
 * would 400 on a slash-containing candidate key before ever reaching this controller's own clean
 * {@code found: false} handling. Same defect class (and fix) as Viewsheets'
 * {@code AdminViewsheetControllerGetFolderTest}.
 *
 * <p>Standalone {@link MockMvc} (no Spring context, matching {@code WizControllerErrorHandlerTest}'s
 * own precedent) -- real Spring MVC request dispatch/binding runs, but there is no real embedded
 * Tomcat connector underneath, so this does not, by itself, prove the original
 * {@code encodedSolidusHandling} rejection would not recur; it does prove the new query-parameter
 * shape correctly binds a slash-containing value end to end through Spring's own request processing,
 * including the {@code %2F}-encoded form.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminLicensingControllerGetKeyTest {
   @Mock private LicenseManager licenseManager;
   @Mock private LicenseChangePlanService planService;
   @Mock private LicenseChangesetApplyService applyService;
   @Mock private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<LicenseManager> licenseManagerStatic;
   private MockMvc mvc;

   private static final Principal TEST_PRINCIPAL = () -> "test-user";

   @BeforeEach
   void setUp() {
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.isSiteAdmin(TEST_PRINCIPAL)).thenReturn(true);

      licenseManagerStatic = mockStatic(LicenseManager.class, withSettings().lenient());
      licenseManagerStatic.when(LicenseManager::isEnterprise).thenReturn(true);

      mvc = standaloneSetup(new AdminLicensingController(licenseManager, planService, applyService))
         .setMessageConverters(new MappingJackson2HttpMessageConverter())
         .build();
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      licenseManagerStatic.close();
   }

   private static License license(String key) {
      return License.builder().key(key).type(LicenseType.CPU)
         .expires(LocalDateTime.now().plusYears(1)).build();
   }

   @Test void getLicenseKeyExtractsSlashContainingKeyFromQueryParameter() throws Exception {
      when(licenseManager.getInstalledLicenses()).thenReturn(Set.of(license("AB/CD")));

      mvc.perform(get("/api/wiz/v1/admin/licensing/key")
            .param("key", "AB/CD")
            .principal(TEST_PRINCIPAL)
            .header("Authorization", "Bearer test-token"))
         .andExpect(status().isOk())
         .andExpect(content().string(containsString("\"found\":true")));
   }

   /** The exact byte sequence ({@code %2F}) the old path-variable request construction would have
    * sent, now in the query string rather than the path -- confirms Spring correctly percent-decodes
    * it back to a literal slash for a {@code @RequestParam}, unlike the path-segment shape this
    * replaces. */
   @Test void getLicenseKeyAcceptsPercentEncodedSlashInQueryString() throws Exception {
      when(licenseManager.getInstalledLicenses()).thenReturn(Set.of(license("AB/CD")));

      mvc.perform(get(URI.create("/api/wiz/v1/admin/licensing/key?key=AB%2FCD"))
            .principal(TEST_PRINCIPAL)
            .header("Authorization", "Bearer test-token"))
         .andExpect(status().isOk())
         .andExpect(content().string(containsString("\"found\":true")));
   }

   @Test void getLicenseKeyNoLongerMapsTheOldPathVariableShape() throws Exception {
      mvc.perform(get("/api/wiz/v1/admin/licensing/keys/AB%2FCD")
            .header("Authorization", "Bearer test-token"))
         .andExpect(status().isNotFound());

      verifyNoInteractions(licenseManager);
   }

   @Test void getLicenseKeyReturnsFoundFalseOnMiss() throws Exception {
      when(licenseManager.getInstalledLicenses()).thenReturn(Set.of());

      mvc.perform(get("/api/wiz/v1/admin/licensing/key")
            .param("key", "GHOST")
            .principal(TEST_PRINCIPAL)
            .header("Authorization", "Bearer test-token"))
         .andExpect(status().isOk())
         .andExpect(content().string(containsString("\"found\":false")));
   }
}
