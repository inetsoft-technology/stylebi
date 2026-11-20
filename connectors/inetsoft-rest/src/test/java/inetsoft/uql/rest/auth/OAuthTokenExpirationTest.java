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
package inetsoft.uql.rest.auth;

import inetsoft.test.*;
import inetsoft.uql.rest.AbstractRestDataSource;
import inetsoft.uql.rest.datasource.adobeanalytics.AdobeAnalyticsDataSource;
import inetsoft.uql.rest.datasource.constantcontact.ConstantContactDataSource;
import inetsoft.uql.rest.datasource.keap.KeapDataSource;
import inetsoft.uql.rest.datasource.quickbooks.QuickbooksReportsDataSource;
import inetsoft.uql.rest.datasource.twitter.TwitterDataSource;
import inetsoft.uql.tabular.oauth.Tokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81961: the token expiration of Keap, Constant Contact and Adobe Analytics when the token
 * result has no (or a bad) issue time; also that the Twitter rule and the base class are intact.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, OAuthTokenUpdateTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
class OAuthTokenExpirationTest {
   private static final long DAY = TimeUnit.HOURS.toMillis(24);
   private static final long TOL = TimeUnit.MINUTES.toMillis(1);

   private static List<Supplier<AbstractRestDataSource<?>>> expirySources() {
      return List.of(KeapDataSource::new, ConstantContactDataSource::new,
                     AdobeAnalyticsDataSource::new);
   }

   private static AbstractRestDataSource<?> withRefreshToken(AbstractRestDataSource<?> ds) {
      ds.setRefreshToken("RT");
      return ds;
   }

   // issued and expiration are required builder attributes; 0 is what a missing value becomes
   private static Tokens.Builder tb() {
      return Tokens.builder().issued(0L).expiration(0L).refreshToken("RT2");
   }

   private static boolean valid(AbstractRestDataSource<?> ds) throws Exception {
      Method m = AbstractRestDataSource.class.getDeclaredMethod("isTokenValid");
      m.setAccessible(true);
      return (Boolean) m.invoke(ds);
   }

   private static void assertAboutNowPlusDay(AbstractRestDataSource<?> ds, long before) {
      long exp = ds.getTokenExpiration();
      assertTrue(exp >= before + DAY && exp <= System.currentTimeMillis() + DAY + TOL,
                 ds.getClass().getSimpleName() + " " + exp);
   }

   @Test
   void absentIssuedGivesAboutNowPlus24hAndAValidToken() throws Exception {
      for(Supplier<AbstractRestDataSource<?>> s : expirySources()) {
         AbstractRestDataSource<?> ds = withRefreshToken(s.get());
         long before = System.currentTimeMillis();
         // issued never set on the builder at all (not via the null string overload)
         ds.updateTokens(tb().accessToken("AT").refreshToken("RT2").build());
         assertAboutNowPlusDay(ds, before);
         assertTrue(valid(ds), ds.getClass().getSimpleName());
      }
   }

   @Test
   void nullStringIssuedAndNegativeIssuedAreTreatedAsUnknown() throws Exception {
      for(Supplier<AbstractRestDataSource<?>> s : expirySources()) {
         for(Tokens t : List.of(tb().accessToken("AT").issued((String) null).build(),
                                tb().accessToken("AT").issued(-5L).build(),
                                tb().accessToken("AT").issued(Long.MIN_VALUE / 4)
                                   .build()))
         {
            AbstractRestDataSource<?> ds = withRefreshToken(s.get());
            long before = System.currentTimeMillis();
            ds.updateTokens(t);
            assertAboutNowPlusDay(ds, before);
            assertTrue(valid(ds));
         }
      }
   }

   @Test
   void recentIssuedIsIssuedPlus24hAndStaleIssuedStaysPast() throws Exception {
      for(Supplier<AbstractRestDataSource<?>> s : expirySources()) {
         long recent = System.currentTimeMillis() - 1000;
         AbstractRestDataSource<?> ds = withRefreshToken(s.get());
         ds.updateTokens(tb().accessToken("AT").issued(recent).build());
         assertEquals(recent + DAY, ds.getTokenExpiration());
         assertTrue(valid(ds));

         long stale = System.currentTimeMillis() - DAY - TimeUnit.HOURS.toMillis(1);
         ds = withRefreshToken(s.get());
         ds.updateTokens(tb().accessToken("AT").issued(stale).build());
         assertEquals(stale + DAY, ds.getTokenExpiration());
         assertFalse(valid(ds), "stale issued must trigger a refresh");
      }
   }

   @Test
   void adobeKeepsServiceExpirationOnlyWithoutIssued() {
      long exp = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(5);
      AdobeAnalyticsDataSource ds = new AdobeAnalyticsDataSource();
      ds.updateTokens(tb().accessToken("AT").expiration(exp).build());
      assertEquals(exp, ds.getTokenExpiration());

      ds = new AdobeAnalyticsDataSource();
      ds.updateTokens(tb().accessToken("AT").issued(-1L).expiration(exp).build());
      assertEquals(exp, ds.getTokenExpiration());

      long issued = System.currentTimeMillis() - 1000;
      ds = new AdobeAnalyticsDataSource();
      ds.updateTokens(tb().accessToken("AT").issued(issued).expiration(exp).build());
      assertEquals(issued + DAY, ds.getTokenExpiration());
   }

   @Test
   void twitterRuleAndBaseClassExpirationAreUnchanged() {
      long now = System.currentTimeMillis();
      TwitterDataSource tw = new TwitterDataSource();
      tw.updateTokens(tb().accessToken("AT").build());
      assertTrue(Math.abs(tw.getTokenExpiration() - (now + TimeUnit.HOURS.toMillis(2))) < TOL);

      long exp = now + TimeUnit.HOURS.toMillis(3);
      tw = new TwitterDataSource();
      tw.updateTokens(tb().accessToken("AT").issued(1L).expiration(exp).build());
      assertEquals(exp - TimeUnit.SECONDS.toMillis(30), tw.getTokenExpiration());

      // base class: the expiration of the result is stored as is, issued is ignored (also 0)
      QuickbooksReportsDataSource qb = new QuickbooksReportsDataSource();
      qb.updateTokens(tb().accessToken("AT").issued(7L).expiration(exp)
                         .properties(java.util.Map.of("realmId", "1")).build());
      assertEquals(exp, qb.getTokenExpiration());
      qb = new QuickbooksReportsDataSource();
      qb.updateTokens(tb().accessToken("AT")
                         .properties(java.util.Map.of("realmId", "1")).build());
      assertEquals(0L, qb.getTokenExpiration());
   }
}
