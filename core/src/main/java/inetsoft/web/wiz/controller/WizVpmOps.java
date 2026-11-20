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
package inetsoft.web.wiz.controller;

import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.sync.DependencyTransformer;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.VpmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.*;

/**
 * The VPM operations behind the kind-agnostic item controller (find, dependents, rename, delete).
 *
 * <p>A VPM belongs to the data source, not to a data model folder, and every operation on it needs
 * WRITE on the data source (the rule of {@code VpmService.checkVpmPermission}); a caller without it
 * may find the VPM (browse alone hides it) but may not rename or delete it, and its dependents are
 * not listed.</p>
 */
@Component
class WizVpmOps implements WizModelKindOps {
   @Autowired
   WizVpmOps(DataSourceService dataSourceService, VpmService vpmService) {
      this.dataSourceService = dataSourceService;
      this.vpmService = vpmService;
   }

   @Override
   public Kind kind() {
      return Kind.VPM;
   }

   @Override
   public Optional<ModelInfo> find(Ctx ctx, ModelRef ref) throws Exception {
      XDataModel dataModel = ctx.dataModel();
      VirtualPrivateModel vpm = dataModel.getVirtualPrivateModel(ref.name());

      // existence is not hidden from the kind-agnostic routes (the shared name space reveals it
      // anyway); mayDelete and mayRename are the gate, so a denial names the action and path
      if(vpm == null) {
         return Optional.empty();
      }

      return Optional.of(new ModelInfo(Kind.VPM, vpm.getName(), null, null, false,
                                       vpm.getDescription()));
   }

   @Override
   public boolean mayDelete(Ctx ctx, ModelRef ref) throws Exception {
      return mayWrite(ctx);
   }

   @Override
   public boolean mayRename(Ctx ctx, ModelRef ref) throws Exception {
      return mayWrite(ctx);
   }

   @Override
   public List<DependentRef> dependents(Ctx ctx, ModelRef ref) throws Exception {
      Map<String, DependentRef> unique = new LinkedHashMap<>();

      if(!mayWrite(ctx)) {
         return List.of();
      }

      for(WizDataModelWire.Dependent dependent : WizDataModelSupport.toDependents(
         ctx.database(), DependencyTransformer.getVPMDependencies(
            ctx.database() + "/" + ref.name())))
      {
         unique.putIfAbsent(dependent.type() + "|" + dependent.path() + "|" + dependent.name(),
                            new DependentRef(dependent.type(), dependent.name(),
                                             dependent.path(), dependent.element()));
      }

      return new ArrayList<>(unique.values());
   }

   @Override
   public OpResult rename(Ctx ctx, ModelRef ref, String newName, String description)
      throws Exception
   {
      vpmService.rename(ctx.database(), ref.name(), newName, description, ctx.principal());
      return new OpResult(true, null, null, List.of(new ModelRef(Kind.VPM, newName, null)));
   }

   @Override
   public OpResult delete(Ctx ctx, ModelRef ref, boolean force) throws Exception {
      vpmService.delete(ctx.database(), List.of(ref.name()), ctx.principal());
      return new OpResult(true, null, null, List.of(ref));
   }

   private boolean mayWrite(Ctx ctx) throws Exception {
      Principal principal = ctx.principal();
      return dataSourceService.checkPermission(ctx.database(), ResourceAction.WRITE, principal);
   }

   private final DataSourceService dataSourceService;
   private final VpmService vpmService;
}
