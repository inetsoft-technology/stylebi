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
package inetsoft.web.composer.ws;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.DependencyComparator;
import inetsoft.util.Catalog;
import inetsoft.util.OrderedMap;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.command.WSFinishPasteWithCutCommand;
import inetsoft.web.composer.ws.event.WSPasteAssembliesEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class PasteAssembliesController extends WorksheetController {
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/paste-assemblies")
   public void pasteAssemblies(
      @Payload WSPasteAssembliesEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      this.event = event;
      this.principal = principal;
      this.nameMap = new OrderedMap<>();
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      RuntimeWorksheet srws = super.getWorksheetEngine()
         .getWorksheet(event.getSourceRuntimeId(), principal);

      Worksheet ws = rws.getWorksheet();
      String[] names = event.getAssemblies();
      int x = event.getLeft();
      int y = event.getTop();
      Worksheet sws = srws.getWorksheet();
      Point topLeft = getTopLeft(sws, names);
      String[] nnames = getSortedAvailableNames(rws, names);
      // current worksheet primary
      String tprimary = ws.getPrimaryAssemblyName();
      // source worksheet primary
      String sprimary = null;
      copyOuterEntry(rws, srws, nnames, commandDispatcher, x, y, topLeft);

      for(int i = 0; i < nnames.length; i++) {
         copyAssembly(rws, nnames[i], x, y, topLeft.x, topLeft.y, false);

         if(nnames[i].equals(sws.getPrimaryAssemblyName())) {
            sprimary = nnames[i];
         }
      }

      // sort the oldName -> newName Map to avoid the rename dependencies error.
      // for example: a1 -> a, a2 -> a1. original relation is table dependency table a,
      // after copy should be table dependency a1 table, but result is table dependency a2 table.
      nameMap.sort((a, b) -> {
         boolean containsValue1 = nameMap.containsValue(a);
         boolean containsValue2 = nameMap.containsValue(b);

         if(containsValue1 && !containsValue2) {
            return 1;
         }
         else if(!containsValue1 && containsValue2) {
            return -1;
         }
         else {
            return nameMap.indexOf((String) a) - nameMap.indexOf((String) b);
         }
      });

      update(rws, tprimary, sprimary, commandDispatcher);
      promptUnSupportedMessage(nnames, names, commandDispatcher);
      WorksheetEventUtil.refreshVariables(
         rws, super.getWorksheetEngine(), commandDispatcher, false);

      // remove process by its own context, so that the undo and redo status
      // will be correct
      // remove assemblies in source worksheet
      if(isCut() && !isSame(rws)) {
         WSFinishPasteWithCutCommand.Builder builder = WSFinishPasteWithCutCommand.builder();
         builder.sourceSheetId(srws.getID());
         builder.assemblies(Arrays.stream(names).collect(Collectors.toList()));
         commandDispatcher.sendCommand(builder.build());
      }
   }


   private void copyOuterEntry(
      RuntimeWorksheet rws, RuntimeWorksheet srws,
      String[] nnames, CommandDispatcher commandDispatcher,
      int x, int y, Point pos)
      throws Exception
   {
      if(isSame(rws)) {
         return;
      }

      Worksheet ws = rws.getWorksheet();
      List<AssetEntry> outer = new ArrayList<>(
         Arrays.asList(ws.getOuterDependents()));
      Worksheet sws = srws.getWorksheet();
      // source worksheet entry
      AssetEntry sentry = srws.getEntry();
      // current worksheet entry
      AssetEntry entry = rws.getEntry();
      outer.add(entry);
      outer.add(sentry);
      int len = sws.getAssemblies().length;

      for(int i = 0; i < nnames.length; i++) {
         Assembly ass = sws.getAssembly(nnames[i]);

         if(ass == null) {
            continue;
         }

         if(ass instanceof MirrorAssembly &&
            ((MirrorAssembly) ass).isOuterMirror())
         {
            AssetEntry tentry = ((MirrorAssembly) ass).getEntry();
            MirrorAssembly mass = (MirrorAssembly) ass;

            if(tentry == null || outer.contains(tentry)) {
               continue;
            }

            outer.add(entry);
            String prefix = AssetUtil.createPrefix(tentry);

            // do not use AssetUtil.copyOuterAssemblies, cause the mirror
            // outer assembly may be not auto update
            for(int j = 0; j < len; j++) {
               WSAssembly wass = (WSAssembly) sws.getAssembly(prefix + j);

               if(wass != null && wass.isOuter() &&
                  !ws.containsAssembly(prefix + j))
               {
                  WSAssembly nass = copyAssembly(rws, wass.getName(), x, y,
                                                 pos.x, pos.y, true);
               }
            }
         }
      }
   }

   /**
    * Get the top left point of the specified assemblies.
    */
   private Point getTopLeft(Worksheet ws, String[] names) {
      if(names.length == 0) {
         return new Point(0, 0);
      }

      Point point = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE);

      for(int i = 0; i < names.length; i++) {
         AbstractWSAssembly assembly = (AbstractWSAssembly) ws.getAssembly(names[i]);

         if(assembly != null) {
            Point pos = assembly.getPixelOffset();
            point.x = Math.min(point.x, pos.x);
            point.y = Math.min(point.y, pos.y);
         }
      }

      return point;
   }

   /**
    * Copy one assembly.
    */
   private WSAssembly copyAssembly(
      RuntimeWorksheet rws, String name, int x,
      int y, int left, int top, boolean outer)
      throws Exception
   {
      Worksheet ws = rws.getWorksheet();
      // self worksheet entry
      Worksheet sws = getSRWS().getWorksheet();
      AbstractWSAssembly assembly = (AbstractWSAssembly) sws.getAssembly(name);
      Point pos = assembly.getPixelOffset();
      x += pos.x - left;
      y += pos.y - top;

      String nname = getNewName(rws, name);
      nameMap.put(nname, name);
      AbstractWSAssembly nassembly = getNewAssembly(rws, assembly, nname);
      nassembly.setOuter(outer);

      if(!isSame(rws) && isDependNamedGroup(sws, assembly) &&
         nassembly instanceof TableAssembly)
      {
         ((TableAssembly) nassembly).getAggregateInfo().clear();
      }

      if(outer) {
         nassembly.setPixelSize(new java.awt.Dimension(AssetUtil.defw, AssetUtil.defh));
      }

      nassembly.setWorksheet(ws);
      nassembly.setPixelOffset(new Point(x, y));

      // copy or cut to another worksheet, should add the assembly to
      // target worksheet
      if(!isCut() || !isSame(rws)) {
         ws.addAssembly(nassembly);
      }

      nassembly.update();
      nassembly.setOldName(null);

      return nassembly;
   }

   /**
    * Update primary and depending information.
    */
   private void update(
      RuntimeWorksheet rws, String tprimary, String sprimary,
      CommandDispatcher commandDispatcher)
      throws Exception
   {
      Enumeration e = nameMap.keys();
      Worksheet ws = rws.getWorksheet();

      while(e.hasMoreElements()) {
         String nname = (String) e.nextElement();
         String oname = (String) nameMap.get(nname);

         // update primary
         if(tprimary == null && sprimary != null && sprimary.equals(oname)) {
            ws.setPrimaryAssembly(nname);
         }

         if((nname + "").equals(oname)) {
            continue;
         }

         Enumeration e2 = nameMap.keys();

         while(e2.hasMoreElements()) {
            renameDependence(rws, nname, oname, (String) e2.nextElement(),
                             commandDispatcher);
         }
      }

      e = nameMap.keys();
      List<String> pastedAssemblies = new ArrayList<>();

      while(e.hasMoreElements()) {
         String nname = (String) e.nextElement();
         pastedAssemblies.add(nname);
         WSAssembly assembly = (WSAssembly) ws.getAssembly(nname);
         WorksheetEventUtil.createAssembly(rws, assembly, commandDispatcher, principal);
         WorksheetEventUtil.loadTableData(rws, nname, false, false);
         WorksheetEventUtil.layout(rws, commandDispatcher);

         if(ws.getAssembly(nname) instanceof DateRangeAssembly) {
            WorksheetEventUtil.refreshDateRange(ws);
         }
      }

      WorksheetEventUtil.focusAssemblies(pastedAssemblies, commandDispatcher);
   }

   /**
    * Rename the dependence.
    *
    * @param rws   the worksheet.
    * @param nname the dependent assembly's new name.
    * @param oname the dependent assembly's old name.
    * @param rname the name for the assembly will be renamed.
    */
   private void renameDependence(
      RuntimeWorksheet rws, String nname,
      String oname, String rname, CommandDispatcher commandDispatcher)
   {
      Worksheet ws = rws.getWorksheet();
      Assembly assembly = ws.getAssembly(nname);
      Assembly rassembly = ws.getAssembly(rname);
      Set<AssemblyRef> set = new HashSet<>();
      rassembly.getDependeds(set);

      for(AssemblyRef ref : set) {
         if(oname.equals(ref.getEntry().getAbsoluteName())) {
            rassembly.renameDepended(oname, nname);
            break;
         }
      }
   }

   /**
    * Get names are available for cut and copy, and the names are sorted by
    * dependency relations.
    */
   private String[] getSortedAvailableNames(RuntimeWorksheet rws, String[] names) throws Exception {
      Worksheet ws = getSRWS().getWorksheet();
      Set<String> nameset = new HashSet<>();

      // build nameset
      for(int i = 0; i < names.length; i++) {
         nameset.add(names[i]);
      }

      Assembly[] asses = getAssemblies(ws, names);
      // sort by dependency desc, first check child support copy/cut, then
      // check parent support copy/cut
      Arrays.sort(asses, new DependencyComparator(ws, false));
      String[] nnames = getNames(asses);
      List<String> list = new ArrayList<>();
      removeUnavailableName(rws, ws, nameset, nnames, list);
      asses = getAssemblies(ws, list.toArray(new String[list.size()]));
      // sort by dependency asc, first copy/cut parent, then copy/cut child
      Arrays.sort(asses, new DependencyComparator(ws, true));
      nnames = getNames(asses);
      return nnames;
   }

   /**
    * Remove those assemblies which can not be cut or copied.
    */
   private void removeUnavailableName(
      RuntimeWorksheet rws, Worksheet ws,
      Set nameset, String[] nnames, List<String> list)
   {
      List<String> list0 = new ArrayList<>();
      AssetEntry entry = rws.getEntry();

      // except not avaliable names
      for(int i = 0; i < nnames.length; i++) {
         Assembly assembly = ws.getAssembly(nnames[i]);

         // @by davyc, if a copied assembly is outer assembly from current
         // worksheet, ignore it, because we do not know the really source
         // outer assembly if the mirror assembly is not auto update
         if(!isSame(rws) && assembly != null &&
            !isDependNamedGroup(ws, assembly) &&
            (fromSelf(entry, assembly) || hasDepended(assembly, ws, nameset)))
         {
            nameset.remove(nnames[i]);
            continue;
         }

         list0.add(nnames[i]);
      }

      String[] escSortedNames = list0.toArray(new String[list0.size()]);
      Assembly[] asses = getAssemblies(ws, escSortedNames);
      Arrays.sort(asses, new DependencyComparator(ws, true));
      String[] ascSortedNames = getNames(asses);
      List<String> removed = new ArrayList<>();
      checkDependent(rws, ws, nameset, escSortedNames,
                     ascSortedNames, removed);

      for(int i = 0; i < list0.size(); i++) {
         String name = list0.get(i);

         if(removed.contains(name)) {
            continue;
         }

         list.add(name);
      }
   }

   /**
    * Check this depended assembly is NamedGroupAssembly.
    */
   private boolean isDependNamedGroup(Worksheet ws, Assembly assembly) {
      AssemblyRef[] arr = ws.getDependeds(assembly.getAssemblyEntry());

      for(int i = 0; i < arr.length; i++) {
         Assembly ass = ws.getAssembly(arr[i].getEntry());

         if(ass instanceof NamedGroupAssembly) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check a assembly is outer mirror and it is from a specified entry.
    */
   private boolean fromSelf(AssetEntry entry, Assembly assembly) {
      return (assembly instanceof MirrorAssembly) &&
         ((MirrorAssembly) assembly).isOuterMirror() &&
         entry.equals(((MirrorAssembly) assembly).getEntry());
   }

   /**
    * Check every selected assembly's depended relation(current assembly
    * depended by others).
    *
    * @param escSortedNames selected assemblies' names ordered
    *                       from children to parent(if dependent exists).
    * @param ascSortedNames selected assemblies' names ordered
    *                       from parent to children(if dependent exists).
    * @param removed        list contains names of those assemblies to be removed.
    */
   private void checkDependent(
      RuntimeWorksheet rws, Worksheet ws,
      Set nameset, String[] escSortedNames,
      String[] ascSortedNames, List<String> removed)
   {
      if(nameset.size() == 0) {
         return;
      }

      for(int i = 0; i < ascSortedNames.length; i++) {
         String name = ascSortedNames[i];
         Assembly assembly = ws.getAssembly(name);

         if(removed.contains(name)) {
            continue;
         }

         // cut, not same worksheet, some depend on self?
         if(isCut() && !isSame(rws) && assembly != null &&
            AssetEventUtil.hasDependent(assembly, ws, nameset))
         {
            nameset.remove(name);
            removed.add(name);
            // fix bug1257229826861,every time we remove an assembly, we should
            // check depended or dependent relation of other assemblies
            checkDepended(rws, ws, nameset, escSortedNames,
                          ascSortedNames, removed);
         }
      }
   }

   /**
    * Check every selected assembly's dependent relation(current assembly
    * depending on others).
    *
    * @param escSortedNames selected assemblies' names ordered
    *                       from children to parent(if dependent exists).
    * @param ascSortedNames selected assemblies' names ordered
    *                       from parent to children(if dependent exists).
    * @param removed        list contains names of those assemblies to be removed.
    */
   private void checkDepended(
      RuntimeWorksheet rws, Worksheet ws,
      Set nameset, String[] escSortedNames,
      String[] ascSortedNames, List<String> removed)
   {
      if(nameset.size() == 0) {
         return;
      }

      for(int i = 0; i < escSortedNames.length; i++) {
         String name = escSortedNames[i];

         if(removed.contains(name)) {
            continue;
         }

         Assembly assembly = ws.getAssembly(name);

         if(!isSame(rws) && assembly != null &&
            hasDepended(assembly, ws, nameset))
         {
            nameset.remove(name);
            removed.add(name);
            // fix bug1257229826861,every time we remove an assembly, we should
            // check depended or dependent relation of other assemblies
            checkDependent(rws, ws, nameset, escSortedNames,
                           ascSortedNames, removed);
         }
      }
   }

   /**
    * Get assemblies by names.
    */
   private Assembly[] getAssemblies(Worksheet ws, String[] names) {
      return Arrays.stream(names).map(name -> ws.getAssembly(name)).filter(a -> a != null)
         .toArray(Assembly[]::new);
   }

   /**
    * Get names by assemblies.
    */
   private String[] getNames(Assembly[] asses) {
      String[] names = new String[asses.length];

      for(int i = 0; i < names.length; i++) {
         names[i] = asses[i].getAbsoluteName();
      }

      return names;
   }

   /**
    * Get new name for assembly when cut/copy.
    */
   private String getNewName(RuntimeWorksheet rws, String name) {
      Worksheet ws = rws.getWorksheet();

      // in same worksheet
      if(isSame(rws)) {
         if(isCut()) {
            return name;
         }
         else {
            return AssetUtil.getNextName(ws, "Copy of " + name);
         }
      }
      else {
         return ws.containsAssemblyIgnoreCase(name) ? AssetUtil.getNextName(ws, name) : name;
      }
   }

   /**
    * Get new assembly for cut/copy.
    */
   private AbstractWSAssembly getNewAssembly(
      RuntimeWorksheet rws, AbstractWSAssembly assembly,
      String nname)
   {
      if(isCut() && isSame(rws)) {
         return assembly;
      }
      else {
         AbstractWSAssembly nassembly = (AbstractWSAssembly) assembly.clone();

         if(nassembly instanceof VariableAssembly) {
            ((VariableAssembly) nassembly).setName(nname, true);
         }
         else {
            nassembly.getWSAssemblyInfo().setName(nname);
         }

         nassembly.pasted();

         return nassembly;
      }
   }

   /**
    * Check if is cut action.
    */
   private boolean isCut() {
      return event.getCut();
   }

   /**
    * Check if cut/copy in same worksheet.
    */
   private boolean isSame(RuntimeWorksheet rws) {
      return super.getRuntimeViewsheetRef().getRuntimeId()
         .equals(event.getSourceRuntimeId());
   }

   /**
    * Get source runtime worksheet.
    */
   private RuntimeWorksheet getSRWS() throws Exception {
      return super.getWorksheetEngine()
         .getWorksheet(event.getSourceRuntimeId(), principal);
   }

   /**
    * Prompt unsupport cut and copy message if nessary.
    */
   private void promptUnSupportedMessage(
      String[] nnames, String[] allNames,
      CommandDispatcher commandDispatcher)
   {
      // message that some assemblies not support cut/copy
      if(nnames.length < allNames.length) {
         String skip = "";

         for(int i = 0; i < allNames.length; i++) {
            if(Arrays.binarySearch(nnames, allNames[i]) < 0) {
               skip += (skip.length() > 0 ? ", " : "") + allNames[i];
            }
         }

         if(skip.length() > 0) {
            MessageCommand command = new MessageCommand();
            command.setMessage(Catalog.getCatalog().
               getString("assembly.invalidCopyCut", skip));
            command.setType(MessageCommand.Type.ERROR);
            commandDispatcher.sendCommand(command);
         }
      }
   }

   /**
    * Check this assembly depend on other assembly, but the assembly is not
    * in the nameset, this function will ignore the variable assembly dependent.
    */
   private boolean hasDepended(Assembly assembly, Worksheet ws, Set nameset) {
      if(assembly == null) {
         return false;
      }

      AssemblyRef[] arr = ws.getDependeds(assembly.getAssemblyEntry());

      for(int i = 0; i < arr.length; i++) {
         String name = arr[i].getEntry().getName();
         Assembly ass = ws.getAssembly(name);

         // ignore outer depend(outer entry will be added),
         // ignore variable depend(variable will not be renamed in condition)
         if(assembly instanceof MirrorAssembly &&
            ((MirrorAssembly) assembly).isOuterMirror() ||
            ass instanceof VariableAssembly)
         {
            continue;
         }

         if(!nameset.contains(arr[i].getEntry().getName())) {
            return true;
         }

         Assembly dep = ws.getAssembly(arr[i].getEntry().getName());

         if(hasDepended(dep, ws, nameset)) {
            return true;
         }
      }

      return false;
   }

   private Principal principal;
   private OrderedMap<String, String> nameMap;
   // self worksheet primary assembly and dependings
   private Assembly[] passemblies;
   private WSPasteAssembliesEvent event;
}
