/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.analytic.composition.event;

import inetsoft.analytic.composition.ViewsheetEvent;
import inetsoft.report.composition.*;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * Change vstab object event.
 *
 * @version 8.5, 08/01/2006
 * @author InetSoft Technology Corp
 */
public class ChangeVSTabEvent extends ViewsheetEvent {
   /**
    * Constructor.
    */
   public ChangeVSTabEvent() {
      super();
   }

   /**
    * Constructor.
    * @param tabName tab name that the dragged object is moved to.
    * @param srcName the dragged object name.
    * @param targetName the dragged object dragged to overloap with.
    * @param type the type of the event, move out or move in.
    */
   public ChangeVSTabEvent(String tabName, String srcName, String targetName,
                           String type)
   {
      if(tabName != null) {
         put("tabName", tabName);
      }

      if(srcName != null) {
         put("sourceObjName", srcName);
      }

      if(targetName != null) {
         put("targetObjName", targetName);
      }

      put("type", type);
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Change VSTab");
   }

   /**
    * Check if is undoable/redoable.
    * @return <tt>true</tt> if undoable/redoable.
    */
   @Override
   public boolean isUndoable() {
      String type = (String) get("type");
      return !type.equals(AssetEvent.MOVE_OUT_TAB);
   }

   /**
    * Get the influenced assemblies.
    * @return the influenced assemblies, <tt>null</tt> means all.
    */
   @Override
   public String[] getAssemblies() {
      List names = new ArrayList();
      String name = (String) get("tabName");

      if(name != null) {
         names.add(name);
      }

      name = (String) get("sourceObjName");

      if(name != null) {
         names.add(name);
      }

      name = (String) get("targetObjName");

      if(name != null) {
         names.add(name);
      }

      String[] arr = new String[names.size()];
      names.toArray(arr);

      return arr;
   }

   /**
    * Process event.
    */
   @Override
   public void process(RuntimeViewsheet rvs, AssetCommand command)
      throws Exception
   {
      String type = (String) get("type");

      // move an object out of the tab
      if(type.equals(AssetEvent.MOVE_OUT_TAB)) {
         moveOutTab(rvs, command, false);
      }
      else {
         moveInTab(rvs, command);
      }

      // viewsheet as child, should specified add/delete some objects in the
      // viewsheet not matter move in or move out if the visible property not
      // same for the tab and viewsheet
      VSEventUtil.addDeleteEmbeddedViewsheet(rvs, this, getLinkURI(), command);
   }

   /**
    * Process move out tab.
    * @param rvs runtime viewsheet.
    * @param command the asset command.
    * @param secondProcess to identify the function is called by moveInTab,
    *  if true, some commands may not need to be processed.
    */
   private void moveOutTab(RuntimeViewsheet rvs, AssetCommand command,
                           boolean secondProcess)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      String sourceObjName = (String) get("sourceObjName");
      VSAssembly srcObj = (sourceObjName != null) ?
         (VSAssembly) vs.getAssembly(sourceObjName) : null;
      String tabName = (String) get("tabName");
      TabVSAssembly tab = (tabName != null) ?
         (TabVSAssembly) vs.getAssembly(tabName) : null;

      // sanity check, assembly is not on the tab
      if(tab != null && Arrays.asList(tab.getAssemblies()).indexOf(sourceObjName) < 0) {
         return;
      }

      if(secondProcess) {
         tab = null;
         Assembly c = srcObj == null ? null : srcObj.getContainer();

         if(c instanceof TabVSAssembly) {
            tab = (TabVSAssembly) c;
         }
         // moved from GroupContainer, remove it from container
	 else if(c instanceof GroupContainerVSAssembly) {
            GroupContainerVSAssembly container = (GroupContainerVSAssembly) c;
            container.removeAssembly(sourceObjName);
            String[] children = container.getAssemblies();

            // only one child left, remove the container
            if(children.length == 1) {
               Assembly c2 = container.getContainer();

               if(c2 instanceof TabVSAssembly) {
                  TabVSAssembly tab2 = (TabVSAssembly) c2;
                  String[] tabs = tab2.getAssemblies();
                  
                  for(int i = 0; i < tabs.length; i++) {
                     if(tabs[i].equals(container.getName())) {
                        tabs[i] = children[0];
                        break;
                     }
                  }

                  tab2.setAssemblies(tabs);
               }

               VSEventUtil.removeVSAssembly(rvs, this, getID(), getLinkURI(),
                                            container, command, false, false);
            }
         }

         tabName = tab == null ? null : tab.getAbsoluteName();
      }

      String[] tarr = tab == null ? null : tab.getAssemblies();

      // move an object out of the tab
      if(tab != null) {
         // remove tab if only one left
         if(tarr.length <= 2) {
            // @by davyc, should not layout viewsheet and change object's
            // position, this will be done in event MoveVSAssemblyEvent, or
            // the object's position will be error, see bug1246240739600
            VSEventUtil.removeVSAssembly(rvs, this, getID(), getLinkURI(), tab,
                                         command, false, false);
            //int x = tab.getPosition().x;

            for(int i = 0; tarr != null && i < tarr.length; i++) {
               VSAssembly assembly = (VSAssembly) vs.getAssembly(tarr[i]);

               if(assembly == null) {
                  continue;
               }

               //Point tpos = new Point(x++, assembly.getPosition().y);
               //assembly.setPosition(tpos);
               VSEventUtil.refreshVSAssembly(rvs, assembly.getAbsoluteName(),
                                             command, true);

               if(!Tool.equals(tab.getSelectedValue(),
			       assembly.getAbsoluteName()))
               {
                  VSEventUtil.loadTableLens(rvs, this,
                     assembly.getAbsoluteName(), getLinkURI(), command);
               }
            }

            //VSEventUtil.layoutViewsheet(rvs, this, getID(), getLinkURI(),
            //                            command);
            VSEventUtil.updateZIndex(vs, tab);

            if(!secondProcess) {
               VSEventUtil.shrinkZIndex(vs, command);
            }

            return;
         }

         String embed = tabName.lastIndexOf(".") < 0 ? "" :
            tabName.substring(0, tabName.lastIndexOf(".") + 1);

         tab.removeAssembly(sourceObjName);
         tarr = tab.getAssemblies();

         String select = embed + tarr[0];
         // set first object selected
         new ChangeTabStateEvent(tabName, select).process(rvs, command);
         VSEventUtil.refreshVSAssembly(rvs, tabName, command);
         VSEventUtil.updateZIndex(vs, tab, new String[] {sourceObjName});

         if(!secondProcess) {
            VSEventUtil.shrinkZIndex(vs, command);
         }

         return;
      }
   }

   /**
    * Move in tab.
    */
   private void moveInTab(RuntimeViewsheet rvs, AssetCommand command)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      moveOutTab(rvs, command, true);

      String tabName = (String) get("tabName");
      TabVSAssembly tab = (tabName != null) ?
         (TabVSAssembly) vs.getAssembly(tabName) : null;
      String targetObjName = (String) get("targetObjName");
      VSAssembly targetObj = (targetObjName != null) ?
         (VSAssembly) vs.getAssembly(targetObjName) : null;
      String sourceObjName = (String) get("sourceObjName");
      VSAssembly srcObj = (sourceObjName != null) ?
         (VSAssembly) vs.getAssembly(sourceObjName) : null;
      String[] tarr = tab == null ? null : tab.getAssemblies();
      Vector comps = (tab == null) ? new Vector() : Tool.toVector(tarr);
      String[] larr = tab == null ? null : tab.getLabelsValue();
      Vector labels = (tab == null) ? new Vector() : Tool.toVector(larr);

      boolean newtab = false;
      Point position;

      // create a tab to move components to
      if(tab == null) {
         if(targetObj == null) {
            return;
         }

         position = targetObj.getPixelOffset();
         tabName = AssetUtil.getNextName(vs, AbstractSheet.TAB_ASSET);
         tab = new TabVSAssembly(vs, tabName);
         tab.setPixelOffset(position);
         tab.initDefaultFormat();
         vs.addAssembly(tab);
         newtab = true;
      }
      else {
         position = tab.getPixelOffset();
      }

      // get components to add to the tab
      VSAssembly[] arr = {targetObj, srcObj};
      String selected = null;

      if(srcObj instanceof TabVSAssembly) {
         selected = ((TabVSAssembly) srcObj).getSelectedValue();
      }

      for(int i = 0; i < arr.length; i++) {
         if(arr[i] instanceof TabVSAssembly) {
            comps.addAll(Tool.toVector(
                            ((TabVSAssembly) arr[i]).getAssemblies()));
            labels.addAll(Tool.toVector(
                            ((TabVSAssembly) arr[i]).getLabelsValue()));
            // do not want to layout viewsheet after removal
            // for the assemblies will be added to the target tab container
            VSEventUtil.updateZIndex(vs, arr[i]);
            VSEventUtil.removeVSAssembly(rvs, this, getID(), getLinkURI(),
                                         arr[i], command, false, false);
         }
         else if(arr[i] != null) {
            VSAssemblyInfo info = arr[i].getVSAssemblyInfo();
            comps.add(arr[i].getName());
            labels.add(null);
         }
      }

      makeUnique(labels);
      tab.setAssemblies((String[]) comps.toArray(new String[comps.size()]));
      tab.setLabelsValue((String[]) labels.toArray(new String[labels.size()]));

      if(comps.size() > 0) {
         selected = selected != null ? selected :
                                       (String) comps.get(comps.size() - 1);
         tab.setSelectedValue(selected);
      }

      // make sure primary status is consistent
      vs.validate();

      // move child components to below tab
      for(int i = 0; i < comps.size(); i++) {
         String name = (String) comps.get(i);
         VSAssembly comp = (VSAssembly) vs.getAssembly(name);
         Point npos = new Point(position.x, position.y + AssetUtil.defh);

         if(comp instanceof FloatableVSAssembly) {
            ((FloatableVSAssembly) comp).setPixelOffset(new Point(0, 0));
         }

         if(!npos.equals(comp.getPixelOffset())) {
            comp.setPixelOffset(npos);

            //bug1377508669644, execute the tab in container.
            if(comp instanceof ContainerVSAssembly) {
               ContainerVSAssembly cassembly = (ContainerVSAssembly) comp;
               String[] names = cassembly.getAbsoluteAssemblies();

               for(int j = 0; j < names.length; j++) {
                  if(vs.getAssembly(names[j]) instanceof TabVSAssembly) {
                     VSEventUtil.execute(rvs, this, names[j], getLinkURI(),
                        VSAssembly.VIEW_CHANGED, command);
                  }
               }
            }

            VSEventUtil.refreshVSAssembly(rvs, name, command, true);
         }
      }

      if(newtab) {
         VSEventUtil.addDeleteVSObject(rvs, this, tab, getLinkURI(), command);
      }

      VSEventUtil.fixTipOrPopAssemblies(rvs, command);
      VSEventUtil.layoutViewsheet(rvs, this, getID(), getLinkURI(), command);

      int tabw = AssetUtil.defw;

      // calculate size after layout since the floatable size may have
      // changed after layout (and the pixeloffset set to 0)
      for(int i = 0; i < comps.size(); i++) {
         String name = (String) comps.get(i);
         VSAssembly comp = (VSAssembly) vs.getAssembly(name);

         tabw = Math.max(tabw, comp.getPixelSize().width);
      }

      // set tab size to the full width of all children
      tab.setPixelSize(new Dimension(tabw, AssetUtil.defh));
      
      VSEventUtil.refreshVSAssembly(rvs, tabName, command);
      VSEventUtil.shrinkZIndex(vs, command);
   }

   /**
    * Make labels unique.
    */
   private void makeUnique(Vector labels) {
      Set used = new HashSet();

      for(int i = 0; i < labels.size(); i++) {
         Object label = labels.get(i);

         if(label == null || label.equals("")) {
            continue;
         }

         int n = 2;

         while(used.contains(label)) {
            label = labels.get(i) + "" + n++;
         }

         labels.setElementAt(label, i);
         used.add(label);
      }
   }
}
