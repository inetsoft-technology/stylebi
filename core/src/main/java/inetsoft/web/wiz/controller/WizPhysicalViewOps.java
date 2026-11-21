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

import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.PhysicalModelManagerService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.*;

import static inetsoft.web.wiz.controller.WizDataModelWire.*;

/**
 * The stateless physical view operations of the data model bridge: find, dependents, rename and
 * delete. None opens a runtime partition, so none can leak one; the session operations (get,
 * create, update) belong to phase 2.
 *
 * <p>A view needs WRITE on the data source, or on its data model folder, for every operation, as
 * {@code PhysicalModelManagerService.checkWritePermission} requires; a caller without it does not
 * see the view at all ({@link #find} is empty).</p>
 */
@Component
class WizPhysicalViewOps implements WizModelKindOps {
   @Autowired
   WizPhysicalViewOps(DataSourceService dataSourceService,
                      PhysicalModelManagerService physicalModelService)
   {
      this.dataSourceService = dataSourceService;
      this.physicalModelService = physicalModelService;
   }

   @Override
   public Kind kind() {
      return Kind.PHYSICAL_VIEW;
   }

   @Override
   public Optional<ModelInfo> find(Ctx ctx, ModelRef ref) throws Exception {
      XDataModel dataModel = ctx.dataModel();
      boolean extended = !StringUtils.isEmpty(ref.parent());
      XPartition base = extended ? dataModel.getPartition(ref.parent()) : null;
      XPartition view = extended ? (base == null ? null : base.getPartition(ref.name())) :
         dataModel.getPartition(ref.name());

      if(view == null) {
         return Optional.empty();
      }

      String folder = extended ? base.getFolder() : view.getFolder();

      if(!dataSourceService.checkPermission(
         ctx.database(), folder, ResourceAction.WRITE, ctx.principal()))
      {
         return Optional.empty();
      }

      return Optional.of(new ModelInfo(
         Kind.PHYSICAL_VIEW, ref.name(), folder, extended ? ref.parent() : null, extended,
         view.getDescription()));
   }

   @Override
   public boolean mayDelete(Ctx ctx, ModelRef ref) throws Exception {
      return find(ctx, ref).isPresent();
   }

   @Override
   public boolean mayRename(Ctx ctx, ModelRef ref) throws Exception {
      return find(ctx, ref).isPresent();
   }

   @Override
   public List<DependentRef> dependents(Ctx ctx, ModelRef ref) throws Exception {
      List<Dependent> all = new ArrayList<>(
         WizDataModelSupport.viewUsers(ctx.dataModel(), ctx.database(), ref.name()));
      all.addAll(WizDataModelSupport.toDependents(
         ctx.database(),
         DependencyTool.getPartitionDependencies(ctx.database() + "/" + ref.name())));
      Map<String, DependentRef> unique = new LinkedHashMap<>();

      for(Dependent dependent : all) {
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
      if(!StringUtils.isEmpty(ref.parent())) {
         return new OpResult(false, EXTENDED_UNSUPPORTED,
                             "extended views are renamed with their base view only", List.of());
      }

      ModelInfo info = find(ctx, ref).orElseThrow(
         () -> new java.io.FileNotFoundException(ctx.database() + "/" + ref.name()));
      physicalModelService.renameModel(
         ctx.database(), info.folder(), ref.name(), newName, description, ctx.principal());

      return new OpResult(true, null, null, List.of(new ModelRef(Kind.PHYSICAL_VIEW, newName, null)));
   }

   @Override
   public OpResult delete(Ctx ctx, ModelRef ref, boolean force) throws Exception {
      ModelInfo info = find(ctx, ref).orElseThrow(
         () -> new java.io.FileNotFoundException(ctx.database() + "/" + ref.name()));
      List<ModelRef> affected = new ArrayList<>();
      affected.add(ref);

      if(!info.extended()) {
         XPartition view = ctx.dataModel().getPartition(ref.name());

         for(String child : view.getPartitionNames()) {
            affected.add(new ModelRef(Kind.PHYSICAL_VIEW, child, ref.name()));
         }
      }

      boolean removed = physicalModelService.removeModel(
         ctx.database(), info.folder(), ref.name(), ref.parent(), force, ctx.principal());

      if(!removed) {
         return new OpResult(false, VIEW_IN_USE,
                             "the physical view \"" + ref.name() + "\" is in use", List.of());
      }

      return new OpResult(true, null, null, affected);
   }

   private final DataSourceService dataSourceService;
   private final PhysicalModelManagerService physicalModelService;
}
