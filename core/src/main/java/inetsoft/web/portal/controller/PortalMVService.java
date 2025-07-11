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
package inetsoft.web.portal.controller;

import inetsoft.web.admin.content.repository.MVSupportService;
import inetsoft.web.portal.model.AnalyzeMVPortalRequest;
import inetsoft.web.portal.model.MVTreeModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PortalMVService {
   @Autowired
   public PortalMVService(MVSupportService support) {
      this.support = support;
   }

   public MVSupportService.AnalysisResult analyze(AnalyzeMVPortalRequest analyzeMVPortalRequest,
                                                  Principal principal)
      throws Exception
   {
      List<String> identifiers = analyzeMVPortalRequest.nodes().stream()
         .map(MVTreeModel::identifier)
         .collect(Collectors.toList());

      return support.analyze(
         identifiers, analyzeMVPortalRequest.expanded(),
         analyzeMVPortalRequest.bypass(), analyzeMVPortalRequest.full(), principal, true,
         analyzeMVPortalRequest.applyParentVsParameters());
   }

   private final MVSupportService support;
}
