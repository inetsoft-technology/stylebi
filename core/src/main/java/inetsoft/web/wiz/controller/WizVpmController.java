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
 * STUB (J1.0): {@code /vpm/get}, {@code /vpm/create}, {@code /vpm/update}, {@code /vpm/test}.
 * Every route answers 501 through {@code UnsupportedOperationException} until lane J3 replaces
 * this file. Never declares a local {@code @ExceptionHandler}.
 */
@RestController
@RequestMapping("/api/wiz/data-models")
public class WizVpmController {
   @PostMapping(value = "/vpm/get", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object get(@RequestBody WizVpmWire.GetRequest request, Principal principal) {
      throw new UnsupportedOperationException("VPM get is not available in this build");
   }

   @PostMapping(value = "/vpm/create", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object create(@RequestBody WizVpmWire.WriteRequest request, Principal principal) {
      throw new UnsupportedOperationException("VPM create is not available in this build");
   }

   @PostMapping(value = "/vpm/update", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object update(@RequestBody WizVpmWire.WriteRequest request, Principal principal) {
      throw new UnsupportedOperationException("VPM update is not available in this build");
   }

   @PostMapping(value = "/vpm/test", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object test(@RequestBody WizVpmWire.TestRequest request, Principal principal) {
      throw new UnsupportedOperationException("VPM test is not available in this build");
   }
}
