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
package inetsoft.report.composition;

import inetsoft.report.composition.execution.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.QueryManager;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.security.Principal;
import java.util.Arrays;
import java.util.LinkedList;

/**
 * RuntimeWorksheet represents a runtime worksheet in editing time.
 * It contains a worksheet and the accessorial information like asset entry,
 * the user who opened it, editable option, etc.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class RuntimeWorksheet extends RuntimeSheet
   implements PropertyChangeListener
{
   /**
    * Constructor.
    */
   public RuntimeWorksheet() {
      super();
   }

   /**
    * Constructor.
    */
   public RuntimeWorksheet(AssetEntry entry, Worksheet ws, Principal user) {
      this(entry, ws, user, new AssetQuerySandbox(ws), false);
   }

   /**
    * Constructor.
    */
   public RuntimeWorksheet(AssetEntry entry, Worksheet ws, Principal user,
                           boolean syncData) {
      this(entry, ws, user, new AssetQuerySandbox(ws), syncData);
   }

   /**
    * Constructor.
    */
   protected RuntimeWorksheet(AssetEntry entry, Worksheet ws, Principal user,
                              AssetQuerySandbox box, boolean syncData)
   {
      if(entry == null) {
         entry = new AssetEntry();
      }

      this.entry = entry;
      this.user = user;
      this.ws = ws;
      this.box = box;
      this.box.setWSName(entry.getSheetName());
      this.box.setWSEntry(entry);
      this.box.setBaseUser(user);
      this.box.setActive(true);
      this.box.setQueryManager(new QueryManager());
      this.preview = "true".equals(entry.getProperty("preview"));
      this.gettingStarted = "true".equals(entry.getProperty("gettingStarted"));
      this.syncData = syncData;

      if(ws != null) {
         addCheckpoint(ws.prepareCheckpoint(), null);
      }

      if(syncData) {
         DataSourceRegistry.getRegistry().addRefreshedListener(this);
         DataSourceRegistry.getRegistry().addModifiedListener(this);
      }
   }

   /**
    * Triggered when data changed.
    */
   @Override
   public void propertyChange(PropertyChangeEvent evt) {
      if(box != null) {
         box.reset();
      }
   }

   /**
    * Get the asset query sandbox.
    * @return the asset query sandbox.
    */
   public AssetQuerySandbox getAssetQuerySandbox() {
      return box;
   }

   /**
    * Get the worksheet.
    * @return the worksheet of the runtime worksheet.
    */
   public Worksheet getWorksheet() {
      return ws;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean undo(ChangedAssemblyList clist) {
      if(point - 1 >= 0 && point < size()) {
         Worksheet ws = (Worksheet) points.get(point - 1);
         EventInfo event = events.get(point);

         if(point == savePoint) {
            updateDependencyState(ws, (Worksheet) points.get(savePoint));
         }
         else {
            updateDependencyState(ws, this.ws);
         }

         for(Assembly assembly : ws.getAssemblies()) {
            if(!assembly.isUndoable()) {
               throw new MessageException("Undo not supported: " + assembly.getName());
            }
         }

         this.ws = (Worksheet) ws.clone();
         box.setWorksheet(this.ws);
         String[] eventAssemblies = event.getAssemblies();

         // Bug #39589, make sure tables are rest on undo
         if(eventAssemblies != null && eventAssemblies.length == 0) {
            eventAssemblies = null;
         }

         reset(eventAssemblies);

         // Bug #39589, clear cache for bound tables on undo
         for(Assembly assembly : ws.getAssemblies()) {
            if(assembly instanceof BoundTableAssembly) {
               try {
                  DataKey key = AssetDataCache.getCacheKey(
                     (TableAssembly) assembly, box, null, AssetQuerySandbox.LIVE_MODE, true);
                  AssetDataCache.removeCachedData(key);
               }
               catch(Exception e) {
                  LOG.warn("Failed to clear cache for {}", assembly.getAbsoluteName(), e);
               }
            }
         }

         point -= 1;
         return true;
      }

      return false;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean redo(ChangedAssemblyList clist) {
      if(point + 1 >= 0 && point + 1 < size()) {
         Worksheet ws = (Worksheet) points.get(point + 1);
         EventInfo event = events.get(point + 1);
         this.ws = (Worksheet) ws.clone();
         box.setWorksheet(this.ws);
         reset(event.getAssemblies());
         point += 1;
         return true;
      }

      return false;
   }

   /**
    * Rollback last changes.
    */
   @Override
   public void rollback() {
      if(points.size() > 0) {
         this.ws = (Worksheet) points.get(points.size() - 1);
         this.ws = (Worksheet) this.ws.clone();
         box.setWorksheet(this.ws);
      }
   }

   public void cloneWS() throws Exception {
      this.joinWS = new RuntimeWorksheet();
      this.joinWS.ws = (Worksheet) this.ws.clone();

      for(int i = 0; i < points.size(); i++) {
         joinWS.points.add(points.get(i));
         joinWS.events.add(events.get(i));
      }

      joinWS.point = point;
   }

   public void cancelJoin() throws Exception {
      this.ws = this.joinWS.ws;
      points = new XSwappableSheetList();
      events = new LinkedList<>();

      for(int i = 0; i < joinWS.points.size(); i++) {
         points.add(joinWS.points.get(i));
         events.add(joinWS.events.get(i));
      }

      point = joinWS.point;
      box.setWorksheet(this.ws);
      this.joinWS = null;
   }

   public RuntimeWorksheet getJoinWS() throws Exception {
      return this.joinWS;
   }

   /**
    * Get the contained sheet.
    * @return the contained sheet.
    */
   @Override
   public AbstractSheet getSheet() {
      return getWorksheet();
   }

   /**
    * Reset the influenced assemblies.
    * @param assemblies the influenced assemblies.
    */
   private void reset(String[] assemblies) {
      // reset all?
      if(assemblies == null) {
         box.reset();
         return;
      }

      try {
         for(int i = 0; i < assemblies.length; i++) {
            Assembly assembly = ws.getAssembly(assemblies[i]);

            if(assembly == null) {
               continue;
            }

            box.reset(assembly.getAssemblyEntry(), true);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to reset assemblies: " + Arrays.toString(assemblies), ex);
      }
   }

   /**
    * Dispose the runtime worksheet.
    */
   @Override
   public void dispose() {
      super.dispose();

      if(box != null) {
         box.dispose();
         box = null;
      }

      ws = null;

      if(syncData) {
         DataSourceRegistry.getRegistry().removeRefreshedListener(this);
         DataSourceRegistry.getRegistry().removeModifiedListener(this);
      }
   }

   /**
    * Check if is a preview runtime worksheet.
    * @return <tt>true</tt> if a preview runtime worksheet, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean isPreview() {
      return preview;
   }

   /**
    * Check if is a runtime sheet.
    * @return <tt>true</tt> if a runtime sheet, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean isRuntime() {
      return false;
   }

   /**
    * Get the mode of this runtime sheet.
    * @return the mode of this runtime sheet.
    */
   @Override
   public int getMode() {
      return 0;
   }

   public boolean isGettingStarted() {
      return gettingStarted;
   }

   /**
    * Get the worksheet's parent id.
    * @return the worksheet's parent id.
    */
   public String getParentID() {
      return pid;
   }

   /**
    * Set the worksheet's parent id.
    * @param id the specified worksheet's parent id.
    */
   public void setParentID(String id) {
      this.pid = id;
   }

   /**
    * Get the string representaion.
    * @return the strung representation.
    */
   public String toString() {
      return "RuntimeWorksheet:[" + entry + "," + user + "]";
   }

   /**
    * After an undo we need to update the "oldName" fields of the assemblies and columns
    * so the next save will contain the correct rename information.
    *
    * @param undoWs the worksheet at the undo point
    * @param currentSheet the current save point worksheet before oldName/newName are synced
    */
   private void updateDependencyState(Worksheet undoWs, Worksheet currentSheet) {
      Assembly[] assemblies = undoWs.getAssemblies();

      for(Assembly assembly : assemblies) {
         String name = assembly.getName();

         if(ws.containsAssembly(name)) {
            Assembly cass = ws.getAssembly(name);
            fixUndoOldName(assembly, cass);
         }
      }
   }

   /**
    * After undo action, should reset old name to latest save state. Such as:
    * 1. When open and do not save, all points save values as following:
    *      rename:  a -> b -> c -> d
    *      oldName: a -> a -> a -> a
    *      lastOldName: a -> a -> a -> a
    *
    * 2. Save ws, it will change current ws, but the undo/redo points do not change:
    *      save ws: d  oname -> d   lastOldName -> a
    *
    * 3. Do undo actions
    *     undo:    d -> c
    *     current ws: saved ws d
    *     find oldName: find c by its old name(a)  in current ws. find d, set d's lastOldName to c.
    *     result:  c  oldName -> d, lastOldName -> a.
    *
    *     undo:    c -> b
    *     current ws: last undo ws c
    *     find oldName: find c by its old name(a)  in current ws. find d, set d's lastOldName to c.
    *     result:  b  oldName -> d, lastOldName -> a.
    */
   private void fixUndoOldName(Assembly undo, Assembly current) {
      if(!(undo instanceof AbstractTableAssembly) || !(current instanceof AbstractTableAssembly)) {
         return;
      }

      AbstractTableAssembly undoAss = (AbstractTableAssembly) undo;
      AbstractTableAssembly currAss = (AbstractTableAssembly) current;
      ColumnSelection ncols = undoAss.getColumnSelection(false);
      ColumnSelection ocols = currAss.getColumnSelection(false);

      for(int i = 0; i < ncols.getAttributeCount(); i++) {
         ColumnRef ref = (ColumnRef) ncols.getAttribute(i);
         String oname = ref.getOldName();

         if(oname == null) {
            return;
         }

         for(int j = 0; j < ocols.getAttributeCount(); j++) {
            ColumnRef oref = (ColumnRef) ocols.getAttribute(j);
            String lastOldName = oref.getLastOldName();

            if(Tool.equals(oname, lastOldName)) {
               ref.setOldName(oref.getOldName());
               break;
            }
         }
      }
   }

   private Worksheet ws;          // worksheet
   private AssetQuerySandbox box; // query sandbox
   private boolean preview;       // preview flag
   private boolean gettingStarted; // getting started flag.
   private String pid;
   private boolean syncData;
   private RuntimeWorksheet joinWS = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(RuntimeWorksheet.class);
}
