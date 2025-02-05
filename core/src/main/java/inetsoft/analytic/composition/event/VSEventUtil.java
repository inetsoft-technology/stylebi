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
package inetsoft.analytic.composition.event;

import inetsoft.analytic.composition.*;
import inetsoft.analytic.composition.command.*;
import inetsoft.report.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.AssetTreeModel.Node;
import inetsoft.report.composition.command.InitGridCommand;
import inetsoft.report.composition.command.MessageCommand;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.*;
import inetsoft.report.filter.*;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.internal.table.XTableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.sree.*;
import inetsoft.sree.internal.AnalyticEngine;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.util.log.LogContext;
import inetsoft.util.script.ScriptException;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import org.slf4j.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.*;
import java.security.Principal;
import java.util.List;
import java.util.*;

/**
 * Utility methods for viewsheet event.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
@SuppressWarnings("WeakerAccess")
public final class VSEventUtil {
   /**
    * Base worksheet.
    */
   public static final String BASE_WORKSHEET = "baseWorksheet";
   /**
    * Convert to measure.
    */
   public static final int CONVERT_TO_MEASURE = 1;
   /**
    * Convert to dimension.
    */
   public static final int CONVERT_TO_DIMENSION = 2;

   /**
    * Layout viewsheet.
    * @param rvs the specified runtime worksheet.
    * @param id the specified viewsheet id.
    * @param uri the specified link uri.
    * @param command the specified command container.
    * @return <tt>true</tt> if size changed, <tt>false</tt> otherwise.
    */
   public static boolean layoutViewsheet(RuntimeViewsheet rvs, AssetEvent event,
                                         String id, String uri,
                                         AssetCommand command)
      throws Exception
   {
      return layoutViewsheet(rvs, event, id, uri, command, new String[0],
                             new ChangedAssemblyList());
   }

   /**
    * Layout viewsheet.
    * @param rvs the specified runtime worksheet.
    * @param id the specified worksheet id.
    * @param uri the specified link uri.
    * @param command the specified command container.
    * @param names the names of moved assemblies.
    * @param clist the specified changed assembly list.
    * @return <tt>true</tt> if size changed, <tt>false</tt> otherwise.
    */
   public static boolean layoutViewsheet(RuntimeViewsheet rvs, AssetEvent event,
                                         String id, String uri,
                                         AssetCommand command, String[] names,
                                         ChangedAssemblyList clist)
      throws Exception
   {
      // fix assembly size
      List rlist = fixAssemblySize(rvs);
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return false;
      }

      java.awt.Dimension osize = vs.getLastSize();
      Assembly[] assemblies = vs.layout(names);
      java.awt.Dimension nsize = vs.getPixelSize();

      if(nsize.width > osize.width || nsize.height > osize.height) {
         refreshViewsheet(rvs, event, id, uri, command, false, false, false, clist);
         return true;
      }

      // resized assembly
      for(Object rassembly : rlist) {
         VSAssembly assembly = (VSAssembly) rassembly;
         refreshVSAssembly(rvs, assembly, command);
      }

      // manually moved assembly
      Set<String> list = new HashSet<>();

      for(String name : names) {
         list.add(name);
         VSAssembly assembly = vs.getAssembly(name);

         if(!rlist.contains(assembly)) {
            refreshVSAssembly(rvs, assembly, command);
         }
      }

      // automatically moved assembly
      for(Assembly assembly : assemblies) {
         if(rlist.contains(assembly)) {
            continue;
         }

         String name = assembly.getName();

         if(!list.contains(name)) {
            refreshVSAssembly(rvs, (VSAssembly) assembly, command);
         }
      }

      return false;
   }

   /**
    * Fix the size of all assemblies.
    * @param rvs the specified runtime viewsheet.
    * @return the list contains the changed assemblies.
    */
   public static List fixAssemblySize(RuntimeViewsheet rvs) throws Exception {
      List list = new ArrayList();
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null) {
         return list;
      }

      fixAssemblySize0(vs, box, list);
      return list;
   }

   /**
    * Fix the size of all assemblies.
    * @param vs the specified viewsheet.
    * @param box the specified viewsheet sandbox.
    * @param list the list contains the changed assemblies.
    */
   private static void fixAssemblySize0(Viewsheet vs, ViewsheetSandbox box, List list)
      throws Exception
   {
      Assembly[] assemblies = vs.getAssemblies();

      for(Assembly assembly : assemblies) {
         fixSize(assembly, box, list);
      }
   }

   /**
    * Fix the size of the assembly.
    * @param assembly the specified assembly.
    * @param box the specified viewsheet sandbox.
    * @param list the list contains the changed assemblies.
    */
   public static void fixSize(Assembly assembly, ViewsheetSandbox box, List list) throws Exception {
      if(box == null || assembly instanceof VSAssembly && !isVisibleTabVS((VSAssembly) assembly)) {
         return;
      }

      VSAssemblyInfo info = (VSAssemblyInfo) assembly.getInfo();

      // ignore temp created for cardinality. (51019)
      if(info.isWizardTemporary() && !info.isVisible()) {
         return;
      }

      if(assembly instanceof CalcTableVSAssembly) {
         String name = assembly.getAbsoluteName();

         try {
            TableLens table = (TableLens) box.getData(name);

            if(table != null) {
               CalcTableVSAssembly cassembly = (CalcTableVSAssembly) assembly;
               CalcTableVSAssemblyInfo calcInfo =
                  (CalcTableVSAssemblyInfo) cassembly.getInfo();
               int width = (calcInfo.getHeaderColCount() + 1) * 20;
               int height = (calcInfo.getHeaderRowCount() + 2) * AssetUtil.defh;
               Dimension size = assembly.getPixelSize();
               size = (Dimension) size.clone();

               if(size.width < width || size.height < height) {
                  size.width = Math.max(size.width, width);
                  size.height = Math.max(size.height, height);
                  assembly.setPixelSize(size);
                  list.add(assembly);
               }
            }
         }
         catch(ConfirmException cex) {
            if(!(cex instanceof ConfirmDataException)) {
               throw cex;
            }
         }
         catch(BoundTableNotFoundException | ExpiredSheetException debugException) {
            LOG.debug("Failed to fix the size of a table", debugException);
         }
         catch(Exception ex) {
            LOG.error("Failed to fix the size of a table", ex);
         }
      }
      else if(assembly instanceof CrosstabVSAssembly) {
         String name = assembly.getAbsoluteName();

         try {
            TableLens table = (TableLens) box.getData(name);

            if(table != null) {
               int width = (table.getHeaderColCount() + 1) * 20;
               // make sure the vertical scroll is usable
               int height = (table.getHeaderRowCount() + 2) * AssetUtil.defh;
               Dimension size = assembly.getPixelSize();
               size = (Dimension) size.clone();
               boolean flag = false;

               if(size.width < width) {
                  size.width = width;
                  flag = true;
               }

               if(size.height < height) {
                  size.height = height;
                  flag = true;
               }

               if(flag) {
                  assembly.setPixelSize(size);
                  list.add(assembly);
               }
            }
         }
         catch(ConfirmException cex) {
            if(!(cex instanceof ConfirmDataException)) {
               throw cex;
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to fix the size of a crosstab table", ex);
         }
      }
      else if(assembly instanceof Viewsheet) {
         fixAssemblySize0((Viewsheet) assembly, box, list);
      }
      else if(assembly instanceof CompositeVSAssembly) {
         int span = ((CompositeVSAssembly) assembly).getCellWidth() * AssetUtil.defw;

         if(span > AssetUtil.defw) {
            java.awt.Dimension size = assembly.getPixelSize();
            size = (java.awt.Dimension) size.clone();
            int width = size.width;
            width = (int) (Math.ceil(((double) width) / span) * span);

            if(width != size.width) {
               size = (java.awt.Dimension) size.clone();
               size.width = width;
               assembly.setPixelSize(size);
               list.add(assembly);
            }
         }
      }
      else if(assembly instanceof GroupContainerVSAssembly) {
         if(fixGroupContainerSize((GroupContainerVSAssembly) assembly)) {
            list.add(assembly);
         }
      }
   }

   /**
    * Calculate the group container size from the children.
    * @return true if size is changed.
    */
   public static boolean fixGroupContainerSize(GroupContainerVSAssembly group) {
      group.updateGridSize();
      Dimension nsize = group.getPixelSize();
      VSAssembly container = group.getContainer();

      if(container instanceof TabVSAssembly) {
         Point groupOffset = group.getPixelOffset();
         Point containerOffset = container.getPixelOffset();

         if(groupOffset.x != containerOffset.x) {
            containerOffset.x = groupOffset.x;
            container.setPixelOffset(containerOffset);
         }
      }

      if(!nsize.equals(group.getPixelSize())) {
         group.setPixelSize(nsize);
         return true;
      }

      return false;
   }

   /**
    * Move vsassembly.
    * @param rvs the specified viewsheet.
    * @param command the specified command container.
    * @param pixel true to move pixel position for floatable.
    * @param snap true to snap the edge to the grid.
    */
   public static void moveVSAssembly(RuntimeViewsheet rvs,
                                     String[] names, int x, int y,
                                     AssetCommand command, boolean pixel,
                                     boolean snap)
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      List<VSAssembly> objs = new ArrayList<>();
      boolean allfloat = true;

      for(String name : names) {
         VSAssembly assembly = vs.getAssembly(name);

         objs.add(assembly);
         allfloat = allfloat && (assembly instanceof FloatableVSAssembly);
      }

      for(VSAssembly assembly : objs) {
         Point pos = assembly.getPixelOffset();
         boolean isAdhocFilter = false;
         VSAssemblyInfo info = assembly.getVSAssemblyInfo();

         if((info instanceof SelectionVSAssemblyInfo)) {
            isAdhocFilter = ((SelectionVSAssemblyInfo) info).isAdhocFilter();
         }

         if(assembly instanceof FloatableVSAssembly && snap &&
            // don't clear offset if moving multiple objs together
            (names.length == 1 || pixel && allfloat) && !isAdhocFilter)
         {
            FloatableVSAssembly floatable = (FloatableVSAssembly) assembly;
            Point offset = floatable.getPixelOffset();

            if(offset == null) {
               offset = new Point(0, 0);
            }

            if(pixel) {
               offset.x += x;
               offset.y += y;

               floatable.setPixelOffset(offset);
            }
            // clear pixel offset on move so the obj lines up with grid
            else if(x < 0 && offset.x > 0) {
               floatable.setPixelOffset(new Point(0, offset.y));
            }
            else if(y < 0 && offset.y > 0) {
               floatable.setPixelOffset(new Point(offset.x, 0));
            }
            else {
               if(x > 0) {
                  offset.x = 0;
               }

               if(y > 0) {
                  offset.y = 0;
               }

               floatable.setPixelOffset(offset);
               pos = new Point(pos.x + x, pos.y + y);
            }
         }
         else {
            pos = new Point(pos.x + x, pos.y + y);
         }

//         if(pos.x >= 0 && pos.y >= 0) {
//            assembly.setPosition(pos);
//         }
      }
   }

   /**
    * Remove vsassembly.
    * @param rvs the specified viewsheet.
    * @param id the specified viewsheet id.
    * @param uri the specified link uri.
    * @param assembly the specified assembly.
    * @param command the specified command container.
    * @param replace true if this assembly will be replaced (added) later
    * and should not be permanently removed.
    */
   public static void removeVSAssembly(RuntimeViewsheet rvs, AssetEvent event,
                                       String id, String uri,
                                       VSAssembly assembly,
                                       AssetCommand command, boolean replace)
      throws Exception
   {
      removeVSAssembly(rvs, event, id, uri, assembly, command, replace, true);
   }

   /**
    * Remove vsassembly.
    * @param rvs the specified viewsheet.
    * @param id the specified viewsheet id.
    * @param uri the specified link uri.
    * @param assembly the specified assembly.
    * @param command the specified command container.
    * @param replace true if this assembly will be replaced (added) later
    * and should not be permanently removed.
    * @param layout true to layout the viewsheet after removal.
    */
   public static void removeVSAssembly(RuntimeViewsheet rvs, AssetEvent event,
                                       String id, String uri,
                                       VSAssembly assembly,
                                       AssetCommand command, boolean replace,
                                       boolean layout)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null) {
         return;
      }

      Viewsheet gvs = vs; // gloval viewsheet
      String name = assembly.getAbsoluteName();
      String fname = name;
      int dot = name.indexOf(".");

      if(dot >= 0) {
         String bname = name.substring(0, dot);
         Assembly vsc = vs.getAssembly(bname);
         ViewsheetSandbox box2 = box.getSandbox(bname);

         if(vsc instanceof Viewsheet && box2 != null) {
            vs = (Viewsheet) vsc;
            box = box2;
            name = name.substring(dot + 1);
         }
      }

      Worksheet ws = vs.getBaseWorksheet();
      AssemblyRef[] refs = vs.getDependings(assembly.getAssemblyEntry());
      List<Assembly> rlist = new ArrayList<>();
      SelectionVSAssembly sassembly2 = null;

      if(!replace && assembly instanceof SelectionVSAssembly) {
         SelectionVSAssembly sassembly = (SelectionVSAssembly) assembly;

         String table = sassembly.getTableName();
         SelectionVSAssembly[] sarr = box.getSelectionVSAssemblies(table);

         for(SelectionVSAssembly tsa : sarr) {
            if(tsa != assembly) {
               sassembly2 = tsa;
               break;
            }
         }
      }
      else if(!replace && assembly instanceof EmbeddedTableVSAssembly) {
         new EmbeddedTableVSAQuery(box, assembly.getName(), false).
            resetEmbeddedData();
      }
      else if(!replace && assembly instanceof InputVSAssembly) {
         InputVSAssembly vassembly = (InputVSAssembly) assembly;
         new InputVSAQuery(box, assembly.getName()).resetEmbeddedData(false);

         if(vassembly.isVariable()) {
            String tname = vassembly.getTableName();
            tname = tname.substring(2, tname.length() - 1);
            VariableAssembly vass = (VariableAssembly) ws.getAssembly(tname);
            Object dvalue = null;

            if(vass != null) {
               dvalue = vass.getVariable().getValueNode() == null ?
                  null : vass.getVariable().getValueNode().getValue();
            }

            VSAssemblyInfo info = vassembly.getVSAssemblyInfo();

            if(dvalue != null) {
               if(info instanceof SliderVSAssemblyInfo) {
                  ((SliderVSAssemblyInfo) info).setSelectedObject(dvalue);
               }
               else if(info instanceof SpinnerVSAssemblyInfo) {
                  ((SpinnerVSAssemblyInfo) info).setSelectedObject(dvalue);
               }
               else if(info instanceof CheckBoxVSAssemblyInfo) {
                  Object[] objs = new Object[] {dvalue};
                  ((CheckBoxVSAssemblyInfo) info).setSelectedObjects(objs);
               }
               else if(info instanceof RadioButtonVSAssemblyInfo) {
                  ((RadioButtonVSAssemblyInfo) info).setSelectedObject(dvalue);
               }
               else if(info instanceof ComboBoxVSAssemblyInfo) {
                  ((ComboBoxVSAssemblyInfo) info).setSelectedObject(dvalue);
               }
            }
            else {
               if(vassembly instanceof NumericRangeVSAssembly) {
                  ((NumericRangeVSAssembly) vassembly).clearSelectedObject();
               }
               else if(vassembly instanceof ListInputVSAssembly) {
                  ((ListInputVSAssembly) vassembly).clearSelectedObjects();
               }
            }

            AssetQuerySandbox wbox = box.getAssetQuerySandbox();
            wbox.setIgnoreFiltering(false);
            ChangedAssemblyList clist = ViewsheetEvent.createList(true,
               event, command, rvs, uri);
            VSEventUtil.refreshEmbeddedViewsheet(rvs, event, uri,
               command);
            box.resetRuntime();
            VSEventUtil.refreshViewsheet(rvs, event, id, uri, command,
                                         false, true, true, clist);
         }
      }
      else if(!replace && assembly instanceof AnnotationVSAssembly) {
         Assembly base = AnnotationVSUtil.getBaseAssembly(
            gvs, assembly.getAbsoluteName());

         if(base != null && base.getInfo() instanceof BaseAnnotationVSAssemblyInfo) {
            BaseAnnotationVSAssemblyInfo baseInfo =
               (BaseAnnotationVSAssemblyInfo) base.getInfo();
            baseInfo.removeAnnotation(assembly.getAbsoluteName());
            refreshVSAssembly(rvs, base.getAbsoluteName(), command);
         }
      }

      for(int i = 0; !replace && i < refs.length; i++) {
         AssemblyEntry entry = refs[i].getEntry();
         Assembly tassembly = null;

         if(entry.isWSAssembly()) {
            tassembly = ws != null ? ws.getAssembly(entry) : null;

            // reexecute runtime condition list
            if(tassembly instanceof TableAssembly &&
               !(assembly instanceof EmbeddedTableVSAssembly))
            {
               ((TableAssembly) tassembly).setPreRuntimeConditionList(null);
               ((TableAssembly) tassembly).setPostRuntimeConditionList(null);
               AssemblyRef[] refs2 = vs.getDependeds(entry);

               for(int j = 0; refs2 != null && j < refs2.length; j++) {
                  Assembly assembly2 = vs.getAssembly(refs2[j].getEntry());

                  if(assembly2 instanceof SelectionVSAssembly) {
                     rlist.add(assembly2);
                  }
               }
            }
         }
         else if(!name.equals(entry.getName())) {
            tassembly = vs.getAssembly(entry);
         }

         if(tassembly != null) {
            rlist.add(tassembly);
         }
      }

      Assembly[] rarr = new Assembly[rlist.size()];
      rlist.toArray(rarr);
      AssemblyRef[] vrefs = vs.getViewDependings(assembly.getAssemblyEntry());

      if(vs.removeAssembly(name)) {
         command.addCommand(new RemoveVSObjectCommand(fname));

         // reexecute depending assemblies
         if(rarr.length > 0) {
            ChangedAssemblyList clist = new ChangedAssemblyList();
            box.reset(null, rarr, clist, false, false, null);
            execute(rvs, event, fname, null, clist, command, false);
         }

         // reprocess associated assemblies
         if(sassembly2 != null) {
            try {
              int hint = VSAssembly.OUTPUT_DATA_CHANGED;
              execute(rvs, event, sassembly2.getAbsoluteName(), null, hint,
                      command);
            }
            catch(ConfirmDataException ex) {
               // do nothing, it doesn't need the confirm data exception
               // when remove assembly
            }
            catch(ScriptException ex) {
               List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

               if(exs != null) {
                  exs.add(ex);
               }
            }
         }

         // refresh views dependings
         for(int i = 0; !replace && i < vrefs.length; i++) {
            AssemblyEntry entry = vrefs[i].getEntry();

            if(entry.isVSAssembly()) {
               try {
                  box.executeView(entry.getName(), true);
               }
               catch(ConfirmDataException ex) {
                  // do nothing, it doesn't need the confirm data exception
                  // when remove assembly
               }
               catch(ScriptException ex) {
                  List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

                  if(exs != null) {
                     exs.add(ex);
                  }
               }

               refreshVSAssembly(rvs, entry.getAbsoluteName(), command);
            }
         }

         if(!replace) {
            // current selection supports remove child
            // when in embedded viewsheet
            if(assembly.getContainer() instanceof CurrentSelectionVSAssembly) {
               ContainerVSAssembly cass =
                  (ContainerVSAssembly) assembly.getContainer();

               // do not use absolute name
               if(cass.removeAssembly(assembly.getName())) {
                  refreshVSAssembly(rvs, cass.getAbsoluteName(), command);
               }
            }
            else {
               Assembly[] arr = vs.getAssemblies();

               for(Assembly assembly1 : arr) {
                  if(!(assembly1 instanceof ContainerVSAssembly)) {
                     continue;
                  }

                  ContainerVSAssembly container = (ContainerVSAssembly) assembly1;

                  if(!container.removeAssembly(name)) {
                     continue;
                  }

                  refreshVSAssembly(rvs, container.getAbsoluteName(), command);

                  if(!(assembly1 instanceof TabVSAssembly)) {
                     continue;
                  }

                  TabVSAssembly tab = (TabVSAssembly) assembly1;
                  String[] names = tab.getAssemblies();

                  // for viewsheet performance, we don't refresh the assembly
                  // which is not the selected tab in tab assembly,
                  // so if we remove the selected assembly in tab, we should
                  // refresh the next selected assembly.
                  // see bug1302191571448
                  tab.setSelectedValue(names[0]);
                  loadTableLens(rvs, event, names[0], event.getLinkURI(),
                                command);

                  if(names.length <= 1) {
                     removeVSAssembly(rvs, event, id, uri, tab, command, false);

                     if(names.length > 0) {
                        VSAssembly comp = tab.getViewsheet().getAssembly(names[0]);
                        comp.setZIndex(tab.getZIndex());
                        refreshVSAssembly(rvs, comp.getAbsoluteName(),
                                          command);
                     }
                  }
               }
            }
         }

         if(assembly instanceof Viewsheet) {
            List<Assembly> objList = new ArrayList<>();
            appendEmbeddedChild((Viewsheet) assembly, objList, true, rvs);

            for(Assembly item : objList) {
               VSAssembly childAssembly = (VSAssembly) item;
               command.addCommand(new RemoveVSObjectCommand(
                  childAssembly.getAbsoluteName()));
            }
         }
         else if(assembly instanceof TabVSAssembly && layout) {
            layoutViewsheet(rvs, event, id, uri, command);

            TabVSAssembly tab = (TabVSAssembly) assembly;
            String[] names = tab.getAssemblies();

            // move floatable children apart since layout would not move them
            for(int k = 0; k < names.length; k++) {
               VSAssembly comp = tab.getViewsheet().getAssembly(names[k]);

               if(comp instanceof FloatableVSAssembly) {
                  Point pos = comp.getPixelOffset();
                  comp.setPixelOffset(new Point(pos.x + (k * AssetUtil.defw), pos.y));
               }

               refreshVSAssembly(rvs, names[k], command, true);
               loadTableLens(rvs, event, names[k], event.getLinkURI(), command);
            }
         }
         else if(assembly instanceof GroupContainerVSAssembly && layout) {
            String[] children =
               ((GroupContainerVSAssembly) assembly).getAssemblies();

            for(String child : children) {
               refreshVSAssembly(rvs, child, command);
            }
         }
      }
      else {
         command.addCommand(new MessageCommand(
            Catalog.getCatalog().getString(
               "common.removeAssemblyFailed"),
            MessageCommand.WARNING));
      }
   }

   /**
    * Open viewsheet.
    * @param engine the specified viewsheet engine.
    * @param user the specified user.
    * @param uri the specified service request uri.
    * @param entry the specified viewsheet entry.
    * @param command the specified command container.
    * @param viewer <tt>true</tt> if is viewer, <tt>false</tt> otherwise.
    * @param drillfrom the viewsheet id of the original viewsheet if this
    * is a drilldown.
    * @param vars viewsheet parameters.
    * @param fullScreenId the view sheet id which is to be full screen.
    */
   public static String openViewsheet(ViewsheetService engine, AssetEvent event,
                                    Principal user, String uri, String eid,
                                    AssetEntry entry, AssetCommand command,
                                    boolean viewer, String drillfrom,
                                    VariableTable vars, String fullScreenId)
      throws Exception
   {
      ChangedAssemblyList clist = ViewsheetEvent.createList(true, event,
                                                            command, null, uri);
      String vsID = entry.getProperty("vsID");
      String id = Tool.byteDecode(fullScreenId);
      String bookmarkIndex =
         Tool.byteDecode(entry.getProperty("bookmarkIndex"));
      entry.setProperty("drillfrom", drillfrom);
      entry.setProperty("vsID", null);
      entry.setProperty("bookmarkIndex", null);
      RuntimeViewsheet rvs;
      RuntimeViewsheet rvs2 = null;

      if(id != null && id.length() > 0) {
         rvs = engine.getViewsheet(id, user);
         rvs.getEntry().setProperty("bookmarkIndex", bookmarkIndex);
         // optimization, this shouldn't be needed for new vs since the
         // RuntimeViewsheet cstr calls updateVSBookmark through setEntry
         rvs.updateVSBookmark();
         // @by davyc, if full screen viewsheet, keep its variables
         // fix bug1366833082660
         VariableTable temp = rvs.getViewsheetSandbox().getVariableTable();

         if(temp != null) {
            temp.addAll(vars);
         }

         vars = temp;
      }
      else {
         if(vsID != null) {
            rvs2 = engine.getViewsheet(vsID, user);
         }

         id = engine.openViewsheet(entry, user, viewer);
         rvs = engine.getViewsheet(id, user);
      }

      syncEmbeddedTableVSAssembly(rvs.getViewsheet());
      rvs.setEmbeddedID(eid);

      // if opened for editing from a viewer vs, copy the current viewer vs so
      // editing starts from the same state as the viewer
      if(rvs2 != null) {
         Viewsheet sheet = rvs2.restoreViewsheet();
         //if opened for editing from viewer vs, clear layout position and size
         sheet.clearLayoutState();
         rvs.setViewsheet(sheet);
      }

      ChangedAssemblyList.ReadyListener rlistener = clist.getReadyListener();
      command.setID(id);
      setPermission(rvs, user, command);
      setExportType(command);

      if(rlistener != null) {
         rlistener.setRuntimeSheet(rvs);
         rlistener.setID(id);
      }

      Set<String> scopied = new HashSet<>();
      ViewsheetSandbox vbox = rvs.getViewsheetSandbox();
      AssetQuerySandbox box = vbox.getAssetQuerySandbox();

      // drilldown vs inherit selections from source
      if(drillfrom != null) {
         RuntimeViewsheet ovs = engine.getViewsheet(drillfrom, user);

         if(ovs != null) {
            ViewsheetSandbox ovbox = ovs.getViewsheetSandbox();
            copySelections(ovs.getViewsheet(), rvs.getViewsheet(), scopied);

            if(scopied.size() > 0) {
               // in case calendar changed from single to double
               rvs.getViewsheet().layout();
            }

            if(box != null) {
               vbox.setPViewsheet(ovbox.getScope());
            }
         }

         if(box != null) {
            // replace all drilldown variables so they don't accumulate
            box.getVariableTable().clear();
         }
      }

      if(vars != null && box != null) {
         Enumeration iter = vars.keys();

         while(iter.hasMoreElements()) {
            String key = (String) iter.nextElement();
            Object val = vars.get(key);
            box.getVariableTable().put(key, val);
         }

         vbox.resetRuntime();
      }

      List ids = null;

      if(eid != null) {
         RuntimeViewsheet prvs = engine.getViewsheet(eid, user);

         if(prvs != null) {
            Viewsheet parent = prvs.getViewsheet();
            parent.addChildId(id);

            ids = parent.getChildrenIds();

            // use parent viewsheet to refresh viewsheet
            RuntimeViewsheet crvs = engine.getViewsheet(id, user);
            Viewsheet embed = VSEventUtil.getViewsheet(parent,
                                                       crvs.getEntry());
            VSEventUtil.updateViewsheet(embed, crvs, command);
         }
      }

      refreshViewsheet(rvs, event, id, uri, command, true, false, false,
                       clist, scopied, vars);

      if(ids != null) {
         SetVSEmbedCommand scmd = new SetVSEmbedCommand(ids);
         command.addCommand(scmd);
      }

      command.addCommand(new SetLayoutInfoCommand(rvs));

      return id;
   }

   /**
    * Sync embedded table assembly.
    */
   public static void syncEmbeddedTableVSAssembly(Viewsheet vs) {
      Assembly[] assemblies = vs == null ? new Assembly[0] : vs.getAssemblies(true);

      for(Assembly assembly : assemblies) {
         if(assembly instanceof EmbeddedTableVSAssembly) {
            EmbeddedTableVSAssembly etable = (EmbeddedTableVSAssembly) assembly;
            // just set self info to self, to make sure validate function will
            // be called, and in fact, only a bc embedded table need to call
            // this function when open viewsheet or add a embedded viewsheet,
            // but this method is not heavy, and always mark a bc property seems
            // not so comfortable
            etable.setVSAssemblyInfo(etable.getVSAssemblyInfo());
         }
      }
   }

   /**
    * Copy selection states from a viewsheet to the other.
    */
   public static void copySelections(Viewsheet from, Viewsheet to, Set<String> scopied) {
      if(!Tool.equals(from.getBaseEntry(), to.getBaseEntry())) {
         return;
      }

      Assembly[] arr1 = from.getAssemblies(true);
      Assembly[] arr2 = to.getAssemblies(true);

      for(Assembly item : arr1) {
         if(!(item instanceof SelectionVSAssembly)) {
            continue;
         }

         SelectionVSAssembly obj1 = (SelectionVSAssembly) item;

         next1:
         for(Assembly item2 : arr2) {
            if(!item2.getClass().equals(obj1.getClass())) {
               continue;
            }

            SelectionVSAssembly obj2 = (SelectionVSAssembly) item2;

            // make sure it's bound to the same table
            if(!Tool.equals(obj1.getTableName(), obj2.getTableName())) {
               continue;
            }

            DataRef[] cols1 = obj1.getDataRefs();
            DataRef[] cols2 = obj2.getDataRefs();

            if(cols1.length != cols2.length) {
               continue;
            }

            for(int k = 0; k < cols1.length; k++) {
               if(!Tool.equals(cols1[k], cols2[k])) {
                  continue next1;
               }
            }

            scopied.add(obj2.getName());
            obj2.copyStateSelection(obj1);
         }
      }

      // if selections are copied, the new selection states may not be
      // consistent. Since we don't have the information to reset only
      // necessary associations, we will reset all to be safe.
      if(scopied.size() > 0) {
         for(Assembly item2 : arr2) {
            if(!(item2 instanceof SelectionVSAssembly) ||
               scopied.contains(item2.getName())) {
               continue;
            }

            SelectionVSAssembly obj2 = (SelectionVSAssembly) item2;
            obj2.resetSelection();
         }
      }
   }

   public static void setExportType(AssetCommand cmd) {
      String types = SreeEnv.getProperty("vsexport.menu.options");

      // when not to set the viewsheet export type in EM side, the default
      // type is all export type can be used.
      if(types == null) {
         types = "Excel,PowerPoint,PDF,Html,Snapshot";
      }

      cmd.put("_vsexporttype_", types);
   }

   /**
    * Check permission of viewsheet.
    * @param rvs the specified runtime viewsheet.
    * @param user the specified user.
    * @param cmd the specified command container.
    */
   public static void setPermission(RuntimeViewsheet rvs, Principal user,
                                    AssetCommand cmd) throws Exception
   {
      String permission = "";

      if(rvs.isRuntime()) {
         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "AddBookmark", ResourceAction.READ))
         {
            permission += "AddBookmark;";
         }

         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "BrowserBookmark", ResourceAction.READ))
         {
            permission += "BrowserBookmark;";
         }

         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Print", ResourceAction.READ))
         {
            permission += "PrintVS;";
         }

         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "SaveSnapshot", ResourceAction.READ))
         {
            permission += "SaveSnapshot;";
         }

         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "HOME", ResourceAction.READ))
         {
            permission += "Home;";
         }

         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "PageNavigation", ResourceAction.READ))
         {
            permission += "PageNavigation;";
         }

         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Edit", ResourceAction.READ))
         {
            permission += "Edit;";
         }

         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "ExportExpandComponents",
            ResourceAction.READ))
         {
            permission += "ExportExpandComponents;";
         }

         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "ScheduleExpandComponents",
            ResourceAction.READ))
         {
            permission += "ScheduleExpandComponents;";
         }

         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "SaveSnapshot", ResourceAction.READ))
         {
            permission += "SaveSnapshot;";
         }

         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Export", ResourceAction.READ))
         {
            permission += "ExportVS;";
         }

         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Import", ResourceAction.READ) ||
            !FormUtil.containsForm(rvs.getViewsheet(), true))
         {
            permission += "ImportXLS;";
         }

         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Email", ResourceAction.READ))
         {
            permission += "Email;";
         }

         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Schedule", ResourceAction.READ) ||
            !SecurityEngine.getSecurity().checkPermission(
               user, ResourceType.SCHEDULER, "*", ResourceAction.ACCESS))
         {
            permission += "Schedule;";
         }

         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Refresh", ResourceAction.READ))
         {
            permission += "Refresh;";
         }

         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Social Sharing", ResourceAction.READ))
         {
            permission += "Social Sharing;";
         }

         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_ACTION, "Bookmark", ResourceAction.READ))
         {
            permission += "AllBookmark;";
         }

         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_ACTION, "OpenBookmark", ResourceAction.READ))
         {
            permission += "OpenBookmark;";
         }

         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_ACTION, "ShareBookmark", ResourceAction.READ))
         {
            permission += "ShareBookmark;";
         }

         if(!SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.VIEWSHEET_ACTION, "ShareToAll", ResourceAction.READ))
         {
            permission += "ShareToAll;";
         }

         if(SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.REPORT, rvs.getEntry().getPath(), ResourceAction.WRITE))
         {
            permission += "Viewsheet_Write";
         }
      }

      cmd.put("_permission_", permission);
   }

   /**
    * Refresh viewsheet.
    * @param rvs the specified runtime viewsheet.
    * @param id the specified viewsheet id.
    * @param uri the specified service request uri.
    * @param cmd the specified command container.
    * @param component <tt>true</tt> if refresh component only.
    * @param reset true to reset viewsheet values.
    * @param clist the specified changed assembly list.
    */
   public static void refreshViewsheet(RuntimeViewsheet rvs, AssetEvent event,
                                       String id, String uri, AssetCommand cmd,
                                       boolean initing, boolean component,
                                       boolean reset, ChangedAssemblyList clist)
      throws Exception
   {
      refreshViewsheet(rvs, event, id, uri, cmd, initing, component, reset,
                       clist, null, null);
   }

   /**
    * Refresh viewsheet.
    * @param rvs the specified runtime viewsheet.
    * @param id the specified viewsheet id.
    * @param uri the specified service request uri.
    * @param cmd the specified command container.
    * @param component <tt>true</tt> if refresh component only.
    * @param reset true to reset viewsheet values.
    * @param clist the specified changed assembly list.
    * @param scopied the set contains the names of the selection assemblies
    * whose selections are copied from another viewsheet. Here we should start
    * to process selection from these selections.
    * @param initvars parameters passed in for opening a new vs. They shouldn't
    * be prompted again.
    */
   public static void refreshViewsheet(RuntimeViewsheet rvs, AssetEvent event,
                                       String id, String uri, AssetCommand cmd,
                                       boolean initing, boolean component,
                                       boolean reset, ChangedAssemblyList clist,
                                       Set<String> scopied, VariableTable initvars)
      throws Exception
   {
      Viewsheet sheet = rvs.getViewsheet();

      if(sheet == null) {
         return;
      }

      boolean inited = false;

      for(int i = 0; i < cmd.getCommandCount(); i++) {
         if(cmd.getCommand(i) instanceof InitGridCommand) {
            inited = true;
            break;
         }
      }

      ViewsheetInfo vsinfo = sheet.getViewsheetInfo();
      // Include invisible assemblies when calculating the view size because
      // they may be made visible at a later time, e.g. with a script.
      // Assemblies that are not included in the layout have already been
      // explicitly positioned and sized to zero so that they don't affect
      // anything else.
      clearScale(sheet);
      Dimension viewSize = sheet.getPreferredSize(true, false);

      // reset the runtime values before onLoad/onInit, so we don't
      // clear out values set in onLoad/onInit
      // @by stephenwebster, For Bug #7758, I moved resetRuntimeValues before
      // scale to screen operations so scaled column widths would not get reset.
      VSUtil.resetRuntimeValues(sheet, false);

      if(event.get("viewWidth") != null && event.get("viewHeight") != null) {
         viewSize = new Dimension(
            Integer.parseInt((String) event.get("viewWidth")),
            Integer.parseInt((String) event.get("viewHeight")));
      }

      if(vsinfo != null && vsinfo.isScaleToScreen() &&
         event.get("propWidth") != null && event.get("propHeight") != null)
      {
         double width  = Double.parseDouble((String) event.get("propWidth")); //EVPane width
         double height = Double.parseDouble((String) event.get("propHeight")); //EVPane height

         boolean mobile = "true".equals(event.get("_device_mobile"));
         String userAgent = (String) event.get("_device_user_agent");

         Point2D.Double scaleRatio = calcScalingRatio(sheet, viewSize, width, height, mobile);
         applyScale(sheet, scaleRatio, mobile, userAgent, width, rvs.getViewsheetSandbox());

         // remember the current bounds
         rvs.setProperty("viewsheet.init.bounds", sheet.getPreferredBounds());
      }

      if(!component && !inited) {
         sheet.setLastSize(sheet.getPixelSize());
         // @damianwysocki Bug#9543 - remove grid sizes
         // this is the old command, should be removed eventually
         int[] cols = new int[0];
         int[] rows = new int[0];

         InitGridCommand icmd = new InitGridCommand(id, cols, rows,
                                                    rvs.getEntry(), null);
         long modified = sheet.getLastModified();
         Date date = new Date(modified);
         icmd.setIniting(initing);
         icmd.setViewSize(viewSize);
         icmd.setEditable(rvs.isEditable());
         icmd.setLockOwner(rvs.getLockOwner());
         icmd.setEmbeddedID(rvs.getEmbeddedID());

         if(initing) {
            icmd.setLastModified(AssetUtil.getDateTimeFormat().format(date));
         }

         icmd.put("__permission__", (String) cmd.get("_permission_"));
         icmd.put("__scope__", rvs.getEntry().getScope() + "");
         icmd.put("__vsexporttype__", (String) cmd.get("_vsexporttype_"));
         icmd.put("_rscaleFont_", sheet.getRScaleFont() + "");
         cmd.addCommand(icmd, true);
         setToolbar(sheet);
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return;
      }

      // viewsheetsandbox tracks whether onInit needs to be executed
      box.processOnInit();

      if(initing) {
         // set infos so the isScaleToScreen() is correct when vsobject
         // is refreshed
         SetViewsheetInfoCommand setInfoCommand = new SetViewsheetInfoCommand(rvs);
         setInfoCommand.setID(id);
         cmd.addCommand(setInfoCommand);

         // make sure containers are created before the children
         // so the check for whether a component is in container is correct
         refreshContainer(rvs);

         // make sure embedded viewsheet is created before the children so
         // the position is available in VSObject.getPixelPosition
         refreshEmbeddedViewsheet(rvs, event, uri, cmd);

         // @by mikec, collect parameters before reset the sandbox seems
         // more reasonable, otherwise if during reset box the code need
         // parameters to access database, error will be thrown.
         // @see bug1234937851455
         List<UserVariable> vars = new ArrayList<>();
         WorksheetService engine = event.getWorksheetEngine();
         refreshParameters(engine, box, sheet, false, initvars, vars);

         if(vars.size() > 0 && !Boolean.valueOf(System.getProperty("ScheduleServer"))) {
            ViewsheetCommand cmd2 = new SetViewsheetInfoCommand(rvs);
            cmd2.setID(id);
            cmd.addCommand(cmd2, true);

            UserVariable[] vtable = vars.toArray(new UserVariable[0]);
            cmd2 = new CollectParametersCommand(vtable, id, true);
            cmd2.setID(id);
            ((CollectParametersCommand) cmd2).setViewsheetInfo(
               sheet.getViewsheetInfo());
            cmd.addCommand(cmd2);

            return;
         }

         Assembly[] assemblies = sheet.getAssemblies(false, true);
         Arrays.sort(assemblies, new AnnotationComparator());

         // make the assemblies paint before the data is fetched so the
         // user could see the vs without a long wait
         for(Assembly assembly : assemblies) {
            VSAssembly vsassembly = (VSAssembly) assembly;

            // bug1390407468587, avoid triggering a query (e.g. chart)
            // before condition is set (in box.reset later in this method)
            if(vsassembly instanceof DataVSAssembly ||
               vsassembly instanceof OutputVSAssembly ||
               vsassembly instanceof ContainerVSAssembly ||
               vsassembly instanceof Viewsheet ||
               !isVisibleInTab(vsassembly.getVSAssemblyInfo())) {
               //bug1395797472256, chart need update for onload script.
               if(vsassembly instanceof ChartVSAssembly) {
                  box.updateAssembly(vsassembly.getAbsoluteName());
               }

               continue;
            }

            VSAssemblyInfo info = (VSAssemblyInfo) vsassembly.getInfo();
            boolean enabled = info.isEnabled();

            info.setEnabled(false);
            addDeleteVSObject(rvs, event, vsassembly, uri, cmd);
            info.setEnabled(enabled);
         }

         cmd.fireEvent();
      }

      // need process onload every time when refresh the viewsheet, since
      // onload script will effect without delay

      if(reset) {
         box.reset(null, sheet.getAssemblies(), clist, true, true, null);
      }
      else if(initing) {
         box.reset(null, sheet.getAssemblies(), clist, true, true, scopied);
      }

      if(initing) {
         sheet = rvs.getViewsheet();

         if(sheet == null) {
            return;
         }

         rvs.replaceCheckpoint(sheet.prepareCheckpoint(), null);
         // visibility may be changed in reset
         sheet.layout();
      }
      // @by davyc, InitGridCommand may removed all objects, so here
      // should refresh container to make sure the child get container is
      // correct in client side, see bug1239603135453
      else if(inited) {
         Viewsheet vs = rvs.getViewsheet();

         if(vs == null) {
            return;
         }

         Assembly[] assemblies = vs.getAssemblies(false, true);

         for(Assembly assembly : assemblies) {
            VSAssembly vsassembly = (VSAssembly) assembly;

            if(vsassembly instanceof ContainerVSAssembly &&
               !(vsassembly instanceof TabVSAssembly) ||
               vsassembly instanceof SelectionVSAssembly) {
               refreshVSAssembly(rvs, vsassembly.getAbsoluteName(), cmd, true);
            }
         }
      }

      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      Assembly[] assemblies = vs.getAssemblies(false, true);
      Arrays.sort(assemblies, new AnnotationComparator());

      for(Assembly assembly : assemblies) {
         VSAssembly vsassembly = (VSAssembly) assembly;

         // already processed? ignore it
         if(clist.getProcessedList().contains(vsassembly.getAssemblyEntry())) {
            continue;
         }

         if(vsassembly instanceof Viewsheet) {
            setLinkURI(uri, ((Viewsheet) vsassembly).getAssemblies(true, false));
         }
         else if(vsassembly instanceof OutputVSAssembly) {
            setLinkURI(uri, vsassembly);
         }

         addDeleteVSObject(rvs, event, vsassembly, uri, cmd);

         if(vsassembly instanceof Viewsheet) {
            initTable(rvs, cmd, uri,
                      ((Viewsheet) vsassembly).getAssemblies(true, false));
         }
         else {
            initTable(rvs, cmd, uri, vsassembly);
         }
      }

      List errors = (List) AssetRepository.ASSET_ERRORS.get();

      if(errors != null && errors.size() > 0) {
         StringBuilder sb = new StringBuilder();
         AssetEntry entry = rvs.getEntry();

         for(int i = 0; i < errors.size(); i++) {
            if(i > 0) {
               sb.append(", ");
            }

            sb.append(errors.get(i));
         }

         sb.append("(").append(entry.getDescription()).append(")");
         errors.clear();

         String msg = Catalog.getCatalog().getString(
            "common.mirrorAssemblies.updateFailed", sb.toString());
         MessageCommand mcmd = new MessageCommand(msg, MessageCommand.WARNING);

         cmd.addCommand(mcmd);
      }

      // set info after script is executed
      SetViewsheetInfoCommand cmd2 = new SetViewsheetInfoCommand(rvs);
      cmd.addCommand(cmd2, true);

      for(int i = 0; i < cmd.getCommandCount(); i++) {
         AssetCommand command = cmd.getCommand(i);

         if(command instanceof ViewsheetCommand) {
            command.setID(id);
         }
      }
   }

   public static void clearScale(VSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      info.setScaledPosition(null);
      info.setScaledSize(null);

      if(assembly instanceof Viewsheet) {
         for(Assembly child : ((Viewsheet) assembly).getAssemblies()) {
            clearScale((VSAssembly) child);
         }
      }
   }

   /**
    * Apply scale.
    */
   public static void applyScale(Viewsheet viewsheet, Point2D.Double scaleRatio,
                                 boolean mobile, String userAgent, double width,
                                 ViewsheetSandbox box)
      throws Exception
   {
      applyScale(viewsheet, scaleRatio, mobile, userAgent, width, -1, box);
   }

   /**
    * Apply scale.
    */
   public static void applyScale(Viewsheet viewsheet, Point2D.Double scaleRatio,
                                 boolean mobile, String userAgent, double width, double height,
                                 ViewsheetSandbox box)
         throws Exception
   {
      ViewsheetInfo info = viewsheet.getViewsheetInfo();
      Assembly[] assemblies = viewsheet.getAssemblies();
      List<VSAssembly> popComponents = new ArrayList<>();
      List<GroupContainerVSAssembly> groupComponents = new ArrayList<>();

      for(Assembly assembly : assemblies) {
         VSAssembly vsassembly = (VSAssembly) assembly;
         String assemblyName = vsassembly.getAbsoluteName();
         boolean isFloat = VSUtil.isPopComponent(assemblyName, viewsheet) ||
            VSUtil.isTipView(assemblyName, viewsheet);
         boolean isAnnotation = AnnotationVSUtil.isAnnotation(vsassembly);

         if(vsassembly instanceof GroupContainerVSAssembly) {
            groupComponents.add((GroupContainerVSAssembly) vsassembly);
         }

         // Scale invisible assemblies, because they may be made visible at some
         // later time, e.g. with script. Assemblies that are not included in
         // the layout are explicitly positioned and sized to 0 so that they
         // don't affect anything here.
         if(!isAnnotation) {
            VSAssembly container = vsassembly.getContainer();

            if(container instanceof TabVSAssembly ||
               container instanceof CurrentSelectionVSAssembly)
            {
               continue;
            }

            if(isFloat) {
               popComponents.add(vsassembly);
            }
            else if(!(vsassembly instanceof SelectionListVSAssembly && container == null &&
               ((SelectionListVSAssemblyInfo)vsassembly.getInfo()).isAdhocFilter()))
            {
               applyAssemblyScale(vsassembly, scaleRatio, info, mobile,
                                  isFloat(vsassembly, viewsheet), width, box, false);
            }

            if(vsassembly instanceof CurrentSelectionVSAssembly) {
               applyCurrentSelectionScale(viewsheet,
                                          (CurrentSelectionVSAssembly) vsassembly, scaleRatio);
            }
            else if(vsassembly instanceof TabVSAssembly) {
               applyTabScale(viewsheet, (TabVSAssembly) vsassembly, scaleRatio,
                             mobile, userAgent, box);
            }
            else if(vsassembly instanceof Viewsheet) {
               applyScale((Viewsheet) vsassembly, scaleRatio, mobile, userAgent, width, box);
            }
         }
      }

      List<VSAssembly> popGroupComponents = new ArrayList<>();

      for(VSAssembly popAssembly: popComponents) {
         if(popAssembly instanceof GroupContainerVSAssembly) {
            popGroupComponents.add(popAssembly);
            continue;
         }

         applyAssemblyScale(popAssembly, scaleRatio, info, mobile, true, width, box, false);
      }

      for(VSAssembly popGroupAssembly: popGroupComponents) {
         applyAssemblyScale(popGroupAssembly, scaleRatio, info, mobile, true, width, box, false);
      }

      for(GroupContainerVSAssembly groupComponent : groupComponents) {
         fixGroupContainerTop(groupComponent, viewsheet);
      }

      handleOverlapping(viewsheet, info, scaleRatio, mobile, width, box, assemblies);
      scaleAssemblyAnnotationPos(viewsheet, width, height);
   }

   private static void fixGroupContainerTop(GroupContainerVSAssembly group, Viewsheet vs) {
      if(group == null) {
         return;
      }

      String[] assemblies = group.getAssemblies();

      if(assemblies == null || assemblies.length == 0) {
         return;
      }

      VSAssemblyInfo vsAssemblyInfo = group.getVSAssemblyInfo();

      if(!vsAssemblyInfo.isScaled()) {
         return;
      }

      Integer minScaledY = getMinScaledY(assemblies, vs);
      Point layoutPosition = vsAssemblyInfo.getLayoutPosition(true);

      if(minScaledY != null && layoutPosition.y != minScaledY) {
         layoutPosition.y = minScaledY;
         vsAssemblyInfo.setScaledPosition(layoutPosition);
      }
   }

   private static Integer getMinScaledY(String[] assemblies, Viewsheet vs) {
      Integer minY = null;

      for(String assembly : assemblies) {
         VSAssembly child = vs.getAssembly(assembly);

         if(child == null) {
            continue;
         }

         VSAssemblyInfo info = child.getVSAssemblyInfo();

         if(!info.isScaled()) {
            continue;
         }

         Integer childMinY = null;

         if(child instanceof TabVSAssembly) {
            childMinY = getMinScaledY(((TabVSAssembly) child).getAssemblies(), vs);
         }
         else {
            Point layoutPosition = info.getLayoutPosition(true);

            if(layoutPosition != null) {
               childMinY = layoutPosition.y;
            }
         }

         if(childMinY == null) {
            continue;
         }

         if(minY == null) {
            minY = childMinY;
         }
         else {
            minY = Math.min(childMinY, minY);
         }
      }

      return minY;
   }

   private static void handleOverlapping(Viewsheet viewsheet, ViewsheetInfo info,
                                         Point2D.Double scaleRatio, boolean mobile, double width,
                                         ViewsheetSandbox box, Assembly[] assemblies)
      throws Exception
   {

      for(int i = 0; i < assemblies.length; i++) {
         Assembly assembly = assemblies[i];

         boolean assemblyIsFloat = VSUtil.isPopComponent(assembly.getAbsoluteName(), viewsheet) ||
            VSUtil.isTipView(assembly.getAbsoluteName(), viewsheet);

         if(assembly instanceof ListInputVSAssembly) {
            VSAssembly vsassembly = (VSAssembly) assembly;

            if(!vsassembly.isVisible()) {
               continue;
            }

            VSAssemblyInfo assemblyInfo = vsassembly.getVSAssemblyInfo();
            Point pos = assemblyInfo.getLayoutPosition();
            Dimension size = assemblyInfo.getLayoutSize();

            if(pos == null || size == null) {
               continue;
            }

            Point upperLeft = pos;
            Point bottomRight = new Point(pos.x + size.width, pos.y + size.height);

            for(int j = i + 1; j < assemblies.length; j++) {
               Assembly assembly2 = assemblies[j];

               if(!assembly2.isVisible()) {
                  continue;
               }

               if(assembly2 instanceof ShapeVSAssembly || assembly2 instanceof ImageVSAssembly) {
                  continue;
               }

               if(Tool.equals(((VSAssembly) assembly).getContainer(), assembly2) ||
                  Tool.equals(((VSAssembly)assembly2).getContainer(), assembly2))
               {
                  continue;
               }

               boolean assembly2IsFloat = VSUtil.isPopComponent(assembly2.getAbsoluteName(), viewsheet) ||
                  VSUtil.isTipView(assembly2.getAbsoluteName(), viewsheet);

               // float assembly can not overlap normal assembly.
               if(assemblyIsFloat && !assembly2IsFloat || !assemblyIsFloat && assembly2IsFloat) {
                  continue;
               }

               // two float assemblies can not overlap when them in different container.
               if(assemblyIsFloat && assembly2IsFloat && assembly2 instanceof AbstractVSAssembly &&
                  !Objects.equals(((AbstractVSAssembly) assembly).getContainer(),
                     ((AbstractVSAssembly) assembly2).getContainer()))
               {
                  continue;
               }

               VSAssembly vsassembly2 = (VSAssembly) assembly2;
               VSAssemblyInfo assemblyInfo2 = vsassembly2.getVSAssemblyInfo();
               Point pos2 = assemblyInfo2.getLayoutPosition();
               Dimension size2 = assemblyInfo2.getLayoutSize();
               Point upperLeft2 = pos2;

               if(pos2 == null || size2 == null) {
                  continue;
               }

               Point bottomRight2 = new Point(pos2.x + size2.width, pos2.y + size2.height);

               if(isOverlapping(upperLeft, upperLeft2, bottomRight, bottomRight2)) {
                  applyAssemblyScale(vsassembly, scaleRatio, info, mobile,
                                     isFloat(vsassembly, viewsheet), width, box, true);

                  if(assembly2 instanceof ListInputVSAssembly) {
                     applyAssemblyScale(vsassembly2, scaleRatio, info, mobile,
                                        isFloat(vsassembly2, viewsheet), width, box, true);
                     i++;
                  }

                  break;
               }
            }
         }
      }
   }

   private static boolean isOverlapping(Point upperLeft, Point upperLeft2,
                                        Point bottomRight, Point bottomRight2)
   {
      if(upperLeft.x > bottomRight2.x || upperLeft2.x > bottomRight.x) {
         return false;
      }

      if(upperLeft.y > bottomRight2.y || upperLeft2.y > bottomRight.y) {
         return false;
      }

      return true;
   }

   private static boolean isFloat(VSAssembly vsassembly, Viewsheet viewsheet) {
      if(vsassembly.getContainer() == null) {
         return VSUtil.isPopComponent(vsassembly.getName(), viewsheet)
               || VSUtil.isTipView(vsassembly.getName(), viewsheet);
      }

      return isFloat(vsassembly.getContainer(), viewsheet);
   }

   /**
    * Scale assembly annotation position.
    */
   private static void scaleAssemblyAnnotationPos(Viewsheet viewsheet, double width, double height)
   {
      Assembly[] assemblies = viewsheet.getAssemblies();
      boolean fitToWidth = viewsheet.getViewsheetInfo().isFitToWidth();

      for(Assembly assembly : assemblies) {
         VSAssembly vsassembly = (VSAssembly) assembly;

         if(AnnotationVSUtil.isAnnotation(vsassembly) &&
            vsassembly instanceof AnnotationVSAssembly)
         {
            AnnotationVSAssemblyInfo ainfo =
               (AnnotationVSAssemblyInfo) vsassembly.getInfo();

            if(ainfo.getType() == AnnotationVSAssemblyInfo.ASSEMBLY) {
               VSAssembly parent =
                  (VSAssembly) AnnotationVSUtil.getBaseAssembly(
                  viewsheet, vsassembly.getName());
               VSAssemblyInfo pinfo = parent.getVSAssemblyInfo();

               if(pinfo instanceof TableDataVSAssemblyInfo &&
                  ((TableDataVSAssemblyInfo) pinfo).isShrink())
               {
                  continue;
               }

               Dimension parentCurrentSize = pinfo.getLayoutSize() != null ?
                  pinfo.getLayoutSize() : viewsheet.getPixelSize(pinfo);
               Point parentPosition = pinfo.getLayoutPosition() != null ? pinfo.getLayoutPosition()
                  : viewsheet.getPixelPosition(pinfo);
               Dimension parentSize = viewsheet.getPixelSize(pinfo);
               // 12.3 assembly annotation position is relative to assembly, so only need
               // to know how much assembly size was scaled
               Point2D.Double scaleRatio =
                  new Point2D.Double((double) parentCurrentSize.width / parentSize.width,
                                     (double) parentCurrentSize.height / parentSize.height);
               Point annotationOffset = viewsheet.getPixelPosition(ainfo);
               Point scaledAnnotationOffset =
                  new Point((int) Math.floor(annotationOffset.x * scaleRatio.x),
                            (int) Math.floor(annotationOffset.y * scaleRatio.y));

               // table row height will not be apply the scale.
               if(pinfo instanceof TableVSAssemblyInfo) {
                  scaledAnnotationOffset.y = annotationOffset.y;
               }

               ainfo.setScaledPosition(scaledAnnotationOffset);

               String line = ainfo.getLine();
               Assembly lineobj = viewsheet.getAssembly(line);

               if(lineobj != null) {
                  LineVSAssemblyInfo linfo = (LineVSAssemblyInfo) lineobj.getInfo();

                  if(linfo != null) {
                     linfo.setScaledPosition(scaledAnnotationOffset);
                  }
               }

               String rect = ainfo.getRectangle();
               Assembly rectobj = viewsheet.getAssembly(rect);

               if(rectobj != null) {
                  AnnotationRectangleVSAssemblyInfo rinfo =
                     (AnnotationRectangleVSAssemblyInfo) rectobj.getInfo();
                  Dimension recCurrentSize = rinfo.getLayoutSize() != null ?
                     rinfo.getLayoutSize() : viewsheet.getPixelSize(rinfo);
                  Point rectOffset = viewsheet.getPixelPosition(rinfo);
                  Point nPoint = new Point((int) Math.floor(rectOffset.x * scaleRatio.x),
                     (int) Math.floor(rectOffset.y * scaleRatio.y));

                  if(parentPosition.x + nPoint.x + recCurrentSize.width > width) {
                     nPoint.x = (int) width - recCurrentSize.width - parentPosition.x;
                  }

                  if(!fitToWidth && height > 0 &&
                     parentPosition.y + nPoint.y + recCurrentSize.height > height)
                  {
                     nPoint.y = (int) height - recCurrentSize.height - parentPosition.y;
                  }

                  rinfo.setScaledPosition(nPoint);
               }
            }
         }
      }
   }

   /**
    * Calculate scaling ratio.
    */
   public static Point2D.Double calcScalingRatio(Viewsheet viewsheet, Dimension viewSize,
                                                 double width, double height, boolean mobile)
   {
      ViewsheetInfo info = viewsheet.getViewsheetInfo();
      Point2D.Double scalingRatio = new Point2D.Double(1, 1);

      if(viewSize != null) {
         Point offset = getBoundingAssemblyOffset(viewsheet, mobile);
         // don't know why, but this (+2) is required to prevent scroll bars,
         // maybe due to the border?
         int chartXOffset = info.isBalancePadding() ? offset.x : offset.x + 2;
         int chartYOffset = offset.y;

         double screenWidth = width;
         double screenHeight = height;

         if(info.isBalancePadding()) {
            viewSize.width += chartXOffset;
            viewSize.height += chartYOffset;
         }
         else {
            screenWidth -= chartXOffset;
            screenHeight -= chartYOffset;
         }

         scalingRatio.x = screenWidth / (double) viewSize.width;

         if(info.isFitToWidth()) {
            double scale = scalingRatio.x;

            if(viewSize.height * scale > screenHeight) {
               // there will be a vertical scroll bar, account for that
               double newScale = (screenWidth - (mobile ? 10 : 17)) / viewSize.width;

               if(viewSize.height * newScale < screenHeight) {
                  // After rescaling for the scroll bars, the height fits so maximize to limit the
                  // empty space. Do this by scaling the height to fit. This works because with the
                  // original scale, the scaled height was greater than the view height; at the new
                  // scale the scaled height is less that the view height. This means that if we
                  // scale the height to the view height, it will still be less than the original
                  // scale that we know fit the width.
                  newScale = screenHeight / (double) viewSize.height;
                  assert newScale < scale; // sanity check
               }

               scale = newScale;
               scalingRatio.x = scale;
            }

            scalingRatio.y = scale;
         }
         else {
            scalingRatio.y = screenHeight / getMaxViewHeight(viewsheet, (double) viewSize.height, screenHeight);
         }
      }

      return scalingRatio;
   }

   private static double getMaxViewHeight(Viewsheet viewsheet, double viewHeight, double screenHeight) {
      Assembly noScaleAssembly = getNoScaleAssemblyOfMaxHeight(viewsheet);

      if(noScaleAssembly == null) {
         return viewHeight;
      }

      double ratio =
         (screenHeight - noScaleAssembly.getPixelSize().getHeight()) / noScaleAssembly.getPixelOffset().getY();

      return Math.max(viewHeight, screenHeight / ratio);
   }

   private static Assembly getNoScaleAssemblyOfMaxHeight(Viewsheet viewsheet) {
      Assembly[] assemblies = viewsheet.getAssemblies();
      Assembly noScaleAssembly = null;

      for(int i = 0; i < assemblies.length; i++) {
         if(!isNoScaleAssembly(assemblies[i]) || !assemblies[i].isVisible()) {
            continue;
         }

         AssemblyInfo info = assemblies[i].getInfo();

         if(noScaleAssembly == null) {
            noScaleAssembly = assemblies[i];
         }
         else {
            AssemblyInfo noScaleInfo = noScaleAssembly.getInfo();
            double maxHeight = noScaleInfo.getPixelOffset().y + noScaleInfo.getPixelSize().height;
            double height = info.getPixelOffset().y + info.getPixelSize().height;
            noScaleAssembly = maxHeight > height ? noScaleAssembly : assemblies[i];
         }
      }

      return noScaleAssembly;
   }

   private static boolean isNoScaleAssembly(Assembly assembly) {
      return assembly instanceof InputVSAssembly ||
         assembly instanceof SubmitVSAssembly ||
         assembly instanceof TimeSliderVSAssembly ||
         assembly instanceof UploadVSAssembly ||
         assembly instanceof TabVSAssembly;
   }

   private static Point getBoundingAssemblyOffset(Viewsheet viewsheet, boolean mobile) {
      VSAssembly[] boundingAssemblies = getBoundingAssemblies(viewsheet);
      int x = 0;
      int y = 0;

      if(boundingAssemblies[0] == null || boundingAssemblies[1] == null ||
         boundingAssemblies[2] == null || boundingAssemblies[3] == null)
      {
         // if no assembly, ignore
      }
      else if(viewsheet.getViewsheetInfo().isBalancePadding()) {
         VSAssemblyInfo info = (VSAssemblyInfo) boundingAssemblies[2].getInfo();
         Point pos = info.getLayoutPosition(false) != null ?
            info.getLayoutPosition(false) : viewsheet.getPixelPosition(info);
         x = pos.x;

         info = (VSAssemblyInfo) boundingAssemblies[3].getInfo();
         pos = info.getLayoutPosition(false) != null ?
            info.getLayoutPosition(false) : viewsheet.getPixelPosition(info);
         y = pos.y;
      }
      else {
         if(boundingAssemblies[0] instanceof ChartVSAssembly) {
            // allocate space for the chart's vertical scroll bar
            x = mobile ? 10 : 20;
         }
         else if(boundingAssemblies[0] instanceof TimeSliderVSAssembly ||
            boundingAssemblies[0] instanceof SliderVSAssembly)
         {
            // leave space for thumb
            x = 8;
         }
         else if(boundingAssemblies[0] instanceof CurrentSelectionVSAssembly) {
            // leave space for selection container's vertical scroll bar
            x = mobile ? 10 : 20;
         }
         else if(boundingAssemblies[0] instanceof SelectionListVSAssembly ||
            boundingAssemblies[0] instanceof SelectionTreeVSAssembly)
         {
            // leave space for selection container's vertical scroll bar
            x = mobile ? 10 : 20;
         }
         else if(boundingAssemblies[0] instanceof TableDataVSAssembly) {
            // allocate space for the HTML table's vertical scroll bar
            x = mobile ? 10 : 20;
         }
         else if(boundingAssemblies[0] instanceof Viewsheet) {
            Point offset = getBoundingAssemblyOffset((Viewsheet) boundingAssemblies[0], mobile);
            x = offset.x;
         }

         if(boundingAssemblies[1] instanceof ChartVSAssembly ||
            boundingAssemblies[1] instanceof TableDataVSAssembly)
         {
            // allocate space for the chart's horizontal scroll bar
            y = mobile ? 10 : 20;
         }
         else if(boundingAssemblies[1] instanceof Viewsheet) {
            Point offset = getBoundingAssemblyOffset((Viewsheet) boundingAssemblies[1], mobile);
            y = offset.y;
         }
      }

      return new Point(x, y);
   }

   /**
    * Apply scale of current selection container.
    */
   public static void applyCurrentSelectionScale(Viewsheet viewsheet,
      CurrentSelectionVSAssembly assembly, Point2D.Double scaleRatio)
   {
      VSAssemblyInfo selectionInfo = (VSAssemblyInfo) assembly.getInfo();
      Dimension selectionSize = selectionInfo.getLayoutSize();
      String[] assemblies = assembly.getAssemblies();

      for(String assemblyName : assemblies) {
         VSAssembly child = viewsheet.getAssembly(assemblyName);

         if(child != null) {
            VSAssemblyInfo info = (VSAssemblyInfo) child.getInfo();
            Point pos = info.getLayoutPosition(false) != null ?
               info.getLayoutPosition(false) : viewsheet.getPixelPosition(info);
            Dimension size = info.getLayoutSize(false) != null ?
               info.getLayoutSize(false) : viewsheet.getPixelSize(info);
            Point2D.Double posScale = info.getPositionScale(scaleRatio);
            Point2D.Double sizeScale = info.getSizeScale(scaleRatio);

            if(size.height < AssetUtil.defh) {
               sizeScale = scaleRatio;

               if(info instanceof DropDownVSAssemblyInfo) {
                  // @by jasonshobe, #8122, if the assembly is initially
                  // collapsed, use the correct height
                  size.height = AssetUtil.defh;
               }
            }

            Point scalePos = new Point();
            Dimension scaleSize = new Dimension();
            scalePos.x = (int) Math.floor(pos.x * posScale.x);
            scalePos.y = (int) Math.floor(pos.y * posScale.y);
            info.setScaledPosition(scalePos);
            scaleSize.width = selectionSize != null ? selectionSize.width
               : (int) Math.floor(size.width * sizeScale.x);
            scaleSize.height = (int) Math.floor(size.height * sizeScale.y);
            info.setScaledSize(scaleSize);

            if(info instanceof DropDownVSAssemblyInfo) {
               // @by jasonshobe, #8122, store the scale ratio so that the height
               // can be re-scaled when the assembly is expanded or collapsed
               ((DropDownVSAssemblyInfo) info).setListHeightScale(
                  scaleSize.getHeight() / viewsheet.getPixelSize(info).height);
            }
         }
      }
   }

   /**
    * Apply scale of assembly.
    */
   private static void applyAssemblyScale(VSAssembly assembly, Point2D.Double scaleRatio,
                                          ViewsheetInfo vsinfo, boolean mobile, boolean isFloat,
                                          double width, ViewsheetSandbox box, boolean overlap)
      throws Exception
   {
      if(assembly instanceof TextVSAssembly) {
         box.executeScript(assembly);
      }

      if(isFloat) {
         scaleRatio = new Point2D.Double(Math.min(scaleRatio.x, 1D), Math.min(scaleRatio.y, 1D));
      }

      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      Point2D.Double posscale = info.getPositionScale(scaleRatio);
      Point2D.Double sizescale = info.getSizeScale(scaleRatio);

      // @by ashleystankovits #57656
      // ListInputVSAssembly sizescale.y is default set to 1. If scale to screen is set and
      // the given assembly is overlapping with another assembly, we allow it to scale vertically
      if(vsinfo.isScaleToScreen() && assembly instanceof ListInputVSAssembly && overlap) {
         sizescale.y = scaleRatio.x;
      }

      Viewsheet vs = info.getViewsheet();

      Point pos;
      Dimension size;

      if(info.getLayoutPosition(false) == null) {
         pos = vs.getPixelPosition(info);
      }
      else {
         pos = info.getLayoutPosition(false);
      }

      if(info.getLayoutSize(false) == null) {
         size = vs.getPixelSize(info);
      }
      else {
         size = info.getLayoutSize(false);
      }

      Point scalePos = new Point((int) Math.floor(pos.x * posscale.x),
                                 (int) Math.floor(pos.y * posscale.y));
      Dimension scaleSize = new Dimension((int) Math.floor(size.width * sizescale.x),
                                          (int) Math.floor(size.height * sizescale.y));

      if(info instanceof LineVSAssemblyInfo) {
         if(scaleSize.height == 0) {
            scaleSize.height = size.height;
            sizescale = new Point2D.Double(sizescale.x, 1);
         }

         // Bug #17535 In 12.3 we only use the line start and end position to draw the line
         // Need to scale start and end positions to match new scale size/pos.
         LineVSAssemblyInfo lineInfo = (LineVSAssemblyInfo) info;
         Point linePos = lineInfo.getStartPos();
         lineInfo.setScaledStartPos(new Point((int) Math.floor(linePos.x * sizescale.x),
                                              (int) Math.floor(linePos.y * sizescale.y)));
         linePos = lineInfo.getEndPos();
         lineInfo.setScaledEndPos(new Point((int) Math.floor(linePos.x * sizescale.x),
                                            (int) Math.floor(linePos.y * sizescale.y)));
      }

      scalePos = amendScalePosition(vsinfo, scalePos, size, scaleSize, scaleRatio, pos);

      Dimension pixelSize = info.getLayoutSize() != null ? info.getLayoutSize() : vs.getPixelSize(info);

      // @by stephenwebster, For Bug #7758
      // Scale the column widths of tables based on the new scaled size.
      // The column widths will be proportional to their design-time values,
      // and the design-time values will act as a minimum size to enforce
      // scrolling on a smaller screen size.
      // NOTE: the column widths set here are the runtime values.
      // resetRuntimeValues should be called prior to the scaling operations
      // so the runtime values set here are not lost.
      if(info instanceof TableDataVSAssemblyInfo) {
         TableDataVSAssemblyInfo tableInfo = (TableDataVSAssemblyInfo) info;
         XTable lens;

         try {
            // lens is only used to get data path so meta data is sufficient.
            // need actual data for scaling column width. (57505)
            if(scaleRatio.x == 1) {
               box.getVariableTable().put("calc_metadata", "true");
            }

            lens = box.getVSTableLens(tableInfo.getAbsoluteName(), false);
         }
         catch(ConfirmException e) {
            lens = null;
         }
         finally {
            box.getVariableTable().remove("calc_metadata");
            ViewsheetSandbox[] boxes = box.getSandboxes();

            for(ViewsheetSandbox innnerBox : boxes) {
               innnerBox.getVariableTable().remove("calc_metadata");
            }
         }

         int hiddenColumns = 0;

         if(info instanceof TableVSAssemblyInfo) {
            ColumnSelection columns = ((TableVSAssemblyInfo) info).getColumnSelection();
            hiddenColumns = columns != null ? columns.getHiddenColumnCount() : 0;
         }

         // calc tableInfo column count is 0.
         int colCnt = lens != null ? lens.getColCount() : tableInfo.getColumnCount();
         double tableWidth = Math.floor(pixelSize.width * scaleRatio.x);
         double totalWidth = 0;

         for(int i = 0; i < colCnt - hiddenColumns; i++) {
            double colWidth = tableInfo.getColumnWidth2(i, lens);

            if(Double.isNaN(colWidth)) {
               colWidth = AssetUtil.defw;
            }

            double ratio = pixelSize.width / colWidth;
            double targetWidth = Math.floor(Math.max(colWidth, scaleSize.width / ratio));

            // last column, fill the table.
            if(i == colCnt - hiddenColumns - 1 && totalWidth + targetWidth < tableWidth) {
               targetWidth = tableWidth - totalWidth;
            }

            totalWidth += targetWidth;
            tableInfo.setColumnWidth(i, targetWidth);
         }
      }

      if(vs.getContainer() != null) {
         Point vspos = vs.getVSAssemblyInfo().getLayoutPosition() == null ?
            vs.getVSAssemblyInfo().getPixelPosition() :
            vs.getVSAssemblyInfo().getLayoutPosition();

         if(vspos != null) {
            // if in an embedded viewsheet that is in a tab, we need to shift
            // the assembly's position relative to the tab
            scalePos.x += vspos.x;
            scalePos.y += vspos.y;
         }
      }

      // pop or data tip view component
      if(isFloat && assembly.getContainer() == null) {
         // Check if the size of the component is not greater
         // than the width of the screen
         if(scaleSize.width > width) {
            double ratio = (width - 40) / (double) scaleSize.width;
            scaleSize.width *= ratio;
            scaleSize.height *= ratio;
         }

         // The pop component position is set to (0, 0) so that it won't
         // create any unnecessary scrollbars in VS-HTML.
         Point oscalePos = new Point(scalePos.x, scalePos.y);
         scalePos.setLocation(0, 0);
         fixPopchildrenPoint(assembly, oscalePos, scalePos);
      }

      TabVSAssembly tab = getTabContainer(assembly);

      // fix Bug #20141, for the assemblies which are not the direct child of
      // tab still need to consider the repairH which caused by tab height is not scale.
      if(tab != null) {
         double repairH = getTabRepairH(tab, scaleRatio);
         scalePos.y = (int) Math.floor(scalePos.y - repairH);
         scaleSize.height =  (int) Math.floor(scaleSize.height + repairH);
      }

      if(info instanceof SelectionBaseVSAssemblyInfo) {
         SelectionBaseVSAssemblyInfo sinfo = (SelectionBaseVSAssemblyInfo) info;

         if(sinfo.getShowTypeValue() == SelectionListVSAssemblyInfo.DROPDOWN_SHOW_TYPE) {
            scaleSize.height = info.getPixelSize().height;
         }
      }

      info.setScaledPosition(scalePos);
      info.setScaledSize(scaleSize);
   }

   private static void fixPopchildrenPoint(VSAssembly vsassembly, Point opoint,
                                                                  Point npoint)
   {
      if(vsassembly instanceof GroupContainerVSAssembly) {
         String[] assemblies = ((GroupContainerVSAssembly)vsassembly).getAssemblies();
         Viewsheet viewsheet = vsassembly.getViewsheet();

         for(String assembly : assemblies) {
            VSAssembly child = viewsheet.getAssembly(assembly);
            VSAssemblyInfo childInfo = child.getVSAssemblyInfo();

            if(child != null) {
               Point childPos = childInfo.getLayoutPosition();

               if(childPos == null) {
                  return;
               }

               childPos =
                  new Point(npoint.x + (childPos.x - opoint.x),
                            npoint.y + (childPos.y - opoint.y));
               childInfo.setScaledPosition(childPos);
            }
         }
      }
   }

   /**
    * Get the tab container of the assembly.
    */
   public static TabVSAssembly getTabContainer(VSAssembly obj) {
      if(obj == null) {
         return null;
      }

      List<VSAssembly> tree = new ArrayList<>();
      buildTree(obj, tree);
      return tree.stream()
         .filter(a -> a != obj && (a instanceof TabVSAssembly))
         .map(a -> (TabVSAssembly) a)
         .findFirst()
         .orElse(null);
   }

   /**
    * When apply tab scale, the tab height is not scale, so it's children
    * scale size and position y need to consider the repairH.
    */
   private static double getTabRepairH(TabVSAssembly assembly, Point2D.Double scaleRatio) {
      VSAssemblyInfo tabInfo = (VSAssemblyInfo) assembly.getInfo();
      Dimension tabSize = tabInfo.getLayoutSize();

      if(tabSize == null) {
         tabSize = tabInfo.getPixelSize();
      }

      return tabSize.height * scaleRatio.y - tabSize.height;
   }

   /**
    * Amend control with constant height(such as slide) scaled position
    * to cancel the vertical scrollbar.
    * @param vsinfo The info associated with the viewsheet
    * @param scalePos The new scaled position of the assembly
    * @param originalSize The original size of the assembly before scaling
    * @param scaleSize The scaled size of the assembly
    * @param scaleRatio The overall scaling ration for this viewsheet
    * @param pos The current position of this assembly in the viewsheet
    *
    * @return The newly modified scaled position based on a fixed height assembly
    */
   private static Point amendScalePosition(ViewsheetInfo vsinfo, Point scalePos,
      Dimension originalSize, Dimension scaleSize, Point2D.Double scaleRatio, Point pos)
   {
      if(!vsinfo.isFitToWidth()) {
         if(originalSize.height == scaleSize.height) {
            //scalePos.y = Math.max((int) Math.floor(scalePos.y +
            //               orignalSize.height * (posscale.y - 1.0)),0);

            // @by stephenwebster, For Bug #1570
            // There are a few problems.  If we only amend the scale position
            // when we decrease the size, then if we increase the size, there
            // is nothing to "reverse" the amended position.
            // Also, due to this calculation each time it is done, we lose
            // precision.  A simpler calculation is just to position assemblies
            // which have a fixed height relative to the overall scale of the
            // viewsheet size.  There is still some loss of precision, but it
            // will be much more consistently placed increasing and decreasing
            // the scale.
            scalePos.y = (int) (scaleRatio.y * pos.y);
         }
      }

      return scalePos;
   }

   /**
    * Apply scale of tab.
    */
   private static void applyTabScale(Viewsheet viewsheet,
                                     TabVSAssembly assembly,
                                     Point2D.Double scaleRatio,
                                     boolean mobile, String userAgent,
                                     ViewsheetSandbox box)
         throws Exception
   {
      VSAssemblyInfo tabInfo = (VSAssemblyInfo) assembly.getInfo();
      Point tabPos = tabInfo.getLayoutPosition();
      Dimension tabSize = tabInfo.getLayoutSize();
      String[] assemblies = assembly.getAssemblies();
      // when apply tab scale, the tab height is not scale, so it's children
      // scale size is (pixelsize * scaleRadio + repairH).
      double repairH = tabSize.height * scaleRatio.y - tabSize.height;

      for(String assemblyName : assemblies) {
         VSAssembly child = viewsheet.getAssembly(assemblyName);

         if(child != null) {
            VSAssemblyInfo info = (VSAssemblyInfo) child.getInfo();
            Dimension size = info.getLayoutSize(false) != null ?
               info.getLayoutSize(false) : viewsheet.getPixelSize(info);
            Point2D.Double sizeScale = info.getSizeScale(scaleRatio);
            Dimension scaleSize = new Dimension();

            scaleSize.width = (int) Math.floor(size.width * sizeScale.x);
            scaleSize.height =
               (int) Math.floor(size.height * sizeScale.y + repairH);
            scaleSize.height = Math.max(0, scaleSize.height);

            info.setScaledPosition(
               new Point(tabPos.x, tabPos.y + tabSize.height));
            info.setScaledSize(scaleSize);

            if(child instanceof CurrentSelectionVSAssembly) {
               applyCurrentSelectionScale(viewsheet,
                                          (CurrentSelectionVSAssembly) child, scaleRatio);
            }
            else if(child instanceof Viewsheet) {
               Viewsheet sheet = (Viewsheet) child;

               Rectangle viewBounds = sheet.getPreferredBounds(true, false);
               Dimension viewSize = new Dimension(
                  (int) Math.floor(viewBounds.getMaxX()),
                  (int) Math.floor(viewBounds.getMaxY()));
               Point2D.Double vsScaleRatio = calcScalingRatio(
                  sheet, viewSize, scaleSize.width, scaleSize.height, mobile
               );

               // temporarily set the origin of the viewsheet so that the scaled
               // viewsheet contents are positioned relative to the tab bar
               int vsOffsetX = (int) Math.round(-1 * viewBounds.x * vsScaleRatio.x);
               int vsOffsetY = (int) Math.round(-1 * viewBounds.y * vsScaleRatio.y);
               info.setScaledPosition(
                  new Point(tabPos.x + vsOffsetX, tabPos.y + tabSize.height + vsOffsetY));

               // scale the viewsheet contents
               applyScale(sheet, vsScaleRatio, mobile, userAgent, scaleSize.width, box);

               // set the viewsheet position to the tab bar's position so that
               // the edit button is positioned correctly.
               info.setScaledPosition(new Point(tabPos.x, tabPos.y + tabSize.height));
            }
         }
      }
   }

   /**
    * Get right-most and bottom-most assembly in the viewsheet.
    */
   private static VSAssembly[] getBoundingAssemblies(Viewsheet viewsheet) {
      Assembly[] assemblies = viewsheet.getAssemblies();
      VSAssembly rightAssembly = null;
      VSAssembly bottomAssembly = null;
      VSAssembly leftAssembly = null;
      VSAssembly topAssembly = null;
      int rightEdge = 0;
      int bottomEdge = 0;
      int leftEdge = 0;
      int topEdge = 0;

      for(Assembly assemblyItem : assemblies) {
         VSAssembly assembly = (VSAssembly) assemblyItem;

         if(!assembly.isVisible()) {
            continue;
         }

         if(assembly.getContainer() instanceof CurrentSelectionVSAssembly) {
            continue;
         }

         if(assembly instanceof AnnotationVSAssembly ||
            assembly instanceof AnnotationLineVSAssembly ||
            assembly instanceof AnnotationRectangleVSAssembly)
         {
            continue;
         }

         VSAssemblyInfo info = (VSAssemblyInfo) assembly.getInfo();
         Point pos = info.getLayoutPosition(false) != null ?
            info.getLayoutPosition(false) : viewsheet.getPixelPosition(info);
         Dimension size = info.getLayoutSize(false) != null ?
            info.getLayoutSize(false) : viewsheet.getPixelSize(info);
         int right = pos.x + size.width;
         int bottom = pos.y + size.height;
         int left = pos.x;
         int top = pos.y;

         if(assembly instanceof ChartVSAssembly) {
            right += 10;
            bottom += 10;
         }
         else if(assembly instanceof TableDataVSAssembly) {
            right += 10;
            bottom += 10;
         }
         else if(assembly instanceof SelectionListVSAssembly ||
            assembly instanceof SelectionTreeVSAssembly)
         {
            right += 10;
         }
         else if(assembly instanceof TimeSliderVSAssembly) {
            right += 8;
         }
         else if(assembly instanceof CurrentSelectionVSAssembly) {
            right += 10;
         }
         else if(assembly instanceof Viewsheet) {
            Point offset = getBoundingAssemblyOffset((Viewsheet) assembly, false);
            right += offset.x;
            bottom += offset.y;
         }

         if(right > rightEdge) {
            rightEdge = right;
            rightAssembly = assembly;
         }
         else if(right == rightEdge && assembly instanceof ChartVSAssembly) {
            rightAssembly = assembly;
         }

         if(bottom > bottomEdge) {
            bottomEdge = bottom;
            bottomAssembly = assembly;
         }
         else if(bottom == bottomEdge && assembly instanceof ChartVSAssembly) {
            bottomAssembly = assembly;
         }

         if(left < leftEdge || leftAssembly == null) {
            leftEdge = left;
            leftAssembly = assembly;
         }

         if(top < topEdge || topAssembly == null) {
            topEdge = top;
            topAssembly = assembly;
         }
      }

      return new VSAssembly[] { rightAssembly, bottomAssembly, leftAssembly, topAssembly };
   }

   /**
    * Set the toolbar visibility.
    */
   public static void setToolbar(Viewsheet vs) {
      List<SUtil.ToolBarElement> buttons = SUtil.getVSToolBarElements(false);
      VSAssemblyInfo info = (VSAssemblyInfo) vs.getInfo();

      for(SUtil.ToolBarElement button : buttons) {
         info.setActionVisible(button.id, "true".equals(button.visible));
      }
   }

   /**
    * Refresh viewsheet to add/delete all the embedded viewsheet objects.
    */
   public static void addDeleteEmbeddedViewsheet(RuntimeViewsheet rvs,
                                                 AssetEvent evt, String linkURI,
                                                 AssetCommand command)
      throws Exception
   {
      List<Assembly> assemblies = new ArrayList<>();
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      listEmbeddedAssemblies(vs, assemblies);

      for(Assembly assembly : assemblies) {
         addDeleteVSObject(rvs, evt, (VSAssembly) assembly, linkURI,
                           command);
      }
   }

   /**
    * List all embedded viewsheet assembly.
    */
   public static void listEmbeddedAssemblies(Viewsheet vs, List<Assembly> assemblies) {
      Assembly[] vsAssemblies = vs.getAssemblies();

      for(Assembly assembly : vsAssemblies) {
         if(((VSAssembly) assembly).isEmbedded()) {
            assemblies.add(assembly);
         }

         if(assembly instanceof Viewsheet) {
            listEmbeddedAssemblies((Viewsheet) assembly, assemblies);
         }
      }
   }

   /**
    * Set the link URI of vs assemblies.
    */
   public static void setLinkURI(String uri, Assembly... vsobjs) {
      for(Assembly obj : vsobjs) {
         if(obj instanceof OutputVSAssembly) {
            ((OutputVSAssembly) obj).setLinkURI(uri);
         }
      }
   }

   /**
    * Initialize table lens.
    */
   public static void initTable(RuntimeViewsheet rvs, AssetCommand cmd,
                                String uri, Assembly... vsobjs)
         throws Exception
   {
      for(Assembly obj : vsobjs) {
         VSAssembly assembly = (VSAssembly) obj;

         if(assembly instanceof TableDataVSAssembly &&
            ((isVisible(rvs, assembly) && isVisibleTabVS(assembly, rvs.isRuntime())) ||
             rvs.isRuntime() && rvs.isTipView(assembly.getAbsoluteName())))
         {
            LoadTableLensEvent loadEvent = new LoadTableLensEvent(
               assembly.getAbsoluteName(), 0, 0, 100);
            loadEvent.setLinkURI(uri);
            loadEvent.process(rvs, cmd);
         }

         // @by billh, performance optimization for metlife
         if(isVisibleTabVS(assembly) && (assembly instanceof CubeVSAssembly)) {
            refreshVSAssembly(rvs, assembly, cmd);
         }
      }
   }

   /**
    * Refresh the embedded viewsheet objects.
    */
   public static void refreshEmbeddedViewsheet(RuntimeViewsheet rvs,
      AssetEvent event, String uri, AssetCommand cmd)
      throws Exception
   {
      refreshEmbeddedViewsheet0(rvs, event, rvs.getViewsheet(), uri, cmd);
   }

   /**
    * Refresh the embedded viewsheet objects.
    */
   private static void refreshEmbeddedViewsheet0(RuntimeViewsheet rvs,
                                                 AssetEvent event,
                                                 Viewsheet sheet,
                                                 String uri, AssetCommand cmd)
      throws Exception
   {
      if(sheet == null || !sheet.isPrimary()) {
         return;
      }

      Assembly[] assemblies = sheet.getAssemblies(false, true);

      for(Assembly assembly : assemblies) {
         VSAssembly vsassembly = (VSAssembly) assembly;

         if(vsassembly instanceof Viewsheet) {
            addDeleteVSObject(rvs, event, vsassembly, uri, cmd, false);
            refreshEmbeddedViewsheet0(rvs, event, (Viewsheet) vsassembly, uri, cmd);
         }
      }
   }

   /**
    * Refresh containers.
    */
   private static void refreshContainer(RuntimeViewsheet rvs) {
      refreshContainer(rvs.getViewsheet());
   }

   /**
    * Refresh containers.
    */
   private static void refreshContainer(Viewsheet vs) {
      if(vs == null) {
         return;
      }

      Assembly[] assemblies = vs.getAssemblies(false, true);
      List<VSAssembly> containers = new ArrayList<>();

      for(Assembly assembly : assemblies) {
         VSAssembly vsassembly = (VSAssembly) assembly;

         if(vsassembly instanceof ContainerVSAssembly) {
            // @by stephenwebster, fix bug1395088797566
            // By calling updateZIndex here, we ensure that elements
            // within the container have a ZIndex of at least the ZIndex
            // of its container at runtime.  Otherwise, elements in the
            // container may have a ZIndex as the first level of the
            // viewsheet. This causes tabbed elements to show below a
            // shape object, for example, even though the tabbed element
            // has a higher ZIndex.  This is only a runtime modification.
            // The design time ZIndex is controlled by
            // VSUtil.calcChildZIndex.
            updateZIndex(vs, assembly);
            containers.add(vsassembly);
         }
      }

      containers.sort(new Comparator<VSAssembly>() {
         @Override
         public int compare(VSAssembly v1, VSAssembly v2) {
            if(v1 == null || v2 == null) {
               return 0;
            }

            // v1 contains v2?
            if(contains(v1, v2)) {
               return -1;
            }

            // v2 contains v1?
            if(contains(v2, v1)) {
               return 1;
            }

            return 0;
         }

         private boolean contains(VSAssembly v1, VSAssembly v2) {
            VSAssembly p = v2.getContainer();

            while(p != null) {
               if(p == v1) {
                  return true;
               }

               p = p.getContainer();
            }

            return false;
         }
      });

      for(VSAssembly vsAssembly : containers) {
         ContainerVSAssembly container = (ContainerVSAssembly) vsAssembly;
         String[] children = container.getAbsoluteAssemblies();

         if(children != null && children.length > 0) {
            for(String child : children) {
               if(vs.getAssembly(child) == null) {
                  container.removeAssembly(child);
               }
            }
         }

         if(container instanceof CurrentSelectionVSAssembly) {
            continue;
         }

         children = container.getAbsoluteAssemblies();

         if(children.length <= 1) {
            vs.removeAssembly(container);

            if(children.length > 0) {
               VSAssembly comp = vs.getAssembly(children[0]);
               comp.setZIndex(container.getZIndex());
            }

            continue;
         }

         // @by billh, performance optimization for metalife, why we need to
         // add/delete object here? What we should do is to refresh container
         // in sub viewsheet
         // addDeleteVSObject(rvs, event, containers.get(i), uri, cmd);
         if(container instanceof Viewsheet) {
            refreshContainer((Viewsheet) container);
         }

         // do not check tab, process it as other containers
         // fix bug1253777394963
         /*
         if(vsassembly instanceof ContainerVSAssembly) &&
            !(vsassembly instanceof TabVSAssembly))
         {
            addDeleteVSObject(rvs, event, vsassembly, uri, cmd);
         }
         */
      }
   }

   /**
    * Reset variables.
    */
   public static void refreshParameters(WorksheetService engine,
                                        ViewsheetSandbox vbox, Viewsheet vs,
                                        boolean reset, VariableTable initvars,
                                        List list)
      throws Exception
   {
      refreshParameters(engine, vbox, vs, reset, initvars, list, false);
   }

   /**
    * Reset variables.
    * @param varNameOnly just get variable name only.
    */
   public static void refreshParameters(WorksheetService engine,
                                        ViewsheetSandbox vbox, Viewsheet vs,
                                        boolean reset, VariableTable initvars,
                                        List list, boolean varNameOnly)
      throws Exception
   {
      if(vbox == null || vs == null) {
         return;
      }

      Assembly[] assemblies = vs.getAssemblies();
      String vsName = vbox.getSheetName();

      for(Assembly assembly : assemblies) {
         if(assembly instanceof Viewsheet) {
            refreshParameters(engine, vbox.getSandbox(assembly.getAbsoluteName()),
                              (Viewsheet) assembly, reset, initvars, list, varNameOnly);
         }
      }

      AssetQuerySandbox box = vbox.getAssetQuerySandbox();

      if(box == null) {
         return;
      }

      //Disable parameter by CollectParametersCommand.
//      ViewsheetInfo vinfo = vs.getViewsheetInfo();
//
//      if(vinfo != null && vinfo.isDisableParameterSheet()) {
//         return;
//      }

      VariableTable vart = box.getVariableTable();

      if(reset) {
         box.resetVariableTable();
      }

      UserVariable[] vars = AssetEventUtil.executeVariables(
         engine, box, vart, null, vsName, null, initvars, varNameOnly);

      if(!"true".equals(vart.get("disableParameterSheet"))) {
         OUTER:
         for(UserVariable userVar : vars) {
            if(!SUtil.isNeedPrompt(box.getUser(), userVar)) {
               continue;
            }

            String varName = userVar.getName();
            varName = varName.replaceAll("\\.drillMember$", "");
            Assembly vobj = !vs.containsAssembly(varName) ? null :
               vs.getAssembly(varName);

            if(vobj instanceof InputVSAssembly) {
               continue;
            }

            for(Object item : list) {
               UserVariable var = (UserVariable) item;

               // avoid duplicated variables
               if(var != null && var.getName().equals(varName)) {
                  continue OUTER;
               }
            }

            list.add(userVar);
         }
      }

      if(vars == null || vars.length == 0) {
         return;
      }

      for(Assembly assembly : assemblies) {
         if(assembly.getInfo() instanceof DataVSAssemblyInfo) {
            DataVSAssemblyInfo info = (DataVSAssemblyInfo) assembly.getInfo();
            ConditionList cond = info.getPreConditionList();

            for(UserVariable var : vars) {
               if(var.isUsedInOneOf()) {
                  continue;
               }

               if(AssetEventUtil.checkUsed(cond, var.getName())) {
                  var.setUsedInOneOf(true);
               }
               else if(info instanceof TableDataVSAssemblyInfo) {
                  TableDataVSAssemblyInfo tinfo =
                     (TableDataVSAssemblyInfo) info;

                  if(tinfo.getHighlightAttr() == null) {
                     continue;
                  }

                  if(tinfo.getHighlightAttr().checkUsed(var.getName())) {
                     var.setUsedInOneOf(true);
                  }
               }
               else if(info instanceof ChartVSAssemblyInfo) {
                  VSChartInfo cinfo =
                     ((ChartVSAssemblyInfo) info).getVSChartInfo();
                  HighlightGroup hgroup = cinfo.getHighlightGroup();

                  if(checkUsed(hgroup, var.getName())) {
                     var.setUsedInOneOf(true);
                     continue;
                  }

                  VSDataRef[] dataRefs = cinfo.getAggregateRefs();

                  for(VSDataRef dataRef : dataRefs) {
                     if(dataRef instanceof VSChartAggregateRef) {
                        ((VSChartAggregateRef) dataRef).highlights().forEach(hg -> {
                           if(checkUsed(hg, var.getName())) {
                              var.setUsedInOneOf(true);
                           }
                        });
                     }
                  }
               }
            }
         }
         else if(assembly.getInfo() instanceof OutputVSAssemblyInfo) {
            OutputVSAssemblyInfo info = (OutputVSAssemblyInfo) assembly.getInfo();
            ConditionList cond = info.getPreConditionList();
            HighlightGroup hgroup = info.getHighlightGroup();

            for(UserVariable var : vars) {
               if(var.isUsedInOneOf()) {
                  continue;
               }

               if(AssetEventUtil.checkUsed(cond, var.getName())) {
                  var.setUsedInOneOf(true);
                  continue;
               }

               if(checkUsed(hgroup, var.getName())) {
                  var.setUsedInOneOf(true);
               }
            }
         }
      }
   }

   /**
    * Find the specified variable is used in higlight one of condition.
    */
   private static boolean checkUsed(HighlightGroup hgroup, String variable) {
      if(hgroup == null || hgroup.isEmpty()) {
         return false;
      }

      String[] names = hgroup.getNames();

      for(String name : names) {
         Highlight h = hgroup.getHighlight(name);

         if(h.isConditionEmpty()) {
            continue;
         }

         ConditionList cond = h.getConditionGroup();

         if(AssetEventUtil.checkUsed(cond, variable)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Refresh the data contained in the assembly for display.
    * @param rvs the specified runtime viewsheet.
    * @param cmd the specified asset command.
    * @param entry the specified assembly entry.
    */
   public static void refreshData(RuntimeViewsheet rvs, AssetCommand cmd,
                                  AssemblyEntry entry, String uri)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null) {
         return;
      }

      VSAssembly assembly = vs.getAssembly(entry.getAbsoluteName());

      if(assembly instanceof InputVSAssembly) {
         // box.getData(entry.getAbsoluteName(), false, true);
      }
      else if(assembly instanceof DataVSAssembly) {
         // box.getData(entry.getAbsoluteName(), false, true, true);
      }
      else if(assembly instanceof OutputVSAssembly) {
         // set link uri to output assembly info
         OutputVSAssemblyInfo oinfo = (OutputVSAssemblyInfo) assembly.getInfo();
         Object data = box.getData(entry.getAbsoluteName());
         oinfo.setLinkURI(uri);
         BindingInfo binding = oinfo.getBindingInfo();
         boolean noBinding = binding == null || binding.isEmpty();
         boolean isText = (assembly instanceof TextVSAssembly);
         boolean outputNullToZero =
            "true".equals(SreeEnv.getProperty("output.null.to.zero"));

         if(data != null || (!noBinding && (isText || outputNullToZero))) {
            if(data == null && outputNullToZero && !isText) {
               data = 0;
            }

            ((OutputVSAssembly) assembly).setValue(data);
         }
      }

      refreshVSAssembly(rvs, entry.getAbsoluteName(), cmd);
   }

   /**
    * Get assembly info for vsobject.
    * @param rvs the runtime viewsheet.
    * @param assembly the assembly.
    */
   public static VSAssemblyInfo getAssemblyInfo(RuntimeViewsheet rvs, VSAssembly assembly)
      throws Exception
   {
      VSAssemblyInfo info = (VSAssemblyInfo) assembly.getInfo();
      info = info.clone(true);
      fixAssemblyInfo(info, assembly, rvs);
      return info;
   }

   /**
    * Fix assembly info.
    * @param info the specified assembly info.
    * @param assembly the specified assembly.
    * @param rvs the specified runtime worksheet.
    */
   public static void fixAssemblyInfo(VSAssemblyInfo info, VSAssembly assembly,
                                       RuntimeViewsheet rvs)
      throws Exception
   {
      info.setClassName(assembly.getClass().getName());
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null || box == null) {
         return;
      }

      if(rvs.getMode() == RuntimeViewsheet.VIEWSHEET_RUNTIME_MODE &&
         ((VSUtil.isTipView(info.getAbsoluteName(), vs) ||
           VSUtil.isPopComponent(info.getAbsoluteName(), vs)) &&
         !VSUtil.isFlyOver(info.getAbsoluteName(), vs)))
      {
         info.setVisible(false);
      }
      else if(!AnnotationVSUtil.isAnnotation(assembly)) {
         // handle tab component visibility
         info.setVisible(vs.isVisible(assembly, rvs.getMode()));
      }

      if(info instanceof ViewsheetVSAssemblyInfo) {
         Viewsheet svs = (Viewsheet) vs.getAssembly(info.getAbsoluteName());
         info.setPixelSize(svs.getPixelSize());

         List<Assembly> objList = new ArrayList<>();
         ViewsheetVSAssemblyInfo vsInfo = (ViewsheetVSAssemblyInfo) info;

         appendEmbeddedChild((Viewsheet) assembly, objList, false, rvs);
         vsInfo.setPrimaryCount(objList.size());
         vsInfo.setAssemblyCount(svs.getAssemblies().length);
         vsInfo.setChildAssemblies(objList);
         vsInfo.setEntry(svs.getEntry());
      }
      else if(info instanceof ListInputVSAssemblyInfo) {
         ListInputVSAssemblyInfo listInfo = (ListInputVSAssemblyInfo) info;
         ListData listData = listInfo.getListData();

         // keep consistent of the data type
         if(listData != null && !Objects.equals(listData.getDataType(), listInfo.getDataType())) {
            listData.setDataType(listInfo.getDataType());
         }
      }
      else if(info instanceof CrosstabVSAssemblyInfo) {
         CrosstabVSAssemblyInfo cinfo = (CrosstabVSAssemblyInfo) info;
         SourceInfo sinfo = VSUtil.getCubeSource(assembly);

         if(sinfo != null) {
            cinfo.setCubeType(VSUtil.getCubeType(sinfo.getPrefix(), sinfo.getSource()));
         }
      }
      else if(info instanceof ChartVSAssemblyInfo) {
         ChartVSAssemblyInfo cinfo = (ChartVSAssemblyInfo) info;
         SourceInfo sinfo = VSUtil.getCubeSource(assembly);

         if(sinfo != null) {
            cinfo.setCubeType(VSUtil.getCubeType(sinfo.getPrefix(), sinfo.getSource()));
         }
      }
      else if(info instanceof TabVSAssemblyInfo &&
         (rvs.isPreview() || rvs.isViewer() || info.isEmbedded()))
      {
         // perform this in TabVSModel on cloned copy to avoid making this
         // change permanent in vs
         //VSUtil.fixSelected((TabVSAssemblyInfo) info, vs);
      }
      else if(info instanceof SelectionVSAssemblyInfo) {
         SelectionVSAssemblyInfo cinfo = (SelectionVSAssemblyInfo) info;
         String name = assembly.getAbsoluteName();
         int index = name.lastIndexOf('.');

         if(index >= 0) {
            box = box.getSandbox(name.substring(0, index));
         }

         SelectionVSAQuery query = (SelectionVSAQuery) VSAQuery.createVSAQuery(
            box, assembly, DataMap.ZOOM);
         cinfo.setHitsMV(query.hitsMV());
      }
   }

   /**
    * Append the viewsheet's embedded children to the specified list.
    */
   public static void appendEmbeddedChild(Viewsheet vs, List<Assembly> list,
                                          boolean recursive, RuntimeViewsheet rvs)
   {
      Assembly[] assemblies = vs.getAssemblies(false, true);

      for(Assembly assembly : assemblies) {
         VSAssembly vsassembly = (VSAssembly) assembly;
         VSAssemblyInfo info = (VSAssemblyInfo) vsassembly.getInfo();

         if(!isVisible(rvs, vsassembly) &&
            !(rvs.isRuntime() && (rvs.isTipView(info.getAbsoluteName()) ||
                                  rvs.isPopComponent(info.getAbsoluteName()))))
         {
            continue;
         }

         list.add(assembly);

         if(vsassembly instanceof Viewsheet && recursive) {
            appendEmbeddedChild((Viewsheet) assembly, list, true, rvs);
         }
      }
   }

   /**
    * Check if an assembly is visible in tab.
    * @param obj The assembly to check
    * @return returns whether or not the assembly is visible
    */
   public static boolean isVisibleTabVS(VSAssembly obj) {
      return isVisibleTabVS(obj, true);
   }

   /**
    * Check if an assembly is visible in tab.
    * @param obj The assembly to check
    * @param fixSelected true if it should call fixSelected method
    * @return returns whether or not the assembly is visible
    */
   public static boolean isVisibleTabVS(VSAssembly obj, boolean fixSelected) {
      if(obj == null || obj instanceof Viewsheet || obj instanceof ContainerVSAssembly) {
         return true;
      }

      List<VSAssembly> tree = new ArrayList<>();
      buildTree(obj, tree);
      String name = obj.getAbsoluteName();
      boolean visible = true;

      // last is self
      for(int i = tree.size() - 2; i >= 0; i--) {
         VSAssembly parent = tree.get(i);

         if(parent instanceof TabVSAssembly) {
            TabVSAssembly tab = (TabVSAssembly) parent;
            TabVSAssemblyInfo tinfo = (TabVSAssemblyInfo) tab.getInfo();
            tinfo = (TabVSAssemblyInfo) tinfo.clone();

            // @by stephenwebster, For bug1434058776599
            // In normal cases, fixSelected should be true at runtime.  At
            // design time, the client shows tabs and their content regardless
            // of visibility.  We should not change the selected tab in the
            // case where it is design time.
            if(fixSelected) {
               VSUtil.fixSelected(tinfo, tab.getViewsheet(), false, tinfo.getAssemblies());
            }

            String selected = tinfo.getSelected();

            if(selected != null && selected.length() > 0 && !sameAssembly(selected, name)) {
               visible = false;
               break;
            }
         }

         name = parent.getAbsoluteName();
      }

      return visible;
   }

   private static void buildTree(VSAssembly obj, List<VSAssembly> nodes) {
      nodes.add(0, obj);
      VSAssembly parent = obj.getContainer();

      if(parent != null) {
         buildTree(parent, nodes);
         return;
      }

      Viewsheet vs = obj.getViewsheet();

      if(vs != null) {
         buildTree(vs, nodes);
      }
   }

   private static boolean sameAssembly(String name, String aname) {
      return name.equals(aname) || aname.endsWith("." + name);
   }

   /**
    * Check an assembly is visible.
    * @param rvs the specified runtime viewsheet.
    * @param assembly the specified assembly.
    * @return <tt>true</tt> if visible, <tt>false</tt> otherwise.
    */
   public static boolean isVisible(RuntimeViewsheet rvs, VSAssembly assembly) {
      Viewsheet vs = assembly.getViewsheet();

      if(assembly.getContainer() instanceof GroupContainerVSAssembly &&
         !isVisible(rvs, assembly.getContainer()))
      {
         return false;
      }

      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(vs.isMaxMode() && info instanceof DataVSAssemblyInfo &&
         ((DataVSAssemblyInfo) info).getMaxSize() == null &&
         !WizardRecommenderUtil.isTempAssembly(assembly.getName()))
      {
         return false;
      }

      // visible?
      if(assembly.isVisible()) {
          // @by billh, performance optimization for metlife
         return isVisibleTabVS(assembly);
      }

      // send hidden selection container to client to allow adhoc filter
      // with hidden selection container
      if(assembly instanceof CurrentSelectionVSAssembly ||
         // @by davyc, for container assembly, if its visible is controlled by
         // script, it is really hard to check its children visible or not in
         // server, so we just let it added in client, and client will handle
         // its children visible or not
         // fix bug1359103493636
         // assembly instanceof GroupContainerVSAssembly ||
         assembly.getContainer() instanceof CurrentSelectionVSAssembly)
      {
         /* send all child of selection container so the selection (e.g. list)
            create at design time for a column will be used for adhoc filter
            instead of creating a new one (for selection list for number/date)
         if((info instanceof SelectionVSAssemblyInfo) && rvs.isRuntime()) {
            return ((SelectionVSAssemblyInfo) info).isAdhocFilter();
         }
         */

         return true;
      }

      // not visible, run time mode?
      if(rvs.isRuntime()) {
         return false;
      }

      // not visible, not runtime mode, viewsheet?
      if(assembly instanceof Viewsheet) {
         return vs == null || !vs.isEmbedded();
      }

      // not visible, not runtime mode, is primary?
      if(vs != null && vs.isEmbedded() && assembly.isPrimary()) {
         return true;
      }

      return !assembly.isEmbedded();
   }

   /**
    * Add/Remove an assembly in client side.
    * @param rvs the specified runtime viewsheet.
    * @param assembly the specified assembly.
    * @param uri the specified service request uri.
    * @param command the specified command container.
    */
   public static void addDeleteVSObject(RuntimeViewsheet rvs, AssetEvent event,
                                        VSAssembly assembly, String uri,
                                        AssetCommand command)
      throws Exception
   {
      addDeleteVSObject(rvs, event, assembly, uri, command, true);
   }

   /**
    * Add/Remove an assembly in client side.
    * @param rvs the specified runtime viewsheet.
    * @param assembly the specified assembly.
    * @param uri the specified service request uri.
    * @param command the specified command container.
    * @param sub true to add/delete sub objects.
    */
   public static void addDeleteVSObject(RuntimeViewsheet rvs, AssetEvent event,
                                        VSAssembly assembly, String uri,
                                        AssetCommand command, boolean sub)
      throws Exception
   {
      if(assembly == null) {
         throw new ConfirmException();
      }

      String name = assembly.getAbsoluteName();
      VSAssemblyInfo info = getAssemblyInfo(rvs, assembly);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      // @by yanie: bug1422049637079
      // The runtime dynamic values might be reset, so herein execute them to
      // make sure the assembly's visibility state is correct
      if(assembly instanceof SelectionVSAssembly) {
         box.executeDynamicValues(name, assembly.getViewDynamicValues(true));
      }

      if(!isVisible(rvs, assembly) && !rvs.isTipView(name) &&
         !rvs.isPopComponent(name) && !isInTab(assembly) &&
         !info.isControlByScript() && !AnnotationVSUtil.isAnnotation(assembly))
      {
         command.addCommand(new RemoveVSObjectCommand(name));
         deleteEmbeddedViewsheetChildren(assembly, command);

         return;
      }

      int mode = AssetQuerySandbox.DESIGN_MODE;

      if(box == null || (AnnotationVSUtil.isAnnotation(assembly) &&
         (!rvs.isRuntime() || name.contains("."))))
      {
         return;
      }

      try {
         box.updateAssembly(assembly.getAbsoluteName());
      }
      catch(ConfirmException ex) {
         // ignore
      }
      catch(Exception ex) {
         LOG.warn("Failed to update a viewsheet assembly after deleting children",
            ex);
      }

      if(assembly instanceof Viewsheet) {
         ViewsheetVSAssemblyInfo vinfo = (ViewsheetVSAssemblyInfo) info;
         command.addCommand(new AddVSObjectCommand(vinfo, mode));

         if(sub) {
            List objList = vinfo.getChildAssemblies();

            for(Object obj : objList) {
               VSAssembly sassembly = (VSAssembly) obj;
               VSAssemblyInfo cinfo = getAssemblyInfo(rvs, sassembly);

               if(isVisible(rvs, sassembly) ||
                  rvs.isRuntime() && (rvs.isTipView(cinfo.getAbsoluteName()) ||
                     rvs.isPopComponent(cinfo.getAbsoluteName()))) {
                  addDeleteVSObject(rvs, event, sassembly, uri, command);

                  // @by billh, performance optimization for metalife,
                  // load table lens onDemand to avoid data being pre-loaded
                  // loadTableLens(rvs, event, cinfo.getAbsoluteName(), uri, command);
               }
            }
         }
      }
      else {
         if(info instanceof OutputVSAssemblyInfo && info.isEnabled()) {
            Object data = box.getData(assembly.getName());

            if(data == null) {
               data = ((OutputVSAssembly) assembly).getValue();
            }

            OutputVSAssemblyInfo outputInfo = (OutputVSAssemblyInfo) info;
            BindingInfo binding = outputInfo.getBindingInfo();
            // it is not reasonable to disable text, this will cause
            // format problem, fix bug1343748782524
            outputInfo.setOutputEnabled(data != null || !box.isRuntime() ||
               binding == null || binding.isEmpty() ||
               outputInfo instanceof TextVSAssemblyInfo);
         }

         // first refresh, draw with mask
         if(!info.isEnabled()) {
            info = info.clone();
         }

         AddVSObjectCommand cmd = new AddVSObjectCommand(info, mode, box);
         command.addCommand(cmd);

         if(rvs.isRuntime() &&
            AnnotationVSUtil.needRefreshAnnotation(info, command))
         {
            AnnotationVSUtil.refreshAllAnnotations(rvs, assembly, command);
         }
      }
   }

   /**
    *Delete the children of embeded viewsheet.
    */
   private static void deleteEmbeddedViewsheetChildren(VSAssembly assembly,
      AssetCommand command)
   {
      if(!assembly.isEmbedded() || !(assembly instanceof Viewsheet)) {
         return;
      }

      List<Assembly> assemblies = new ArrayList<>();
      listEmbeddedAssemblies((Viewsheet) assembly, assemblies);

      for(Assembly embedded : assemblies) {
         String name = embedded.getAbsoluteName();
         command.addCommand(new RemoveVSObjectCommand(name));
      }
   }

   /**
    * Create an assembly in client side.
    * @param rvs the specified runtime viewsheet.
    * @param typeStr the assembly type.
    * @param command the specified command container.
    * @return the name of the newly created assembly.
    */
   @Deprecated
   public static String createVSAssembly(RuntimeViewsheet rvs, AssetEvent event,
                                         String typeStr, String uri,
                                         AssetCommand command, Point pos)
      throws Exception
   {
      int type = Integer.parseInt(typeStr);
      VSAssembly assembly = createVSAssembly(rvs, type);
      assert assembly != null;
      assembly.setPixelOffset(pos);
      addDeleteVSObject(rvs, event, assembly, uri, command);

      return assembly.getName();
   }

   /**
    * Create an assembly.
    * @param rvs the specified runtime viewsheet.
    * @param type the assembly type.
    * @return the newly created assembly.
    */
   public static VSAssembly createVSAssembly(RuntimeViewsheet rvs, int type) {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return null;
      }

      VSAssembly assembly = null;
      String name = AssetUtil.getNextName(vs, type);

      switch(type) {
      case AbstractSheet.TABLE_VIEW_ASSET:
         assembly = new TableVSAssembly(vs, name);
         break;
      case AbstractSheet.CHART_ASSET:
         assembly = new ChartVSAssembly(vs, name);
         break;
      case AbstractSheet.CROSSTAB_ASSET:
         assembly = new CrosstabVSAssembly(vs, name);
         break;
      case AbstractSheet.FORMULA_TABLE_ASSET:
         assembly = new CalcTableVSAssembly(vs, name);
         break;
      case AbstractSheet.SLIDER_ASSET:
         assembly = new SliderVSAssembly(vs, name);
         break;
      case AbstractSheet.SPINNER_ASSET:
         assembly = new SpinnerVSAssembly(vs, name);
         break;
      case AbstractSheet.CHECKBOX_ASSET:
         assembly = new CheckBoxVSAssembly(vs, name);
         break;
      case AbstractSheet.RADIOBUTTON_ASSET:
         assembly = new RadioButtonVSAssembly(vs, name);
         break;
      case AbstractSheet.COMBOBOX_ASSET:
         assembly = new ComboBoxVSAssembly(vs, name);
         break;
      case AbstractSheet.TEXT_ASSET:
         assembly = new TextVSAssembly(vs, name);
         ((TextVSAssembly) assembly).setValue("text");
         ((TextVSAssembly) assembly).setTextValue("text");
         break;
      case AbstractSheet.IMAGE_ASSET:
         assembly = new ImageVSAssembly(vs, name);
         break;
      case AbstractSheet.GAUGE_ASSET:
         assembly = new GaugeVSAssembly(vs, name);
         break;
      case AbstractSheet.THERMOMETER_ASSET:
         assembly = new ThermometerVSAssembly(vs, name);
         break;
      case AbstractSheet.SLIDING_SCALE_ASSET:
         assembly = new SlidingScaleVSAssembly(vs, name);
         break;
      case AbstractSheet.CYLINDER_ASSET:
         assembly = new CylinderVSAssembly(vs, name);
         break;
      case AbstractSheet.SELECTION_LIST_ASSET:
         assembly = new SelectionListVSAssembly(vs, name);
         break;
      case AbstractSheet.SELECTION_TREE_ASSET:
         assembly = new SelectionTreeVSAssembly(vs, name);
         break;
      case AbstractSheet.TIME_SLIDER_ASSET:
         assembly = new TimeSliderVSAssembly(vs, name);
         break;
      case AbstractSheet.CALENDAR_ASSET:
         assembly = new CalendarVSAssembly(vs, name);
         break;
      case AbstractSheet.EMBEDDEDTABLE_VIEW_ASSET:
         assembly = new EmbeddedTableVSAssembly(vs, name);
         break;
      case AbstractSheet.LINE_ASSET:
         assembly = new LineVSAssembly(vs, name);
         break;
      case AbstractSheet.RECTANGLE_ASSET:
         assembly = new RectangleVSAssembly(vs, name);
         break;
      case AbstractSheet.OVAL_ASSET:
         assembly = new OvalVSAssembly(vs, name);
         break;
      case AbstractSheet.PAGEBREAK_ASSET:
         assembly = new PageBreakVSAssembly(vs, name);
         break;
      case AbstractSheet.GROUPCONTAINER_ASSET:
         assembly = new GroupContainerVSAssembly(vs, name);
         break;
      case AbstractSheet.CURRENTSELECTION_ASSET:
         assembly = new CurrentSelectionVSAssembly(vs, name);
         break;
      case AbstractSheet.TEXTINPUT_ASSET:
         assembly = new TextInputVSAssembly(vs, name);
         break;
      case AbstractSheet.SUBMIT_ASSET:
         assembly = new SubmitVSAssembly(vs, name);
         break;
      case AbstractSheet.UPLOAD_ASSET:
         assembly = new UploadVSAssembly(vs, name);
         break;
      case AbstractSheet.ANNOTATION_ASSET:
         assembly = new AnnotationVSAssembly(vs, name);
         break;
      case AbstractSheet.ANNOTATION_LINE_ASSET:
         assembly = new AnnotationLineVSAssembly(vs, name);
         break;
      case AbstractSheet.ANNOTATION_RECTANGLE_ASSET:
         assembly = new AnnotationRectangleVSAssembly(vs, name);
         break;
      default:
         break;
      }

      assert assembly != null;
      assembly.initDefaultFormat();
      vs.addAssembly(assembly);

      return assembly;
   }

   /**
    * Refresh assembly.
    * @param rvs runtime viewsheet.
    * @param name the specified assembly name.
    * @param command Asset command.
    */
   public static void refreshVSAssembly(RuntimeViewsheet rvs, String name,
                                        AssetCommand command)
      throws Exception
   {
      refreshVSAssembly(rvs, name, command, false);
   }

   /**
    * Refresh assembly.
    * @param rvs runtime viewsheet.
    * @param name the specified assembly name.
    * @param command Asset command.
    * @param recursive refresh the child component or not.
    */
   public static void refreshVSAssembly(RuntimeViewsheet rvs, String name,
                                        AssetCommand command, boolean recursive)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(name);

      if(assembly == null) {
         return;
      }

      if(recursive && assembly instanceof ContainerVSAssembly) {
         ContainerVSAssembly cassembly = (ContainerVSAssembly) assembly;
         // use absolute name
         String[] names = cassembly.getAbsoluteAssemblies();

         for(String absoluteName : names) {
            refreshVSAssembly(rvs, absoluteName, command);
         }
      }

      refreshVSAssembly(rvs, assembly, command);
   }

   /**
    * Refresh assembly.
    * @param rvs runtime viewsheet.
    * @param assembly the assembly object to refresh.
    * @param command Asset command.
    */
   public static void refreshVSAssembly(RuntimeViewsheet rvs,
                                        VSAssembly assembly,
                                        AssetCommand command)
      throws Exception
   {
      refreshVSAssembly(rvs, assembly, command, true);
   }

   /**
    * Refresh assembly.
    * @param rvs runtime viewsheet.
    * @param assembly the assembly object to refresh.
    * @param command Asset command.
    * @param shrink shrink the command or not.
    */
   public static void refreshVSAssembly(RuntimeViewsheet rvs,
                                        VSAssembly assembly,
                                        AssetCommand command,
                                        boolean shrink)
      throws Exception
   {
      String shared = (String) command.get("SHARED_HINT");

      if(assembly == null || (!isVisible(rvs, assembly) &&
         !AnnotationVSUtil.isAnnotation(assembly) &&
         !rvs.isTipView(assembly.getAbsoluteName())))
      {
         return;
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(assembly instanceof Viewsheet) {
         ViewsheetVSAssemblyInfo info =
            (ViewsheetVSAssemblyInfo) getAssemblyInfo(rvs, assembly);
         command.addCommand(new RefreshVSObjectCommand(
                               info, shared, shrink, box));
         List objList = info.getChildAssemblies();

         for(Object obj : objList) {
            VSAssemblyInfo childInfo = getAssemblyInfo(rvs, (VSAssembly) obj);
            command.addCommand(new RefreshVSObjectCommand(childInfo, shared, shrink, box));
         }
      }
      else {
         if(assembly.isEmbedded()) {
            Viewsheet vs2 = assembly.getViewsheet();

            if(vs2 == null) {
               return;
            }

            String name2 = vs2.getAbsoluteName();

            // visible embedded viewsheet?
            if(!name2.contains(".")) {
               VSAssemblyInfo info2 = getAssemblyInfo(rvs, vs2);
               command.addCommand(new RefreshVSObjectCommand(
                                     info2, null, shrink, box));
            }
         }

         VSAssemblyInfo info = getAssemblyInfo(rvs, assembly);
         command.addCommand(new RefreshVSObjectCommand(
                               info, shared, shrink, box));

         if((info instanceof SelectionListVSAssemblyInfo) &&
            ((SelectionListVSAssemblyInfo) info).isAdhocFilter())
         {
            FormatInfo fmtInfo = info.getFormatInfo();
            VSCompositeFormat ofmt = info.getFormat();
            VSCompositeFormat tfmt =
               fmtInfo.getFormat(VSAssemblyInfo.TITLEPATH);
            VSFormat fmt = new VSFormat();
            fmt.setBackgroundValue(Color.WHITE.getRGB() + "");
            ofmt.setUserDefinedFormat(fmt);
            tfmt.setUserDefinedFormat(fmt);
         }

         if(rvs.isRuntime() &&
            AnnotationVSUtil.needRefreshAnnotation(info, command))
         {
            AnnotationVSUtil.refreshAllAnnotations(rvs, assembly, command);
         }
      }
   }

   /*
    * Refresh base worksheet tree.
    * @param engine asset repository engine.
    * @param user user.
    * @param entry the specified entry.
    */
   public static AssetTreeModel refreshBaseWSTree(AssetRepository engine,
      Principal user, Viewsheet vs) throws Exception
   {
      return VSEventUtil.refreshBaseWSTree(engine, user, vs, null);
   }

   /*
    * Refresh base worksheet tree.
    * @param engine asset repository engine.
    * @param user user.
    * @param entry the specified entry.
    * @param cubeProps include which sub nodes of cube.
    */
   public static AssetTreeModel refreshBaseWSTree(AssetRepository engine,
      Principal user, Viewsheet vs, Properties props) throws Exception
   {
      return VSEventUtil.refreshBaseWSTree(engine, user, vs, props, false);
   }

   /**
    * Append viewsheet assembly aggregate columns to model tree.
    */
   public static void appendVSAssemblyTree(Viewsheet vs,
                                           AssetTreeModel model,
                                           Principal user)
   {
      IdentityID pId = user == null ? null : IdentityID.getIdentityIDFromKey(user.getName());
      Node mroot = (Node) model.getRoot();
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.REPOSITORY_FOLDER,
         "/" + Catalog.getCatalog().getString("Components"), pId);
      Node root = new Node(entry);
      mroot.addNode(root);

      List<String> names = new ArrayList<>();
      Assembly[] assemblies = vs.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(assembly instanceof ChartVSAssembly || assembly instanceof CrosstabVSAssembly) {
            DataVSAssemblyInfo info = (DataVSAssemblyInfo) assembly.getInfo();

            if(info.getSourceInfo() != null &&
               info.getSourceInfo().getType() == SourceInfo.VS_ASSEMBLY)
            {
               continue;
            }

            String name = assembly.getAbsoluteName();
            names.add(name);
         }
      }

      Collections.sort(names);

      for(String name : names) {
         Assembly ass = vs.getAssembly(name);
         DataRef[] aggrs;
         boolean isCube = false;

         if(ass instanceof ChartVSAssembly) {
            VSChartInfo cinfo = ((ChartVSAssembly) ass).getVSChartInfo();
            aggrs = cinfo.getRTFields();
         }
         else if(ass instanceof CrosstabVSAssembly) {
            VSCrosstabInfo cinfo = ((CrosstabVSAssembly) ass).getVSCrosstabInfo();
            aggrs = cinfo.getRuntimeAggregates();
            DataVSAssemblyInfo info = (DataVSAssemblyInfo) ass.getInfo();

            if(info.getSourceInfo() != null && info.getSourceInfo().getSource() != null) {
               isCube = VSUtil.isCubeSource(info.getSourceInfo().getSource());
            }

            if(cinfo.getRuntimeAggregates() == null ||
               cinfo.getRuntimeAggregates().length == 1 &&
               VSUtil.isFake(cinfo.getRuntimeAggregates()[0]))
            {
               continue;
            }
         }
         else {
            continue;
         }

         String path = "/assemblies/" + name;
         entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                AssetEntry.Type.TABLE, path, pId);
         entry.setAlias(name);
         Node node = new Node(entry);
         List<String> columns = new ArrayList<>();
         Map<String, String> columnDataTypes = new HashMap<>();

         for(DataRef agg : aggrs) {
            if(agg instanceof AestheticRef) {
               agg = ((AestheticRef) agg).getDataRef();
            }

            if(agg instanceof XAggregateRef) {
               XAggregateRef clone = (XAggregateRef) agg.clone();

               if(clone.getCalculator() != null) {
                  clone.setCalculator(null);
               }

               if(isCube) {
                  clone.setFormula(AggregateFormula.NONE);
               }

               agg = clone;
               String aggr = ((XAggregateRef) agg).getFullName(false);
               aggr = VSUtil.getAggregateField(aggr, agg);
               aggr = VSUtil.normalizeAggregateName(aggr);

               if(!columns.contains(aggr) && !agg.getName().startsWith("Total@")) {
                  columns.add(aggr);
                  columnDataTypes.put(aggr, agg.getDataType());
               }
            }
         }

         if(columns.size() <= 0) {
            continue;
         }

         Collections.sort(columns);
         root.addNode(node);

         for(String column : columns) {
            String path0 = path + "/" + column;
            AssetEntry entry0 = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                               AssetEntry.Type.COLUMN, path0, pId);
            String dataType = columnDataTypes.get(column);
            entry0.setProperty("dtype", XSchema.isDateType(dataType) ? dataType : XSchema.DOUBLE);
            entry0.setProperty("assembly", name);
            entry0.setProperty("attribute", column);
            entry0.setProperty("source", name);
            entry0.setProperty("type", XSourceInfo.VS_ASSEMBLY + "");
            node.addNode(new Node(entry0));
         }
      }
   }

   /*
    * Refresh base worksheet tree.
    * @param engine asset repository engine.
    * @param principal principal.
    * @param entry the specified entry.
    * @param cubeProps include which sub nodes of cube.
    */
   public static AssetTreeModel refreshBaseWSTree(AssetRepository engine,
      Principal principal, Viewsheet vs, Properties props, boolean layout)
      throws Exception
   {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      AssetTreeModel model = new AssetTreeModel();
      AssetEntry root = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                       AssetEntry.Type.FOLDER, "root", null);
      Node rootNode = new Node(root);
      rootNode.setRequested(true);
      model.setRoot(rootNode);

      if(vs == null) {
         return model;
      }

      if(props == null) {
         props = new Properties();
      }

      String source = props.getProperty("source");
      String filter = props.getProperty("filterTable");
      boolean showVars = !("false".equals(props.getProperty("showVariables")));
      boolean showCube = "true".equalsIgnoreCase(props.getProperty("showCube"));
      boolean runtime = "true".equalsIgnoreCase(props.getProperty("runtime"));
      boolean cube = source != null && source.contains(Assembly.CUBE_VS);
      boolean excludeAggCalc =
         "true".equals(props.getProperty("excludeAggCalc"));
      boolean onlyCube = "true".equals(props.getProperty("onlyCube"));
      Node varsNode = null;
      boolean allowed = checkBaseWSPermission(vs, principal, engine, ResourceAction.READ);
      Worksheet ws = vs.getBaseWorksheet();
      AssetEntry baseEntry = vs.getBaseEntry();

      if(vs.isDirectSource() && !onlyCube) {
         AssetEntry.Type type;

         if(baseEntry.isLogicModel()) {
            type = AssetEntry.Type.LOGIC_MODEL;
         }
         else if(baseEntry.isPhysicalTable()){
            type = AssetEntry.Type.PHYSICAL_TABLE;
         }
         else if(baseEntry.isQuery()) {
            type = AssetEntry.Type.TABLE;
         }
         else {
            throw new RuntimeException(baseEntry + " is not supported");
         }

         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, type,
            "/baseWorksheet/" + baseEntry.getName(), pId);
         entry.setAlias(baseEntry.getAlias());
         entry.setReportDataSource(baseEntry.isReportDataSource());
         entry.copyProperties(baseEntry);
         entry.setProperty("Tooltip", baseEntry.getProperty("Tooltip"));

         if(baseEntry.isPhysicalTable()) {
            entry.setProperty("originalType", "physicalTable");
         }
         else if(baseEntry.isLogicModel()) {
            entry.setProperty("originalType", "logicModel");
         }

         Node wsNode = new Node(entry);
         appendChildNodes(wsNode, baseEntry, engine, principal, entry.getName(), vs,
            excludeAggCalc);
         entry.setProperty("source", BASE_WORKSHEET);
         entry.setProperty("embedded", "false");
         rootNode.addNode(wsNode);
         Assembly[] assemblies = allowed ? ws.getAssemblies() : new Assembly[0];
         Arrays.sort(assemblies, new WSAssemblyComparator(ws));

         for(Assembly assembly : assemblies) {
            if(!assembly.isVisible()) {
               continue;
            }

            if((assembly instanceof VariableAssembly) && showVars) {
               varsNode = createVariableNode(varsNode, assembly);
            }
         }
      }

      if(baseEntry != null && (!runtime || !cube) && !vs.isDirectSource() && !onlyCube) {
         baseEntry.setProperty("Tooltip", baseEntry.getProperty("description"));
         Node wsNode = new Node(baseEntry);

         if(ws == null) {
            throw new Exception(Catalog.getCatalog().getString(
               "common.viewsheet.baseworksheet.notExist", baseEntry));
         }

         Assembly[] assemblies = allowed ? ws.getAssemblies() : new Assembly[0];
         Arrays.sort(assemblies, new WSAssemblyComparator(ws));

         for(Assembly assembly : assemblies) {
            if(!assembly.isVisible()) {
               continue;
            }

            if(assembly instanceof TableAssembly) {
               if(filter != null && !filter.equals(assembly.getName())) {
                  continue;
               }

               TableAssembly table = (TableAssembly) assembly;
               AssetEntry tempEntry = new AssetEntry(
                  AssetRepository.QUERY_SCOPE, AssetEntry.Type.TABLE,
                  "/baseWorksheet/" + assembly.getName(), pId);
               Node tempNode = new Node(tempEntry);

               tempEntry.setProperty("Tooltip", table.getDescription());
               tempEntry.setProperty("source", BASE_WORKSHEET);
               tempEntry.setProperty("embedded", isEmbeddedDataSource(assembly) + "");
               appendColumnNodes((TableAssembly) assembly, tempNode, principal, vs, excludeAggCalc);

               if(table.isVisibleTable()) {
                  wsNode.addNode(tempNode);
               }
            }
            else if((assembly instanceof VariableAssembly) && showVars) {
               varsNode = createVariableNode(varsNode, assembly);
            }
         }

         rootNode.addNode(wsNode);
      }

      if(!layout && (!runtime || cube || showCube)) {
         Node cubeNode = getCubeTree(principal, props, vs);

         if(cubeNode.getNodeCount() > 0) {
            rootNode.addNode(cubeNode);
         }
      }

      if(varsNode != null) {
         rootNode.addNode(varsNode);
      }

      return model;
   }

   /**
    * Create variable node.
    */
   private static Node createVariableNode(Node varsNode, Assembly assembly) {
      if(varsNode == null) {
         AssetEntry varsEntry = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE,
            AssetEntry.Type.FOLDER, "Variables", null);
         varsEntry.setProperty("localStr",
            Catalog.getCatalog().getString("Variables"));
         varsNode = new Node(varsEntry);
      }

      AssetEntry varEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VARIABLE, assembly.getName(), null);
      varEntry.setAlias(((VariableAssembly) assembly).getVariable().getAlias());
      varsNode.addNode(new Node(varEntry));
      return varsNode;
   }

   /**
    * Append the child nodes to the root node.
    */
   private static void appendChildNodes(Node root, AssetEntry pentry,
      AssetRepository engine, Principal principal, String assembly, Viewsheet vs,
      boolean excludeAggCalc)
      throws Exception
   {
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      AssetEntry[] entries = engine.getEntries(pentry, principal, ResourceAction.READ,
         new AssetEntry.Selector(AssetEntry.Type.LOGIC_MODEL,
            AssetEntry.Type.PHYSICAL_TABLE, AssetEntry.Type.QUERY));

      if(entries == null || entries.length <= 0) {
         return;
      }

      CalculateRef[] calcs = vs == null ? null :
         vs.getCalcFields(pentry.getName());

      if(calcs != null) {
         List<AssetEntry> calcEntry = new ArrayList<>();
         AssetEntry base = entries[0];

         for(CalculateRef cref : calcs) {
            if(VSUtil.isPreparedCalcField(cref) || excludeAggCalc && !cref.isBaseOnDetail() ||
               cref.isDcRuntime())
            {
               continue;
            }

            AssetEntry tempEntry = new AssetEntry(base.getScope(),
                                                  AssetEntry.Type.COLUMN,
                                                  base.getParentPath() + "/" + cref.getName(),
                                                  pId);
            //centry.copyProperties(base);

            tempEntry.setProperty("prefix", base.getProperty("prefix"));
            tempEntry.setProperty("source", base.getProperty("source"));
            tempEntry.setProperty("type", base.getProperty("type"));

            tempEntry.setProperty("dtype", cref.getDataType());
            tempEntry.setProperty("attribute", cref.getName());

            if(cref instanceof CalculateRef) {
               if(!Tool.isEmptyString(cref.getAlias())) {
                  tempEntry.setProperty("Tooltip", cref.getAlias());
               }
            }
            else {
               tempEntry.setProperty("Tooltip", cref.toView());
            }

            AssetEventUtil.appendCalcProperty(tempEntry, cref);
            calcEntry.add(tempEntry);
         }

         calcEntry.addAll(Arrays.asList(entries));
         entries = calcEntry.toArray(entries);

         if(pentry.isLogicModel()) {
            Arrays.sort(entries, new LMEntityEntryComparator());
         }
         else {
            Arrays.sort(entries, new EntryComparator());
         }
      }

      for(AssetEntry entry1 : entries) {
         AssetEntry.Type type;
         String name;

         if(entry1.isFolder()) {
            type = AssetEntry.Type.TABLE;
         }
         else {
            type = AssetEntry.Type.COLUMN;
         }

         if(type == AssetEntry.Type.COLUMN) {
            name = "/baseWorksheet/" + entry1.getParent().getName() + "/" +
               entry1.getName();
         }
         else {
            name = "/baseWorksheet/" + entry1.getName();
         }

         assert principal != null;
         AssetEntry entry = new AssetEntry(
            AssetRepository.QUERY_SCOPE, type, name, pId);
         entry.setAlias(entry1.getAlias());
         entry.setReportDataSource(entry1.isReportDataSource());
         entry.copyProperties(entry1);
         entry.setProperty("Tooltip", entry1.getProperty("Tooltip"));

         Node tempNode = new Node(entry);
         root.addNode(tempNode);

         if((XSourceInfo.MODEL + "").equals(entry.getProperty("type"))) {
            entry.setProperty("table", entry.getProperty("source"));
            entry.setProperty("assembly", VSUtil.getTableName(
               entry.getProperty("source")));
            entry.setProperty("originType", XSourceInfo.MODEL + "");
            entry.setProperty("originidentifier", entry1.toIdentifier());
            entry.setProperty("entity", null);
            entry.setProperty("sourceType", AssetEntry.Type.LOGIC_MODEL + "");
         }
         else {
            entry.setProperty("assembly", assembly);
         }

         entry.setProperty("embedded", "false");
         entry.setProperty("source", BASE_WORKSHEET);
         entry.setProperty("type", XSourceInfo.ASSET + "");

         if(entry1.isFolder()) {
            appendChildNodes(tempNode, entry1, engine, principal,
                             assembly, vs, excludeAggCalc);
         }
      }
   }

   /**
    * Get cubes from data source registory.
    * @param user the user property.
    * @param cubeProps include which sub nodes of cube.
    */
   public static Node getCubeTree(Principal user, Properties cubeProps,
                                  Viewsheet vs) throws Exception
   {
      List<Node> cubes = AssetEventUtil.getCubes(user, cubeProps, vs);
      cubes.sort(new NodeComparator());

      AssetEntry cubesEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.FOLDER, Catalog.getCatalog().getString("Cubes"), null);
      cubesEntry.setProperty("localStr", Catalog.getCatalog().getString("Cubes"));
      Node cubesRootNode = new Node(cubesEntry);

      for(Node cubeNode : cubes) {
         cubesRootNode.addNode(cubeNode);
      }

      return cubesRootNode;
   }

   /*
    * Append column nodes.
    * @param assembly table assembly.
    * @param parentNode the parent node.
    * @param user user.
    */
   private static void appendColumnNodes(TableAssembly assembly,
                                         Node parentNode, Principal user,
                                         Viewsheet vs, boolean excludeAggCalc)
   {
      IdentityID pId = user == null ? null : IdentityID.getIdentityIDFromKey(user.getName());
      assembly.setColumnSelection(assembly.getColumnSelection(false), false);
      ColumnSelection columns = assembly.getColumnSelection(true);
      List<ColumnRef> list = new ArrayList<>();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef cref = (ColumnRef) columns.getAttribute(i);

         if(!(cref instanceof CalculateRef)) {
            list.add(cref);
         }
      }

      if(vs != null) {
         VSUtil.appendCalcFields(list, assembly, vs, excludeAggCalc, true);
      }

      list.sort(new VSUtil.DataRefComparator());
      String assemblyName = assembly.getName();
      String embedded = isEmbeddedDataSource(assembly, true, true) + "";
      AggregateInfo aggregateInfo = assembly.getAggregateInfo();

      for(ColumnRef column : list) {
         DataRef ref = column.getDataRef();

         if(aggregateInfo != null) {
            GroupRef group = aggregateInfo.getGroup(column);

            if(group != null && group.getNamedGroupInfo() != null &&
               !group.getNamedGroupInfo().isEmpty() && !column.isDataTypeSet())
            {
               column.setDataType(XSchema.STRING);
            }
         }

         String name = column.getAlias();
         name = name == null || name.length() == 0 ? column.getAttribute() : name;
         AssetEntry tempEntry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.COLUMN,
                                               "/baseWorksheet/" + assembly.getName() + "/" + name,
                                               pId);
         tempEntry.setProperty("dtype", column.getDataType());
         tempEntry.setProperty("assembly", assemblyName);
         tempEntry.setProperty("attribute", name);
         tempEntry.setProperty("source", BASE_WORKSHEET);
         tempEntry.setProperty("type", XSourceInfo.ASSET + "");
         tempEntry.setProperty("embedded", embedded);
         tempEntry.setProperty("expression", (ref instanceof ExpressionRef) + "");
         tempEntry.setProperty("Tooltip", column.getDescription());

         if(assembly instanceof CubeTableAssembly && (column instanceof CalculateRef)) {
            tempEntry.setProperty("wsCubeCalc", "true");
         }

         AssetEventUtil.appendCalcProperty(tempEntry, column);
         parentNode.addNode(new Node(tempEntry));
      }
   }

   /**
    * Load table lens.
    * @param rvs runtime worksheet.
    * @param name the vs assembly name.
    * @param uri the specified service request uri.
    * @param command asset command.
    */
   public static void loadTableLens(RuntimeViewsheet rvs, AssetEvent event,
                                    String name, String uri, AssetCommand command)
   {
      try {
         Viewsheet vs = rvs.getViewsheet();
         VSAssembly assembly = vs == null ? null : vs.getAssembly(name);

         if(!(assembly instanceof TableDataVSAssembly)) {
            return;
         }

         VSAssemblyInfo info = assembly.getVSAssemblyInfo();

         if(!isVisible(rvs, assembly) && !info.isControlByScript() &&
            !(rvs.isRuntime() && (rvs.isTipView(name) ||
            rvs.isPopComponent(name))))
         {
            return;
         }

         int start = 0;
         int mode = 0;
         int num = event.get("ipad") == "true"
            ? LoadTableLensEvent.SMALL_BLOCK : LoadTableLensEvent.BLOCK;

         LoadTableLensEvent.processEvent(rvs, event, name, uri, mode, start,
                                         num, command);
      }
      catch(ConfirmException | CancelledException e) {
         throw e;
      }
      catch(ScriptException e) {
         List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

         if(exs != null) {
            exs.add(e);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to load the table data", ex);
         MessageCommand mcmd = new MessageCommand(ex);
         command.addCommand(mcmd);
      }
   }

   /**
    * Execute the runtime viewsheet.
    * @param rvs runtime worksheet.
    * @param name the assembly name.
    * @param uri the specified service request uri.
    * @param hint output or input.
    * @param command asset command.
    */
   public static void execute(RuntimeViewsheet rvs, AssetEvent event,
                              String name, String uri, int hint,
                              AssetCommand command)
      throws Exception
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return;
      }

      ChangedAssemblyList clist = new ChangedAssemblyList();
      box.processChange(name, hint, clist);
      execute(rvs, event, name, uri, clist, command, true);
   }

   /**
    * Execute the runtime viewsheet.
    * @param rvs runtime worksheet.
    * @param name the assembly name.
    * @param uri the specified service request uri.
    * @param command asset command.
    */
   public static void execute(RuntimeViewsheet rvs, AssetEvent event,
                              String name, String uri,
                              ChangedAssemblyList clist, AssetCommand command,
                              boolean included)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null) {
         return;
      }

      List<Object> processed = new ArrayList<>();

      // refreshData before executeView so the highlight uses the correct value
      List dataList = clist.getDataList();
      Iterator iterator = dataList.iterator();
      Set<String> tableDataAssemblies = new HashSet<>();

      while(iterator.hasNext()) {
         AssemblyEntry entry = (AssemblyEntry) iterator.next();
         String vname = entry.getAbsoluteName();
         VSAssembly assembly = vs.getAssembly(vname);

         // already processed? ignore it
         if(clist.getProcessedList().contains(entry) ||
            AnnotationVSUtil.isAnnotation(assembly))
         {
            continue;
         }

         refreshData(rvs, command, entry, uri);

         if(assembly == null) {
            continue;
         }

         if((included || !vname.equals(name)) && !processed.contains(vname)) {
            addDeleteVSObject(rvs, event, assembly, uri, command);
            tableDataAssemblies.add(vname);
            processed.add(vname);
         }
      }

      List<AssemblyEntry> viewList = clist.getViewList();
      viewList.sort(new DependencyComparator(vs, true));
      iterator = viewList.iterator();

      while(iterator.hasNext()) {
         AssemblyEntry entry = (AssemblyEntry) iterator.next();

         // already processed? ignore it
         if(clist.getProcessedList().contains(entry)) {
            continue;
         }

         String vname = entry.getAbsoluteName();
         VSAssembly assembly = vs.getAssembly(vname);

         if(assembly == null || AnnotationVSUtil.isAnnotation(assembly)) {
            continue;
         }

         box.executeView(entry.getAbsoluteName(), false);

         if(included || !vname.equals(name)) {
            addDeleteVSObject(rvs, event, assembly, uri, command);

            // @by davyc, for view, should also refresh children,
            // to make sure in client the enable is correct
            if(assembly instanceof ContainerVSAssembly) {
               addContainerVSObject(rvs, event, assembly, uri, command,
                  processed);
            }
            else if(assembly instanceof Viewsheet) {
               Assembly[] assems = ((Viewsheet) assembly).getAssemblies(true);

               for(Assembly assem : assems) {
                  if(assem != null) {
                     addDeleteVSObject(rvs, event, (VSAssembly) assem,
                                       uri, command);

                     if(!processed.contains(assem.getAbsoluteName())) {
                        loadTableLens(rvs, event, assem.getAbsoluteName(),
                                      uri, command);
                        processed.add(assem.getAbsoluteName());
                     }
                  }
               }
            }

            if(!processed.contains(vname)) {
               loadTableLens(rvs, event, vname, uri, command);
               processed.add(vname);
            }
         }
      }

      // load table data after execute() so the dynamic values would be
      // updated already
      for(String vname : tableDataAssemblies) {
         loadTableLens(rvs, event, vname, uri, command);
      }

      // the setActionVisible may be called in script so we refresh the info.
      // in the future, we may consider adding the dependency to clist to
      // refresh only necessary
      SetViewsheetInfoCommand cmd2 = new SetViewsheetInfoCommand(rvs);
      command.addCommand(cmd2, true);
   }

   /**
    * Add container with its children.
    * @param rvs the runtime viewsheet.
    * @param event the specified viewsheet event.
    * @param assembly the container assembly.
    * @param uri the specified service request uri.
    * @param command asset command.
    */
   private static void addContainerVSObject(RuntimeViewsheet rvs,
      AssetEvent event, VSAssembly assembly, String uri, AssetCommand command,
      List<Object> processed) throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      List<Assembly> list = new ArrayList<>();
      getAssembliesInContainer(vs, assembly, list);

      for(Assembly item : list) {
         VSAssembly child = (VSAssembly) item;
         addDeleteVSObject(rvs, event, child, uri, command);

         if(!processed.contains(child.getAbsoluteName())) {
            loadTableLens(rvs, event, child.getAbsoluteName(), uri, command);
            processed.add(child);
         }
      }
   }

   /**
    * Get all assemblies in container.
    */
   public static void getAssembliesInContainer(Viewsheet vs,
      VSAssembly assembly, List<Assembly> list)
   {
      String[] children = ((ContainerVSAssembly) assembly).getAbsoluteAssemblies();

      for(String item : children) {
         VSAssembly child = vs.getAssembly(item);

         if(child != null) {
            if(child instanceof ContainerVSAssembly) {
               getAssembliesInContainer(vs, child, list);
            }
            else if(child instanceof Viewsheet) {
               Assembly[] assems = ((Viewsheet) child).getAssemblies(true);

               for(Assembly assem : assems) {
                  if(assem != null) {
                     list.add(assem);
                  }
               }
            }

            list.add(child);
         }
      }
   }

   /**
    * Apply shared selection filters on external viewsheets of the same user.
    *
    * @param assembly the specified selection viewsheet assembly.
    * @param hint the specified hint.
    * @param event the specified viewsheet event.
    * @param vs the specified runtime viewsheet.
    * @param command the specified command.
    * @return <tt>true</tt> if refresh viewsheet, <tt>false</tt> otherwise.
    */
   public static boolean processExtSharedFilters(VSAssembly assembly, int hint,
                                                 ViewsheetEvent event,
                                                 RuntimeViewsheet vs,
                                                 AssetCommand command)
   {
      // @by davidd bug1370506649395, Propagate View Changes as well, for
      // example: Calendar "Switch to ..." operations
      if((hint & VSAssembly.OUTPUT_DATA_CHANGED) !=
         VSAssembly.OUTPUT_DATA_CHANGED && (hint & VSAssembly.VIEW_CHANGED) !=
         VSAssembly.VIEW_CHANGED)
      {
         return false;
      }

      // for shared selection, refresh vsobject command should specifield
      // process, add "SHARED_HINT" to make the RefreshVSObjectCommand not
      // equals the original RefreshVSObjectCommand, see bug1247647231402
      command.put("SHARED_HINT", assembly.getAbsoluteName());

      // @davidd bug1364406849572, Process shared filters in other
      // viewsheets. Local shared filters are processed in
      // ViewsheetSandbox.processSelection
      try {
         RuntimeViewsheet[] arr = event.getViewsheetEngine().
            getRuntimeViewsheets(event.getUser());

         for(RuntimeViewsheet rvs : arr) {
            if(rvs == vs) {
               continue;
            }

            rvs.getViewsheetSandbox().processSharedFilters(
               assembly, null, true);
         }
      }
      catch(ConfirmException cex) {
         if(!(cex instanceof ConfirmDataException)) {
            throw cex;
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to process the shared filters", ex);
      }

      command.remove("SHARED_HINT");
      return false;
   }

   /**
    * Check the access permission of the base worksheet.
    * @param vs the specified viewsheet.
    * @param user the specified user.
    * @param rep the specified asset repository.
    * @param action the specified permission.
    * @return <tt>true</tt> if allowed, <tt>false</tt> denied.
    */
   public static boolean checkBaseWSPermission(Viewsheet vs,
                                               Principal user,
                                               AssetRepository rep,
                                               ResourceAction action) {
      try {
         AssetEntry bentry = vs.getBaseEntry();

         if(bentry == null) {
            return true;
         }

         rep.checkAssetPermission(user, bentry, action);
      }
      catch(Exception ex) {
         return false;
      }

      return true;
   }

   /**
    * Check if an assembly may be treated as the source of an embedded table
    * viewsheet assembly.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public static boolean isEmbeddedDataSource(Assembly assembly) {
      return isEmbeddedDataSource(assembly, false, false);
   }

   public static boolean isEmbeddedDataSource(Assembly assembly, boolean checkMirror,
                                              boolean checkSnapshot)
   {
      Assembly base = assembly;

      if(checkMirror && base instanceof TableAssembly) {
         base = XUtil.getBaseTableAssembly((TableAssembly) base);
      }

      if(!(base instanceof EmbeddedTableAssembly)) {
         return false;
      }

      if(checkSnapshot && base instanceof SnapshotEmbeddedTableAssembly) {
         return false;
      }

      EmbeddedTableAssembly embedded = (EmbeddedTableAssembly) base;
      AggregateInfo ainfo = embedded.getAggregateInfo();

      if(ainfo != null && !ainfo.isEmpty()) {
         return false;
      }

      return embedded.isPlain();
   }

   /**
    * Get the viewsheet embedded data of an embedded table assembly.
    * @param table the specified embedded table assembly.
    * @return the viewsheet embedded data, whose headers apply the public
    * column selection.
    */
   public static XEmbeddedTable getVSEmbeddedData(EmbeddedTableAssembly table) {
      XEmbeddedTable data = table.getOriginalEmbeddedData().clone();
      ColumnSelection columns = table.getColumnSelection(true);

      if(data.getRowCount() == 0) {
         return data;
      }

      // replace column headers, and hide invisible columns
      List<Integer> colInx = new ArrayList<>();
      String[] colHeaders = new String[data.getColCount()];

      for(int i = 0; i < data.getColCount(); i++) {
         ColumnRef column = AssetUtil.findColumn(data, i, columns);

         if(column != null) {
            colInx.add(i);
            colHeaders[i] = column.getName();
         }
      }

      data.setHeaders(colHeaders);

      int[] arr = colInx.stream().mapToInt(i -> i).toArray();
      TableLens base = new XTableLens(data);
      base = new ColumnMapFilter(base, arr);

      return new XEmbeddedTable(base);
   }

   /**
    * Sort assembly by name and put primary to the top.
    */
   public static class WSAssemblyComparator implements Comparator {
      public WSAssemblyComparator(Worksheet ws) {
         this.ws = ws;
      }

      @Override
      public int compare(Object v1, Object v2) {
         try {
            String a1;
            String a2;

            if(v1 instanceof String && v2 instanceof String){
               a1 = (String) v1;
               a2 = (String) v2;
            }
            else {
               a1 = ((Assembly) v1).getName();
               a2 = ((Assembly) v2).getName();
            }

            if(a1.equals(ws.getPrimaryAssemblyName())) {
                return -1;
            }
            else if(a2.equals(ws.getPrimaryAssemblyName())) {
               return 1;
            }
            else {
               return a1.compareToIgnoreCase(a2);
            }
         }
         catch(Exception ex) {
            return 0;
         }
      }

      private Worksheet ws;
   }

   /**
    * Case insensitive comparison of node entry names.
    */
   private static class NodeComparator implements Comparator<Node> {
      @Override
      public int compare(Node a, Node b) {
         if(a == null && b == null) {
            return 0;
         }
         else if(a == null) {
            return -1;
         }
         else if(b == null) {
            return 1;
         }

         return compareEntry(a.getEntry(), b.getEntry(), false);
      }
   }

   /**
    * Case insensitive comparison of logic model entity level entry names.
    */
   private static class LMEntityEntryComparator implements Comparator<AssetEntry> {
      @Override
      public int compare(AssetEntry a, AssetEntry b) {
         if(a == null && b == null) {
            return 0;
         }
         else if(a == null) {
            return -1;
         }
         else if(b == null) {
            return 1;
         }

         return compareEntry(a, b, true);
      }
   }

   /**
    * Case insensitive comparison of entry names.
    */
   private static class EntryComparator implements Comparator<AssetEntry> {
      @Override
      public int compare(AssetEntry a, AssetEntry b) {
         if(a == null && b == null) {
            return 0;
         }
         else if(a == null) {
            return -1;
         }
         else if(b == null) {
            return 1;
         }

         return compareEntry(a, b, false);
      }
   }

   /**
    * Case insensitive comparison of entry names.
    */
   private static int compareEntry(AssetEntry entry0, AssetEntry entry1,
                                   boolean isLogicModelEntityLevel) {
      if(isLogicModelEntityLevel) {
         if(entry0.getProperty("isCalc") != null &&
            entry1.getProperty("isCalc") == null)
         {
            return -1;
         }
         else if(entry0.getProperty("isCalc") == null &&
            entry1.getProperty("isCalc") != null)
         {
            return 1;
         }
      }

      String label0 = entry0.getProperty("localStr");
      String label1 = entry1.getProperty("localStr");
      label0 = label0 == null ? entry0.getName() : label0;
      label1 = label1 == null ? entry1.getName() : label1;
      return label0.compareTo(label1);
   }

   /**
    * Add the add warning text command if the query.runtime.maxrow take effect.
    * @param appendToWarningAssembly create a text showing the warning at bottom of viewsheet.
    */
   public static String addWarningText(XTable lens, ViewsheetSandbox box, String vsobj,
                                       boolean appendToWarningAssembly)
   {
      Viewsheet vs = box.getViewsheet();
      ViewsheetInfo vsInfo = vs.getViewsheetInfo();

      if(!vsInfo.isMaxRowsWarning()) {
         return null;
      }

      String mstr = SreeEnv.getProperty("query.runtime.maxrow");
      String tableMaxStr = SreeEnv.getProperty("table.output.maxrow");
      int actualRowCount = lens.getRowCount() - lens.getHeaderRowCount();
      int dmax;
      int tmax;

      try {
         dmax = Integer.parseInt(mstr);
         tmax = Integer.parseInt(tableMaxStr);
      }
      catch(Exception e) {
         return null;
      }

      int amax = 0;
      HashMap maxRowHint = Util.getHintBasedMaxRow(lens);
      Object hintMax = maxRowHint == null ? null : maxRowHint.get(Util.HINT_MAX_ROW_KEY);
      Object baseMaxRow = maxRowHint == null ? null : maxRowHint.get(Util.BASE_MAX_ROW_KEY);
      Object subMaxRow = maxRowHint == null ? null : maxRowHint.get(Util.SUB_MAX_ROW_KEY);
      Catalog catalog = Catalog.getCatalog();

      if(baseMaxRow == null && subMaxRow == null) {
         amax = Util.getAppliedMaxRows(lens);

         if(amax == 0 && vs != null) {
            VSAssembly vsAssembly;
            String vsAssemblyNamePrefix = vs.getAbsoluteName() + ".";

            if(vsobj.startsWith(vsAssemblyNamePrefix) &&
               vsobj.length() > vsAssemblyNamePrefix.length())
            {
               vsAssembly = vs.getAssembly(vsobj.substring(vsAssemblyNamePrefix.length()));
            }
            else {
               vsAssembly = vs.getAssembly(vsobj);
            }

            String tableName = vsAssembly.getTableName();
            Worksheet ws = box.getWorksheet();

            if(ws != null) {
               Assembly table = ws.getAssembly(tableName);

               while(table instanceof MirrorTableAssembly) {
                  MirrorTableAssembly mirrorTable = (MirrorTableAssembly) table;

                  if(mirrorTable.getMaxRows() > 0) {
                     amax = mirrorTable.getMaxRows();
                     break;
                  }

                  table = mirrorTable.getTableAssembly();
               }

               if(amax == 0 && table instanceof TableAssembly &&
                  ((TableAssembly) table).getMaxRows() > 0)
               {
                  amax = ((TableAssembly) table).getMaxRows();

                  if(vsAssembly instanceof CalcTableVSAssembly) {
                     final RuntimeCalcTableLens runtimeCalcTableLens =
                        (RuntimeCalcTableLens) Util.getNestedTable(lens, RuntimeCalcTableLens.class);

                     final Integer scriptTableRowCount = Optional.ofNullable(runtimeCalcTableLens)
                        .map(RuntimeCalcTableLens::getCalcTableLens)
                        .map(CalcTableLens::getScriptTable)
                        .map(l -> l.getRowCount() - l.getHeaderRowCount())
                        .orElse(-1);

                     if(scriptTableRowCount > -1) {
                        actualRowCount = scriptTableRowCount;
                     }
                  }
               }
            }
         }
      }

      if(vs != null && (amax > 0 || baseMaxRow != null || subMaxRow != null || hintMax != null)) {
         if(tmax > 0 && tmax < dmax) {
            mstr = tableMaxStr;
            dmax = Integer.parseInt(mstr);

            if(actualRowCount < dmax) {
               return null;
            }
         }

         AssetEntry entry = vs.getRuntimeEntry();
         String vsName = "";

         if(entry != null) {
            vsName = Tool.isEmptyString(entry.getAlias()) ? entry.getName() : entry.getAlias();
         }
         else {
            vs.getName();
         }

         Assembly assembly = vs.getAssembly(vsobj);

         if(!(assembly instanceof BindableVSAssembly)) {
            return null;
         }

         String source = ((BindableVSAssembly) assembly).getTableName();
         Viewsheet viewsheet = ((BindableVSAssembly) assembly).getViewsheet();
         AssetEntry baseEntry = viewsheet == null ? null : viewsheet.getBaseEntry();

         if(baseEntry != null && !Tool.isEmptyString(baseEntry.getName()) &&
            !Tool.isEmptyString(source))
         {
            source = baseEntry.getName() + "." + source;
         }

         if(source != null && source.startsWith(Assembly.TABLE_VS_BOUND)) {
            source = source.substring(Assembly.TABLE_VS_BOUND.length());
         }

         String objFullNameName = vsName + "." + vsobj;
         String warningMsg = null;
         String logMsg = null;
         MDC.put(LogContext.ASSEMBLY.name(), assembly.getName());

         // 1. current query has maxrow.
         if(baseMaxRow == null && subMaxRow == null) {
            // 2. hint max may be the maxrow from mirror's base table.
            int limitRow = hintMax != null ? (int) hintMax  : (dmax == 0 ? amax : dmax);

            // should show min row limit.
            if(amax > 0 && limitRow > amax) {
               limitRow = amax;
            }

            AssemblyInfo info = assembly.getInfo();
            int vsTableMaxRow = info instanceof TableVSAssemblyInfo ?
               ((TableVSAssemblyInfo) info).getMaxRows() : -1;

            if(vsTableMaxRow > 0 && limitRow > vsTableMaxRow) {
               limitRow = vsTableMaxRow;
            }

            if(actualRowCount < limitRow) {
               return null;
            }

            String tableOutputLimit = catalog.getString("table.output.maxrow.limited");

            if(!Tool.isEmptyString(tableOutputLimit) && tmax > 0 && limitRow == tmax) {
               warningMsg = logMsg = catalog.getString("table.output.maxrow.limited", tmax);
            }
            else if(vsTableMaxRow > -1 && vsTableMaxRow == limitRow) {
               warningMsg = logMsg = catalog.getString(
                  "viewer.viewsheet.table.maxRows.warning", vsobj, vsTableMaxRow);
            }
            else {
               // for the prompt user message, only display the binding source in the message,
               // the assemblies binding same source will share same message
               // to avoid prompt too many messages.
               warningMsg = catalog.getString(
                  "viewer.viewsheet.common.maxRows.warning", source, limitRow);
               // display detail assembly name in the log to provide more detail information for user.
               logMsg = catalog.getString(
                  "viewer.viewsheet.common.maxRows.log", objFullNameName, limitRow);
            }
         }
         // 3. when sql is mergeablem, current query has aggregate or condition, and has maxrow,
         // the maxrow is applied to the base query, so result of the aggregate/condition
         // query will never prompt maxrow limit warning, here we need to check the
         // max row limit for the base query, and prompt for user.
         else if(hintMax != null && baseMaxRow != null) {
            if(actualRowCount < (int) hintMax) {
               return null;
            }

            warningMsg = catalog.getString(
               "viewer.viewsheet.common.maxRows.base.warning", source, hintMax);
            logMsg = catalog.getString(
               "viewer.viewsheet.common.maxRows.base.log", source, hintMax, objFullNameName);
         }
         // 4. maxrow settings for sub queries, like join tables.
         else if(subMaxRow != null) {
            warningMsg = catalog.getString(
               "viewer.viewsheet.common.maxRows.sub.message", source, subMaxRow, vsobj);
            logMsg = catalog.getString(
               "viewer.viewsheet.common.maxRows.sub.message", source, subMaxRow, objFullNameName);
         }

         Tool.addUserMessage(warningMsg);
         LOG.info(logMsg);

         if(appendToWarningAssembly && vsInfo != null && !vsInfo.isScaleToScreen()) {
            TextVSAssembly textAssembly = vs.getWarningTextAssembly();

            if(textAssembly != null && textAssembly.getTextValue() != null &&
               !textAssembly.getTextValue().contains(warningMsg))
            {
               textAssembly.setTextValue(textAssembly.getTextValue() + "\n" + warningMsg);
            }
         }

         if(warningMsg != null) {
            box.setLimitMessage(vsobj, warningMsg);
         }

         return warningMsg;
      }

      return null;
   }

   /**
    * Get the repository entries.
    * @param selector OR'ed value of RepositoryEntry types.
    * @param detailType empty, "viewsheet", or "snapshot".
    */
   public static RepositoryEntry[] getRepositoryEntries(
      AnalyticEngine engine, Principal user, ResourceAction action, int selector,
      String detailType, RepositoryEntry[] pentries, boolean isDefaultOrgAsset)
   {
      List<RepositoryEntry> entrylist = new ArrayList<>();
      RepositoryEntry[] entries;

      if(pentries == null) {
         pentries = new RepositoryEntry[1];
         pentries[0] = new RepositoryEntry("/", RepositoryEntry.FOLDER);
      }

      try {
         for(RepositoryEntry pentry : pentries) {
            entries = engine.getRepositoryEntries(pentry.getPath(), user, action, selector, isDefaultOrgAsset);

            for(RepositoryEntry entry : entries) {
               if(!isDetailTypeMatched(entry, selector, detailType)
                  || entry.getName().equals("Recycle Bin"))
               {
                  continue;
               }

               entrylist.add(entry);
            }
         }
      }
      catch(Exception e) {
         try {
            pentries = new RepositoryEntry[1];
            pentries[0] = new RepositoryEntry("/", RepositoryEntry.FOLDER);
            entries = engine.getRepositoryEntries(pentries[0].getPath(), user, action, selector, isDefaultOrgAsset);
            entrylist = new ArrayList<>();

            for(RepositoryEntry entry : entries) {
               if(!isDetailTypeMatched(entry, selector, detailType)
                  || entry.getName().equals("Recycle Bin")) {
                  continue;
               }

               entrylist.add(entry);
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to get repository entries", ex);
         }
      }

      Collections.sort(entrylist);
      entries = new RepositoryEntry[entrylist.size()];
      entrylist.toArray(entries);

      return entries;
   }

   /**
    * Check whether the detail type mathes.
    * @param detailType empty, "viewsheet", or "snapshot".
    */
   private static boolean isDetailTypeMatched(RepositoryEntry entry,
                                              int selector, String detailType)
   {
      if((selector & RepositoryEntry.VIEWSHEET) == RepositoryEntry.VIEWSHEET &&
          entry instanceof ViewsheetEntry)
      {
         ViewsheetEntry ventry = (ViewsheetEntry) entry;

         if(detailType.equals("")) {
            return true;
         }
         else if(detailType.equals("viewsheet")) {
            return !ventry.isSnapshot();
         }
         else if(detailType.equals("snapshot")) {
            return ventry.isSnapshot();
         }
      }

      return true;
   }

   /**
    * Return a parameter table.
    */
   public static VariableTable decodeParameters(ItemMap params) {
      if(params == null) {
         return null;
      }

      VariableTable vars = new VariableTable();

      Iterator iter = params.itemKeys();

      while(iter.hasNext()) {
         String key = (String) iter.next();
         Object val = params.getItem(key);
         Object value0 = (val instanceof String)
            ? Tool.decodeParameter(Tool.byteDecode(val.toString())) : val;
         Object value = null;

         if(AssetUtil.isSelectionParam(key)) {
            try {
               value = AssetUtil.getSelectionParam(key, value0);
            }
            catch(Exception ex) {
               LOG.warn("Failed to get selection parameter: " + key, ex);
            }
         }

         value = value == null ? value0 : value;
         vars.put(key, value);
      }

      return vars;
   }

   /**
    * Get assembly info from command.
    */
   private static VSAssemblyInfo getInfo(AssetCommand acmd) {
      if(acmd instanceof RefreshVSObjectCommand ||
         acmd instanceof LoadTableLensCommand)
      {
         return (VSAssemblyInfo) acmd.get("info");
      }

      return null;
   }

   /**
    * Check the visibility of assembly in a tab via the assembly info
    * @param info The assembly info to check visibility
    * @return whether or not the assembly is visible.
    */
   public static boolean isVisibleInTab(VSAssemblyInfo info) {
      return isVisibleInTab(info, true);
   }

   /**
    * Check the visibility of assembly in a tab via the assembly info
    * @param info The assembly info to check visibility
    * @param fixSelected true if it should call fixSelected method
    * @return whether or not the assembly is visible.
    */
   public static boolean isVisibleInTab(VSAssemblyInfo info,
                                        boolean fixSelected)
   {
      if(info == null) {
         return false;
      }

      String name = info.getAbsoluteName();
      Viewsheet vs = info.getViewsheet();

      if(vs == null) {
         return true;
      }

      VSAssembly assembly = vs.getAssembly(name);
      return isVisibleTabVS(assembly, fixSelected);
   }

   /**
    * Check whether the assembly is in tabs.
    */
   public static boolean isInTab(VSAssembly obj) {
      if(obj == null) {
         return false;
      }

      List<VSAssembly> tree = new ArrayList<>();
      buildTree(obj, tree);

      for(VSAssembly parent : tree) {
         if(parent != obj && parent instanceof TabVSAssembly) {
            return true;
         }
      }

      return false;
   }

   /**
    * Fix all assemblies' tip or pop object.
    */
   public static void fixTipOrPopAssemblies(RuntimeViewsheet rvs,
                                            AssetCommand cmd)
                                            throws Exception
   {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      Assembly[] assemblies = vs.getAssemblies();

      for(Assembly assembly : assemblies) {
         AssemblyInfo info = assembly.getInfo();

         if(info instanceof TipVSAssemblyInfo) {
            String tip = ((TipVSAssemblyInfo) info).getTipViewValue();

            if(tip == null) {
               continue;
            }

            if(!VSEventUtil.checkTipViewValid((VSAssembly) assembly, vs)) {
               ((TipVSAssemblyInfo) info).setTipViewValue(null);
               VSEventUtil.refreshVSAssembly(rvs, info.getName(), cmd);
            }
         }
         else if(info instanceof PopVSAssemblyInfo) {
            String pop = ((PopVSAssemblyInfo) info).getPopComponentValue();

            if(pop == null) {
               continue;
            }

            if(!VSEventUtil.checkPopComponentValid(
               (VSAssembly) assembly, vs)) {
               ((PopVSAssemblyInfo) info).setPopComponentValue(null);
               VSEventUtil.refreshVSAssembly(rvs, info.getName(), cmd);
            }
         }
      }
   }

   /**
    * Check whether the tip view is valid.
    * @param assembly the assembly to be checked.
    * @param vs the view sheet.
    */
   public static boolean checkTipViewValid(VSAssembly assembly, Viewsheet vs) {
      if(assembly == null) {
         return false;
      }

      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info == null || !(info instanceof TipVSAssemblyInfo)) {
         return false;
      }

      TipVSAssemblyInfo source = (TipVSAssemblyInfo) info;
      String tipName = source.getTipView();
      tipName = tipName == null ? source.getTipViewValue() : tipName;
      VSAssembly tip = vs.getAssembly(tipName);

      if(tip == null) {
         return false;
      }

      VSAssemblyInfo tipInfo = tip.getVSAssemblyInfo();

      if(!isSupportTipType(tipInfo, vs)) {
         return false;
      }

      // can not set part object(s) in container as tip
      if(tip.getContainer() != null) {
         return false;
      }

      // check source object in container and the tip is also in
      // the same container
      VSAssembly container = assembly.getContainer();

      if(container == null) {
         return true;
      }

      if(Tool.equals(tipName, container.getName())) {
         return false;
      }

      ContainerVSAssembly cass = (ContainerVSAssembly) container;
      return cass != null && !isUseSiblingAsTipView(tipName, cass, vs);
   }

   /**
    * Check if use sibling object as data tip.
    * @param tip the tip view name.
    * @param cass the container that the source object in.
    */
   private static boolean isUseSiblingAsTipView(String tip,
      ContainerVSAssembly cass, Viewsheet vs)
   {
      String[] childs = cass.getAssemblies();

      for(String child : childs) {
         if(Tool.equals(child, tip)) {
            return true;
         }

         if(vs.getAssembly(child) instanceof ContainerVSAssembly) {
            if(isUseSiblingAsTipView(tip,
                                     (ContainerVSAssembly) vs.getAssembly(child), vs)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check whether the pop component is valid.
    * @param assembly the assembly to be checked.
    * @param vs the view sheet.
    */
   public static boolean checkPopComponentValid(VSAssembly assembly,
                                                Viewsheet vs) {
      if(assembly == null) {
         return false;
      }

      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info == null || !(info instanceof PopVSAssemblyInfo)) {
         return false;
      }

      PopVSAssemblyInfo source = (PopVSAssemblyInfo) info;
      String popName = source.getPopComponent();
      popName = popName == null ? source.getPopComponentValue() : popName;
      VSAssembly pop = vs.getAssembly(popName);

      if(pop == null) {
         return false;
      }

      // can not set part object(s) in container as pop
      if(pop.getContainer() != null) {
         return false;
      }

      VSAssemblyInfo popInfo = pop.getVSAssemblyInfo();

      if(popInfo instanceof SelectionBaseVSAssemblyInfo &&
         ((SelectionBaseVSAssemblyInfo) popInfo).getShowType() !=
            SelectionVSAssemblyInfo.LIST_SHOW_TYPE) {
         return false;
      }

      // check source object in container and the pop is also in
      // the same container
      VSAssembly container = assembly.getContainer();

      if(container == null) {
         return true;
      }

      if(Tool.equals(popName, container.getName())) {
         return false;
      }

      ContainerVSAssembly cass = (ContainerVSAssembly) container;
      return cass != null && !isUseSiblingAsPopComponent(popName, cass, vs);
   }

   /**
    * Check if use sibling object as pop component.
    * @param cass the container that the source object in.
    */
   private static boolean isUseSiblingAsPopComponent(String pop,
      ContainerVSAssembly cass, Viewsheet vs)
   {
      String[] childs = cass.getAssemblies();

      for(String child : childs) {
         if(Tool.equals(child, pop)) {
            return true;
         }

         if(vs.getAssembly(child) instanceof ContainerVSAssembly) {
            if(isUseSiblingAsPopComponent(pop,
                                          (ContainerVSAssembly) vs.getAssembly(child), vs)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check if is support tip info type.
    */
   private static boolean isSupportTipType(VSAssemblyInfo info, Viewsheet vs) {
      if(info instanceof GroupContainerVSAssemblyInfo) {
         String[] assemblies =
            ((GroupContainerVSAssemblyInfo) info).getAssemblies();

         for(String assembly : assemblies) {
            VSAssembly ass = vs.getAssembly(assembly);

            if(ass == null) {
               return false;
            }

            if(isSupportTipType(ass.getVSAssemblyInfo(), vs)) {
               return true;
            }
         }
      }

      return (info instanceof OutputVSAssemblyInfo ||
         info instanceof DataVSAssemblyInfo ||
         info instanceof GroupContainerVSAssemblyInfo) &&
         !(info instanceof EmbeddedTableVSAssemblyInfo);
   }

   /**
    * Copy format from source assembly to new assembly.
    */
   public static void copyFormat(VSAssembly sass, VSAssembly tass) {
      copyFormat((VSAssemblyInfo) sass.getInfo(),
         (VSAssemblyInfo) tass.getInfo());
   }

   /**
    * Copy format from source info to new info.
    */
   public static void copyFormat(VSAssemblyInfo sinfo, VSAssemblyInfo tinfo) {
      FormatInfo finfo = sinfo.getFormatInfo();
      VSCompositeFormat objfmt = finfo.getFormat(VSAssemblyInfo.OBJECTPATH);
      TableDataPath tpath = new TableDataPath(-1, TableDataPath.TITLE);
      VSCompositeFormat titlefmt = finfo.getFormat(tpath, false);
      copyFormat(objfmt, titlefmt, tinfo);
   }

   /**
    * Copy the object and title format to the new info.
    */
   private static void copyFormat(VSCompositeFormat objfmt,
      VSCompositeFormat titlefmt, VSAssemblyInfo info)
   {
      FormatInfo finfo = info.getFormatInfo();

      if(titlefmt != null) {
         TableDataPath tpath = new TableDataPath(-1, TableDataPath.TITLE);
         VSCompositeFormat tfmt = finfo.getFormat(tpath);
         copyFormat(tfmt.getDefaultFormat(), tfmt.getDefaultFormat());
         copyFormat(tfmt.getUserDefinedFormat(), tfmt.getUserDefinedFormat());
      }

      if(objfmt != null) {
         VSCompositeFormat cfmt = finfo.getFormat(VSAssemblyInfo.OBJECTPATH);
         copyFormat(cfmt.getDefaultFormat(), cfmt.getDefaultFormat());
         copyFormat(cfmt.getUserDefinedFormat(), cfmt.getUserDefinedFormat());
      }
   }

   /**
    * Copy all format setting to this format.
    */
   public static void copyFormat(VSFormat tfmt, XVSFormat sfmt) {
      tfmt.setAlignmentValue(sfmt.getAlignmentValue(), sfmt.isAlignmentDefined());
      tfmt.setBackgroundValue(sfmt.getBackgroundValue(), sfmt.isBackgroundDefined());
      tfmt.setForegroundValue(sfmt.getForegroundValue(), sfmt.isForegroundDefined());
      tfmt.setFontValue(sfmt.getFontValue(), sfmt.isFontDefined());
      tfmt.setFormatValue(sfmt.getFormatValue());
      tfmt.setFormatExtentValue(sfmt.getFormatExtentValue());
      tfmt.setWrappingValue(sfmt.getWrappingValue(), sfmt.isWrappingDefined());
      tfmt.setSpan(sfmt.getSpan());
      tfmt.setAlphaValue(sfmt.getAlphaValue(), sfmt.isAlphaDefined());
      tfmt.setBordersValue(sfmt.getBordersValue(), sfmt.isBordersDefined());
      tfmt.setBorderColorsValue(sfmt.getBorderColorsValue(), sfmt.isBorderColorsDefined());
   }

   /**
    * Copy all format setting to this format.
    */
   public static void copyFormat(VSFormat tfmt, XVSFormat sfmt, boolean border)
   {
      copyFormat(tfmt, sfmt, border, true);
   }

   /**
    * Copy all format setting to this format.
    */
   public static void copyFormat(VSFormat tfmt, XVSFormat sfmt, boolean border,
      boolean isValueDefined)
   {
      tfmt.setAlignmentValue(sfmt.getAlignmentValue(), isValueDefined);
      tfmt.setBackgroundValue(sfmt.getBackgroundValue(), isValueDefined);
      tfmt.setForegroundValue(sfmt.getForegroundValue(), isValueDefined);
      tfmt.setFontValue(sfmt.getFontValue(), isValueDefined);
      tfmt.setFormatValue(sfmt.getFormatValue(), isValueDefined);
      tfmt.setFormatExtentValue(sfmt.getFormatExtentValue(), isValueDefined);
      tfmt.setWrappingValue(sfmt.getWrappingValue(), isValueDefined);
      tfmt.setSpan(sfmt.getSpan());
      tfmt.setAlphaValue(sfmt.getAlphaValue(), isValueDefined);

      if(border) {
         tfmt.setBordersValue(sfmt.getBordersValue(), isValueDefined);
      }

      tfmt.setBorderColorsValue(sfmt.getBorderColorsValue(), isValueDefined);
   }

   /**
    * Add layer command to set all objects.
    */
   public static void addLayerCommand(Viewsheet vs, AssetCommand cmd) {
      ArrayList list = new ArrayList();
      getLayerAssembly(vs, list);
      Assembly[] ass = new Assembly[list.size()];
      list.toArray(ass);
      addLayerCommand(vs, ass, cmd);
   }

   /**
    * Get all assemblies in viewsheet, include the viewsheet in a viewsheet, and
    * the viewsheet's children.
    */
   private static void getLayerAssembly(Viewsheet vs, List<Assembly> list) {
      Assembly[] assemblies = vs.getAssemblies(false, false);
      Arrays.stream(assemblies).filter(a -> !list.contains(a)).forEachOrdered(list::add);
      Arrays.stream(assemblies).
         filter(a -> a instanceof Viewsheet)
         .map(a -> (Viewsheet) a)
         .forEachOrdered(v -> getLayerAssembly(v, list));
   }

   /**
    * Add layer command.
    */
   public static void addLayerCommand(Viewsheet vs, Assembly[] assemblies, AssetCommand cmd) {
      Arrays.sort(assemblies, new ZIndexComparator(true, vs));
      StringBuilder names = new StringBuilder();
      StringBuilder indexes = new StringBuilder();

      for(int i = 0; i < assemblies.length; i++) {
         VSAssembly vsass = (VSAssembly) assemblies[i];

         if(i > 0) {
            names.append("::");
            indexes.append("::");
         }

         names.append(vsass.getAbsoluteName());
         indexes.append(vsass.getZIndex());
      }

      cmd.addCommand(new SetLayerIndexCommand(names.toString(), indexes.toString()));
   }

   /**
    * Adjust the zindex for all the compoents in viewsheet.
    */
   public static void shrinkZIndex(Viewsheet vs, AssetCommand cmd) {
      shrinkZIndex0(vs);
      addLayerCommand(vs, cmd);
   }

   /**
    * Shrink all object in viewsheet, include viewsheet in viewsheet.
    */
   private static void shrinkZIndex0(Viewsheet vs) {
      if(vs == null) {
         return;
      }

      vs.calcChildZIndex();
      Assembly[] assemblies = vs.getAssemblies(false, false);

      for(Assembly assembly : assemblies) {
         if(assembly instanceof Viewsheet) {
            shrinkZIndex0((Viewsheet) assembly);
         }
      }
   }

   /**
    * Update all the children zindex when remove container.
    */
   public static void updateZIndex(Viewsheet vs, Assembly assembly) {
      if(!(assembly instanceof ContainerVSAssembly) || vs == null) {
         return;
      }

      ContainerVSAssembly cass = (ContainerVSAssembly) assembly;
      String[] assemblies = cass.getAssemblies();
      updateZIndex(vs, cass, assemblies);
   }

   /**
    * Update specifield child zindex when remove container assembly or move out
    * of container assembly.
    */
   public static void updateZIndex(Viewsheet vs, ContainerVSAssembly assembly,
      String[] children)
   {
      String name = assembly.getName();
      String prefix = name.contains(".") ?
         name.substring(0, name.indexOf('.') + 1) : "";

      for(String child : children) {
         name = child;
         name = name.contains(".") ? name : prefix + name;
         VSAssembly vsobj = vs.getAssembly(name);

         if(vsobj != null) {
            vsobj.setZIndex(vsobj.getZIndex() + assembly.getZIndex());
         }

         if(vsobj instanceof GroupContainerVSAssembly) {
            GroupContainerVSAssembly group = (GroupContainerVSAssembly) vsobj;
            updateZIndex(vs, group);
         }
      }
   }

   /**
    * Check if an array of data refs is valid for a selection tree.
    * @param tname the specified table name.
    * @param refs an array of data refs.
    * @return <tt>true</tt> if is valid refs, <tt>false</tt> otherwise.
    */
   public static boolean isValidDataRefs(String tname, DataRef[] refs) {
      if(tname == null || !tname.startsWith(Assembly.CUBE_VS) ||
         refs == null || refs.length == 0)
      {
         return true;
      }

      XCube cube = AssetUtil.getCube(null, tname);

      if(cube == null) {
         return true;
      }

      String lastDim = null;

      for(int i = 0; i < refs.length; i++) {
         String dim = refs[i].getEntity();
         String attr = refs[i].getAttribute();

         if(dim == null) {
            String name = refs[i].getName();
            int idx = name.lastIndexOf('.');

            if(idx >= 0) {
               dim = name.substring(0, idx);
               attr = name.substring(idx + 1);
            }
         }

         if(lastDim != null && !lastDim.equals(dim)) {
            return false;
         }

         XDimension dimension = cube.getDimension(dim);

         if(dimension != null) {
            XCubeMember member = dimension.getLevelAt(i);

            if(member != null) {
               if(!attr.equals(member.getName())) {
                  return false;
               }
            }
         }

         lastDim = dim;
      }

      return true;
   }

   /**
    * Update target viewsheet from source.
    * @param source the source viewsheet.
    * @param rtarget the target viewsheet.
    */
   public static void updateViewsheet(Viewsheet source,
      RuntimeViewsheet rtarget, AssetCommand command) throws Exception
   {
      Viewsheet target = rtarget.getViewsheet();

      if(target == null) {
         return;
      }

      Assembly[] assemblies = target.getAssemblies();
      // sort assemblies, first copy container, then copy child, so
      // the container's selection and child visible will be correct
      // fix bug1257130820064
      Arrays.sort(assemblies, new Comparator<Assembly>() {
         @Override
         public int compare(Assembly obj1, Assembly obj2) {
            if(!(obj1 instanceof VSAssembly) || !(obj2 instanceof VSAssembly)) {
               return 0;
            }

            VSAssembly ass1 = (VSAssembly) obj1;
            VSAssembly ass2 = (VSAssembly) obj2;

            if(isParent(ass1, ass2)) {
               return 1;
            }
            else if(isParent(ass2, ass1)) {
               return -1;
            }

            return 0;
         }

         private boolean isParent(VSAssembly child, VSAssembly parent) {
            VSAssembly p = child.getContainer();

            while(p != null) {
               if(p == parent) {
                  return true;
               }

               p = p.getContainer();
            }

            return false;
         }
      });

      for(Assembly assembly1 : assemblies) {
         VSAssembly assembly = (VSAssembly) assembly1;
         String name = assembly.getName();
         VSAssembly nassembly = source == null ? null : source.getAssembly(name);

         target.removeAssembly(assembly.getAbsoluteName(), false);

         if(nassembly != null) {
            VSAssembly cassembly = (VSAssembly) nassembly.clone();

            // @by cehnw, fix bug1258511736132.
            // Embedd viewsheet's assemblies visible property is runtime's.
            // When the embedd viewsheet is drilled, the opened viewsheet's
            // component is cloned from the parent, it could be wrong.
            // I fixed visible property according to the original viewsheet.
            VSAssemblyInfo info = assembly.getVSAssemblyInfo();

            if((info.getVisibleValue().equals("" + VSAssembly.ALWAYS_SHOW) ||
               info.getVisibleValue().equals("show")) && info.isVisible()) {
               cassembly.getVSAssemblyInfo().setVisible(true);
            }

            target.addAssembly(cassembly, false, false);

            if(!assembly.getVSAssemblyInfo().equals(
               cassembly.getVSAssemblyInfo())) {
               refreshVSAssembly(rtarget, cassembly, command);
            }
         }
      }
   }

   /**
    * Get child embedded viewsheet from specified parent viewsheet.
    * @param pvs parent viewsheet.
    * @param entry the specified viewsheet entry.
    */
   public static Viewsheet getViewsheet(Viewsheet pvs, AssetEntry entry) {
      Assembly[] assemblies = pvs.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(!(assembly instanceof Viewsheet)) {
            continue;
         }

         Viewsheet cvs = (Viewsheet) assembly;

         if(entry.equals(cvs.getEntry())) {
            return cvs;
         }
      }

      return null;
   }

   /**
    * Create aggregate info by a table assembly.
    * @param tbl the table assembly.
    * @param nainfo the aggregate info to be fixed.
    * @param oainfo the old aggregate info.
    * @param vs the viewsheet
    * @param excludePreparedCalc exclude wizard created calc fields
    */
   public static void createAggregateInfo(TableAssembly tbl, AggregateInfo nainfo,
                                          AggregateInfo oainfo, Viewsheet vs,
                                          boolean excludePreparedCalc)
   {
      if(tbl == null) {
         return;
      }

      ColumnSelection columns = tbl.getColumnSelection(true);
      XUtil.addDescriptionsFromSource(tbl, columns);
      List<ColumnRef> list = new ArrayList<>();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef cref = (ColumnRef) columns.getAttribute(i);
         if(!(cref instanceof CalculateRef)) {
            list.add(VSUtil.getVSColumnRef(cref));
         }
      }

      // add user create calculate field
      VSUtil.appendCalcFields(list, tbl, vs, false, excludePreparedCalc);

      list.sort(new VSUtil.DataRefComparator());

      for(ColumnRef column : list) {
         GroupRef group = oainfo != null ? oainfo.getGroup(column) : null;

         if(group != null && (group.getDataRef() instanceof ColumnRef) &&
            Tool.equals(group.getDataRef().getDataType(), column.getDataType()))
         {
            nainfo.addGroup(group);
            continue;
         }

         AggregateRef agg = oainfo != null ? oainfo.getAggregate(column) : null;

         if(agg != null && (agg.getDataRef() instanceof ColumnRef) &&
            Tool.equals(agg.getDataRef().getDataType(), column.getDataType()))
         {
            nainfo.addAggregate(agg);
            continue;
         }

         if(isMeasure(column)) {
            AggregateFormula formula = AssetUtil.getDefaultFormula(column);
            nainfo.addAggregate(new AggregateRef(column, formula));
         }
         else {
            nainfo.addGroup(new GroupRef(column));
         }
      }
   }

   /**
    * Check if the ref is measure.
    */
   public static boolean isMeasure(ColumnRef ref) {
      if(VSUtil.isAggregateCalc(ref)) {
         return true;
      }

      int refType = ref.getRefType();
      return refType == DataRef.NONE
         ? AssetUtil.isNumberType(ref.getDataType())
         : (refType & DataRef.MEASURE) != 0;
   }

   /**
    * Fix aggregate info by convert data ref.
    */
   public static boolean fixAggInfoByConvertRef(AggregateInfo ainfo, int type, String refName) {
      if(ainfo == null) {
         return false;
      }

      boolean changed = false;

      if(type == CONVERT_TO_MEASURE) {
         AggregateRef newref = new AggregateRef();
         int index = 0;
         GroupRef[] grefs = ainfo.getGroups();

         for(int i = 0; i < grefs.length; i++) {
            if(Tool.equals(refName, grefs[i].getName())) {
               newref.setDataRef(grefs[i].getDataRef());
               newref.setFormula(AssetUtil.getDefaultFormula(grefs[i]));
               index = i;
               changed = true;
               break;
            }
         }

         if(changed) {
            ainfo.removeGroup(index);
            ainfo.addAggregate(newref);
         }
      }
      else {
         GroupRef newref = new GroupRef();
         int index = 0;
         AggregateRef[] arefs = ainfo.getAggregates();

         for(int i = 0; i < arefs.length; i++) {
            if(Tool.equals(refName, arefs[i].getName())) {
               newref.setDataRef(arefs[i].getDataRef());
               index = i;
               changed = true;
               break;
            }
         }

         if(changed) {
            ainfo.removeAggregate(index);
            ainfo.addGroup(newref);
         }
      }

      return changed;
   }

   /**
    * Fix aggregate info by binding refs.
    * @param ainfo the original aggregate info.
    * @param aggs the refs binding in aggregate.
    * @param groups the refs binding in column/row header.
    */
   public static void fixAggregateInfoByBinding(AggregateInfo ainfo,
                                                DataRef[] aggs,
                                                DataRef[] groups) {
      for(DataRef agg : aggs) {
         String refName;

         if(agg instanceof VSAggregateRef) {
            refName = ((VSAggregateRef) agg).getColumnValue();
         }
         else {
            refName = agg.getName();
         }

         VSEventUtil.fixAggInfoByConvertRef(ainfo,
                                            VSEventUtil.CONVERT_TO_MEASURE, refName);
      }

      for(DataRef group : groups) {
         String refName;

         if(group instanceof VSDimensionRef) {
            refName = ((VSDimensionRef) group).getGroupColumnValue();
         }
         else {
            refName = group.getName();
         }

         VSEventUtil.fixAggInfoByConvertRef(ainfo,
                                            VSEventUtil.CONVERT_TO_DIMENSION, refName);
      }
   }

   /**
    * Get the table assembly by the source info.
    * @param vs the specifield viewsheet, direct parent of the assembly.
    * @param sinfo the source info.
    * @param engine the viewsheet engine.
    * @param user the specifield user.
    */
   public static TableAssembly getTableAssembly(Viewsheet vs, SourceInfo sinfo,
                                                AssetRepository engine,
                                                Principal user) {
      Worksheet baseWS = vs == null ? null : vs.getBaseWorksheet();
      AssetEntry baseEntry = vs == null ? null : vs.getBaseEntry();

      if(vs == null || sinfo == null || engine == null || baseWS == null) {
         return null;
      }

      if(baseEntry != null) {
         boolean r = VSEventUtil.checkBaseWSPermission(vs, user, engine, ResourceAction.READ);
         Assembly[] wasses = r ? baseWS.getAssemblies() : new Assembly[0];

         if(sinfo.getType() == XSourceInfo.VS_ASSEMBLY) {
            return (TableAssembly) baseWS.getAssembly(sinfo.getSource());
         }
         else {
            for(Assembly wass : wasses) {
               if(wass.isVisible() && wass instanceof TableAssembly &&
                  Tool.equals(wass.getName(), VSUtil.getTableName(sinfo.getSource()))) {
                  return (TableAssembly) wass;
               }
            }
         }
      }

      return baseWS.getCubeTableAssembly(sinfo.getSource());
   }

   /**
    * Create the pseudo base table assemblies for the logical model.
    */
   public static List<TableAssembly> createPseudoAssemblies(
      AssetRepository engine, AssetEntry baseEntry, Principal principal)
   {
      AssetEntry[] entries;

      List<AssetEntry> lentries = new ArrayList<>();

      try {
         entries = engine.getEntries(baseEntry, principal, ResourceAction.READ);

         for(AssetEntry entry : entries) {
            lentries.addAll(Arrays.asList(
               engine.getEntries(entry, principal, ResourceAction.READ)));
         }
      }
      catch(Exception e) {
         return new ArrayList<>();
      }

      entries = lentries.toArray(new AssetEntry[] {});
      ColumnSelection selection = new ColumnSelection();
      String prefix = baseEntry.getProperty("prefix");
      String source = baseEntry.getProperty("source");
      String type = baseEntry.getProperty("type");
      SourceInfo sinfo = new SourceInfo(Integer.parseInt(type), prefix, source);
      XLogicalModel lmodel = XUtil.getLogicModel(sinfo, principal);

      for(AssetEntry temp : entries) {
         String entity = temp.getProperty("entity");
         String attr = temp.getProperty("attribute");
         AttributeRef attributeRef = new AttributeRef(entity, attr);
         ColumnRef ref = new ColumnRef(attributeRef);
         ref.setDataType(temp.getProperty("dtype"));
         selection.addAttribute(ref);

         // if logic model, keep ref type
         if(lmodel != null) {
            XEntity xentity = lmodel.getEntity(entity);
            XAttribute xattr = xentity.getAttribute(
               VSUtil.trimEntity(attr, entity));
            attributeRef.setRefType(xattr.getRefType());
            attributeRef.setDefaultFormula(xattr.getDefaultFormula());
            ref.setDescription(xattr.getDescription());
         }
      }

      List<DataRef> columns = new ArrayList<>();

      for(int i = 0; i < selection.getAttributeCount(); i++) {
         columns.add(selection.getAttribute(i));
      }

      /* don't sort it so the order of the column is the same as the
         order in the data model
      Collections.sort(columns, new Comparator<DataRef>() {
         public int compare(DataRef d1, DataRef d2) {
            int en = d1.getEntity().compareToIgnoreCase(d2.getEntity());

            if(en != 0) {
               return en;
            }

            return d1.getAttribute().compareToIgnoreCase(d2.getAttribute());
         }
      });
      */

      List<TableAssembly> assemblies = new ArrayList<>();
      String lastEntity = null;
      ColumnSelection newCols = null;

      for(DataRef ref : columns) {
         if(!Tool.equals(ref.getEntity(), lastEntity)) {
            newCols = new ColumnSelection();
            TableAssembly ably = new BoundTableAssembly(null, ref.getEntity());
            assert lmodel != null;
            ably.setProperty("Tooltip", lmodel.getEntity(ref.getEntity()).getDescription());
            ably.setColumnSelection(newCols, false);
            ably.setColumnSelection(newCols, true);
            assemblies.add(ably);
            lastEntity = ref.getEntity();
         }

         assert newCols != null;
         newCols.addAttribute(ref);
      }

      return assemblies;
   }

   /**
    * Get all parameters of report.
    * @param name the specified report name.
    * @return the array contains the specified report parameters.
    */
   public static String[] getReportParameters(String name, Principal principal) {
//      TODO: remove as part of replet removal
//      RegistryInfo reginfo = RegistryInfo.getRegistryInfo();
//      return reginfo.getReportParameters(name, principal);
      return new String[0];
   }

   /**
    * Sort the columns by name.
    */
   public static ColumnSelection getAggregateColumnSelection(Viewsheet vs, String tname) {
      AggregateRef[] autos = VSUtil.getAggregates(vs, tname, true);
      ColumnSelection selection = new ColumnSelection();

      for(int i = 0; autos != null && i < autos.length; i++) {
         addAggregateRef(autos[i], selection);
      }

      return VSUtil.sortColumns(selection);
   }

   /**
    * Sort the columns by name.
    */
   private static void addAggregateRef(DataRefWrapper ref, ColumnSelection cols) {
      DataRef dref = ref.getDataRef();

      if(!VSUtil.isAggregateCalc(dref)) {
         cols.addAttribute(ref, false);
      }
   }

   /**
    * Fix crosstab aggregate info.
    */
   public static void fixAggregateInfo(CrosstabVSAssembly cass, RuntimeViewsheet rvs,
      AssetRepository repository, Principal user)
   {
      Viewsheet vs = rvs.getViewsheet();
      CrosstabVSAssemblyInfo cinfo = (CrosstabVSAssemblyInfo) cass.getInfo();
      SourceInfo sinfo = cinfo == null ? null : cinfo.getSourceInfo();
      VSCrosstabInfo vsinfo = cinfo == null ? null : cinfo.getVSCrosstabInfo();

      if(cinfo == null || vsinfo == null || sinfo == null) {
         return;
      }

      TableAssembly tbl = null;

      if(sinfo.getType() == SourceInfo.VS_ASSEMBLY) {
         try {
            CrosstabVSAQuery query = new CrosstabVSAQuery(rvs.getViewsheetSandbox(),
                                         cass.getAbsoluteName(), false);
            tbl = query.createAssemblyTable(sinfo.getSource());
         }
         catch(Exception e) {
         }
      }
      else {
         tbl = getTableAssembly(vs, sinfo, repository, user);
      }

      AggregateInfo ainfo = vsinfo.getAggregateInfo();

      if(ainfo == null || ainfo.isEmpty()) {
         ainfo = new AggregateInfo();

         if(tbl != null) {
            createAggregateInfo(tbl, ainfo, null, vs, true);
         }

         vsinfo.setAggregateInfo(ainfo);
      }

      fixBindingAggregateInfo(ainfo, vsinfo);
   }

   private static void fixBindingAggregateInfo(AggregateInfo ainfo,
                                        VSCrosstabInfo cinfo) {
      DataRef[] aggs = cinfo == null ? null : cinfo.getDesignAggregates();
      aggs = aggs == null ? new DataRef[0] : aggs;
      DataRef[] cols = cinfo == null ? null : cinfo.getDesignColHeaders();
      cols = cols == null ? new DataRef[0] : cols;
      DataRef[] rows = cinfo == null ? null : cinfo.getDesignRowHeaders();
      rows = rows == null ? new DataRef[0] : rows;
      DataRef[] groups = new DataRef[cols.length + rows.length];
      System.arraycopy(cols, 0, groups, 0, cols.length);
      System.arraycopy(rows, 0, groups, cols.length, rows.length);
      fixAggregateInfoByBinding(ainfo, aggs, groups);
   }

   /**
    * If adhoc filter exists in viewsheet, before save viewsheet,need group
    * the adhoc filter to container first.
    */
   public static void changeAdhocFilterStatus(RuntimeViewsheet rvs,
      AssetCommand command)
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      Assembly[] assems = vs.getAssemblies();
      String target = null;

      for(Assembly assem : assems) {
         if(assem instanceof CurrentSelectionVSAssembly) {
            target = assem.getAbsoluteName();

            break;
         }
      }

      if(target != null) {
         for(Assembly assem : assems) {
            if(assem instanceof AbstractSelectionVSAssembly) {
               AbstractSelectionVSAssembly ass =
                  (AbstractSelectionVSAssembly) assem;

               VSAssemblyInfo info = ass.getVSAssemblyInfo();

               if(!(info instanceof SelectionVSAssemblyInfo) ||
                  !((SelectionVSAssemblyInfo) info).isAdhocFilter()) {
                  continue;
               }

               String name = assem.getAbsoluteName();
               VSLayoutEvent levent = new VSLayoutEvent(name, null, target,
                                                        VSLayoutEvent.GROUP, null, false, -1);
               levent.put("adhocFilter", "true");

               try {
                  levent.process(rvs, command);
               }
               catch(Throwable ex) {
                  LOG.error("Failed to process viewsheet layout event", ex);
               }
            }
         }
      }
   }

   /**
    * Use the old viewsheet state to update the new viewsheet state.
    * @param ovs the specified old viewsheet.
    * @param runtime <tt>true</tt> if runtime, <tt>false</tt> otherwise.
    * @param nvs the specified old viewsheet.
    */
   public static void updateViewsheet(Viewsheet ovs, boolean runtime,
      Viewsheet nvs) throws Exception
   {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "utf-8"));
      ovs.writeState(writer, runtime);
      writer.close();

      byte[] bytes = out.toByteArray();
      ByteArrayInputStream in = new ByteArrayInputStream(bytes);
      Document doc = Tool.parseXML(in);
      Element elem = doc.getDocumentElement();
      boolean fireEvent = nvs.isFireEvent();
      nvs.setFireEvent(false);
      nvs.parseState(elem, runtime);
      nvs.setFireEvent(fireEvent);
   }

   /**
    * Delete auto saved file.
    * 1 for saved dashboard, keep old logic, delete auto save file when select no.
    * 2 for untitled dashboard, select no will move auto save file to recycle bin.
    */
   public static void deleteAutoSavedFile(AssetEntry entry, Principal user) {
      if(entry.getScope() != AssetRepository.TEMPORARY_SCOPE) {
         AutoSaveUtils.deleteAutoSaveFile(entry, user);
         return;
      }

      String savefile = AutoSaveUtils.getAutoSavedFile(entry, user);

      if(AutoSaveUtils.exists(savefile, user)) {
         String recyclefile = AutoSaveUtils.getAutoSavedFile(entry, user, true);
         AutoSaveUtils.renameAutoSaveFile(savefile, recyclefile, user);
      }
   }

   public static void removeVSObject(RuntimeViewsheet rvs, String assembly,
                                     CommandDispatcher dispatcher) {
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly vsAssembly = vs.getAssembly(assembly);
      vs.removeAssembly(assembly);
      // TODO rename command or create new VSEventUtil for 12.3
      inetsoft.web.viewsheet.command.RemoveVSObjectCommand command =
         new inetsoft.web.viewsheet.command.RemoveVSObjectCommand();
      command.setName(assembly);
      ContainerVSAssembly container = (ContainerVSAssembly) vsAssembly.getContainer();

      if(container != null) {
         container.removeAssembly(assembly);
      }

      if(container instanceof CurrentSelectionVSAssembly) {
         // If vsAssembly is in selection container, send event to selection container
         // to refresh child object
         dispatcher.sendCommand(container.getAbsoluteName(), command);
      }
      else {
         dispatcher.sendCommand(command);
      }
   }

   public static double calcCenterStart(double width1, double width2) {
      return 1.0 * Math.abs(width1 - width2) / 2;
   }

   /**
    * Get the applied alias asset path.
    * @return
    */
   public static String getViewhseetAssetPath(AssetEntry entry, AssetRepository assetRepository)
      throws Exception
   {
      entry = assetRepository.getAssetEntry(entry);

      if(entry == null) {
         return "";
      }

      if(!entry.isViewsheet()) {
         return entry.getPath();
      }

      AssetEntry pEntry = entry.getParent();

      if(pEntry != null) {
         String pPath = getFolderEntryAliasPath(assetRepository.getAssetEntry(pEntry),
            assetRepository);

         if(Tool.isEmptyString(pPath)) {
            return Tool.isEmptyString(entry.getAlias()) ? entry.getName() : entry.getAlias();
         }
         else {
            return pPath + "/" +
               (Tool.isEmptyString(entry.getAlias()) ? entry.getName() : entry.getAlias());
         }
      }
      else {
         return Tool.isEmptyString(entry.getAlias()) ? entry.getName() : entry.getAlias();
      }
   }

   private static String getFolderEntryAliasPath(AssetEntry entry, AssetRepository assetRepository)
      throws Exception
   {
      if(entry == null || entry.isRoot()) {
         return "";
      }

      AssetEntry pEntry = entry.getParent();

      if(pEntry == null || pEntry.isRoot()) {
         return Tool.isEmptyString(entry.getAlias()) ? entry.getPath() : entry.getAlias();
      }

      pEntry = assetRepository.getAssetEntry(pEntry);

      return getFolderEntryAliasPath(pEntry, assetRepository) + "/" +
         (Tool.isEmptyString(entry.getAlias()) ? entry.getName() : entry.getAlias());
   }

   private static final Logger LOG = LoggerFactory.getLogger(VSEventUtil.class);
}
