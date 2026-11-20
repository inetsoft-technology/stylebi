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
 * STUB (J1.0): {@code /logical/get}, {@code /logical/create}, {@code /logical/update}. Every route
 * answers 501 through {@code UnsupportedOperationException} until lane J2 replaces this file.
 * Never declares a local {@code @ExceptionHandler}, which would beat
 * {@code WizControllerErrorHandler}.
 */
@RestController
@RequestMapping("/api/wiz/data-models")
public class WizLogicalModelController {
   @PostMapping(value = "/logical/get", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object get(@RequestBody WizLogicalModelWire.GetRequest request, Principal principal) {
      throw new UnsupportedOperationException("logical model get is not available in this build");
   }

   @PostMapping(value = "/logical/create", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object create(@RequestBody WizLogicalModelWire.WriteRequest request,
                        Principal principal)
   {
      throw new UnsupportedOperationException(
         "logical model create is not available in this build");
   }

   @PostMapping(value = "/logical/update", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object update(@RequestBody WizLogicalModelWire.WriteRequest request,
                        Principal principal)
   {
      throw new UnsupportedOperationException(
         "logical model update is not available in this build");
   }
}
