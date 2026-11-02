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

package inetsoft.web.portal.controller;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.ThreadContext;
import inetsoft.web.portal.model.CurrentUserModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * On an SSO site an unauthenticated request can reach get-current-user with no principal
 * (Shell-Login header, or a Custom SSO filter that passes the request through). The
 * endpoint must answer with the anonymous user instead of failing on the email lookup.
 * (Bug #78770)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PortalControllerCurrentUserNullPrincipalTest {
   private static final String ORG = "orgA78770";
   private static final String USER = "alice78770";
   private static final String EMAIL = "alice78770@example.com";

   private SecurityTestDataBuilder builder;
   private PortalController controller;
   private String oldProvider;
   private Principal savedContextPrincipal;
   private Principal savedPrincipal;

   @BeforeEach
   void setUp() throws Exception {
      savedContextPrincipal = ThreadContext.getContextPrincipal();
      savedPrincipal = ThreadContext.getPrincipal();
      oldProvider = SreeEnv.getProperty("security.provider");
      SreeEnv.setProperty("security.provider", "file");

      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG, ORG)
         .addUser(USER, ORG, "password")
         .setup();

      // give the user an email through the provider the builder wrote it with
      FileAuthenticationProvider provider =
         (FileAuthenticationProvider) ReflectionTestUtils.getField(builder, "authcProvider");
      IdentityID userId = new IdentityID(USER, ORG);
      FSUser user = (FSUser) provider.getUser(userId);
      user.setEmails(new String[] { EMAIL });
      provider.setUser(userId, user);

      controller = new PortalController(SecurityEngine.getSecurity(), null, null, null,
                                        null, null, null);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedContextPrincipal);
      ThreadContext.setPrincipal(savedPrincipal);
      SreeEnv.setProperty("security.provider", oldProvider == null ? "" : oldProvider);

      if(builder != null) {
         builder.teardown();
      }
   }

   @Test
   void nullPrincipalReturnsAnonymousUser() {
      CurrentUserModel model = assertDoesNotThrow(() -> controller.getCurrentUser(null));

      assertNotNull(model);
      assertTrue(model.anonymous());
      assertEquals(XPrincipal.ANONYMOUS, model.name().name);
      assertArrayEquals(new String[0], model.email());
      assertFalse(model.isSysAdmin());
   }

   @Test
   void loggedInPrincipalKeepsEmails() {
      SRPrincipal alice = builder.principalOf(USER, ORG);

      CurrentUserModel model = controller.getCurrentUser(alice);

      assertFalse(model.anonymous());
      assertEquals(new IdentityID(USER, ORG), model.name());
      assertArrayEquals(new String[] { EMAIL }, model.email());
   }
}
