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
package inetsoft.web.composer.controller;

import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
public class HeartBeatController {

   @RequestMapping(value="/test/vs/touch-asset", method = RequestMethod.POST)
   @ResponseBody
   public String touchAsset(@RequestParam("viewsheetId") String vsId,
                          @RequestParam(value = "design", defaultValue = "false") boolean design,
                          @RequestParam(value = "changed", defaultValue = "false") boolean changed,
                          @RequestParam(value = "update", defaultValue = "false") boolean update,
                          Principal principal,
                          @LinkUri String linkUri)
      throws Exception
   {
      return heartBeatService.touchAsset(vsId, design, changed, update, principal, linkUri);
   }

   @Autowired
   @Qualifier("worksheetService")
   public void setHeartBeatService(HeartBeatServiceProxy heartBeatService) {
      this.heartBeatService = heartBeatService;
   }

   private HeartBeatServiceProxy heartBeatService;
}
