/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.presentation;

import inetsoft.sree.SreeEnv;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.presentation.model.PresentationShareSettingsModel;
import inetsoft.web.viewsheet.Audited;
import org.springframework.stereotype.Component;

@Component
public class ShareSettingsService {
   public PresentationShareSettingsModel getModel(boolean globalProperty) {
      return PresentationShareSettingsModel.builder()
         .emailEnabled("true".equals(SreeEnv.getProperty("share.email.enabled", false, !globalProperty)))
         .facebookEnabled("true".equals(SreeEnv.getProperty("share.facebook.enabled", false, !globalProperty)))
         .googleChatEnabled("true".equals(SreeEnv.getProperty("share.googlechat.enabled", false, !globalProperty)))
         .googleChatUrl(SreeEnv.getProperty("share.googlechat.url", false, !globalProperty))
         .linkedinEnabled("true".equals(SreeEnv.getProperty("share.linkedin.enabled", false, !globalProperty)))
         .slackEnabled("true".equals(SreeEnv.getProperty("share.slack.enabled", false, !globalProperty)))
         .slackUrl(SreeEnv.getProperty("share.slack.url", false, !globalProperty))
         .twitterEnabled("true".equals(SreeEnv.getProperty("share.twitter.enabled", false, !globalProperty)))
         .linkEnabled("true".equals(SreeEnv.getProperty("share.link.enabled", false, !globalProperty)))
         .openGraphSiteName(SreeEnv.getProperty("share.opengraph.sitename", false, !globalProperty))
         .openGraphTitle(SreeEnv.getProperty("share.opengraph.title", false, !globalProperty))
         .openGraphDescription(SreeEnv.getProperty("share.opengraph.description", false, !globalProperty))
         .openGraphImageUrl(SreeEnv.getProperty("share.opengraph.image", false, !globalProperty))
         .build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Share",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setModel(PresentationShareSettingsModel model, boolean globalSettings) throws Exception {
      SreeEnv.setProperty("share.email.enabled", Boolean.toString(model.emailEnabled()), !globalSettings);
      SreeEnv.setProperty("share.facebook.enabled", Boolean.toString(model.facebookEnabled()), !globalSettings);
      SreeEnv.setProperty("share.googlechat.enabled", Boolean.toString(model.googleChatEnabled()), !globalSettings);
      SreeEnv.setProperty("share.googlechat.url", model.googleChatUrl(), !globalSettings);
      SreeEnv.setProperty("share.linkedin.enabled", Boolean.toString(model.linkedinEnabled()), !globalSettings);
      SreeEnv.setProperty("share.slack.enabled", Boolean.toString(model.slackEnabled()), !globalSettings);
      SreeEnv.setProperty("share.slack.url", model.slackUrl(), !globalSettings);
      SreeEnv.setProperty("share.twitter.enabled", Boolean.toString(model.twitterEnabled()), !globalSettings);
      SreeEnv.setProperty("share.link.enabled", Boolean.toString(model.linkEnabled()), !globalSettings);
      SreeEnv.setProperty("share.opengraph.sitename", model.openGraphSiteName(), !globalSettings);
      SreeEnv.setProperty("share.opengraph.title", model.openGraphTitle(), !globalSettings);
      SreeEnv.setProperty("share.opengraph.description", model.openGraphDescription(), !globalSettings);
      SreeEnv.setProperty("share.opengraph.image", model.openGraphImageUrl(), !globalSettings);
      SreeEnv.save();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Share",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void resetSettings(boolean globalSettings) throws Exception {
      SreeEnv.resetProperty("share.email.enabled", !globalSettings);
      SreeEnv.resetProperty("share.facebook.enabled", !globalSettings);
      SreeEnv.resetProperty("share.googlechat.enabled", !globalSettings);
      SreeEnv.resetProperty("share.googlechat.url", !globalSettings);
      SreeEnv.resetProperty("share.linkedin.enabled", !globalSettings);
      SreeEnv.resetProperty("share.slack.enabled", !globalSettings);
      SreeEnv.resetProperty("share.slack.url", !globalSettings);
      SreeEnv.resetProperty("share.twitter.enabled", !globalSettings);
      SreeEnv.resetProperty("share.link.enabled", !globalSettings);
      SreeEnv.resetProperty("share.opengraph.sitename", !globalSettings);
      SreeEnv.resetProperty("share.opengraph.title", !globalSettings);
      SreeEnv.resetProperty("share.opengraph.description", !globalSettings);
      SreeEnv.resetProperty("share.opengraph.image", !globalSettings);

      SreeEnv.save();
   }
}
