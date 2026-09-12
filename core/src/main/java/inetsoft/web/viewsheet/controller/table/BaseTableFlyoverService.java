/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.viewsheet.controller.table;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.execution.VSAQuery;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.event.table.FlyoverEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
@ClusterProxy
public class BaseTableFlyoverService extends BaseTableService<FlyoverEvent> {
   public BaseTableFlyoverService(CoreLifecycleService coreLifecycleService,
                                  ViewsheetService viewsheetService)
   {
      super(coreLifecycleService, viewsheetService);
   }

   @Override
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void eventHandler(@ClusterProxyKey String runtimeId,
                            FlyoverEvent event,
                            Principal principal,
                            CommandDispatcher dispatcher,
                            String linkUri) throws Exception
   {

      // get event properties
      String name = event.getAssemblyName();
      Map<Integer, int[]> selectedCells = event.getSelectedCells();

      // get runtime viewsheet and assembly
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      if(box.isEmpty()) {
         return null;
      }

      Viewsheet viewsheet = box.get().getViewsheet();

      box.get().lockRead();

      try {
         VSAssembly comp = viewsheet.getAssembly(name);
         ConditionList clist = null;

         // If row or col (or both) are less than 0 then use an
         // empty condition list to clear the flyover
         // If map is empty then clear
         if(!selectedCells.isEmpty()) {
            if(comp instanceof TableVSAssembly) {
               clist = this.processTable((TableVSAssembly) comp, box.get(), name, selectedCells);
            }
            else if(comp instanceof CrosstabVSAssembly) {
               clist = this.processCrosstab(box.get(), (CrosstabVSAssembly) comp, name, selectedCells);
            }
            else if(comp instanceof CalcTableVSAssembly) {
               clist = createCalcTableConditions(
                  (CalcTableVSAssembly) comp, box.get(), name, selectedCells, null, dispatcher);
            }
         }
         else {
            clist = new ConditionList();
         }

         Worksheet ws = box.get().getWorksheet();

         // applyFlyovers() manages its own lock scoping internally (see its doc comment and
         // applyFlyoversLocked()/doApplyFlyovers()): it needs to be called while this thread
         // still holds the sandbox read lock acquired above, so that the condition-list
         // mutation it performs stays protected by that lock.
         applyFlyovers(name, comp, rvs, ws, clist, linkUri, dispatcher);
      }
      finally {
         box.get().unlockRead();
      }

      return null;
   }



   /**
    * Get condition list for VSTable
    */
   private ConditionList processTable(TableVSAssembly comp,
                                      ViewsheetSandbox box,
                                      String name,
                                      Map<Integer, int[]> selectedCells) throws Exception
   {
      TableLens lens = (TableLens) box.getData(name);
      ConditionList clist = new ConditionList();

      for(Map.Entry<Integer, int[]> entry : selectedCells.entrySet()) {
         int row = entry.getKey();
         int[] cols = entry.getValue();

         for(int col : cols) {
            clist = TableConditionUtil.createTableConditions(comp, clist, row, col, lens, true);
         }
      }

      return clist;
   }

   /**
    * Get condition list for VSCrosstab
    */
   private ConditionList processCrosstab(ViewsheetSandbox box,
                                         CrosstabVSAssembly comp,
                                         String name,
                                         Map<Integer, int[]> selectedCells) throws Exception
   {
      if(box == null) {
         return new ConditionList();
      }

      TableLens lens = (TableLens) box.getData(name);
      VSCrosstabInfo crosstabInfo = comp.getVSCrosstabInfo();
      DataRef[] rheaders = crosstabInfo.getRuntimeRowHeaders();
      DataRef[] cheaders = crosstabInfo.getRuntimeColHeaders();
      boolean period = crosstabInfo.getHeaderColCountWithPeriod() >
         crosstabInfo.getHeaderColCount();
      int offset = period ? 1 : 0;
      SourceInfo sinfo = comp.getSourceInfo();
      String cubeType = VSUtil.getCubeType(sinfo.getPrefix(), sinfo.getSource());
      boolean xmla = XCube.SQLSERVER.equals(cubeType) ||
         XCube.MONDRIAN.equals(cubeType);
      ConditionList rowConds = new ConditionList();
      ConditionList colConds = new ConditionList();

      for(Map.Entry<Integer, int[]> entry : selectedCells.entrySet()) {
         int row = entry.getKey();
         int[] cols = entry.getValue();

         for(int col : cols) {
            TableDataPath path = lens.getDescriptor().getCellDataPath(row, col);

            if(path == null || path.getType() != TableDataPath.SUMMARY &&
               path.getType() != TableDataPath.GRAND_TOTAL &&
               path.getType() != TableDataPath.GROUP_HEADER)
            {
               continue;
            }

            TableConditionUtil.createCrosstabConditions(
               comp, colConds, cheaders, col, lens, true, cubeType, 0, path, xmla, 1);
            TableConditionUtil.createCrosstabConditions(
               comp, rowConds, rheaders, row, lens, false, cubeType, offset, path, xmla, 1);
            rowConds.trim();
            colConds.trim();

            if(rowConds.getSize() > 0) {
               rowConds.append(new JunctionOperator(JunctionOperator.OR, 1));
            }

            if(colConds.getSize() > 0) {
               colConds.append(new JunctionOperator(JunctionOperator.OR, 1));
            }
         }
      }

      rowConds.trim();
      colConds.trim();

      if(rowConds.getSize() > 0) {
         rowConds.append(new JunctionOperator(JunctionOperator.AND, 0));
      }

      for(int i = 0; i < colConds.getSize(); i++) {
         rowConds.append(colConds.getItem(i));
      }

      rowConds = VSAQuery.replaceGroupValues(rowConds, comp, true);
      rowConds.trim();
      return rowConds;
   }

   /**
    * Execute the runtime viewsheet with the given condition list.
    *
    * Two flyover requests (e.g. from two different source assemblies that both fly over the
    * same target, or whose source tables coincide) can otherwise run this concurrently on
    * different threads, racing on the target's query state and on the source table's
    * pre-runtime condition list save/restore. Serialize per touched assembly (narrower than
    * a full per-runtimeId lock) so unrelated flyovers on the same viewsheet are not blocked.
    *
    * Callers must call this method while still holding the sandbox's own read lock: the
    * condition-list mutation performed below (applyCondition(), and the final
    * setPreRuntimeConditionList() restore) must run under that lock so it cannot interleave
    * with a concurrent sandbox write-lock holder unrelated to flyovers (e.g. a script-triggered
    * refresh or a binding/condition edit) — see doApplyFlyovers().
    *
    * Internally, this method's own lock scoping (applyFlyoversLocked()/doApplyFlyovers()) drops
    * that read lock — via ViewsheetSandbox.unlockAll()/restoreLocks() — only for the narrow
    * windows where it must: while this thread is acquiring (or blocked acquiring) the
    * per-target flyoverLock monitors below, and around the two calls inside doApplyFlyovers()
    * that can reach ViewsheetSandbox.lockWrite() (executeView(), coreLifecycleService.execute()).
    * A thread blocked entering a flyoverLock monitor held by another thread must not itself be
    * holding the sandbox lock: if it did, and the monitor's holder is itself blocked in
    * lockWrite() waiting for that same lock to be released, the two threads deadlock (this was
    * round 1's bug). This method restores the read lock as soon as all monitors are held, before
    * any mutation runs, and re-acquires it between the two narrow release windows inside
    * doApplyFlyovers(), so the mutation sequence stays protected everywhere except the specific
    * calls that need to be lock-free.
    */
   private void applyFlyovers(String name, VSAssembly comp,
                              RuntimeViewsheet rvs, Worksheet ws,
                              ConditionList clist, String linkUri,
                              CommandDispatcher dispatcher) throws Exception
   {
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      if(comp == null || box.isEmpty()) {
         return;
      }

      AbstractTableAssembly tassembly = (AbstractTableAssembly)
         ws.getAssembly(comp.getTableName());
      TipVSAssemblyInfo minfo = (TipVSAssemblyInfo) comp.getVSAssemblyInfo();
      String[] views = minfo.getFlyoverViews();

      if(views == null || views.length == 0) {
         return;
      }

      TreeSet<String> lockNames = new TreeSet<>();

      if(tassembly != null) {
         lockNames.add(tassembly.getName());
      }

      for(String view : views) {
         VSAssembly tip = comp.getViewsheet().getAssembly(view);

         if(tip != null && !view.equals(name)) {
            lockNames.add(tip.getAbsoluteName());
         }
      }

      List<Object> locks = new ArrayList<>();

      for(String lockName : lockNames) {
         locks.add(box.get().getFlyoverLock(lockName));
      }

      // Release this thread's sandbox lock before attempting to enter any of the per-target
      // flyoverLock monitors acquired by applyFlyoversLocked() below — see this method's doc
      // comment for why. Restored once applyFlyoversLocked() returns; by then, doApplyFlyovers()
      // has already left the thread holding exactly the lock state it had here (its own narrow
      // release windows are self-balancing), so this pairing is a no-op restore in the normal
      // case and only matters if something above throws before reaching that point.
      box.get().unlockAll();

      try {
         applyFlyoversLocked(locks, 0, name, comp, rvs, clist, linkUri, dispatcher, tassembly,
                              views, box.get());
      }
      finally {
         box.get().restoreLocks();
      }
   }

   /**
    * Recursively acquire the given locks (already sorted into a deterministic order by the
    * caller to avoid deadlock against another request acquiring an overlapping lock set) while
    * holding no sandbox lock (see applyFlyovers()), then re-acquire the sandbox read lock and
    * run the actual flyover apply/execute/restore logic while holding all the per-target locks.
    */
   private void applyFlyoversLocked(List<Object> locks, int index, String name, VSAssembly comp,
                                    RuntimeViewsheet rvs, ConditionList clist, String linkUri,
                                    CommandDispatcher dispatcher, AbstractTableAssembly tassembly,
                                    String[] views, ViewsheetSandbox box) throws Exception
   {
      if(index >= locks.size()) {
         // All per-target monitors are held and this thread holds no sandbox lock (per
         // applyFlyovers()). Re-acquire the read lock now, before any mutation runs, and hold
         // it for the rest of this per-request body except the two narrow windows inside
         // doApplyFlyovers() that must be lock-free.
         box.restoreLocks();

         try {
            doApplyFlyovers(name, comp, rvs, clist, linkUri, dispatcher, tassembly, views, box);
         }
         finally {
            // Leave this thread holding no sandbox lock again while unwinding back out through
            // the monitors below (harmless — exiting a monitor never blocks), matching the
            // lock-free state applyFlyovers() expects to restore once this call returns.
            box.unlockAll();
         }

         return;
      }

      synchronized(locks.get(index)) {
         applyFlyoversLocked(locks, index + 1, name, comp, rvs, clist, linkUri, dispatcher,
                              tassembly, views, box);
      }
   }

   private void doApplyFlyovers(String name, VSAssembly comp, RuntimeViewsheet rvs,
                                ConditionList clist, String linkUri,
                                CommandDispatcher dispatcher, AbstractTableAssembly tassembly,
                                String[] views, ViewsheetSandbox box) throws Exception
   {
      ConditionList preList = null;

      if(tassembly != null) {
         preList = (ConditionList) tassembly.getPreRuntimeConditionList();
      }

      ArrayList<Integer> hints = new ArrayList<>();

      for(String view : views) {
         VSAssembly tip = comp.getViewsheet().getAssembly(view);

         // if the flyover component is not same source with current component,
         // just ignore the flyover component, instead of clear the flyover
         // component from current component, so if user change the flyover
         // source info, it may still working
         // ignore self
         if(tip == null || view.equals(name)) {
            continue;
         }

         int hint = applyCondition(rvs, comp, tip, clist);
         hints.add(hint);

         if(hint != VSAssembly.NONE_CHANGED) {
            // @by stephenwebster, For bug1433886201619
            // This fixes a unique case where flyover elements in a tabbed
            // assembly have their visibility affected by the tip conditions
            // First, make sure all the tip conditions are applied.
            // Second, make sure all scripts get executed on each flyover so
            // that when checking the visibility of an element in a tab the
            // correct tab gets selected based on the new conditions.
            // Third, though not ideal, execute and refresh the assemblies
            // separately in another loop.  This will ensure if the tip
            // assemblies are dependent on each other, their state will be
            // correct before one of the tip assemblies is executed.
            //
            // executeView() can reach ViewsheetSandbox.doExecuteData()'s lockWrite() upgrade
            // (via reentrant execution paths). Drop the sandbox lock narrowly around just this
            // call, mirroring the bug-74001 precedent (ViewsheetSandbox.doExecuteData() around
            // query.getData()) — mutations above (applyCondition()) already ran under the lock.
            box.unlockAll();

            try {
               box.executeView(tip.getAbsoluteName(), true);
            }
            finally {
               box.restoreLocks();
            }
         }
      }

      // @by stephenwebster, For bug1433886201619
      // Execute elements in a separate loop.
      for(String view : views) {
         VSAssembly tip = comp.getViewsheet().getAssembly(view);

         if(tip == null || view.equals(name)) {
            continue;
         }

         int hint = hints.remove(0);

         if(hint != VSAssembly.NONE_CHANGED) {
            // coreLifecycleService.execute() reaches ViewsheetSandbox.doExecuteData(), which
            // does a real read-to-write lock upgrade (lockWrite()). Drop the sandbox lock
            // narrowly around just this call, same reasoning as the executeView() call above.
            box.unlockAll();

            try {
               coreLifecycleService.execute(rvs, tip.getAbsoluteName(), linkUri, hint, dispatcher);
            }
            finally {
               box.restoreLocks();
            }

            coreLifecycleService.refreshVSAssembly(rvs, view, dispatcher);
         }
         // @by ankitmathur, For bug1432218253134, We need to clear the
         // Pre-Runtime Condition List of the base assembly shared between
         // each "tip" assembly. Because it is never cleared, the base assembly
         // keeps "merging" the condition's of the processed "tip" assemblies
         // and therefore starts incorrectly filtering the upcoming "tip"
         // assemblies.
         // UPDATE: 8-3-2015, For IssueId #409, Reset the Pre-Runtime Condition
         // List after the assembly has been processed. This will prevent
         // removing  any conditions which are inherited from Selection
         // components (or any other non fly-over assemblies).
         // UPDATE: 8-7-2015, Reset the Pre-Runtime Condition List to the
         // original value.
         //
         // This mutation must stay under the sandbox lock (held here, since the narrow
         // release above is already restored) — it is the exact mutation round-1's F3
         // required to be atomic with the query submission it wraps.
         if(tassembly != null) {
            tassembly.setPreRuntimeConditionList(preList);
         }
      }
   }

   /**
    * Apply the range condition on the worksheet.
    */
   private int applyCondition(RuntimeViewsheet rvs, VSAssembly comp,
                              VSAssembly tip, ConditionList conds) throws Exception
   {
      // Value is only used when chart is the host component
      Object clist = VSUtil.fixCondition(rvs, tip, conds, comp.getName(), null);

      if(Tool.equals(VSAssembly.NONE_CHANGED, clist)) {
         return VSAssembly.NONE_CHANGED;
      }

      tip.setTipConditionList(!(clist instanceof ConditionList) ? null : (ConditionList) clist);
      return VSAssembly.INPUT_DATA_CHANGED;
   }
}
