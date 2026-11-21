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
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.DependencyException;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.util.Tool;
import inetsoft.web.portal.controller.database.LogicalModelService;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * The logical model implementation of {@link WizModelKindOps}: what the kind-agnostic item
 * controller (dependencies, delete, rename) needs from a logical model. Every check is the one
 * {@code LogicalModelService} makes itself, applied first so a denial is decided before anything
 * is touched; the service remains the authority.
 *
 * <p>Only base models are handled here. An extended model (a {@code parent}) is found and listed
 * but is never renamed or deleted by this tool: the services look a rename up in the base
 * registry only, and a delete of an extended model leaves dependency bookkeeping to the base.</p>
 */
@Component
final class WizLogicalModelOps implements WizModelKindOps {
   WizLogicalModelOps(LogicalModelService modelService) {
      this.modelService = modelService;
   }

   @Override
   public Kind kind() {
      return Kind.LOGICAL_MODEL;
   }

   @Override
   public Optional<ModelInfo> find(Ctx ctx, ModelRef ref) {
      XLogicalModel model = lookup(ctx.dataModel(), ref);

      if(model == null) {
         return Optional.empty();
      }

      XLogicalModel base = model.getBaseModel();
      String folder = base == null ? model.getFolder() : base.getFolder();

      if(!modelService.checkPermission(ctx.database(), folder, model.getName(),
                                       model.getConnection(), ResourceAction.READ,
                                       ctx.principal()))
      {
         return Optional.empty();
      }

      return Optional.of(new ModelInfo(Kind.LOGICAL_MODEL, model.getName(), blankToNull(folder),
                                       base == null ? null : base.getName(), base != null,
                                       model.getDescription()));
   }

   /** DELETE on the model, resolved with its STORED folder (LMS removeModel). */
   @Override
   public boolean mayDelete(Ctx ctx, ModelRef ref) {
      return has(ctx, ref, ResourceAction.DELETE);
   }

   /** A rename needs DELETE on the model as well (LMS renameModel). */
   @Override
   public boolean mayRename(Ctx ctx, ModelRef ref) {
      return has(ctx, ref, ResourceAction.DELETE);
   }

   @Override
   public List<DependentRef> dependents(Ctx ctx, ModelRef ref) {
      XLogicalModel model = lookup(ctx.dataModel(), ref);

      if(model == null) {
         return List.of();
      }

      List<DependentRef> result = new ArrayList<>();

      for(WizDataModelWire.Dependent dependent : dependentsOf(model.getOuterDependencies())) {
         result.add(new DependentRef(dependent.type(), dependent.name(), dependent.path(),
                                     dependent.element()));
      }

      return result;
   }

   /**
    * Renames a base model. {@code description} was resolved by the caller (the service overwrites
    * the stored description with whatever it is given).
    */
   @Override
   public OpResult rename(Ctx ctx, ModelRef ref, String newName, String description)
      throws Exception
   {
      if(!Tool.isEmptyString(ref.parent())) {
         return new OpResult(false, WizDataModelWire.EXTENDED_UNSUPPORTED,
                             "extended models are not supported by this tool in this version",
                             List.of());
      }

      XLogicalModel model = ctx.dataModel().getLogicalModel(ref.name());

      if(model == null) {
         return new OpResult(false, WizDataModelWire.NOT_FOUND,
                             "no logical model named \"" + ref.name() + "\" in " + ctx.database(),
                             List.of());
      }

      modelService.renameModel(ctx.database(), model.getFolder(), newName, ref.name(),
                               description, ctx.principal());
      return new OpResult(true, null, null,
                          List.of(new ModelRef(Kind.LOGICAL_MODEL, newName, null)));
   }

   /** Deletes a base model; its extended children go with it and are listed in {@code affected}. */
   @Override
   public OpResult delete(Ctx ctx, ModelRef ref, boolean force) throws Exception {
      if(!Tool.isEmptyString(ref.parent())) {
         return new OpResult(false, WizDataModelWire.EXTENDED_UNSUPPORTED,
                             "extended models are not supported by this tool in this version",
                             List.of());
      }

      XLogicalModel model = ctx.dataModel().getLogicalModel(ref.name());

      if(model == null) {
         return new OpResult(false, WizDataModelWire.NOT_FOUND,
                             "no logical model named \"" + ref.name() + "\" in " + ctx.database(),
                             List.of());
      }

      List<ModelRef> affected = new ArrayList<>();
      affected.add(new ModelRef(Kind.LOGICAL_MODEL, ref.name(), null));
      String[] children = model.getLogicalModelNames();

      for(int i = 0; children != null && i < children.length; i++) {
         affected.add(new ModelRef(Kind.LOGICAL_MODEL, children[i], ref.name()));
      }

      modelService.removeModel(ctx.database(), model.getFolder(), ref.name(), null,
                               ctx.principal());
      return new OpResult(true, null, null, affected);
   }

   /** The recorded dependents of a model or element, as the wire reports them. */
   static List<WizDataModelWire.Dependent> dependentsOf(Object[] outerDependencies) {
      Object[] entries = DependencyException.getExistingDependencies(outerDependencies);
      List<WizDataModelWire.Dependent> result = new ArrayList<>();

      for(int i = 0; entries != null && i < entries.length; i++) {
         result.add(dependent(entries[i]));
      }

      return result;
   }

   static WizDataModelWire.Dependent dependent(Object entry) {
      if(entry instanceof AssetEntry) {
         AssetEntry asset = (AssetEntry) entry;
         String type = asset.isWorksheet() ? "worksheet" : asset.isViewsheet() ? "viewsheet" :
            "other";
         return new WizDataModelWire.Dependent(type, asset.getDescription(), asset.getPath(),
                                               null);
      }
      else if(entry instanceof XLogicalModel) {
         return new WizDataModelWire.Dependent("logicalModel", ((XLogicalModel) entry).getName(),
                                               null, null);
      }
      else if(entry instanceof XPartition) {
         return new WizDataModelWire.Dependent("physicalView", ((XPartition) entry).getName(),
                                               null, null);
      }
      else if(entry instanceof VirtualPrivateModel) {
         return new WizDataModelWire.Dependent("vpm", ((VirtualPrivateModel) entry).getName(),
                                               null, null);
      }

      return new WizDataModelWire.Dependent("other", String.valueOf(entry), null, null);
   }

   private boolean has(Ctx ctx, ModelRef ref, ResourceAction action) {
      XLogicalModel model = lookup(ctx.dataModel(), ref);

      if(model == null) {
         return false;
      }

      XLogicalModel base = model.getBaseModel();
      String folder = base == null ? model.getFolder() : base.getFolder();
      return modelService.checkPermission(ctx.database(), folder, model.getName(),
                                          model.getConnection(), action, ctx.principal());
   }

   static XLogicalModel lookup(XDataModel dataModel, ModelRef ref) {
      if(dataModel == null) {
         return null;
      }

      if(Tool.isEmptyString(ref.parent())) {
         return dataModel.getLogicalModel(ref.name());
      }

      XLogicalModel base = dataModel.getLogicalModel(ref.parent());
      return base == null ? null : base.getLogicalModel(ref.name());
   }

   private static String blankToNull(String value) {
      return Tool.isEmptyString(value) ? null : value;
   }

   private final LogicalModelService modelService;
}
