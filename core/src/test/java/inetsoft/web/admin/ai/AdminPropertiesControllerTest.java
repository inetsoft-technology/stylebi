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
   void withholdsACredentialWhoseNameMatchesNoSecretPattern() {
      // Redmine #76006. These four are written encrypted and were read back anyway, because
      // isSecret tested the name and the names miss: "pass" is not "password", the OAuth tokens
      // carry no magic substring at all, and sharedkey ends in "key" without the dot. Reading is
      // the disclosure - the value goes to the caller, which relays it to a model provider.
      //
      // Stubbing a value for each is the whole point: if the mask were removed these assertions
      // would fail on the returned secret rather than passing vacuously on a null.
      for(String name : new String[] { "mail.smtp.pass", "mail.smtp.accesstoken",
                                       "mail.smtp.refreshtoken", "log.fluentd.security.sharedkey" })
      {
         sreeEnv.when(() -> SreeEnv.getProperty(name, false, false)).thenReturn("s3cret-" + name);

         PropertyView view = controller.get(name, principal);

         assertNull(view.currentValue(), name + ": the stored credential must not be returned");
         sreeEnv.verify(() -> SreeEnv.getProperty(name, false, false), never());
      }
   }

   @Test
   void withholdsAWebhookUrlWhoseNameAndWriterBothLookHarmless() {
      // Redmine #76170. A Slack or Google Chat incoming-webhook URL is a bearer credential - the
      // token is in the path, and nothing else is required to post into that channel. #76006's
      // remedy is an allow-list of properties whose WRITER encrypts, so it cannot reach these by
      // construction: ShareSettingsService writes both with a plain setProperty. The name test
      // misses them too, so before CONFIRMED_SECRET the full URL was read and returned.
      //
      // Stubbing a value matters here for the same reason as the credential test above: without
      // it a removed mask would pass vacuously on a null.
      for(String name : new String[] { "share.slack.url", "share.googlechat.url" })
      {
         sreeEnv.when(() -> SreeEnv.getProperty(name, false, false))
            .thenReturn("https://hooks.example.test/services/T0/B0/" + name);

         PropertyView view = controller.get(name, principal);

         assertNull(view.currentValue(), name + ": the webhook URL must not be returned");
         sreeEnv.verify(() -> SreeEnv.getProperty(name, false, false), never());
      }
   }

   @Test
   void tellsTheCallerAWithheldWebhookUrlExistsAndWhyItIsWithheld() {
      // The regression masking these introduced, and the reason `confirmed` needed a third term.
      // Both URLs are declared in defaults.properties, so the defaults chain resolved them through
      // getProperty and `stored != null` reported them confirmed - until this path stopped reading
      // them. Falling back to unknown hands the caller guidance saying the name may be a typo and
      // that an unrecognised name is written verbatim while reporting success; all false here,
      // because AdminChangePlanService refuses the change outright. Same failure the credential
      // term was added to stop, on a list verified the same way.
      PropertyView view = controller.get("share.slack.url", principal);

      assertEquals(PropertyView.EXISTS_CONFIRMED, view.exists());
      assertNull(view.guidance());
      assertNull(view.currentValue());

      // And the reason must be the true one. The pattern wording is false twice for these: the
      // name does not match the pattern (see withholdsABearerCredentialThatNeitherItsNameNorIts-
      // WriterGivesAway), and the list IS evidence the property exists.
      assertFalse(view.description().contains("matches admin-chat's secret pattern"),
                  "a CONFIRMED_SECRET name is not withheld by the name pattern");
      assertFalse(view.description().contains("nothing about whether the property exists"),
                  "membership in a hand-verified list is evidence the property exists");
      assertTrue(view.description().contains("bearer credential"));
   }

   @Test
   void leavesTheShareSettingsBesideAWebhookUrlReadable() {
      // The mask must be the two URLs, not the share.* prefix - an operator needs to know whether
      // sharing is enabled, and the flag discloses nothing.
      sreeEnv.when(() -> SreeEnv.getProperty("share.slack.enabled", false, false))
         .thenReturn("true");

      PropertyView view = controller.get("share.slack.enabled", principal);

      assertEquals("true", view.currentValue());
   }

   @Test
   void tellsTheCallerAWithheldCredentialExistsAndCanStillBeSet() {
      // Two regressions the mask would otherwise introduce, both of which cost an agent a task it
      // could complete. mail.smtp.pass is uncatalogued, so before #76006 its existence was known
      // only from the value now withheld - without the credential term in `confirmed` it would
      // report unknown, and the guidance for unknown tells the caller the name may be a typo.
      // And the pattern branch's wording says a secret is not changed here, which is false for a
      // credential: the plan service exempts exactly these from that refusal.
      PropertyView view = controller.get("mail.smtp.pass", principal);

      assertEquals(PropertyView.EXISTS_CONFIRMED, view.exists());
      assertNull(view.guidance());
      assertFalse(view.description().contains("neither read nor changed"),
                  "a credential IS changeable through preview_changes/apply_changes");
      assertTrue(view.description().contains("credential"));
   }

   @Test
   void confirmsExistenceOfACataloguedProperty() {
      sreeEnv.when(() -> SreeEnv.getProperty("query.runtime.maxrow", false, false))
         .thenReturn(null);
      PropertyView view = controller.get("query.runtime.maxrow", principal);
      // Catalogued but unset: the catalog alone settles that the name is real.
      assertEquals(PropertyView.EXISTS_CONFIRMED, view.exists());
      assertNull(view.guidance());
   }

   @Test
   void confirmsExistenceOfAnUncataloguedPropertyThatHoldsAValue() {
      // Covers anything declared in defaults.properties: the defaults chain resolves through
      // getProperty, so a declared-but-never-set property still reads non-null.
      sreeEnv.when(() -> SreeEnv.getProperty("role.administrator", false, false))
         .thenReturn("Administrator");
      PropertyView view = controller.get("role.administrator", principal);
      assertFalse(view.recognized());
      assertEquals(PropertyView.EXISTS_CONFIRMED, view.exists());
      assertNull(view.guidance());
   }

   @Test
   void reportsExistenceAsUnknownWhenUncataloguedAndUnset() {
      // The regression this whole field exists for. It was openid.client.id: a real property with
      // a read site in OpenIDConfig which, on a server where SSO had never been configured, was
      // unset AND uncatalogued - byte-identical to a name that does not exist. An agent read that
      // as "OIDC settings are not stored in server properties" and abandoned the task.
      //
      // That property is catalogued now, so it reports confirmed and no longer demonstrates the
      // case. permission.andcondition stands in: real, community, still uncatalogued, unset by
      // default. If cataloguing ever reaches it this test will fail, and that failure is a prompt
      // to move the example on - not a defect.
      sreeEnv.when(() -> SreeEnv.getProperty("permission.andcondition", false, false))
         .thenReturn(null);
      PropertyView view = controller.get("permission.andcondition", principal);
      assertFalse(view.recognized());
      assertNull(view.currentValue());
      assertEquals(PropertyView.EXISTS_UNKNOWN, view.exists());
      assertNotNull(view.guidance());
   }

   @Test
   void doesNotClaimAValueWasWithheldForANameThatMerelyLooksSecret() {
      // isSecret matches on the name alone, so an invented name ending .key took the secret
      // branch and came back asserting a secret had been withheld - inventing evidence that the
      // property exists out of a string suffix. It must report that it does not know instead.
      PropertyView invented = controller.get("openid.completely.made.up.key", principal);
      assertEquals(PropertyView.EXISTS_UNKNOWN, invented.exists());
      assertNotNull(invented.guidance());
      assertFalse(invented.description().contains("Value withheld"));
      // The description must be about the NAME, not the property: a caller who stops reading at
      // the first clause must not come away with "this exists and is a secret".
      assertFalse(invented.description().startsWith("Secret property"));
      assertTrue(invented.description().contains("name"));
      sreeEnv.verify(
         () -> SreeEnv.getProperty("openid.completely.made.up.key", false, false), never());
   }

   @Test
   void distinguishesARealUnsetPropertyFromAnInventedOneOnlyByGuidance() {
      // Both are unknown - the server genuinely cannot tell them apart, and the fix is to say so
      // rather than to guess. This pins that the ambiguity is reported, not silently resolved.
      sreeEnv.when(() -> SreeEnv.getProperty("permission.andcondition", false, false))
         .thenReturn(null);
      sreeEnv.when(() -> SreeEnv.getProperty("permission.notarealproperty", false, false))
         .thenReturn(null);

      PropertyView real = controller.get("permission.andcondition", principal);
      PropertyView invented = controller.get("permission.notarealproperty", principal);

      assertEquals(PropertyView.EXISTS_UNKNOWN, real.exists());
      assertEquals(PropertyView.EXISTS_UNKNOWN, invented.exists());
      assertEquals(real.guidance(), invented.guidance());
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
