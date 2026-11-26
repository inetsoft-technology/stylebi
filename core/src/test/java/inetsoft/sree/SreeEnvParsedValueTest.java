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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81932: {@link SreeEnv#getInt(String)}, {@link SreeEnv#getLong(String)} and
 * {@link SreeEnv#getBoolean(String)} must return the property's current value, not the value
 * parsed the first time the name was read.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SreeEnvParsedValueTest {
   @BeforeEach
   void setUp() {
      savedPrincipal = ThreadContext.getPrincipal();
      savedContextPrincipal = ThreadContext.getContextPrincipal();
      clearThread();
   }

   @AfterEach
   void tearDown() {
      clearThread();
      SreeEnv.remove(INT_NAME);
      SreeEnv.remove(LONG_NAME);
      SreeEnv.remove(BOOL_NAME);
      SreeEnv.remove(SHARED_NAME);
      SreeEnv.remove(ORG_A_LONG_NAME);
      ThreadContext.setPrincipal(savedPrincipal);
      ThreadContext.setContextPrincipal(savedContextPrincipal);
   }

   @Test
   void getLong_afterChange_returnsNewValue() {
      SreeEnv.setProperty(LONG_NAME, "600000");
      assertEquals(600000L, SreeEnv.getLong(LONG_NAME));

      SreeEnv.setProperty(LONG_NAME, "0");
      assertEquals(0L, SreeEnv.getLong(LONG_NAME));
   }

   @Test
   void getInt_afterChange_returnsNewValue() {
      SreeEnv.setProperty(INT_NAME, "5");
      assertEquals(5, SreeEnv.getInt(INT_NAME));

      SreeEnv.setProperty(INT_NAME, "7");
      assertEquals(7, SreeEnv.getInt(INT_NAME));
   }

   @Test
   void getBoolean_afterChange_returnsNewValue() {
      SreeEnv.setProperty(BOOL_NAME, "true");
      assertEquals(Boolean.TRUE, SreeEnv.getBoolean(BOOL_NAME));

      SreeEnv.setProperty(BOOL_NAME, "false");
      assertEquals(Boolean.FALSE, SreeEnv.getBoolean(BOOL_NAME));
   }

   @Test
   void removeThenSetAgain_returnsNullThenNewValue() {
      SreeEnv.setProperty(LONG_NAME, "10");
      assertEquals(10L, SreeEnv.getLong(LONG_NAME));

      SreeEnv.remove(LONG_NAME);
      assertNull(SreeEnv.getLong(LONG_NAME));

      SreeEnv.setProperty(LONG_NAME, "20");
      assertEquals(20L, SreeEnv.getLong(LONG_NAME));
   }

   @Test
   void getIntThenGetLong_sameName_noClassCastException() {
      SreeEnv.setProperty(SHARED_NAME, "42");
      assertEquals(42, SreeEnv.getInt(SHARED_NAME));
      assertEquals(42L, SreeEnv.getLong(SHARED_NAME));
   }

   @Test
   void getLong_resolvesOrgOverrideForEachOrg() {
      SreeEnv.setProperty(LONG_NAME, "100");
      SreeEnv.setProperty(ORG_A_LONG_NAME, "200");

      inOrg("orga");
      assertEquals(200L, SreeEnv.getLong(LONG_NAME));

      inOrg("orgb");
      assertEquals(100L, SreeEnv.getLong(LONG_NAME));
   }

   @Test
   void malformedNumber_stillThrowsNumberFormatException() {
      SreeEnv.setProperty(LONG_NAME, "not-a-number");
      assertThrows(NumberFormatException.class, () -> SreeEnv.getLong(LONG_NAME));
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

   private Principal savedPrincipal;
   private Principal savedContextPrincipal;

   private static final String INT_NAME = "test81932.int";
   private static final String LONG_NAME = "test81932.long";
   private static final String BOOL_NAME = "test81932.bool";
   private static final String SHARED_NAME = "test81932.shared";
   private static final String ORG_A_LONG_NAME = "inetsoft.org.orga." + LONG_NAME;
}
