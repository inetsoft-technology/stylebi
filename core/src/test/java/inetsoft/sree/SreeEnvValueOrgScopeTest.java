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
package inetsoft.sree;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Calendar;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #77277: a {@link SreeEnv.Value} must resolve an <code>inetsoft.org.&lt;org&gt;.</code>
 * override exactly as an uncached {@link SreeEnv#getProperty(String)} does, rather than serving
 * the value resolved by whichever thread refreshed it last to every organization.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SreeEnvValueOrgScopeTest {
   @BeforeEach
   void setUp() {
      clearThread();
      SreeEnv.setProperty(NAME, "global");
      SreeEnv.setProperty(ORG_A_NAME, "a");
   }

   @AfterEach
   void tearDown() {
      clearThread();
      SreeEnv.remove(NAME);
      SreeEnv.remove(ORG_A_NAME);
      SreeEnv.remove(ORG_B_NAME);
      SreeEnv.remove("week.start");
      SreeEnv.remove("inetsoft.org.orga.week.start");
      Tool.clearWeekStartCache();
   }

   @Test
   void orgARefresh_thenOrgBRead_resolvesOrgBValue() {
      SreeEnv.Value value = new SreeEnv.Value(NAME, TIMEOUT);

      inOrg("orga");
      assertEquals("a", value.get());

      inOrg("orgb");
      assertEquals("global", value.get());
      assertEquals(SreeEnv.getProperty(NAME), value.get());
   }

   @Test
   void orgBRefresh_thenOrgARead_resolvesOrgAOverride() {
      SreeEnv.Value value = new SreeEnv.Value(NAME, TIMEOUT);

      inOrg("orgb");
      assertEquals("global", value.get());

      inOrg("orga");
      assertEquals("a", value.get());
      assertEquals(SreeEnv.getProperty(NAME), value.get());
   }

   @Test
   void eachOrgOverride_isKeptSeparately() {
      SreeEnv.setProperty(ORG_B_NAME, "b");
      SreeEnv.Value value = new SreeEnv.Value(NAME, TIMEOUT, "def");

      inOrg("orga");
      assertEquals("a", value.get());
      inOrg("orgb");
      assertEquals("b", value.get());
      inOrg("orga");
      assertEquals("a", value.get());
   }

   @Test
   void principalLessThread_resolvesGlobalKey() {
      SreeEnv.Value value = new SreeEnv.Value(NAME, TIMEOUT);

      inOrg("orga");
      assertEquals("a", value.get());

      clearThread();
      assertEquals("global", value.get());
      assertEquals(SreeEnv.getProperty(NAME), value.get());
   }

   @Test
   void organizationContextHolderOnlyThread_resolvesGlobalKey() {
      SreeEnv.Value value = new SreeEnv.Value(NAME, TIMEOUT);

      inOrg("orga");
      assertEquals("a", value.get());

      clearThread();
      OrganizationContextHolder.setCurrentOrgId("orga");
      // a read without a principal never consults the org override, cached or not
      assertEquals("global", SreeEnv.getProperty(NAME));
      assertEquals("global", value.get());
   }

   @Test
   void mixedCaseName_resolvesOrgOverride() {
      SreeEnv.Value value = new SreeEnv.Value("Test77277.Key", TIMEOUT);

      inOrg("orga");
      assertEquals("a", value.get());
      inOrg("orgb");
      assertEquals("global", value.get());
   }

   @Test
   void valueIsCachedWithinTimeout_perOrg() {
      SreeEnv.Value value = new SreeEnv.Value(NAME, TIMEOUT);

      inOrg("orga");
      assertEquals("a", value.get());
      clearThread();
      assertEquals("global", value.get());

      SreeEnv.setProperty(NAME, "global2");
      SreeEnv.setProperty(ORG_A_NAME, "a2");

      inOrg("orga");
      assertEquals("a", value.get());
      clearThread();
      assertEquals("global", value.get());
   }

   @Test
   void updateValue_invalidatesEveryOrg() {
      SreeEnv.Value value = new SreeEnv.Value(NAME, TIMEOUT);

      inOrg("orga");
      assertEquals("a", value.get());
      inOrg("orgb");
      assertEquals("global", value.get());
      clearThread();
      assertEquals("global", value.get());

      SreeEnv.setProperty(NAME, "global2");
      SreeEnv.setProperty(ORG_A_NAME, "a2");
      // invalidated from a thread in another organization than the ones cached
      inOrg("orgc");
      value.updateValue();

      inOrg("orga");
      assertEquals("a2", value.get());
      inOrg("orgb");
      assertEquals("global2", value.get());
      clearThread();
      assertEquals("global2", value.get());
   }

   @Test
   void resolveRacingUpdateValue_doesNotServeStaleValue() {
      SreeEnv.Value value = new SreeEnv.Value(NAME, TIMEOUT);
      AtomicReference<String> current = new AtomicReference<>("stale");

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         // the first read resolves the old value, then the property changes and updateValue()
         // runs before that read stores its result
         sreeEnv.when(() -> SreeEnv.getProperty(anyString())).thenAnswer(inv -> {
            String result = current.getAndSet("fresh");

            if("stale".equals(result)) {
               value.updateValue();
            }

            return result;
         });

         assertEquals("stale", value.get());
         assertEquals("fresh", value.get());
      }
   }

   @Test
   void weekStart_resolvedPerOrg() {
      SreeEnv.setProperty("week.start", "sunday");
      SreeEnv.setProperty("inetsoft.org.orga.week.start", "monday");
      Tool.clearWeekStartCache();

      inOrg("orga");
      assertEquals(Calendar.MONDAY, Tool.getFirstDayOfWeek());
      inOrg("orgb");
      assertEquals(Calendar.SUNDAY, Tool.getFirstDayOfWeek());
      inOrg("orga");
      assertEquals(Calendar.MONDAY, Tool.getFirstDayOfWeek());
      clearThread();
      assertEquals(Calendar.SUNDAY, Tool.getFirstDayOfWeek());
   }

   private static void inOrg(String orgID) {
      clearThread();
      ThreadContext.setContextPrincipal(new XPrincipal(new IdentityID("user", orgID)));
   }

   private static void clearThread() {
      ThreadContext.setContextPrincipal(null);
      ThreadContext.setPrincipal(null);
      OrganizationContextHolder.clear();
   }

   private static final String NAME = "test77277.key";
   private static final String ORG_A_NAME = "inetsoft.org.orga." + NAME;
   private static final String ORG_B_NAME = "inetsoft.org.orgb." + NAME;
   private static final int TIMEOUT = 600000;
}
