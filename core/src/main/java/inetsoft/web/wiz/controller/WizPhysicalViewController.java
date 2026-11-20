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

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * STUB (J1.0): {@code /physical/get}, {@code /physical/create}, {@code /physical/update}
 * (phase 2). Every route answers 501 through {@code UnsupportedOperationException} until lane J4
 * replaces this file. Never declares a local {@code @ExceptionHandler}.
 */
@RestController
@RequestMapping("/api/wiz/data-models")
public class WizPhysicalViewController {
   @PostMapping(value = "/physical/get", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object get(@RequestBody WizPhysicalViewWire.GetRequest request, Principal principal) {
      throw new UnsupportedOperationException("physical view get is not available in this build");
   }

   @PostMapping(value = "/physical/create", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object create(@RequestBody WizPhysicalViewWire.WriteRequest request,
                        Principal principal)
   {
      throw new UnsupportedOperationException(
         "physical view create is not available in this build");
   }

   @PostMapping(value = "/physical/update", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object update(@RequestBody WizPhysicalViewWire.WriteRequest request,
                        Principal principal)
   {
      throw new UnsupportedOperationException(
         "physical view update is not available in this build");
   }
}
