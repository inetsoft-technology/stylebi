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
package inetsoft.web.share;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.Mailer;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.util.IdentityNode;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.model.UsersModel;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.viewsheet.model.dialog.EmailAddrDialogModel;
import inetsoft.web.viewsheet.model.dialog.EmailPaneModel;
import inetsoft.web.viewsheet.service.VSEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.security.Principal;
import java.util.*;

@RestController
public class ShareController {
   @Autowired
   public ShareController(SecurityEngine securityEngine,
                          ScheduleService scheduleService)
   {
      this.securityEngine = securityEngine;
      this.scheduleService = scheduleService;
   }

   /**
    * Gets the sharing configuration.
    *
    * @param user a principal that identifies the remote user.
    *
    * @return the configuration
    */
   @GetMapping("/api/share/config")
   public ShareConfig getConfig(Principal user) {
      boolean emailEnabled = "true".equals(SreeEnv.getProperty("share.email.enabled")) &&
         checkPermission("email", user);
      boolean facebookEnabled = "true".equals(SreeEnv.getProperty("share.facebook.enabled")) &&
         checkPermission("facebook", user);
      boolean googleChatEnabled = "true".equals(SreeEnv.getProperty("share.googlechat.enabled")) &&
         StringUtils.hasText("share.googlechat.url") && checkPermission("googlechat", user);
      boolean linkedinEnabled = "true".equals(SreeEnv.getProperty("share.linkedin.enabled")) &&
         checkPermission("linkedin", user);
      boolean slackEnabled = "true".equals(SreeEnv.getProperty("share.slack.enabled")) &&
         StringUtils.hasText(SreeEnv.getProperty("share.slack.url")) &&
         checkPermission("slack", user);
      boolean twitterEnabled = "true".equals(SreeEnv.getProperty("share.twitter.enabled")) &&
         checkPermission("twitter", user);
      boolean linkEnabled = "true".equals(SreeEnv.getProperty("share.link.enabled")) &&
         checkPermission("link", user);

      return ShareConfig.builder()
         .emailEnabled(emailEnabled)
         .facebookEnabled(facebookEnabled)
         .googleChatEnabled(googleChatEnabled)
         .linkedinEnabled(linkedinEnabled)
         .slackEnabled(slackEnabled)
         .twitterEnabled(twitterEnabled)
         .linkEnabled(linkEnabled)
         .build();
   }

   @GetMapping("/api/share/email")
   public ShareEmailModel getEmailModel(Principal user) throws Exception {
      String email = SreeEnv.getProperty("mail.from.address");
      boolean securityEnabled = securityEngine.isSecurityEnabled();
      boolean userDialogEnabled = securityEnabled;
      boolean useSelf = !"false".equals(SreeEnv.getProperty("em.mail.defaultEmailFromSelf"));
      IdentityID pId = user == null ? null : IdentityID.getIdentityIDFromKey(user.getName());

      if(!"anonymous".equals(pId.name) && useSelf) {
         String[] emails = SUtil.getEmails(pId);
         email = emails.length > 0 ? emails[0] : email;
         userDialogEnabled = true;
      }

      List<TreeNodeModel> nodes = new ArrayList<>();
      TreeNodeModel userTree = TreeNodeModel.builder()
         .label(Catalog.getCatalog().getString("Users"))
         .data("")
         .type(IdentityNode.USERS + "")
         .leaf(false)
         .build();

      nodes.add(userTree);

      if(!(user instanceof SRPrincipal) || !((SRPrincipal) user).isSelfOrganization()) {
         TreeNodeModel groupTree = TreeNodeModel.builder()
            .label(Catalog.getCatalog().getString("Groups"))
            .data("")
            .type(IdentityNode.GROUPS + "")
            .leaf(false)
            .build();
         nodes.add(groupTree);
      }

      TreeNodeModel rootTree = TreeNodeModel.builder()
         .label(Catalog.getCatalog().getString("Root"))
         .data("")
         .type(IdentityNode.ROOT + "")
         .children(nodes)
         .leaf(false)
         .build();

      EmailAddrDialogModel emailAddrDialogModel = EmailAddrDialogModel.builder()
         .rootTree(rootTree)
         .build();
      UsersModel usersModel = scheduleService.getUsersModel(user);

      EmailPaneModel emailPaneModel = EmailPaneModel.builder()
         .fromAddress(email != null ? email : "")
         .fromAddressEnabled(false)
         .userDialogEnabled(userDialogEnabled)
         .emailAddrDialogModel(emailAddrDialogModel)
         .users(usersModel == null ? new ArrayList<>() : usersModel.emailUsers())
         .groups(usersModel == null ? new ArrayList<>() : Arrays.stream(usersModel.groups()).map(id -> id.name).toList())
         .emailGroups(usersModel == null ? new ArrayList<>() : usersModel.emailGroups())
         .build();
      boolean historyEnabled = "true".equalsIgnoreCase(SreeEnv.getProperty("mail.history.enabled"));

      return ShareEmailModel.builder()
         .emailModel(emailPaneModel)
         .historyEnabled(historyEnabled)
         .securityEnabled(securityEnabled)
         .build();
   }

   /**
    * Share a viewsheet or report via email.
    *
    * @param message the message object.
    * @param user    a principal that identifies the remote user.
    */
   @PostMapping("/api/share/email")
   public void sendEmailMessage(@RequestBody ShareMessage message, Principal user) throws Exception
   {
      ShareConfig config = getConfig(user);

      if(!config.emailEnabled()) {
         throw new IllegalStateException("Sharing with email is not enabled");
      }

      String link = Tool.replaceLocalhost(message.link());
      String body = String.format("%1$s\n<p><a href=\"%2$s\">%2$s</a></p>", message.message(), link);
      String from = SreeEnv.getProperty("mail.from.address");
      String to = VSEmailService.getEmailsString(VSEmailService.getEmailsList(
         String.join(",", Objects.requireNonNull(message.recipients())), false, user));
      String cc = message.ccs() != null ? VSEmailService.getEmailsString(VSEmailService.getEmailsList(
         String.join(",", message.ccs()), false, user)) : null;
      String bcc = message.bccs() != null ?VSEmailService.getEmailsString(VSEmailService.getEmailsList(
         String.join(",", message.bccs()), false, user)) : null;
      String subject = message.subject();

      mailer.send(to, cc, bcc, from, subject, body, null, true);
   }

   /**
    * Share a viewsheet or report with a Google Hangouts chat.
    *
    * @param message the message object.
    * @param user    a principal that identifies the remote user.
    *
    * @see <a href="https://developers.google.com/hangouts/chat/how-tos/webhooks">Hangouts Webhooks</a>
    */
   @PostMapping("/api/share/google-chat")
   public void sendGoogleChatMessage(@RequestBody ShareMessage message, Principal user) {
      ShareConfig config = getConfig(user);

      if(!config.googleChatEnabled()) {
         throw new IllegalStateException("Sharing with Google Hangouts is not enabled");
      }

      String url = SreeEnv.getProperty("share.googlechat.url");

      if(url == null || url.trim().isEmpty()) {
         throw new IllegalStateException("The Google Hangouts webhook URL is not set");
      }

      Catalog catalog = Catalog.getCatalog(user);
      String link = message.link();

      String text = message.message() +
         "\n<users/all>: <" + link + "|" + catalog.getString("share.clickHere") + ">";
      GoogleHangoutsMessage body = GoogleHangoutsMessage.builder().text(text).build();
      postToWebhook(url.trim(), body);
   }

   /**
    * Share a viewsheet or report with a Slack channel.
    *
    * @param message the message object.
    * @param user    a principal that identifies the remote user.
    *
    * @see <a href="https://api.slack.com/incoming-webhooks">Slack Incoming Webhooks</a>
    */
   @PostMapping("/api/share/slack")
   public void sendSlackMessage(@RequestBody ShareMessage message, Principal user) {
      ShareConfig config = getConfig(user);

      if(!config.slackEnabled()) {
         throw new IllegalStateException("Sharing with Slack is not enabled");
      }

      String url = SreeEnv.getProperty("share.slack.url");

      if(url == null || url.trim().isEmpty()) {
         throw new IllegalStateException("The Slack webhook URL is not set");
      }

      String link = message.link();
      String text = message.message() + "\n" + link;
      SlackMessage body = SlackMessage.builder().text(text).build();
      postToWebhook(url.trim(), body);
   }

   private void postToWebhook(String url, Object message) {
      try {
         HttpHeaders headers = new HttpHeaders();
         headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
         headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
         RequestEntity<Object> request =
            new RequestEntity<>(message, headers, HttpMethod.POST, new URI(url));
         restTemplate.exchange(request, String.class);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to post message", e);
      }
   }

   private boolean checkPermission(String type, Principal user) {
      try {
         return securityEngine.checkPermission(
            user, ResourceType.SHARE, type, ResourceAction.ACCESS);
      }
      catch(SecurityException e) {
         LOG.warn("Failed to check permission", e);
      }

      return false;
   }

   private final SecurityEngine securityEngine;
   private final ScheduleService scheduleService;
   private final RestTemplate restTemplate = new RestTemplate();
   private final Mailer mailer = new Mailer();

   private static final Logger LOG = LoggerFactory.getLogger(ShareController.class);
}
