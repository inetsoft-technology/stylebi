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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * Mirrors the "link" channel of the native {@code /api/share/config} endpoint
 * (ShareController.getConfig()'s linkEnabled computation — no share logic is reimplemented
 * here) under wiz's own JWT-authenticated, CSRF-exempt /api/wiz namespace.
 *
 * <p>Unlike export/email, generating the shareable URL itself needs no backend call: StyleBI's
 * own Share-link dialog (ShareService.getViewsheetLink() in the Angular app) builds it purely
 * client-side from the asset entry identifier (global/&lt;path&gt; or user/&lt;name&gt;/&lt;path&gt;
 * under viewer/view/), so wiz replicates that same string-building logic in its own frontend
 * instead of wrapping a link-generation service that doesn't exist server-side. This controller
 * only exposes the admin-configurable on/off switch so wiz can hide the Share button consistently
 * with however the deployment has "Share via Link" configured.
 */
@RestController
@RequestMapping("/api/wiz")
public class WizShareController {
   public WizShareController(SecurityEngine securityEngine) {
      this.securityEngine = securityEngine;
   }

   @GetMapping("/viewsheet/share-link-enabled")
   public boolean isShareLinkEnabled(Principal principal) {
      return "true".equals(SreeEnv.getProperty("share.link.enabled")) &&
         checkLinkPermission(principal);
   }

   private boolean checkLinkPermission(Principal principal) {
      try {
         return securityEngine.checkPermission(
            principal, ResourceType.SHARE, "link", ResourceAction.ACCESS);
      }
      catch(Exception e) {
         LOG.warn("Failed to check share-link permission", e);
         return false;
      }
   }

   private final SecurityEngine securityEngine;

   private static final Logger LOG = LoggerFactory.getLogger(WizShareController.class);
}
