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

import inetsoft.uql.asset.AssemblyEntry;

import java.io.Serializable;
import java.util.*;

/**
 * Changed assembly list contained the changed assemblies whose view or data
 * should be regenerated.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ChangedAssemblyList implements Serializable {
   /**
    * Create a changed assembly set.
    */
   public ChangedAssemblyList() {
      this(false);
   }

   /**
    * Create a changed assembly set.
    */
   public ChangedAssemblyList(boolean breakable) {
      super();

      this.breakable = breakable;
      this.datas = new ArrayList<>();
      this.ready = new ArrayList<>();
      this.processed = new ArrayList<>();
      this.pending = new ArrayList<>();
      this.views = new ArrayList<>();
      this.selections = new ArrayList<>();
      this.scripts = new HashSet<>();
      this.smap = new HashMap<>();
      this.tables = new ArrayList<>();
      this.filters = new HashSet<>();
      this.inputChangeProcessed = new HashSet<>();
   }

   /**
    * Get the ready listener.
    * @return the ready listener.
    */
   public ReadyListener getReadyListener() {
      return rlistener;
   }

   /**
    * Set the ready listener.
    * @param listener the specified ready listener.
    */
   public void setReadyListener(ReadyListener listener) {
      this.rlistener = listener;
   }

   /**
    * Get the viewsheet assemblies whose data is changed.
    * @return the list contains the changed assembly entries.
    */
   public List<AssemblyEntry> getDataList() {
      return datas;
   }

   /**
    * Add an entry to the data list
    * @param entry   the entry to add
    */
   private void addEntryToDataList(AssemblyEntry entry) {
      if(!datas.contains(entry)) {
         datas.add(entry);
      }
   }

   /**
    * Get the viewsheet assemblies whose view is changed.
    * @return the list contains the changed assembly entries.
    */
   public List<AssemblyEntry> getViewList() {
      return views;
   }

   /**
    * Add an entry to the view list
    * @param entry   the entry to add
    */
   private void addEntryToViewList(AssemblyEntry entry) {
      if(!views.contains(entry)) {
         views.add(entry);
      }
   }

   /**
    * Get the viewsheet assemblies whose selection is changed.
    * @return the list contains the changed assembly entries.
    */
   public List<AssemblyEntry> getSelectionList() {
      return selections;
   }

   /**
    * Removes a single assembly from the Selection List. If a fully qualified
    * entry is NOT found and removed then the non-qualified entry will be
    * attempted to be found and removed.
    *
    * @param entry an AssemblyEntry
    */
   public void removeFromSelectionList(AssemblyEntry entry) {
      // @by david bug1369123998487, Selections are process by processSelections
      // in ViewsheetSandbox, but entries in the list may be fully qualified or
      // unqualified, depending on how the entry was added to the list and by
      // which sandbox. This method will try both qualified and unqualified.

      if(selections.contains(entry)) {
         selections.remove(entry);
      }
      else {
         AssemblyEntry unqualifiedEntry = entry.getUnqualifiedEntry();

         if(unqualifiedEntry != null) {
            selections.remove(unqualifiedEntry);
         }
      }
   }

   /**
    * Get the table assemblies in base worksheet whose data is changed.
    * @return the list contains the changed assembly entries.
    */
   public List<AssemblyEntry> getTableList() {
      return tables;
   }

   /**
    * Add the processed times of a selection assembly.
    */
   public void addProcessedTimes(AssemblyEntry entry) {
      Integer val = smap.get(entry);

      if(val == null) {
         val = 1;
      }
      else {
         val += 1;
      }

      smap.put(entry, val);
   }

   /**
    * Get the processed times of a selection assembly.
    */
   public int getProcessedTimes(AssemblyEntry entry) {
      Integer val = smap.get(entry);
      return val == null ? 0 : val;
   }

   /**
    * Add a ready assembly.
    * @param assembly the specified assembly.
    * @param event <tt>true</tt> to fire ready event, <tt>false</tt> otherwise.
    */
   public void addReady(AssemblyEntry assembly, boolean event) throws Exception {
      if(!breakable) {
         throw new RuntimeException("I am not breakable: " + this);
      }

      if(!ready.contains(assembly)) {
         ready.add(assembly);

         if(event) {
            fireReadyEvent();
         }
      }
   }

   /**
    * Fire a ready event.
    */
   public void fireReadyEvent() throws Exception {
      if(rlistener != null) {
         rlistener.onReady();
      }
   }

   /**
    * Get the viewsheet assemblies which are ready.
    * @return the list contains the ready viewsheet assemblies.
    */
   public List<AssemblyEntry> getReadyList() {
      return ready;
   }

   /**
    * Get the viewsheet assemblies which are processed.
    * @return the list contains the processed viewsheet assemblies.
    */
   public List<AssemblyEntry> getProcessedList() {
      return processed;
   }

   /**
    * Add an entry to the table list
    * @param entry   the entry to add
    */
   private void addEntryToProcessedList(AssemblyEntry entry) {
      if(!processed.contains(entry)) {
         processed.add(entry);
      }
   }

   /**
    * Get the pending list which are to be processed.
    * @return the pending list.
    */
   public List<AssemblyEntry> getPendingList() {
      return pending;
   }

   /**
    * Returns whether a shared filter has previously been processed and applied
    * to other assemblies.
    *
    * @param filterId   the shared filter id (@see ViewsheetInfo.getFilterID)
    * @return  <tt>true</tt> if already processed, <tt>false</tt> otherwise
    */
   public boolean isShareFilterProcessed(String filterId) {
      return filters.contains(filterId);
   }

   /**
    * Sets whether a shared filter has been processed and applied to other
    * assemblies.
    *
    * @param filterId   the shared filter id (@see ViewsheetInfo.getFilterID)
    */
   public void setShareFilterProcessed(String filterId) {
      filters.add(filterId);
   }

   /**
    * Set the assembly whose script is processed.
    */
   public void addScriptDone(AssemblyEntry entry) {
      scripts.add(entry);
   }

   /**
    * Check if the assembly whose script is processed.
    */
   public boolean isScriptDone(AssemblyEntry entry) {
      return scripts.contains(entry);
   }

   /**
    * Check if contains an assembly entry.
    * @param entry the specified assembly entry.
    * @return <tt>true</tt> if contains the assembly entry, <tt>false</tt>
    * otherwise.
    */
   public boolean contains(AssemblyEntry entry) {
      return datas.contains(entry) || views.contains(entry) ||
         tables.contains(entry);
   }

   /**
    * Check if this assembly list is breakable.
    * @return <tt>true</tt> if breakable, <tt>false</tt> otherwise.
    */
   public boolean isBreakable() {
      return breakable;
   }

   /**
    * Check if inputDataChanged has been processed for the assembly.
    */
   public boolean isInputChangeProcessed(AssemblyEntry entry) {
      return inputChangeProcessed.contains(entry);
   }

   /**
    * Mark inputDataChanged has been processed for the assembly.
    */
   public void addInputChangeProcessed(AssemblyEntry entry) {
      inputChangeProcessed.add(entry);
   }

   /**
    * Mark inputDataChanged has been processed for the assembly.
    */
   public void removeInputChangeProcessed(AssemblyEntry entry) {
      inputChangeProcessed.remove(entry);
   }

   /**
    * Merges the "core" lists from another ChangedAssemblyList.
    *
    * Some of the lists in ChangedAssemblyList are used to control the flow
    * within ViewsheetSandbox.processChange, while others are used to control
    * the flow in VSEventUtil.execute. The "core" lists are those used by the
    * execute method.
    *
    * @param from the other list to merge from
    */
   public void mergeCore(ChangedAssemblyList from) {
      for(AssemblyEntry entry : from.getDataList()) {
         addEntryToDataList(entry);
      }

      for(AssemblyEntry entry : from.getViewList()) {
         addEntryToViewList(entry);
      }

      for(AssemblyEntry entry : from.getProcessedList()) {
         addEntryToProcessedList(entry);
      }
   }

   /**
    * Merges the processed filterIds from one change list to this change list.
    *
    * @param from the other list to merge from
    */
   public void mergeFilters(ChangedAssemblyList from) {
      for(String filterId : from.filters) {
         setShareFilterProcessed(filterId);
      }
   }

   public boolean isObjectPropertyChanged() {
      return objectPropertyChanged;
   }

   public void setObjectPropertyChanged(boolean objectPropertyChanged) {
      this.objectPropertyChanged = objectPropertyChanged;
   }

   /**
    * Get the string representaion.
    * @return the string representation.
    */
   public String toString() {
      return "ChangedAssemblyList[data: " + datas + "][view: " + views +
         "][selection: " + selections + "][wstable: " + tables +
         "][ready: " + ready + "][processed: " + processed + "][smap: " + smap +
         "]";
   }

   /**
    * Ready listener.
    */
   public interface ReadyListener {
      /**
       * Triggered when more assembly gets ready.
       */
      void onReady() throws Exception;

      /**
       * Set the runtime sheet.
       * @param rs the specified runtime sheet.
       */
      void setRuntimeSheet(RuntimeSheet rs);

      /**
       * Get the runtime sheet.
       * @return the runtime sheet if any, <tt>null</tt> otherwise.
       */
      RuntimeSheet getRuntimeSheet();

      /**
       * Set whether to initialize grid.
       * @param grid <tt>true</tt> to initialize grid, <tt>false</tt> otherwise.
       */
      void setInitingGrid(boolean grid);

      /**
       * Check if should initialize grid.
       * @return <tt>true</tt> if should initialize grid, <tt>false</tt>
       * otherwise.
       */
      boolean isInitingGrid();

      /**
       * Get the viewsheet id.
       * @return the worksheet id of the list.
       */
      String getID();

      /**
       * Set the viewsheet id to the list.
       * @param id the specified worksheet id.
       */
      void setID(String id);
   }

   private boolean breakable;
   private boolean objectPropertyChanged;
   private ReadyListener rlistener;
   private List<AssemblyEntry> datas;
   private List<AssemblyEntry> views;
   private List<AssemblyEntry> selections;
   private Set<String> filters;
   private Set<AssemblyEntry> scripts;
   private Map<AssemblyEntry, Integer> smap;
   private List<AssemblyEntry> tables;
   private List<AssemblyEntry> ready;
   private List<AssemblyEntry> processed;
   private List<AssemblyEntry> pending;
   private Set<AssemblyEntry> inputChangeProcessed;
}
