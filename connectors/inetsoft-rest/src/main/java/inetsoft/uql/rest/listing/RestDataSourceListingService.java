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
package inetsoft.uql.rest.listing;

import inetsoft.uql.DataSourceListing;
import inetsoft.uql.DataSourceListingService;
import inetsoft.uql.rest.datasource.activecampaign.ActiveCampaignListing;
import inetsoft.uql.rest.datasource.adobeanalytics.AdobeAnalyticsListing;
import inetsoft.uql.rest.datasource.airtable.AirtableListing;
import inetsoft.uql.rest.datasource.appfigures.AppfiguresListing;
import inetsoft.uql.rest.datasource.asana.AsanaListing;
import inetsoft.uql.rest.datasource.azureblob.AzureBlobListing;
import inetsoft.uql.rest.datasource.azuresearch.AzureSearchListing;
import inetsoft.uql.rest.datasource.box.BoxListing;
import inetsoft.uql.rest.datasource.campaignmonitor.CampaignMonitorListing;
import inetsoft.uql.rest.datasource.chargebee.ChargebeeListing;
import inetsoft.uql.rest.datasource.chargify.ChargifyListing;
import inetsoft.uql.rest.datasource.chartmogul.ChartMogulListing;
import inetsoft.uql.rest.datasource.clockify.ClockifyListing;
import inetsoft.uql.rest.datasource.constantcontact.ConstantContactListing;
import inetsoft.uql.rest.datasource.copper.CopperListing;
import inetsoft.uql.rest.datasource.datadotworld.DataDotWorldListing;
import inetsoft.uql.rest.datasource.firebase.FirebaseListing;
import inetsoft.uql.rest.datasource.fortytwomatters.FortyTwoMattersListing;
import inetsoft.uql.rest.datasource.freshdesk.FreshdeskListing;
import inetsoft.uql.rest.datasource.freshsales.FreshsalesListing;
import inetsoft.uql.rest.datasource.freshservice.FreshserviceListing;
import inetsoft.uql.rest.datasource.fusebill.FusebillListing;
import inetsoft.uql.rest.datasource.github.GitHubListing;
import inetsoft.uql.rest.datasource.gitlab.GitLabListing;
import inetsoft.uql.rest.datasource.googlecal.GoogleCalendarListing;
import inetsoft.uql.rest.datasource.gosquared.GoSquaredListing;
import inetsoft.uql.rest.datasource.graphql.GraphQLListing;
import inetsoft.uql.rest.datasource.gsearchconsole.GoogleSearchConsoleListing;
import inetsoft.uql.rest.datasource.harvest.HarvestListing;
import inetsoft.uql.rest.datasource.helpscoutdocs.HelpScoutDocsListing;
import inetsoft.uql.rest.datasource.hubspot.HubSpotListing;
import inetsoft.uql.rest.datasource.influxdb.InfluxDBListing;
import inetsoft.uql.rest.datasource.keap.KeapListing;
import inetsoft.uql.rest.datasource.insightly.InsightlyListing;
import inetsoft.uql.rest.datasource.intervals.IntervalsListing;
import inetsoft.uql.rest.datasource.jira.JiraListing;
import inetsoft.uql.rest.datasource.jive.JiveListing;
import inetsoft.uql.rest.datasource.lighthouse.LighthouseListing;
import inetsoft.uql.rest.datasource.linkedin.LinkedinListing;
import inetsoft.uql.rest.datasource.liveagent.LiveAgentListing;
import inetsoft.uql.rest.datasource.mailchimp.MailchimpListing;
import inetsoft.uql.rest.datasource.mailgun.MailgunListing;
import inetsoft.uql.rest.datasource.mixpanel.MixpanelListing;
import inetsoft.uql.rest.datasource.monday.MondayListing;
import inetsoft.uql.rest.datasource.nicereply.NicereplyListing;
import inetsoft.uql.rest.datasource.pipedrive.PipedriveListing;
import inetsoft.uql.rest.datasource.pipelinecrm.PipelineCRMListing;
import inetsoft.uql.rest.datasource.prometheus.PrometheusListing;
import inetsoft.uql.rest.datasource.quickbooks.QuickbooksReportsListing;
import inetsoft.uql.rest.datasource.remedyforce.RemedyforceListing;
import inetsoft.uql.rest.datasource.sendgrid.SendGridListing;
import inetsoft.uql.rest.datasource.seomonitor.SEOmonitorListing;
import inetsoft.uql.rest.datasource.servicenow.ServiceNowListing;
import inetsoft.uql.rest.datasource.sfreports.SalesforceReportsListing;
import inetsoft.uql.rest.datasource.shopify.ShopifyListing;
import inetsoft.uql.rest.datasource.smartsheet.SmartsheetListing;
import inetsoft.uql.rest.datasource.square.SquareListing;
import inetsoft.uql.rest.datasource.stripe.StripeListing;
import inetsoft.uql.rest.datasource.surveymonkey.SurveyMonkeyListing;
import inetsoft.uql.rest.datasource.teamdesk.TeamDeskListing;
import inetsoft.uql.rest.datasource.toggl.TogglListing;
import inetsoft.uql.rest.datasource.twilio.TwilioListing;
import inetsoft.uql.rest.datasource.twitter.TwitterListing;
import inetsoft.uql.rest.datasource.wordpress.WordPressListing;
import inetsoft.uql.rest.datasource.xero.XeroListing;
import inetsoft.uql.rest.datasource.youtubeanalytics.YouTubeAnalyticsListing;
import inetsoft.uql.rest.datasource.zendesk.ZendeskListing;
import inetsoft.uql.rest.datasource.zendesksell.ZendeskSellListing;
import inetsoft.uql.rest.datasource.zohocrm.ZohoCRMListing;

import java.util.Arrays;
import java.util.List;

public class RestDataSourceListingService implements DataSourceListingService {
   @Override
   public List<DataSourceListing> getDataSourceListings() {
      return Arrays.asList(
         new RestJsonListing(),
         new RestXmlListing(),
         new ActiveCampaignListing(),
         new AirtableListing(),
         new AppfiguresListing(),
         new AsanaListing(),
         new AzureBlobListing(),
         new AzureSearchListing(),
         new CampaignMonitorListing(),
         new ChargebeeListing(),
         new ChargifyListing(),
         new ChartMogulListing(),
         new ClockifyListing(),
         new ConstantContactListing(),
         new CopperListing(),
         new DataDotWorldListing(),
         new FirebaseListing(),
         new FortyTwoMattersListing(),
         new FreshdeskListing(),
         new FreshsalesListing(),
         new FreshserviceListing(),
         new GitHubListing(),
         new GitLabListing(),
         new GoSquaredListing(),
         new GoogleCalendarListing(),
         new GoogleSearchConsoleListing(),
         new GraphQLListing(),
         new HarvestListing(),
         new HelpScoutDocsListing(),
         new HubSpotListing(),
         new InfluxDBListing(),
         new KeapListing(),
         new InsightlyListing(),
         new IntervalsListing(),
         new JiraListing(),
         new JiveListing(),
         new LighthouseListing(),
         new LinkedinListing(),
         new LiveAgentListing(),
         new MailchimpListing(),
         new MailgunListing(),
         new MixpanelListing(),
         new MondayListing(),
         new NicereplyListing(),
         new PipedriveListing(),
         new PipelineCRMListing(),
         new PrometheusListing(),
         new QuickbooksReportsListing(),
         new SEOmonitorListing(),
         new SalesforceReportsListing(),
         new SendGridListing(),
         new ServiceNowListing(),
         new ShopifyListing(),
         new SmartsheetListing(),
         new SquareListing(),
         new StripeListing(),
         new SurveyMonkeyListing(),
         new TeamDeskListing(),
         new TogglListing(),
         new TwilioListing(),
         new TwitterListing(),
         new WordPressListing(),
         new XeroListing(),
         new YouTubeAnalyticsListing(),
         new ZendeskListing(),
         new ZendeskSellListing(),
         new ZohoCRMListing()
      );
   }
}
