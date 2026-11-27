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

import inetsoft.sree.internal.SUtil;
import inetsoft.web.wiz.model.WizTenancy;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports whether StyleBI is multi-tenant. Deliberately open to every authenticated wiz caller: the
 * answer is one deployment-wide boolean, and wiz refreshes its cached copy with whichever user's token
 * arrives next, so requiring an administrator would leave the copy stale between administrator visits.
 * The value can change while users are logged in, which is why it is an endpoint and not a token claim.
 */
@RestController
@RequestMapping("/api/wiz")
public class WizTenancyController {
   @GetMapping(value = "/tenancy", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizTenancy getTenancy() {
      return new WizTenancy(SUtil.isMultiTenant());
   }
}
