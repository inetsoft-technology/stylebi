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

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.erm.vpm.VpmCondition;

import java.util.*;

import static inetsoft.web.wiz.controller.WizDataModelWire.*;

/**
 * Helpers shared by the kind-agnostic data model controllers: the kind vocabulary, lookups in a
 * data model, the contents of a folder and the conversion of server dependency objects to wire
 * {@code Dependent}s.
 */
final class WizDataModelSupport {
   private WizDataModelSupport() {
   }

   /** Required non-blank request text; a missing one is a 400. */
   static String requireText(String field, String value) {
      if(value == null || value.isBlank()) {
         throw new IllegalArgumentException(field + " is required");
      }

      return value;
   }

   /** The kind vocabulary of a {@code Ref}; anything else is a 400 naming the valid values. */
   static String requireKind(String field, String kind) {
      if(KIND_PHYSICAL_VIEW.equals(kind) || KIND_LOGICAL_MODEL.equals(kind) ||
         KIND_VPM.equals(kind) || KIND_FOLDER.equals(kind))
      {
         return kind;
      }

      throw new IllegalArgumentException(
         field + ": expected physicalView, logicalModel, vpm or folder, got \"" + kind + "\"");
   }

   /** The folder of a request: {@code null} for the root ({@code null}, empty, {@code /}). */
   static String folderOrRoot(String folder) {
      return folder == null || folder.isEmpty() || "/".equals(folder) ? null : folder;
   }

   static WizModelKindOps.Kind toKind(String kind) {
      return switch(kind) {
         case KIND_PHYSICAL_VIEW -> WizModelKindOps.Kind.PHYSICAL_VIEW;
         case KIND_LOGICAL_MODEL -> WizModelKindOps.Kind.LOGICAL_MODEL;
         case KIND_VPM -> WizModelKindOps.Kind.VPM;
         default -> throw new IllegalArgumentException("kind: not a model kind: " + kind);
      };
   }

   static String toWire(WizModelKindOps.Kind kind) {
      return switch(kind) {
         case PHYSICAL_VIEW -> KIND_PHYSICAL_VIEW;
         case LOGICAL_MODEL -> KIND_LOGICAL_MODEL;
         case VPM -> KIND_VPM;
      };
   }

   /** The kind of the model, view or VPM with the name (they share one namespace), or null. */
   static String kindOfName(XDataModel dataModel, String name) {
      if(dataModel.getLogicalModel(name) != null) {
         return KIND_LOGICAL_MODEL;
      }

      if(dataModel.getPartition(name) != null) {
         return KIND_PHYSICAL_VIEW;
      }

      if(dataModel.getVirtualPrivateModel(name) != null) {
         return KIND_VPM;
      }

      return null;
   }

   static boolean hasFolder(XDataModel dataModel, String folder) {
      String[] folders = dataModel.getFolders();
      return folders != null && Arrays.asList(folders).contains(folder);
   }

   /** The stored folder of a base logical model or physical view (null: root). */
   static String storedFolder(XDataModel dataModel, String kind, String name) {
      if(KIND_LOGICAL_MODEL.equals(kind)) {
         XLogicalModel model = dataModel.getLogicalModel(name);
         return model == null ? null : model.getFolder();
      }

      if(KIND_PHYSICAL_VIEW.equals(kind)) {
         XPartition view = dataModel.getPartition(name);
         return view == null ? null : view.getFolder();
      }

      return null;
   }

   /** The base logical models stored in the folder. */
   static List<XLogicalModel> logicalModelsIn(XDataModel dataModel, String folder) {
      List<XLogicalModel> result = new ArrayList<>();

      for(String name : dataModel.getLogicalModelNames()) {
         XLogicalModel model = dataModel.getLogicalModel(name);

         if(model != null && Objects.equals(model.getFolder(), folder)) {
            result.add(model);
         }
      }

      return result;
   }

   /** The base physical views stored in the folder. */
   static List<XPartition> viewsIn(XDataModel dataModel, String folder) {
      List<XPartition> result = new ArrayList<>();

      for(String name : dataModel.getPartitionNames()) {
         XPartition view = dataModel.getPartition(name);

         if(view != null && Objects.equals(view.getFolder(), folder)) {
            result.add(view);
         }
      }

      return result;
   }

   /** Every model and view the folder holds, as refs; extended children are not listed. */
   static List<Ref> contents(XDataModel dataModel, String folder) {
      List<Ref> refs = new ArrayList<>();

      for(XLogicalModel model : logicalModelsIn(dataModel, folder)) {
         refs.add(new Ref(KIND_LOGICAL_MODEL, model.getName(), null));
      }

      for(XPartition view : viewsIn(dataModel, folder)) {
         refs.add(new Ref(KIND_PHYSICAL_VIEW, view.getName(), null));
      }

      refs.sort(REF_ORDER);
      return refs;
   }

   /** The logical models, VPMs and physical views that use the physical view. */
   static List<Dependent> viewUsers(XDataModel dataModel, String database, String view) {
      List<Dependent> users = new ArrayList<>();

      for(String name : dataModel.getLogicalModelNames()) {
         XLogicalModel model = dataModel.getLogicalModel(name);

         if(model != null && view.equals(model.getPartition())) {
            users.add(new Dependent(KIND_LOGICAL_MODEL, name, database + "/" + name, null));
         }
      }

      for(String name : dataModel.getVirtualPrivateModelNames()) {
         VirtualPrivateModel vpm = dataModel.getVirtualPrivateModel(name);

         if(vpm == null) {
            continue;
         }

         Enumeration<?> conditions = vpm.getConditions();

         while(conditions.hasMoreElements()) {
            VpmCondition condition = (VpmCondition) conditions.nextElement();

            if(condition.getType() == VpmCondition.PHYSICMODEL && view.equals(condition.getTable())) {
               users.add(new Dependent(KIND_VPM, name, database + "/" + name, null));
               break;
            }
         }
      }

      return users;
   }

   /** Converts the server's dependency objects (entries, models, VPMs) to wire dependents. */
   static List<Dependent> toDependents(String database, Collection<?> objects) {
      Map<String, Dependent> result = new LinkedHashMap<>();

      if(objects != null) {
         for(Object object : objects) {
            Dependent dependent = toDependent(database, object);

            if(dependent != null) {
               result.putIfAbsent(dependent.type() + "|" + dependent.path() + "|" +
                                     dependent.name(), dependent);
            }
         }
      }

      return new ArrayList<>(result.values());
   }

   private static Dependent toDependent(String database, Object object) {
      if(object instanceof AssetEntry entry) {
         return new Dependent(typeOf(entry), entry.getName(), entry.getPath(), null);
      }
      else if(object instanceof XLogicalModel model) {
         return new Dependent(KIND_LOGICAL_MODEL, model.getName(),
                              database + "/" + model.getName(), null);
      }
      else if(object instanceof VirtualPrivateModel vpm) {
         return new Dependent(KIND_VPM, vpm.getName(), database + "/" + vpm.getName(), null);
      }
      else if(object instanceof XPartition view) {
         return new Dependent(KIND_PHYSICAL_VIEW, view.getName(),
                              database + "/" + view.getName(), null);
      }
      else if(object instanceof AssetObject) {
         return new Dependent("other", String.valueOf(object), null, null);
      }

      return null;
   }

   private static String typeOf(AssetEntry entry) {
      if(entry.getType() == null) {
         return "other";
      }

      return switch(entry.getType()) {
         case WORKSHEET -> "worksheet";
         case VIEWSHEET -> "viewsheet";
         case LOGIC_MODEL -> "logicalModel";
         case PARTITION -> "physicalView";
         case VPM -> "vpm";
         case EXTENDED_LOGIC_MODEL -> "extendedModel";
         case EXTENDED_PARTITION -> "extendedView";
         default -> "other";
      };
   }

   /** A short sentence of the dependents for the {@code text} of a dependency item. */
   static String describe(List<Dependent> dependents) {
      if(dependents.isEmpty()) {
         return "no recorded dependents";
      }

      StringBuilder text = new StringBuilder();
      text.append(dependents.size()).append(" dependent(s): ");

      for(int i = 0; i < dependents.size() && i < 20; i++) {
         Dependent dependent = dependents.get(i);
         text.append(i == 0 ? "" : ", ").append(dependent.type()).append(' ')
            .append(dependent.name());
      }

      if(dependents.size() > 20) {
         text.append(", and ").append(dependents.size() - 20).append(" more");
      }

      return text.toString();
   }

   static final Comparator<Ref> REF_ORDER = Comparator
      .comparing((Ref r) -> r.name().toLowerCase())
      .thenComparing(Ref::name)
      .thenComparing(Ref::kind);
}
