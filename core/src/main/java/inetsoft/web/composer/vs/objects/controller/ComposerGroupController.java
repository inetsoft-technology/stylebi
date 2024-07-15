/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.PopVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.web.composer.vs.VSObjectTreeNode;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.command.PopulateVSObjectTreeCommand;
import inetsoft.web.composer.vs.objects.event.GroupVSObjectsEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;

/**
 * Controller that processes group events.
 */
@Controller
public class ComposerGroupController {
   /**
    * Creates a new instance of <tt>ComposerGroupController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated
    *                            with the WebSocket session.
    */
   @Autowired
   public ComposerGroupController(RuntimeViewsheetRef runtimeViewsheetRef,
                                  PlaceholderService placeholderService,
                                  ViewsheetService viewsheetService,
                                  VSObjectTreeService vsObjectTreeService,
                                  VSObjectPropertyService vsObjectPropertyService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.viewsheetService = viewsheetService;
      this.vsObjectTreeService = vsObjectTreeService;
      this.vsObjectPropertyService = vsObjectPropertyService;
   }

   /**
    * Put assemblies into a group.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/group")
   public void groupComponents(@Payload GroupVSObjectsEvent event, @LinkUri String linkUri,
                               Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      final String runtimeId = runtimeViewsheetRef.getRuntimeId();
      final RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      final Viewsheet viewsheet = rvs.getViewsheet();
      final Set<String> childs = new HashSet<>();
      String oname = null;
      // remove the old group container after the tab has been updated
      // so a tab won't disappear when merging a group container in a tab
      // with another component
      final List<VSAssembly> oldGroups = new ArrayList<>();
      Set<String> tabChildren = new HashSet<>();
      final List<TabVSAssembly> tabParents = new ArrayList<>();
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockWrite();

      try {
         for(String name : event.objects()) {
            final VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(name);

            if(assembly.getContainer() instanceof TabVSAssembly) {
               tabParents.add((TabVSAssembly) assembly.getContainer());
            }

            if(isGroupContainer(assembly)) {
               GroupContainerVSAssembly selGroup = (GroupContainerVSAssembly) assembly;

               if(oname == null) {
                  oname = selGroup.getName();
               }

               oldGroups.add(selGroup);

               String[] subChilds = selGroup.getAssemblies();

               for(int j = 0; j < subChilds.length; j++) {
                  childs.add(subChilds[j]);
               }
            }
            else {
               childs.add(name);
            }

            if(assembly instanceof TabVSAssembly) {
               TabVSAssembly tab = (TabVSAssembly) assembly;
               tabChildren.addAll(Arrays.asList(tab.getAssemblies()));
            }
         }

         // children of tab shouldn't be grouped together with tab
         childs.removeAll(tabChildren);

         final String[] childsName = new String[childs.size()];
         childs.toArray(childsName);

         // grouped element shouldn't exist independently in layout
         childs.forEach(c -> placeholderService.removeLayoutAssembly(viewsheet, c));
         Point[] locs = GroupContainerVSAssembly.getUpperLeftAndBottomRight(viewsheet, childsName);
         Point upperLeft = locs[0];
         Point bottomRight = locs[1];

         Point pos = new Point(upperLeft.x, upperLeft.y);
         Dimension size = new Dimension(bottomRight.x - upperLeft.x, bottomRight.y - upperLeft.y);
         GroupContainerVSAssembly group = (GroupContainerVSAssembly) VSEventUtil
            .createVSAssembly(rvs, AbstractSheet.GROUPCONTAINER_ASSET);
         String nname = group.getName();
         updateGroupContainer(group, viewsheet, childsName, pos, size);
         VSEventUtil.fixGroupContainerSize(group);

         // if the old group container is a child of tab, we need to replace the tab
         // child with the new group container name.
         if(oname != null) {
            for(TabVSAssembly tab : tabParents) {
               String[] children = tab.getAssemblies();

               for(int i = 0; i < children.length; i++) {
                  if(oname.equals(children[i])) {
                     children[i] = nname;
                     tab.setAssemblies(children);
                     tab.setSelectedValue(nname);
                     break;
                  }
               }
            }
         }

         Arrays.stream(childsName)
            .map(c -> viewsheet.getAssembly(c))
            .map(c -> ((VSAssembly) c).getContainer())
            .filter(c -> c instanceof TabVSAssembly)
            .distinct()
            .map(c -> (TabVSAssembly) c)
            .forEach(tab -> {
               String[] children = tab.getAssemblies();
               String[] labels = tab.getLabelsValue();

               for(int i = 0; i < children.length; i++) {
                  for(String name : event.objects()) {
                     if(name.equals(children[i])) {
                        if(children[i].equals(tab.getSelected())) {
                           tab.setSelectedValue(nname);
                        }

                        if(labels != null && i < labels.length && name.equals(labels[i])) {
                           labels[i] = nname;
                        }

                        children[i] = nname;
                        break;
                     }
                  }
               }

               tab.setAssemblies(children);

               // align the children if the group caused the position of
               // the child to be above or left of tab
               tab.setPixelOffset(tab.getPixelOffset());
            });

         //if the group cause dependency circle, throw InvalidDependencyException
         // to avoid stack overflow, the exception handler will rollback the changes.
         if(checkDependency(viewsheet)) {
            throw new InvalidDependencyException(Catalog.getCatalog(principal).getString(
               "common.dependencyCycle"));
         }

         // remove the old groups after a new container is created
         for(VSAssembly vsobj : oldGroups) {
            placeholderService.removeVSAssembly(rvs, linkUri, vsobj, dispatcher, false, true);
         }

         if(oname != null && !group.getAbsoluteName().equals(oname)) {
            viewsheet.renameAssembly(group.getAbsoluteName(), oname);
         }

         final Assembly[] arr = { group };

         if(checkDependency(viewsheet)) {
            throw new InvalidDependencyException(Catalog.getCatalog(principal).getString(
               "common.dependencyCycle"));
         }

         // grouped element shouldn't exist in pop component field
         updatePopComponent(viewsheet);

         for(TabVSAssembly tab : tabParents) {
            ((TabVSAssemblyInfo) tab.getVSAssemblyInfo()).setSelectedValue(tab.getSelected());
            ((TabVSAssemblyInfo) tab.getVSAssemblyInfo()).setSelected(null);
            placeholderService.execute(rvs, tab.getAbsoluteName(), linkUri, VSAssembly.VIEW_CHANGED,
               dispatcher);
         }

         ChangedAssemblyList clist = placeholderService.createList(false, dispatcher, rvs, linkUri);
         box.reset(null, arr, clist, false, false, null);
         placeholderService.execute(rvs, group.getName(), linkUri, clist, dispatcher, true);
         //VSEventUtil.fixTipOrPopAssemblies(rvs, command);
         placeholderService.layoutViewsheet(rvs, rvs.getID(), linkUri, dispatcher);
         placeholderService.addLayerCommand(viewsheet, dispatcher);
         VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
         PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
         dispatcher.sendCommand(treeCommand);
      }
      finally {
         box.unlockWrite();
      }
   }

   /**
    * Ungroup assemblies.
    *
    * @param objectName the object identifier in the vs.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/ungroup/{objectName}")
   public void ungroup(@DestinationVariable("objectName") String objectName,
                       @LinkUri String linkUri, Principal principal,
                       CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(objectName);
      GroupContainerVSAssembly group;

      if(assembly instanceof GroupContainerVSAssembly) {
         group = (GroupContainerVSAssembly) assembly;
      }
      else if(assembly.getContainer() != null
         && assembly.getContainer() instanceof GroupContainerVSAssembly)
      {
         group = (GroupContainerVSAssembly) assembly.getContainer();
      }
      else {
         return;
      }

      updateTab(group, viewsheet);
      placeholderService.removeVSAssembly(rvs, linkUri, group, dispatcher, false, true);
      placeholderService.updateZIndex(viewsheet, group);
      placeholderService.layoutViewsheet(rvs, rvs.getID(), linkUri, dispatcher,
         objectName, new ChangedAssemblyList());

      VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
      PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
      dispatcher.sendCommand(treeCommand);
   }

  /**
    * If a grouped container is ungrouped, replace it with its 1st child
    * in the containing tab.
    */
   private void updateTab(GroupContainerVSAssembly assembly, Viewsheet vs) {
      // replace group container with 1st child in tab
      VSAssembly container = assembly.getContainer();

      if(container instanceof TabVSAssembly) {
         String[] mychildren = assembly.getAssemblies();
         TabVSAssembly tab = (TabVSAssembly) container;
         String[] tabchildren = tab.getAssemblies();
         String[] labels = tab.getLabelsValue();
         Arrays.sort(mychildren, new PosComparator(vs));

         for(int i = 0; i < tabchildren.length; i++) {
            if(tabchildren[i].equals(assembly.getName())) {
               tabchildren[i] = mychildren[0];

               if(assembly.getName().equals(tab.getSelected())) {
                  tab.setSelectedValue(mychildren[0]);
               }

               if(labels != null && i < labels.length &&
                  assembly.getName().equals(labels[i]))
               {
                  labels[i] = mychildren[0];
               }

               break;
            }
         }

         tab.setAssemblies(tabchildren);
         tab.setLabelsValue(labels);
         // re-align the children to the tab
         tab.setPixelOffset(tab.getPixelOffset());
      }
   }

   /**
    * Check the assembly is group container or not.
    */
   private boolean isGroupContainer(Assembly assembly) {
      return assembly.getAssemblyType() == AbstractSheet.GROUPCONTAINER_ASSET;
   }

   /**
    * Update the group container properties.
    */
   private void updateGroupContainer(GroupContainerVSAssembly group,
                                     Viewsheet vs, String[] childsName,
                                     Point pos, Dimension size)
   {
      group.setPixelOffset(pos);
      group.setPixelSize(size);
      group.setAssemblies(childsName);
      vs.calcChildZIndex();
   }

   /**
    * Check if dependency circle exists, the method in AbstractVSAssembly class only test reflexive dependency.
    *
    * @return true if exists, otherwise false
    */
   protected boolean checkDependency(Viewsheet viewsheet) {
      Assembly[] assemblies = viewsheet.getAssemblies();
      Map<String, Set<String>> map = new HashMap<>();
      Map<String, Integer> inDegree = new HashMap<>();
      Deque<String> queue = new ArrayDeque<>(assemblies.length);
      int sorted = 0;

      for(Assembly as : assemblies) {
         ContainerVSAssembly container;

         if(as instanceof ContainerVSAssembly) {
            container = (ContainerVSAssembly) as;
            Set<String> childrenAssemblies = new HashSet<>();
            map.put(container.getAbsoluteName(), childrenAssemblies);

            for(String child : container.getAssemblies()) {

               if(!childrenAssemblies.contains(child)) {
                  childrenAssemblies.add(child);
                  inDegree.compute(child, (k, v) -> v == null ? 1 : v + 1);
               }
            }
         }
      }

      for(Assembly as : assemblies) {
         String name = as.getAbsoluteName();

         if(!inDegree.containsKey(name) || inDegree.get(name) == 0) {
            queue.offer(name);
         }
      }

      while(!queue.isEmpty()) {
         String head = queue.poll();
         sorted++;
         Set<String> children = map.get(head);

         if(children != null) {
            for(String child : children) {
               int degree = inDegree.compute(child, (k, v) -> v - 1);

               if(degree == 0) {
                  queue.offer(child);
               }
            }
         }
      }

      return sorted != assemblies.length;
   }

   /**
    * Compare the position of assemblies.
    */
   private static class PosComparator implements Comparator<String> {
      public PosComparator(Viewsheet vs) {
         this.vs = vs;
      }

      @Override
      public int compare(String v1, String v2) {
         VSAssembly obj1 = (VSAssembly) vs.getAssembly(v1);
         VSAssembly obj2 = (VSAssembly) vs.getAssembly(v2);

         if(obj1 == null || obj2 == null) {
            return 0;
         }

         Point pos1 = vs.getPixelPosition(obj1.getVSAssemblyInfo());
         Point pos2 = vs.getPixelPosition(obj2.getVSAssemblyInfo());

         if(pos1.x != pos2.x) {
            return pos1.x - pos2.x;
         }

         return pos1.y - pos2.y;
      }

      private Viewsheet vs;
   }

   /**
    * Update pop component in pop component info if
    * pop component does not exist.
    */
   private void updatePopComponent(Viewsheet vs) {
      Assembly[] assemblies = vs.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(assembly.getInfo() instanceof PopVSAssemblyInfo) {
            PopVSAssemblyInfo popVSAssemblyInfo = (PopVSAssemblyInfo) assembly.getInfo();
            String popComponentValue = popVSAssemblyInfo.getPopComponentValue();
            popComponentValue = Arrays.asList(this.vsObjectPropertyService.getSupportedPopComponents(
                  vs, assembly.getInfo().getAbsoluteName()))
               .contains(popComponentValue) ? popComponentValue : null;
            popVSAssemblyInfo.setPopComponentValue(popComponentValue);
         }
      }
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final PlaceholderService placeholderService;
   private final ViewsheetService viewsheetService;
   private final VSObjectTreeService vsObjectTreeService;
   private final VSObjectPropertyService vsObjectPropertyService;
}
