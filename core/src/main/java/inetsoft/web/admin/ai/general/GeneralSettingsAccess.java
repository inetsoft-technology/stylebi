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
package inetsoft.web.admin.ai.general;

import inetsoft.web.admin.general.*;
import inetsoft.web.admin.general.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.EnumMap;

/**
 * Bridges the {@link GeneralSubModel} catalog to the real sub-services
 * {@code GeneralSettingsPageController} itself wraps -- never calls that controller, matching the
 * "wrap the service layer, not the EM controller" decision the providers and presentation areas
 * both made.
 *
 * <p>The EM controller's POST is not all-or-nothing -- {@code setPageModel} null-checks each
 * sub-model and skips the ones that are absent -- so going around it is not about avoiding a
 * seven-sub-model payload. It is the usual reason: that controller's {@code @Secured} gate is
 * checkPermission-mediated and inert for a wiz-tagged caller, so the admin-chat area has to carry
 * its own. Going service by service additionally keeps each write scoped to exactly what the plan
 * covers, with a whole model captured per sub-model for rollback.
 *
 * <p>The six readers differ only in whether they take a {@link Principal}; all five writers have
 * the identical {@code setModel(model, principal)} shape. Normalized here, once, into one uniform
 * reader/writer pair per sub-model so the plan and apply services never need to know which shape
 * they are calling.
 *
 * <p>There is no org-scope parameter anywhere in this area, unlike presentation's {@code global}
 * boolean: every one of these services writes global {@code SreeEnv} properties and takes no scope
 * argument at all. See {@code GeneralChangeRequest} for why a caller-supplied {@code scope} is
 * refused rather than ignored.
 */
@Component
public class GeneralSettingsAccess {
   @Autowired
   public GeneralSettingsAccess(LocalizationSettingsService localization,
                                MVSettingsService mv,
                                CacheSettingsService cache,
                                EmailSettingsService email,
                                PerformanceSettingsService performance,
                                DataSpaceSettingsService dataSpace)
   {
      adapters = new EnumMap<>(GeneralSubModel.class);

      adapters.put(GeneralSubModel.LOCALIZATION, new Adapter(
         principal -> localization.getModel(),
         (model, principal) ->
            localization.setModel((LocalizationSettingsModel) model, principal)));

      adapters.put(GeneralSubModel.MV, new Adapter(
         mv::getModel,
         (model, principal) -> mv.setModel((MVSettingsModel) model, principal)));

      adapters.put(GeneralSubModel.CACHE, new Adapter(
         principal -> cache.getModel(),
         (model, principal) -> cache.setModel((CacheSettingsModel) model, principal)));

      adapters.put(GeneralSubModel.EMAIL, new Adapter(
         principal -> email.getModel(),
         (model, principal) -> email.setModel((EmailSettingsModel) model, principal)));

      adapters.put(GeneralSubModel.PERFORMANCE, new Adapter(
         principal -> performance.getModel(),
         (model, principal) -> performance.setModel((PerformanceSettingsModel) model, principal)));

      // No writer: DataSpaceSettingsService has no setModel. GeneralSubModel.writable() is the
      // declared contract and GeneralChangePlanService refuses the sub-model long before a write
      // could be attempted; this throw is the backstop that keeps "read-only" true even if some
      // future caller reaches write() directly.
      adapters.put(GeneralSubModel.DATA_SPACE, new Adapter(
         dataSpace::getModel,
         (model, principal) -> {
            throw new IllegalArgumentException(
               "dataSpace is read-only: the storage types are set in inetsoft.yaml at deployment " +
               "time and DataSpaceSettingsService has no setModel, so there is no runtime write " +
               "path. To take a storage backup, use the storage backup tool instead.");
         }));
   }

   /** Reads the current value of one sub-model. */
   public Object read(GeneralSubModel subModel, Principal principal) throws Exception {
      return adapters.get(subModel).reader.read(principal);
   }

   /** Writes a fully-resolved (never partial) sub-model value -- callers must merge a partial
    * {@code spec} onto a fresh {@link #read} result themselves before calling this, since every
    * real {@code setModel} expects a complete object and none accepts a patch. */
   public void write(GeneralSubModel subModel, Object model, Principal principal) throws Exception {
      adapters.get(subModel).writer.write(model, principal);
   }

   @FunctionalInterface
   private interface Reader {
      Object read(Principal principal) throws Exception;
   }

   @FunctionalInterface
   private interface Writer {
      void write(Object model, Principal principal) throws Exception;
   }

   private record Adapter(Reader reader, Writer writer) {
   }

   private final EnumMap<GeneralSubModel, Adapter> adapters;
}
