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

import inetsoft.web.wiz.AppDomainUtils;
import inetsoft.web.wiz.OrganizationDomains;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Provides endpoints for getting/setting organization application domains.
 */
@RestController("wizAppDomainsController")
@RequestMapping("/api/wiz")
public class AppDomainsController {
   @GetMapping(value = "/appDomains", produces = MediaType.APPLICATION_JSON_VALUE)
   public OrganizationDomains getAppDomains(Principal user) throws Exception {
      return AppDomainUtils.getAppDomains(user);
   }

   @PostMapping(value = "/appDomains", produces = MediaType.APPLICATION_JSON_VALUE)
   public void setAppDomains(@RequestBody OrganizationDomains appDomains,
                             Principal user) throws Exception
   {
      AppDomainUtils.setAppDomains(appDomains, user);
   }
}
