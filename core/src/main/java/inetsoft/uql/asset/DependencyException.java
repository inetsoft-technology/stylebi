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
package inetsoft.uql.asset;

import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.util.Catalog;

import java.util.ArrayList;
import java.util.List;

/**
 * Dependency exception.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class DependencyException extends ConfirmException {
   /**
    * Constructor.
    */
   public DependencyException(Object target) {
      super();

      this.target = target;
      deps = new ArrayList();
      exps = new ArrayList<>();
   }

   public DependencyException(List<DependencyException> depends) {
      super();
      deps = new ArrayList();
      exps = new ArrayList<>();

      if(depends != null) {
         exps = depends;
      }
   }

   /**
    * Add one dependency.
    */
   public void addDependency(Object obj) {
      if(obj instanceof DependencyException) {
         exps.add((DependencyException) obj);
      }
      else {
         deps.add(obj);
      }
   }

   /**
    * Batch add dependencies.
    */
   public void addDependencies(Object[] objs) {
      for(int i = 0; i < objs.length; i++) {
         addDependency(objs[i]);
      }
   }

   /**
    * Remove all the dependencies.
    */
   public void removeDependencies() {
      deps.clear();
      exps.clear();
   }

   /**
    * Check if is empty.
    */
   public boolean isEmpty() {
      return (deps == null || deps.size() == 0) && (exps == null || exps.size() == 0);
   }

   /**
    * Get dependency entries.
    */
   public List getEntries() {
      List entries = new ArrayList(deps);

      for(DependencyException exp : exps) {
         entries.addAll(exp.getEntries());
      }

      return entries;
   }

   /**
    * Get the message.
    */
   @Override
   public String getMessage() {
      return getMessage(true);
   }

   /**
    * Get the message.
    * @param confirm add confirm message if true, else not.
    */
   public String getMessage(boolean confirm) {
      if(isEmpty()) {
         return "";
      }

      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < deps.size(); i++) {
         if(i > 0) {
            sb.append(",\n");
         }

         sb.append(toString(deps.get(i), confirm));
      }

      if(deps.size() > 0) {
         sb.append(' ');
         sb.append(Catalog.getCatalog().getString("depend(s) on"));
         sb.append(' ');
         sb.append(toString(target, confirm));
      }

      for(DependencyException exp : exps) {
         if(sb.length() > 0) {
            sb.append("; ");
         }

         sb.append(toString(exp, confirm));
      }

      sb.append("!");

      // this may be only is a wrapper, so check self has content.
      if(deps.size() > 0 && confirm) {
         sb.append(" ")
            .append(Catalog.getCatalog().getString("Proceed"))
            .append("?");
      }

      return sb.toString();
   }

   /**
    * Get the string representation of an object.
    */
   private String toString(Object obj, boolean confirm) {
      if(obj instanceof AssetEntry) {
         return ((AssetEntry) obj).getDescription();
      }
      else if(obj instanceof DependencyException) {
         return ((DependencyException) obj).getMessage(confirm);
      }
      else if(obj instanceof XAttribute) {
         XAttribute attr = (XAttribute) obj;
         String entity = attr.getEntity();
         String name = attr.getName();
         return entity == null ? name : entity + "." + name;
      }
      else if(obj instanceof XLogicalModel) {
         XLogicalModel model = (XLogicalModel) obj;
         return model == null ? obj.toString() : model.getName();
      }
      else if(obj instanceof VirtualPrivateModel) {
         return ((VirtualPrivateModel) obj).getName();
      }
      else if(obj instanceof XPartition) {
         return ((XPartition) obj).getName();
      }

      return obj == null ? "" : obj.toString();
   }

   private Object target;
   private List deps;
   private List<DependencyException> exps;
}
