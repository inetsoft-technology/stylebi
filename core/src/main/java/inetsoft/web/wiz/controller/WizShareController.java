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
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Wraps the same {@code ShareController} that backs StyleBI's native {@code /api/share/*}
 * endpoints — no share logic is reimplemented here — under wiz's own JWT-authenticated,
 * CSRF-exempt {@code /api/wiz} namespace, so wiz's browser never needs to talk to StyleBI's
 * session/CSRF-protected controller directly.
 *
 * <p>Unlike export/email, there is no separate {@code *Service}/{@code *ServiceProxy} bean for
 * share's email/Slack/Google Chat sending — that logic lives directly in {@code ShareController}
 * itself — so this proxies the controller bean directly rather than a service tier.
 *
 * <p>The Link channel has no endpoint here at all: it was dropped from wiz's Share menu entirely
 * (product decision — the Share icon is for sending a chart to someone/somewhere, not for
 * grabbing a bare URL), and even where it existed it needed no backend call —
 * {@code ShareService.getViewsheetLink()} (the Angular app's own Share-link dialog) builds the
 * shareable URL purely client-side.
 *
 * <p>{@code getConfig}'s {@code orgId} param is passed as {@code null} here: it only matters for
 * the "expose default-org viewsheets to every org" multi-tenant feature (an explicit cross-org
 * query), which wiz's frontend never sends — matching how the Angular app itself only supplies
 * an {@code orgId} for that specific case.
 */
@RestController
@RequestMapping("/api/wiz")
public class WizShareController {
   public WizShareController(ShareController shareController) {
      this.shareController = shareController;
   }

   @GetMapping("/viewsheet/share-config")
   public ShareConfig getShareConfig(Principal principal) throws Exception {
      return shareController.getConfig(null, principal);
   }

   @PostMapping("/viewsheet/share-email")
   public void shareEmail(@RequestBody ShareMessageRequest request, Principal principal) throws Exception {
      shareController.sendEmailMessage(toShareMessage(request), principal);
   }

   @PostMapping("/viewsheet/share-slack")
   public void shareSlack(@RequestBody ShareMessageRequest request, Principal principal) throws Exception {
      shareController.sendSlackMessage(toShareMessage(request), principal);
   }

   @PostMapping("/viewsheet/share-google-chat")
   public void shareGoogleChat(@RequestBody ShareMessageRequest request, Principal principal) throws Exception {
      shareController.sendGoogleChatMessage(toShareMessage(request), principal);
   }

   private ShareMessage toShareMessage(ShareMessageRequest request) {
      return ShareMessage.builder()
         .viewsheetId(request.getViewsheetId())
         .link(request.getLink())
         .message(request.getMessage())
         .subject(request.getSubject())
         .recipients(request.getRecipients())
         .ccs(request.getCcs())
         .bccs(request.getBccs())
         .build();
   }

   private final ShareController shareController;
}
