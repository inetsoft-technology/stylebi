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
package inetsoft.web.composer.vs;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.viewsheet.model.VSObjectModel;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods for creating the Component Tree in the visual composer.
 * This implementation needs to be improved. Currently not very efficient.
 *
 * @since 12.3
 */
@Component
public class VSObjectTreeService {
   @Autowired
   public VSObjectTreeService(VSObjectModelFactoryService objectModelService) {
      this.objectModelService = objectModelService;
   }

   public VSObjectTreeNode getObjectTree(RuntimeViewsheet rvs) throws Exception {
      Viewsheet viewsheet = rvs.getViewsheet();

      if(viewsheet == null) {
         return new VSObjectTreeNode();
      }

      Assembly[] assemblies = viewsheet.getAssemblies();

      List<VSObjectTreeNode> objects = new ArrayList<>();

      VSObjectTreeNode root = new VSObjectTreeNode();
      root.setModel(null);

      for(Assembly assembly: assemblies) {
         // if object is contained, its container will add it as a child node
         if(assembly instanceof VSAssembly && ((VSAssembly) assembly).getContainer() == null) {
            if(assembly instanceof ContainerVSAssembly) {
               ContainerVSAssembly container = (ContainerVSAssembly) assembly;

               VSObjectTreeNode node = addChildren(container, rvs);
               objects.add(node);
            }
            else {
               VSAssembly vsAssembly = (VSAssembly) assembly;
               VSObjectModel object = objectModelService.createModel(vsAssembly, rvs);

               VSObjectTreeNode node = new VSObjectTreeNode();
               node.setModel(object);
               node.setExpanded(true);

               objects.add(node);
            }
         }
      }

      root.setChildren(objects);

      return root;
   }

   private VSObjectTreeNode addChildren(ContainerVSAssembly container, RuntimeViewsheet rvs) {
      VSObjectModel containerModel = objectModelService.createModel(container, rvs);

      VSObjectTreeNode node = new VSObjectTreeNode();
      node.setModel(containerModel);
      node.setExpanded(true);
      List<VSObjectTreeNode> children = new ArrayList<>();

      //Bug #10889 If the container is a tab and its child's absoluteName is different with the tab label, show both in the component tree.
      if(container instanceof TabVSAssembly) {
         TabVSAssembly tab = (TabVSAssembly) container;
         String[] assemblyNames = tab.getAssemblies();
         String[] labels = tab.getLabels();

         for(int i = 0; i < assemblyNames.length; i++) {
            Viewsheet viewsheet = rvs.getViewsheet();
            VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(assemblyNames[i]);

            if(assembly == null) {
               continue;
            }

            VSObjectModel object = objectModelService.createModel(assembly, rvs);
            String nodeLabel = (i < labels.length && !assemblyNames[i].equals(labels[i])) ?
               assemblyNames[i] + " (" + labels[i] + ")" : assemblyNames[i];

            if(assembly instanceof ContainerVSAssembly) {
               ContainerVSAssembly childContainer = (ContainerVSAssembly) assembly;

               VSObjectTreeNode childContainerNode = addChildren(childContainer, rvs);
               childContainerNode.setNodeLabel(nodeLabel);
               children.add(childContainerNode);
            }
            else if(assembly.getContainer() != null) {
               VSObjectTreeNode child = new VSObjectTreeNode();
               child.setModel(object);
               child.setExpanded(true);
               child.setNodeLabel(nodeLabel);

               children.add(child);
            }
         }
      }
      else {
         for(String assemblyName: container.getAssemblies()) {
            Viewsheet viewsheet = rvs.getViewsheet();
            VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(assemblyName);
            VSObjectModel object = objectModelService.createModel(assembly, rvs);

            if(assembly instanceof ContainerVSAssembly) {
               ContainerVSAssembly childContainer = (ContainerVSAssembly) assembly;

               children.add(addChildren(childContainer, rvs));
            }
            else if(assembly.getContainer() != null) {
               VSObjectTreeNode child = new VSObjectTreeNode();
               child.setModel(object);
               child.setExpanded(true);

               children.add(child);
            }
         }
      }

      node.setChildren(children);
      return node;
   }

   private final VSObjectModelFactoryService objectModelService;
}
