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
package inetsoft.web.wiz.controller;

import inetsoft.web.share.ShareConfig;
import inetsoft.web.share.ShareController;
import inetsoft.web.share.ShareMessage;
import inetsoft.web.wiz.model.ShareMessageRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class WizShareControllerTest {

   @Test
   void getShareConfigDelegatesToShareController() throws Exception {
      ShareController shareController = mock(ShareController.class);
      Principal principal = mock(Principal.class);
      ShareConfig config = ShareConfig.builder()
         .emailEnabled(true).facebookEnabled(false).googleChatEnabled(true)
         .linkedinEnabled(false).slackEnabled(true).twitterEnabled(false).linkEnabled(true)
         .build();
      when(shareController.getConfig(isNull(), eq(principal))).thenReturn(config);

      WizShareController ctrl = new WizShareController(shareController);

      assertSame(config, ctrl.getShareConfig(principal));
   }

   @Test
   void shareEmailBuildsShareMessageAndDelegates() throws Exception {
      ShareController shareController = mock(ShareController.class);
      Principal principal = mock(Principal.class);

      ShareMessageRequest request = new ShareMessageRequest();
      request.setViewsheetId("1^4^admin^visualizations-593.../abc^host-org");
      request.setLink("/sree/viewer/view/global/visualizations-593.../abc");
      request.setMessage("Check this out");
      request.setSubject("A chart");
      request.setRecipients(List.of("a@b.com"));
      request.setCcs(List.of("c@d.com"));
      request.setBccs(List.of("e@f.com"));

      WizShareController ctrl = new WizShareController(shareController);
      ctrl.shareEmail(request, principal);

      verify(shareController).sendEmailMessage(argThat((ShareMessage m) ->
         "1^4^admin^visualizations-593.../abc^host-org".equals(m.viewsheetId()) &&
         "/sree/viewer/view/global/visualizations-593.../abc".equals(m.link()) &&
         "Check this out".equals(m.message()) &&
         "A chart".equals(m.subject()) &&
         List.of("a@b.com").equals(m.recipients()) &&
         List.of("c@d.com").equals(m.ccs()) &&
         List.of("e@f.com").equals(m.bccs())
      ), eq(principal));
   }

   @Test
   void shareSlackBuildsShareMessageAndDelegates() throws Exception {
      ShareController shareController = mock(ShareController.class);
      Principal principal = mock(Principal.class);

      ShareMessageRequest request = new ShareMessageRequest();
      request.setLink("/sree/viewer/view/global/visualizations-593.../abc");
      request.setMessage("Check this out");

      WizShareController ctrl = new WizShareController(shareController);
      ctrl.shareSlack(request, principal);

      verify(shareController).sendSlackMessage(argThat((ShareMessage m) ->
         "/sree/viewer/view/global/visualizations-593.../abc".equals(m.link()) &&
         "Check this out".equals(m.message()) &&
         m.viewsheetId() == null
      ), eq(principal));
   }

   @Test
   void shareGoogleChatBuildsShareMessageAndDelegates() throws Exception {
      ShareController shareController = mock(ShareController.class);
      Principal principal = mock(Principal.class);

      ShareMessageRequest request = new ShareMessageRequest();
      request.setLink("/sree/viewer/view/global/visualizations-593.../abc");
      request.setMessage("Check this out");

      WizShareController ctrl = new WizShareController(shareController);
      ctrl.shareGoogleChat(request, principal);

      verify(shareController).sendGoogleChatMessage(argThat((ShareMessage m) ->
         "/sree/viewer/view/global/visualizations-593.../abc".equals(m.link()) &&
         "Check this out".equals(m.message())
      ), eq(principal));
   }
}
