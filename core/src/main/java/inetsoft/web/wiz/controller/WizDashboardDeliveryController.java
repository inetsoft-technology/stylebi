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

import inetsoft.web.viewsheet.service.LinkUri;
import inetsoft.web.wiz.service.WizDashboardDeliveryService;
import inetsoft.web.wiz.service.WizDashboardDeliveryService.EmailOptions;
import inetsoft.web.wiz.service.WizDashboardDeliveryService.EmailRequest;
import inetsoft.web.wiz.service.WizDashboardDeliveryService.EmailResult;
import inetsoft.web.wiz.service.WizDashboardDeliveryService.ShareOptions;
import inetsoft.web.wiz.service.WizDashboardDeliveryService.ShareRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

/**
 * Email and share a saved dashboard by path, for the composer-chat plugin, with only the login
 * authorization (no paired Composer session, no open runtime). The native wiz email and share
 * endpoints ({@code WizEmailController}, {@code WizShareController}) are left as they are for the
 * wiz portal; see {@link WizDashboardDeliveryService} for why these re-check the real user's
 * permissions.
 */
@RestController
@RequestMapping("/api/wiz/v1/viewsheets")
public class WizDashboardDeliveryController {
   public WizDashboardDeliveryController(WizDashboardDeliveryService service) {
      this.service = service;
   }

   @GetMapping(value = "/email-options", produces = MediaType.APPLICATION_JSON_VALUE)
   public EmailOptions emailOptions(@RequestParam("path") String path,
                                    @RequestParam(value = "scope", required = false) String scope,
                                    Principal principal) throws Exception
   {
      return service.getEmailOptions(path, scope, principal);
   }

   @PostMapping(value = "/email", produces = MediaType.APPLICATION_JSON_VALUE)
   public EmailResult email(@RequestBody EmailRequest request, Principal principal,
                            @LinkUri String linkUri) throws Exception
   {
      return service.email(request, principal, linkUri);
   }

   @GetMapping(value = "/share-options", produces = MediaType.APPLICATION_JSON_VALUE)
   public ShareOptions shareOptions(@RequestParam(value = "path", required = false) String path,
                                    @RequestParam(value = "scope", required = false) String scope,
                                    Principal principal, @LinkUri String linkUri) throws Exception
   {
      return service.getShareOptions(path, scope, principal, linkUri);
   }

   @PostMapping(value = "/share", produces = MediaType.APPLICATION_JSON_VALUE)
   public Map<String, Object> share(@RequestBody ShareRequest request, Principal principal,
                                    @LinkUri String linkUri) throws Exception
   {
      service.share(request, principal, linkUri);
      return Map.of("shared", true, "target", request.target());
   }

   private final WizDashboardDeliveryService service;
}
