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
package inetsoft.uql.rest.datasource;

import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
import inetsoft.uql.rest.AbstractRestDataSource;
import inetsoft.uql.rest.datasource.adobeanalytics.AdobeAnalyticsDataSource;
import inetsoft.uql.rest.datasource.constantcontact.ConstantContactDataSource;
import inetsoft.uql.rest.datasource.keap.KeapDataSource;
import inetsoft.uql.rest.datasource.quickbooks.QuickbooksReportsDataSource;
import inetsoft.uql.rest.datasource.remedyforce.RemedyforceDataSource;
import inetsoft.uql.rest.datasource.twitter.TwitterDataSource;
import inetsoft.uql.tabular.HttpParameter;
import inetsoft.uql.tabular.oauth.OAuthDataSource;
import inetsoft.uql.tabular.oauth.Tokens;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the OAuth connectors do with the tokens a refresh returns, and the Twitter specifics.
 * No network is used: the refresh itself goes through the InetSoft token service, so only the
 * handling of its result is covered.
 */
@EndpointsSpringContext
@SreeHome
@Tag("core")
class OAuthTokenUpdateTest {
   static Stream<Supplier<AbstractRestDataSource<?>>> oauthSources() {
      return Stream.of(TwitterDataSource::new, KeapDataSource::new, ConstantContactDataSource::new,
                       AdobeAnalyticsDataSource::new, QuickbooksReportsDataSource::new,
                       RemedyforceDataSource::new);
   }

   private static Tokens tokens(String access, String refresh, long issued, long expiration) {
      return Tokens.builder().accessToken(access).refreshToken(refresh).issued(issued)
         .expiration(expiration).build();
   }

   @ParameterizedTest
   @MethodSource("oauthSources")
   void accessAndRefreshTokensAreStored(Supplier<AbstractRestDataSource<?>> factory) {
      AbstractRestDataSource<?> ds = factory.get();
      long now = System.currentTimeMillis();
      ((OAuthDataSource) ds).updateTokens(tokens("new-access", "new-refresh", now,
                                                 now + Duration.ofHours(1).toMillis()));
      assertEquals("new-access", ds.getAccessToken());
      assertEquals("new-refresh", ds.getRefreshToken());
      assertTrue(ds.getTokenExpiration() > now);
   }

   // RFC 6749 section 6: a refresh response may omit the refresh token, the client then keeps the
   // one it has. The password-grant refresh in AuthorizationClient already does this.
   @ParameterizedTest
   @MethodSource("oauthSources")
   @Disabled("Bug #81957: a refresh result without a refresh token clears the stored one; enable when it is fixed")
   void refreshResultWithoutRefreshTokenKeepsTheOldOne(Supplier<AbstractRestDataSource<?>> factory) {
      AbstractRestDataSource<?> ds = factory.get();
      ds.setRefreshToken("old-refresh");
      long now = System.currentTimeMillis();
      ((OAuthDataSource) ds).updateTokens(tokens("new-access", null, now,
                                                 now + Duration.ofHours(1).toMillis()));
      assertEquals("new-access", ds.getAccessToken());
      assertEquals("old-refresh", ds.getRefreshToken());
   }

   @Test
   void twitterExpirationLeavesA30SecondClockSkewBuffer() {
      TwitterDataSource ds = new TwitterDataSource();
      long expiration = System.currentTimeMillis() + Duration.ofHours(1).toMillis();
      ds.updateTokens(tokens("a", "r", 0L, expiration));
      assertEquals(expiration - 30_000L, ds.getTokenExpiration());
   }

   @Test
   void twitterMissingOrPastExpirationFallsBackToTwoHours() {
      for(long expiration : new long[] { 0L, System.currentTimeMillis() - 1000L }) {
         TwitterDataSource ds = new TwitterDataSource();
         long before = System.currentTimeMillis();
         ds.updateTokens(tokens("a", "r", 0L, expiration));
         long after = System.currentTimeMillis();
         long twoHours = Duration.ofHours(2).toMillis();
         assertTrue(ds.getTokenExpiration() >= before + twoHours &&
                    ds.getTokenExpiration() <= after + twoHours,
                    "expiration " + expiration + " gave " + ds.getTokenExpiration());
      }
   }

   @Test
   void twitterSendsBearerHeaderWithoutRefreshWhileTokenIsValid() {
      TwitterDataSource ds = new TwitterDataSource();
      ds.setAccessToken("tw-access");
      ds.setRefreshToken("tw-refresh");
      ds.setTokenExpiration(System.currentTimeMillis() + Duration.ofHours(1).toMillis());
      HttpParameter[] params = ds.getQueryHttpParameters();
      assertEquals(1, params.length);
      assertEquals(HttpParameter.ParameterType.HEADER, params[0].getType());
      assertEquals("Authorization", params[0].getName());
      assertEquals("Bearer tw-access", params[0].getValue());
   }

   @Test
   void twitterAlwaysAsksForPkce() {
      assertEquals("pkce", new TwitterDataSource().getOauthFlags());
   }

   @Test
   void twitterEndpointsAreFixed() {
      TwitterDataSource ds = new TwitterDataSource();
      ds.setTokenUri("https://evil.example/token");
      ds.setScope("x");
      ds.setRequestsPerSecond(100);
      assertEquals("https://api.x.com/2/oauth2/token", ds.getTokenUri());
      assertTrue(ds.getScope().contains("offline.access"));
      assertEquals(1.0, ds.getRequestsPerSecond());
   }

   // the OAuth server of these services does not return expires_in; their tokens last 24 hours
   @Test
   void keapConstantContactAndAdobeExpireTwentyFourHoursAfterIssue() {
      long issued = System.currentTimeMillis();
      long expected = issued + Duration.ofHours(24).toMillis();
      AbstractRestDataSource<?>[] sources = {
         new KeapDataSource(), new ConstantContactDataSource(), new AdobeAnalyticsDataSource() };

      for(AbstractRestDataSource<?> ds : sources) {
         ((OAuthDataSource) ds).updateTokens(tokens("a", "r", issued, 0L));
         assertEquals(expected, ds.getTokenExpiration(), ds.getClass().getSimpleName());
      }
   }

   // without an issue time the expiration must not land in 1970, which makes every query refresh
   @Test
   @Disabled("Bug #81961: a missing issue time sets the expiration to 1970; enable when it is fixed")
   void keapConstantContactAndAdobeWithoutIssueTimeDoNotExpireInThePast() {
      AbstractRestDataSource<?>[] sources = {
         new KeapDataSource(), new ConstantContactDataSource(), new AdobeAnalyticsDataSource() };

      for(AbstractRestDataSource<?> ds : sources) {
         ((OAuthDataSource) ds).updateTokens(tokens("a", "r", 0L, 0L));
         assertTrue(ds.getTokenExpiration() > System.currentTimeMillis(),
                    ds.getClass().getSimpleName() + " " + ds.getTokenExpiration());
      }
   }

   @Test
   void quickbooksCapturesTheRealmIdOfTheAuthorizedCompany() {
      QuickbooksReportsDataSource ds = new QuickbooksReportsDataSource();
      Map<String, Object> props = new HashMap<>();
      props.put("realmId", "9130");
      ds.updateTokens(Tokens.builder().accessToken("a").refreshToken("r").issued(0L)
                         .expiration(1L).properties(props).build());
      assertEquals("9130", ds.getCompanyId());
   }

   @Test
   void salesforceCapturesTheInstanceUrlOfTheAuthorizedOrg() {
      RemedyforceDataSource ds = new RemedyforceDataSource();
      Map<String, Object> props = new HashMap<>();
      props.put("instance_url", "https://na99.my.salesforce.com");
      ds.updateTokens(Tokens.builder().accessToken("a").refreshToken("r").issued(0L)
                         .expiration(1L).properties(props).build());
      assertEquals("https://na99.my.salesforce.com", ds.getInstanceUrl());
   }
}
