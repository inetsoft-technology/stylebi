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

import inetsoft.uql.erm.XDataModel;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

/**
 * The SPI between the kind-agnostic controllers (browse, item, folder) and the three kinds.
 * {@code WizLogicalModelOps} (J2), {@code WizVpmOps} (J3) and {@code WizPhysicalViewOps} (J1)
 * implement it; the item controller injects {@code List<WizModelKindOps>} and dispatches on
 * {@link #kind()}. A kind with no implementation yet answers {@code UNKNOWN: not available in
 * this build}.
 */
interface WizModelKindOps {
   Kind kind();

   /** Finds the model under the caller's view; empty means NOT_FOUND. */
   Optional<ModelInfo> find(Ctx ctx, ModelRef ref) throws Exception;

   /** The DELETE-equivalent gate of the kind. */
   boolean mayDelete(Ctx ctx, ModelRef ref) throws Exception;

   boolean mayRename(Ctx ctx, ModelRef ref) throws Exception;

   List<DependentRef> dependents(Ctx ctx, ModelRef ref) throws Exception;

   /** Renames; {@code description} is already resolved by the caller. */
   OpResult rename(Ctx ctx, ModelRef ref, String newName, String description) throws Exception;

   OpResult delete(Ctx ctx, ModelRef ref, boolean force) throws Exception;

   enum Kind {
      PHYSICAL_VIEW, LOGICAL_MODEL, VPM
   }

   record Ctx(String database, XDataModel dataModel, Principal principal) {
   }

   record ModelRef(Kind kind, String name, String parent) {
   }

   record ModelInfo(Kind kind, String name, String folder, String parent, boolean extended,
                    String description)
   {
   }

   record DependentRef(String type, String name, String path, String element) {
   }

   record OpResult(boolean ok, String reason, String message, List<ModelRef> affected) {
   }
}
