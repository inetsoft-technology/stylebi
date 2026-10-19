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

import inetsoft.web.viewsheet.service.VSDialogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The built-in table styles a {@code tableStyle} property accepts besides the library styles that
 * {@link WizTableStyleController} lists. They are not library assets: they have no folder, cannot
 * be read, edited or deleted, and are applied by writing their {@code styleId} to {@code tableStyle}.
 * Which assembly types offer one is per assembly -- see that assembly's {@code tableStyle} options
 * in {@code list_assembly_properties}. The ids and labels are the ones the Composer's style tree
 * uses ({@link VSDialogService}), so the two cannot drift.
 */
@RestController
@RequestMapping("/api/wiz/v1/table-style-builtin")
public class WizBuiltInTableStyleController {
   public record BuiltInTableStyle(String styleId, String name, boolean builtIn) {}

   @GetMapping
   public List<BuiltInTableStyle> list() {
      return List.of(new BuiltInTableStyle(
         VSDialogService.CROSSTAB_STYLE_ID, VSDialogService.CROSSTAB_STYLE_LABEL, true));
   }
}
