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
 * The VPM wire records (design 4.1; {@code definition} is the canonical VPM definition of design
 * 3.4 and section 7, the condition tree). {@code revision} is
 * {@link WizDataModelRevision#of} of the definition a get returns or a write read back;
 * {@code baseRevision} on an update is the revision the caller read, and a stored VPM that no
 * longer has it is refused {@code STALE_MODEL} before anything is written. Absent keeps the
 * unguarded behavior.
 */
public final class WizVpmWire {
   private WizVpmWire() {
   }

   public record GetRequest(String database, String name) {
   }

   public record GetResponse(boolean ok, Map<String, Object> definition, String folder,
                             boolean readOnly, String revision)
   {
      public GetResponse(boolean ok, Map<String, Object> definition, String folder,
                         boolean readOnly)
      {
         this(ok, definition, folder, readOnly, null);
      }
   }

   public record WriteRequest(String database, String name, Map<String, Object> definition,
                              Boolean dryRun, String baseRevision)
   {
      public WriteRequest(String database, String name, Map<String, Object> definition,
                          Boolean dryRun)
      {
         this(database, name, definition, dryRun, null);
      }
   }

   public record WriteResponse(boolean ok, String name, Map<String, Object> definition,
                               List<String> warnings, List<WizDataModelWire.Dependent> dependents,
                               String revision)
   {
      public WriteResponse(boolean ok, String name, Map<String, Object> definition,
                           List<String> warnings, List<WizDataModelWire.Dependent> dependents)
      {
         this(ok, name, definition, warnings, dependents, null);
      }
   }

   public record TestRequest(String database, String name, Map<String, Object> definition,
                             String principalType, String principalName)
   {
   }

   public record TestResponse(boolean ok, String result) {
   }
}
