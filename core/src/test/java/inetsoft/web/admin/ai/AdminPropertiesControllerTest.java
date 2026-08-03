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
package inetsoft.web.admin.ai;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.OrganizationManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminPropertiesControllerTest {
   @Mock private OrganizationManager orgManager;
   @Mock private Principal principal;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<SreeEnv> sreeEnv;
   private AdminPropertiesController controller;

   @BeforeEach
   void setUp() {
      AdminPropertyCatalog catalog = new AdminPropertyCatalog();
      controller = new AdminPropertiesController(catalog, new AdminRiskClassifier(catalog));

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      sreeEnv = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer test-jwt");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      sreeEnv.close();
      RequestContextHolder.resetRequestAttributes();
   }

   @Test
   void listsCataloguedPropertiesWithCurrentValues() {
      sreeEnv.when(() -> SreeEnv.getProperty("query.runtime.maxrow", false, false))
         .thenReturn("100");

      List<PropertyView> views = controller.list(null, principal);

      assertFalse(views.isEmpty());
      PropertyView view = views.stream()
         .filter(v -> "query.runtime.maxrow".equals(v.name())).findFirst().orElseThrow();
      assertEquals("100", view.currentValue());
      assertEquals("int", view.type());
      assertTrue(view.recognized());
   }

   @Test
   void filtersBySubstringOfNameAndAlias() {
      assertTrue(controller.list("smtp", principal).stream()
         .allMatch(v -> v.name().contains("smtp")));
      assertFalse(controller.list("max.rows", principal).isEmpty());
      assertTrue(controller.list("no.such.thing", principal).isEmpty());
   }

   @Test
   void getsASinglePropertyByAlias() {
      sreeEnv.when(() -> SreeEnv.getProperty("query.runtime.maxrow", false, false))
         .thenReturn("100");
      PropertyView view = controller.get("max.rows", principal);
      assertEquals("query.runtime.maxrow", view.name());
      assertEquals("100", view.currentValue());
   }

   @Test
   void getsAnUncataloguedPropertyAsUnrecognizedHighRisk() {
      // Still readable, so an operator can inspect anything; guidance is what degrades.
      sreeEnv.when(() -> SreeEnv.getProperty("some.unknown.prop", false, false))
         .thenReturn("whatever");
      PropertyView view = controller.get("some.unknown.prop", principal);
      assertFalse(view.recognized());
      assertEquals("high", view.risk());
      assertEquals("whatever", view.currentValue());
   }

   @Test
   void readsWithOrgScopeDisabled() {
      sreeEnv.when(() -> SreeEnv.getProperty("query.runtime.maxrow", false, false))
         .thenReturn("100");
      controller.get("max.rows", principal);
      sreeEnv.verify(() -> SreeEnv.getProperty(anyString()), never());
   }

   @Test
   void reportsAnOrgQualifiedPropertyWithItsOrgId() {
      sreeEnv.when(() -> SreeEnv.getProperty("inetsoft.org.acme.mail.smtp.host", false, false))
         .thenReturn("smtp.acme.test");
      PropertyView view = controller.get("inetsoft.org.acme.smtp.host", principal);
      assertEquals("inetsoft.org.acme.mail.smtp.host", view.name());
      assertEquals("smtp.acme.test", view.currentValue());
      assertEquals("high", view.risk());
   }

   @Test
   void withholdsTheValueOfASecretPropertyButStillListsIt() {
      // Finding 5a: password.encryption.key is StyleBI's password-encryption master key. It must
      // still be LISTED (an operator legitimately needs to know it exists) but its value must
      // never reach this endpoint's caller, which forwards responses to a model provider.
      PropertyView view = controller.get("password.encryption.key", principal);
      assertNull(view.currentValue());
      assertNotNull(view.description());
      sreeEnv.verify(() -> SreeEnv.getProperty("password.encryption.key", false, false), never());
   }

   @Test
   void refusesANonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
         () -> controller.list(null, principal)).getStatusCode());
   }

   @Test
   void refusesARequestWithoutABearerToken() {
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));
      assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
         () -> controller.list(null, principal)).getStatusCode());
   }
}
