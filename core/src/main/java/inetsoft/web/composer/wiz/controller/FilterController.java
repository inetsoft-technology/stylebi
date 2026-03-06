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

package inetsoft.web.composer.wiz.controller;

import inetsoft.web.composer.model.TreeNodeModel;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
public class FilterController {
   @GetMapping(value = "/api/composer/wiz/filters")
   public TreeNodeModel getFilters(
      @RequestParam("runtimeId")
      @Parameter(
         name = "runtimeId",
         description = "The runtime ID of the viewsheet.",
         in = ParameterIn.QUERY,
         required = true
      )
      String runtimeId,
      Principal principal)
   {
      // TODO: replace with real filter data based on runtimeId/viewsheet
      List<TreeNodeModel> children = List.of(
         TreeNodeModel.builder().label("Order Name").leaf(true).dragName("dragFilter").data("Order Name").build(),
         TreeNodeModel.builder().label("Order Number").leaf(true).dragName("dragFilter").data("Order Number").build(),
         TreeNodeModel.builder().label("ProductName").leaf(true).dragName("dragFilter").data("ProductName").build(),
         TreeNodeModel.builder().label("Product Id").leaf(true).dragName("dragFilter").data("Product Id").build()
      );

      return TreeNodeModel.builder()
         .children(children)
         .build();
   }
}
