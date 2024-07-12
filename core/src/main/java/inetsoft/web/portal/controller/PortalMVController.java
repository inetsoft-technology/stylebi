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
package inetsoft.web.portal.controller;

import inetsoft.web.admin.content.repository.MVService;
import inetsoft.web.admin.content.repository.MVSupportService;
import inetsoft.web.admin.content.repository.model.MVManagementModel;
import inetsoft.web.portal.model.AnalyzeMVPortalRequest;
import inetsoft.web.portal.model.MVTreeModel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class PortalMVController {
   @Autowired
   public PortalMVController(PortalMVService portalMVService, MVService mvService) {
      this.portalMVService = portalMVService;
      this.mvService = mvService;
   }

   @PostMapping("/api/portal/content/repository/mv/analyze")
   public void analyze(@RequestBody AnalyzeMVPortalRequest analyzeMVPortalRequest,
                       HttpServletRequest req, Principal principal)
      throws Exception
   {
      HttpSession session = req.getSession(true);
      MVSupportService.AnalysisResult jobs =
         portalMVService.analyze(analyzeMVPortalRequest, principal);
      session.setAttribute("mv_jobs", jobs);
   }

   @PostMapping("/api/portal/content/materialized-view/info")
   public MVManagementModel getMVInfo(@RequestBody(required = false) AnalyzeMVPortalRequest request,
                                      Principal principal)
      throws Exception
   {
      List<String> ids = null;

      if(request != null) {
         ids = request.nodes().stream()
            .map(MVTreeModel::identifier)
            .collect(Collectors.toList());
      }

      return mvService.getMVInfo(ids, principal);
   }

   private final PortalMVService portalMVService;
   private final MVService mvService;
}
