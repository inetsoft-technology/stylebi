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
package inetsoft.web.composer.ws;

import inetsoft.util.Tool;
import inetsoft.web.composer.ws.assembly.ConditionTrapModel;
import inetsoft.web.composer.ws.assembly.ConditionTrapValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Controller for condition traps
 */
@Controller
public class ConditionTrapController extends WorksheetController {
   @Autowired
   public ConditionTrapController(ConditionTrapServiceProxy conditionTrapService) {
      this.conditionTrapService = conditionTrapService;
   }

   /**
    * Checks whether a new condition list will cause a trap.
    * Also finds the trap-causing columns for a given condition list.
    *
    * @param model     the model containing the old and new condition lists
    * @param runtimeId the runtime id
    * @param principal the user principal
    *
    * @return the condition trap validator if there is one, null otherwise
    */
   @PostMapping("/api/composer/worksheet/check-condition-trap/{runtimeId}")
   @ResponseBody
   public ConditionTrapValidator checkConditionTrap(
      @RequestBody() ConditionTrapModel model, @PathVariable("runtimeId") String runtimeId,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return conditionTrapService.checkConditionTrap(runtimeId, model, principal);
   }

   private final ConditionTrapServiceProxy conditionTrapService;
}
