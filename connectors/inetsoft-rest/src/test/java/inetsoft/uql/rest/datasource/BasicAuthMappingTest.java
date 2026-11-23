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
import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.datasource.campaignmonitor.CampaignMonitorDataSource;
import inetsoft.uql.rest.datasource.chargebee.ChargebeeDataSource;
import inetsoft.uql.rest.datasource.chartmogul.ChartMogulDataSource;
import inetsoft.uql.rest.datasource.freshdesk.FreshdeskDataSource;
import inetsoft.uql.rest.datasource.freshservice.FreshserviceDataSource;
import inetsoft.uql.rest.datasource.helpscoutdocs.HelpScoutDocsDataSource;
import inetsoft.uql.rest.datasource.insightly.InsightlyDataSource;
import inetsoft.uql.rest.datasource.intervals.IntervalsDataSource;
import inetsoft.uql.rest.datasource.mixpanel.MixpanelDataSource;
import inetsoft.uql.rest.datasource.stripe.StripeDataSource;
import inetsoft.uql.rest.datasource.toggl.TogglDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The user name and password that these services' Basic authentication expects, as their API
 * references describe it: the key or token as the user name and a fixed or empty password
 * (Freshdesk, Freshservice, Help Scout Docs, Intervals: X; Stripe, Insightly, Mixpanel, Chargebee,
 * Campaign Monitor: empty; Toggl: the text api_token), or a key pair (ChartMogul).
 */
@EndpointsSpringContext
@SreeHome
@Tag("core")
class BasicAuthMappingTest {
   private static void check(AbstractRestDataSource<?> ds, String user, String password) {
      assertEquals(AuthType.BASIC, ds.getAuthType(), ds.getClass().getSimpleName());
      assertEquals(user, ds.getUser(), ds.getClass().getSimpleName() + " user");
      assertEquals(password, ds.getPassword(), ds.getClass().getSimpleName() + " password");
   }

   @Test
   void keyAsUserAndFixedPassword() {
      FreshdeskDataSource freshdesk = new FreshdeskDataSource();
      freshdesk.setApiKey("k1");
      check(freshdesk, "k1", "X");
      FreshserviceDataSource freshservice = new FreshserviceDataSource();
      freshservice.setApiKey("k2");
      check(freshservice, "k2", "X");
      HelpScoutDocsDataSource helpscout = new HelpScoutDocsDataSource();
      helpscout.setApiKey("k3");
      check(helpscout, "k3", "X");
      IntervalsDataSource intervals = new IntervalsDataSource();
      intervals.setApiToken("k4");
      check(intervals, "k4", "X");
      TogglDataSource toggl = new TogglDataSource();
      toggl.setApiToken("k5");
      check(toggl, "k5", "api_token");
   }

   @Test
   void keyAsUserAndEmptyPassword() {
      StripeDataSource stripe = new StripeDataSource();
      stripe.setApiKey("sk_test");
      check(stripe, "sk_test", "");
      InsightlyDataSource insightly = new InsightlyDataSource();
      insightly.setApiKey("k6");
      check(insightly, "k6", "");
      MixpanelDataSource mixpanel = new MixpanelDataSource();
      mixpanel.setApiSecret("k7");
      check(mixpanel, "k7", "");
   }

   @Test
   void chartMogulUsesTheAccountTokenAndSecretKey() {
      ChartMogulDataSource ds = new ChartMogulDataSource();
      ds.setAccountToken("acct");
      ds.setSecretKey("secret");
      check(ds, "acct", "secret");
   }

   @Test
   void chargebeeAndCampaignMonitorSendTheKeyWithAnEmptyPassword() {
      ChargebeeDataSource chargebee = new ChargebeeDataSource();
      chargebee.setApiKey("k8");
      assertEquals("k8", chargebee.getUser());
      assertEquals("", chargebee.getPassword());
      CampaignMonitorDataSource monitor = new CampaignMonitorDataSource();
      monitor.setApiKey("k9");
      assertEquals("k9", monitor.getUser());
      assertEquals("", monitor.getPassword());
   }
}
