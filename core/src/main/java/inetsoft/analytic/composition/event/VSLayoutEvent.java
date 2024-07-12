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
import inetsoft.report.StyleConstants;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.AssetCommand;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;

import java.awt.*;
import java.util.ArrayList;

/**
 * VSLayoutEvent process the layout option, MOVE, GROUP or COLOCATE, for eache
 * option, working as following:
 * 1: MOVE
 *   1): first move the object out of a container
 *   2) :if the moved object is in current selection, fix the object properties,
 *       and update all current selections' out selection.
 *   3): move the object to the specifield position
 *
 * 2: GROUP: the moved object is current selection component or
 *    current selection, and target is current selection
 *   1): move the component out from its container
 *   2): add the moved object to target (if moved object is current selection,
 *       add all children to target, and remove self)
 *
 * 3: COLOCATE:
 *   1): move the object out from its container
 *   2) :if the moved object is in current selection, fix the object properties,
 *       and update all current selections' out selection.
 *   3): colocate the moved object and target object
 */
public class VSLayoutEvent extends ViewsheetEvent {
   /**
    * Only move assembly.
    */
   public static final String MOVE = "move";
   /**
    * Move current selection component(s) or current selection in to
    * current selection.
    */
   public static final String GROUP = "group";
   /**
    * Move two assemblies colocate as tab.
    */
   public static final String COLOCATE = "colocate";

   /**
    * Default constructor.
    */
   public VSLayoutEvent() {
      super();
   }

   /**
    * Constructor.
    * @param moved the move object(s) absolute name(s).
    * @param movedP the move objects' container absolute name(s).
    * @param target the target object absolute name.
    * @param option the layout option.
    * @param off the offset for moved.
    * @param pixel the new position is pixel positon.
    * @param place for current selection, drag current selection component
    *  from one current selection to target current selection, the index
    *  that the component to place in target current selection, -1 means no.
    */
   public VSLayoutEvent(String moved, String movedP, String target,
                        String option, Point off, boolean pixel, int place) {
      super();

      if(moved != null) {
         put("moved", moved);
      }

      if(movedP != null) {
         put("movedP", movedP);
      }

      if(target != null) {
         put("target", target);
      }

      if(option != null) {
         put("option", option);
      }

      if(off != null) {
         put("off", off.x + "X" + off.y);
      }

      if(place >= 0) {
         put("place", place + "");
      }

      put("pxiel", pixel + "");
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Layout");
   }

   /**
    * Check if is undoable/redoable.
    * @return <tt>true</tt> if undoable/redoable.
    */
   @Override
   public boolean isUndoable() {
      return undoable;
   }

   /**
    * Get the influenced assemblies.
    * @return the influenced assemblies, <tt>null</tt> means all.
    */
   @Override
   public String[] getAssemblies() {
      String moved = (String) get("moved");
      String[] moveds = moved == null ? new String[0] : moved.split("/");
      String movedP = (String) get("movedP");
      String[] movedPs = movedP == null ? new String[0] : movedP.split("/");
      String target = (String) get("target");
      ArrayList arr = new ArrayList();

      if(target != null) {
         arr.add(target);
      }

      for(int i = 0; i < moveds.length; i++) {
         if(!"".equals(moveds[i].trim()) && arr.indexOf(moveds[i]) < 0) {
            arr.add(moveds[i]);
         }
      }

      for(int i = 0; i < movedPs.length; i++) {
         if(!"".equals(movedPs[i].trim()) && arr.indexOf(movedPs[i]) < 0) {
            arr.add(movedPs[i]);
         }
      }

      // index is important for undo/redo
      return (String[]) arr.toArray(new String[arr.size()]);
   }

   /**
    * Process event.
    */
   @Override
   public void process(RuntimeViewsheet rvs, AssetCommand command)
      throws Exception
   {
      boolean adhocFilter = "true".equals((String) get("adhocFilter"));

      if(adhocFilter) {
         undoable = false;
      }

      String option = (String) get("option");

      if(option == null || rvs.getViewsheet() == null) {
         return;
      }

      if(MOVE.equals(option)) {
         moveAssembly(rvs, command);
      }
      else if(GROUP.equals(option)) {
         groupAssembly(rvs, command);
      }
      else if(COLOCATE.equals(option)) {
         colocateAssembly(rvs, command);
      }
   }

   /**
    * Move assembly, only move assembly to new position.
    */
   private void moveAssembly(RuntimeViewsheet rvs, AssetCommand command)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      Point off = getOff();
      boolean pixel = "true".equals("pixel");

      if(off == null) {
         return;
      }

      String moved = (String) get("moved");
      String[] moveds = moved == null ? new String[0] : moved.split("/");
      boolean haveCS = false;

      for(int i = 0; i < moveds.length; i++) {
         VSAssembly mobj = (VSAssembly) vs.getAssembly(moveds[i]);
         VSAssembly mobjP = mobj.getContainer();
         moveOutContainer(rvs, mobj, command);

         if(mobjP instanceof CurrentSelectionVSAssembly) {
            fixCSProperty(rvs, mobj, false, null, command);
            haveCS = true;
         }
      }

      if(haveCS) {
         updateOutSelection(rvs, command);
      }

      if("true".equals((String) get("adhocFilter"))) {
         VSAssembly tobj =
            (VSAssembly) vs.getAssembly((String) get("targetObj"));
         AbstractSelectionVSAssembly mobj =
            (AbstractSelectionVSAssembly) vs.getAssembly(moved);
         String p = (String) get("offsetPixel");
         Point offsetPixel = new Point(Integer.parseInt(p.split("X")[0]),
            Integer.parseInt(p.split("X")[1]));

         AdhocFilterEvent.changeAssemblyToAdhocFilter(mobj, tobj, offsetPixel);
         mobj.setPixelOffset(off);
         off = new Point(0, 0);
      }

      new MoveVSAssemblyEvent(moved, off, pixel).process(rvs, command);
   }

   /**
    * Group current selection component.
    */
   private void groupAssembly(RuntimeViewsheet rvs, AssetCommand cmd)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      String moveName = (String) get("moved");
      String[] moveds = moveName == null ? new String[0] : moveName.split("/");
      String targetName = (String) get("target");
      VSAssembly tmp = (VSAssembly) vs.getAssembly(targetName);

      if(!(tmp instanceof CurrentSelectionVSAssembly)) {
         return;
      }

      String placeStr = (String) get("place");
      int place = placeStr == null ? -1 : Integer.parseInt(placeStr);
      CurrentSelectionVSAssembly target = (CurrentSelectionVSAssembly) tmp;
      boolean updateOut = false;
      String[] allnames = target.getAssemblies();
      boolean adhocFilter = "true".equals((String) get("adhocFilter"));

      if("true".equals(get("replace")) && place > -1 && place < allnames.length)
      {
         target.removeAssembly(allnames[place]);
         VSAssembly child =
            (VSAssembly) target.getViewsheet().getAssembly(allnames[place]);
         VSEventUtil.removeVSAssembly(rvs, this, getID(), getLinkURI(),
            child, cmd, false, false);
         allnames = target.getAssemblies();
      }

      for(int i = 0; i < moveds.length; i++) {
         VSAssembly moved = (VSAssembly) vs.getAssembly(moveds[i]);

         // not support
         if(!(moved instanceof SelectionListVSAssembly ||
              moved instanceof TimeSliderVSAssembly ||
              moved instanceof CurrentSelectionVSAssembly) ||
              target.getViewsheet() != moved.getViewsheet())
         {
            return;
         }

         VSAssembly movedP = moved.getContainer();

         if(movedP == target) {
            continue;
         }

         moveOutContainer(rvs, moved, cmd);

         String[] names = {};

         if(moved instanceof SelectionListVSAssembly ||
            moved instanceof TimeSliderVSAssembly)
         {
            boolean empty = !((AbstractSelectionVSAssembly) moved).
               containsSelection();
            SelectionVSAssemblyInfo minfo =
               (SelectionVSAssemblyInfo)moved.getVSAssemblyInfo();
            boolean adhocCreated = minfo.isCreatedByAdhoc();

            if(empty && adhocFilter && adhocCreated) {
               // @by: ChrisSpagnoli bug1412261632374 #4 2014-10-16
               // It is possible for client to send VSLayoutEvent with an
               // assembly which has been deleted by ApplySelectionListEvent.
               // So, check if the assembly referenced is still in the
               // Viewsheet before deleting it.
               if(rvs.getViewsheet().getAssembly(moved.getAbsoluteName())!=null) {
                  VSEventUtil.removeVSAssembly(rvs, this, getID(),
                     getLinkURI(), moved, cmd, false, false);
               }
            }
            else {
               names = new String[] {moved.getName()};

               //move out from a current selection container into another
               //current selection container, fix size
               if(movedP instanceof CurrentSelectionVSAssembly) {
                  fixCSProperty(rvs, moved, true, target, cmd);
                  updateOut = true;
               }

               fixCSFormat(rvs, moved, true, target, cmd, true);

               if(adhocFilter) {
                  AdhocFilterEvent.changeAdhocFilterToAssembly(
                     (AbstractSelectionVSAssembly) moved);
               }
            }
         }
         else {
            CurrentSelectionVSAssembly obj = (CurrentSelectionVSAssembly) moved;
            names = obj.getAssemblies();
            VSEventUtil.removeVSAssembly(rvs, this, getID(), getLinkURI(), obj,
                                         cmd, false, false);
         }

         int len = place < 0 ? allnames.length : place;
         len = Math.min(len, allnames.length);
         String[] tmpnames = new String[allnames.length + names.length];
         System.arraycopy(allnames, 0, tmpnames, 0, len);
         System.arraycopy(names, 0, tmpnames, len, names.length);

         if(len < allnames.length) {
            System.arraycopy(allnames, len, tmpnames, len + names.length,
                             allnames.length - len);
         }

         allnames = tmpnames;
      }

      target.setAssemblies(allnames);

      if(updateOut) {
         updateOutSelection(rvs, cmd);
      }

      target.layout();
      refresh(rvs, target.getAbsoluteName(), cmd, false);
   }

   /**
    * Colocate assembly as tab.
    */
   private void colocateAssembly(RuntimeViewsheet rvs, AssetCommand cmd)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      String moved = (String) get("moved");
      VSAssembly mobj = (VSAssembly) vs.getAssembly(moved);
      String target = (String) get("target");
      VSAssembly tobj = (VSAssembly) vs.getAssembly(target);

      if(tobj == null || mobj == null) {
         return;
      }

      VSAssembly parent = mobj.getContainer();
      moveOutContainer(rvs, mobj, cmd);

      if(parent instanceof CurrentSelectionVSAssembly) {
         fixCSProperty(rvs, mobj, false, null, cmd);
         updateOutSelection(rvs, cmd);
      }

      String tabname = null;

      if(tobj instanceof TabVSAssembly) {
         tabname = tobj.getAbsoluteName();
      }
      else if(tobj.getContainer() instanceof TabVSAssembly) {
         tabname = tobj.getContainer().getAbsoluteName();
      }

      new ChangeVSTabEvent(tabname, moved, tabname == null ? target : null,
         MOVE_IN_TAB).process(rvs, cmd);
   }

   /**
    * Get offset property.
    */
   private Point getOff() {
      String str = (String) get("off");
      String[] xys = str == null ? null : str.split("X");

      if(xys == null || xys.length != 2) {
         return null;
      }

      return new Point(Integer.parseInt(xys[0]), Integer.parseInt(xys[1]));
   }

   /**
    * Just only move assembly out of container.
    * @param rvs runtime viewsheet.
    * @param mobj the moved object.
    * @param cmd the AssetCommand.
    */
   private void moveOutContainer(RuntimeViewsheet rvs, VSAssembly mobj,
                                 AssetCommand cmd)
      throws Exception
   {
      if(mobj == null || cmd == null) {
         return;
      }

      VSAssembly mobjP = mobj.getContainer();

      // not in container
      if(mobjP == null) {
         return;
      }

      String name = mobj.getAbsoluteName();
      String cname = mobjP.getAbsoluteName();

      if(mobjP instanceof TabVSAssembly) {
         new ChangeVSTabEvent(cname, name, null,
            MOVE_OUT_TAB).process(rvs, cmd);
      }
      else if(mobjP instanceof CurrentSelectionVSAssembly) {
         CurrentSelectionVSAssembly cmobjP = (CurrentSelectionVSAssembly) mobjP;
         String[] arr = cmobjP.getAssemblies();
         int dot = name.lastIndexOf(".");
         String name0 = dot >= 0 ? name.substring(dot + 1) : name;
         boolean adhocFilter = "true".equals((String) get("adhocFilter"));

         // popping up a selection (from a hidden container), make it visible
         if(adhocFilter && !mobj.isVisible()) {
            VSAssemblyInfo minfo = mobj.getVSAssemblyInfo();
            minfo.setVisible(true);
            minfo.setPrimary(true); // for embedded viewsheet
            minfo.setVisibleValue("true");
         }

         for(int i = 0; i < arr.length; i++) {
            if(name0.equals(arr[i])) {
               String[] narr = new String[arr.length - 1];
               System.arraycopy(arr, 0, narr, 0, i);
               System.arraycopy(arr, i + 1, narr, i, arr.length - i - 1);
               cmobjP.setAssemblies(narr);
               cmobjP.layout();
               refresh(rvs, cname, cmd, true);
               refresh(rvs, name, cmd, true);
               break;
            }
         }
      }
   }

   /**
    * Fix the current selection component property if moved out of
    * current selection.
    */
   public static void fixCSProperty(RuntimeViewsheet rvs, VSAssembly moved,
      boolean toCS, CurrentSelectionVSAssembly nparent, AssetCommand cmd)
      throws Exception
   {
      if(!(moved instanceof SelectionListVSAssembly) &&
         !(moved instanceof TimeSliderVSAssembly))
      {
         return;
      }

      Dimension size = moved.getPixelSize();
      VSAssemblyInfo info = moved.getVSAssemblyInfo();
      DropDownVSAssemblyInfo dinfo = (DropDownVSAssemblyInfo) info;

      fixCSFormat(rvs, moved, toCS, nparent, cmd, false);

      if(!toCS) {
         int h = dinfo.getListHeight() * AssetUtil.defh;

         if(size.height == AssetUtil.defh && h > 0) {
            size.height = size.height + h;
         }

         // time slider no header
         if(info instanceof TimeSliderVSAssemblyInfo) {
            size.height = size.height - AssetUtil.defh;
         }

         // set show type to list
         if(info instanceof SelectionListVSAssemblyInfo) {
            ((SelectionListVSAssemblyInfo) info).setShowTypeValue(0);
         }
      }
      else {
         if(moved instanceof TimeSliderVSAssembly) {
            size.height += AssetUtil.defh;
            dinfo.setListHeight(size.height / AssetUtil.defh - 1);
         }
         else if(info instanceof SelectionListVSAssemblyInfo) {
            SelectionListVSAssemblyInfo linfo =
               (SelectionListVSAssemblyInfo) info;

            if(linfo.getShowTypeValue() != 0) {
               size.height = 6 * AssetUtil.defh;
            }

            dinfo.setListHeight(size.height / AssetUtil.defh - 1);
         }

         if(nparent != null) {
            info.setPrimary(nparent.isPrimary());
            info.setVisibleValue(
               ((VSAssemblyInfo) nparent.getInfo()).getVisibleValue());
            info.setVisible(nparent.isVisible());
         }
      }

      refresh(rvs, moved.getAbsoluteName(), cmd, true);
   }

   public static void fixCSFormat(RuntimeViewsheet rvs, VSAssembly moved,
      boolean toCS, CurrentSelectionVSAssembly nparent, AssetCommand cmd,
      boolean refresh) throws Exception
   {
      if(!(moved instanceof SelectionListVSAssembly) &&
         !(moved instanceof TimeSliderVSAssembly))
      {
         return;
      }

      VSAssemblyInfo info = moved.getVSAssemblyInfo();
      DropDownVSAssemblyInfo dinfo = (DropDownVSAssemblyInfo) info;
      FormatInfo fmtinfo = info.getFormatInfo();

      if(!toCS) {
         // time slider no header
         if(info instanceof TimeSliderVSAssemblyInfo) {
            VSCompositeFormat tfmt = info.getFormat();
            VSFormat fmt = tfmt == null ? null : tfmt.getUserDefinedFormat();

            if(fmt != null) {
               fmt.setBordersValue(null);
            }
         }
      }
      else {
         if(moved instanceof TimeSliderVSAssembly) {
            VSCompositeFormat tfmt = fmtinfo == null ? null :
               fmtinfo.getFormat(new TableDataPath(-1, TableDataPath.TITLE));
            VSFormat fmt = tfmt == null ? null : tfmt.getDefaultFormat();

            if(fmt != null && fmt.getBordersValue() == null &&
               fmt.getBorderColorsValue() == null)
            {
               Insets borders = new Insets(StyleConstants.THIN_LINE,
                              StyleConstants.THIN_LINE,
                              StyleConstants.THIN_LINE,
                              StyleConstants.THIN_LINE);
               BorderColors bcolors = new BorderColors(
                  VSAssemblyInfo.DEFAULT_BORDER_COLOR,
                  VSAssemblyInfo.DEFAULT_BORDER_COLOR,
                  VSAssemblyInfo.DEFAULT_BORDER_COLOR,
                  VSAssemblyInfo.DEFAULT_BORDER_COLOR);
               fmt.setBordersValue(borders);
               fmt.setBorderColorsValue(bcolors);
            }
         }
      }

      if(refresh) {
         refresh(rvs, moved.getAbsoluteName(), cmd, true);
      }
   }

   /**
    * Update current selections' out selections.
    */
   public static void updateOutSelection(RuntimeViewsheet rvs) throws Exception
   {
      updateOutSelection(rvs, null);
   }

   /**
    * Update current selections' out selections.
    */
   public static void updateOutSelection(RuntimeViewsheet rvs, AssetCommand cmd)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      Assembly[] ass = vs.getAssemblies(true);

      for(int i = 0; i < ass.length; i++) {
         if(ass[i] instanceof CurrentSelectionVSAssembly) {
            CurrentSelectionVSAssembly cs = (CurrentSelectionVSAssembly) ass[i];

            // only show current selection need update
            if(cs.isShowCurrentSelection()) {
               cs.updateOutSelection();
               cs.layout();

               if(cmd != null) {
                  refresh(rvs, cs.getAbsoluteName(), cmd, true);
               }
            }
         }
      }
   }

   /**
    * Refresh assembly, if assembly is container, first refresh the container,
    * then refresh the children.
    */
   private static void refresh(RuntimeViewsheet rvs, String name,
                               AssetCommand cmd, boolean shrink)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly ass = vs == null ? null : (VSAssembly) vs.getAssembly(name);

      if(ass == null) {
         return;
      }

      boolean incs = ass.getContainer() instanceof ContainerVSAssembly;
      VSEventUtil.refreshVSAssembly(rvs, ass, cmd, shrink || !incs);

      if(ass instanceof ContainerVSAssembly) {
         String[] names = ((ContainerVSAssembly) ass).getAbsoluteAssemblies();

         for(int i = 0; i < names.length; i++) {
            refresh(rvs, names[i], cmd, shrink);
         }
      }
   }

   private boolean undoable = true;
}
