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

import java.util.List;
import java.util.Map;

/**
 * STUB (J1.0): the physical view wire records (phase 2). Replaced wholesale by lane J4, which owns
 * this file; nobody else edits it. The envelopes follow design 4.1.
 */
public final class WizPhysicalViewWire {
   private WizPhysicalViewWire() {
   }

   public record GetRequest(String database, String name, String parent) {
   }

   public record GetResponse(boolean ok, Map<String, Object> definition, String folder,
                             boolean readOnly)
   {
   }

   public record WriteRequest(String database, String name, String parent,
                              Map<String, Object> definition, Boolean dryRun)
   {
   }

   public record Warning(String code, String message, String table1, String table2) {
   }

   public record WriteResponse(boolean ok, String name, String folder,
                               Map<String, Object> definition, List<Warning> warnings,
                               List<WizDataModelWire.Dependent> dependents)
   {
   }
}
