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

/**
 * Asset event, the event sent from exploratory analyzer to process, and an
 * asset command will be sent back as the response.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class GridEvent extends AssetEvent {
   /**
    * Constructor.
    */
   public GridEvent() {
      super();
   }

   /**
    * Get the name of the sheet container.
    * @return the name of the sheet container.
    */
   public String getSheetName() {
      // this method is very vital for undo/redo, for we should
      // find the proper sheet container to perform undo/redo
      return null;
   }

   /**
    * Check if requires return.
    * @return <tt>true</tt> if requires, <tt>false</tt> otherwise.
    */
   public boolean requiresReturn() {
      return true;
   }

   /**
    * Close the expired sheet or not.
    */
   public boolean isCloseExpired() {
      return false;
   }

   /**
    * Check if is secondary event. A secondary event accepts exception.
    */
   public boolean isSecondary() {
      return false;
   }

   /**
    * Check if is undoable/redoable.
    * @return <tt>true</tt> if undoable/redoable.
    */
   public abstract boolean isUndoable();

   /**
    * Get the influenced assemblies. When undo/redo the data of the influenced
    * assemblies will be reset, so please implement the method properly to
    * provide the influenced assemblies for better performance, and avoid
    * error-prone meanwhile.
    * @return the influenced assemblies, <tt>null</tt> means all.
    */
   public abstract String[] getAssemblies();

   /**
    * Get the grid id.
    * @return the grid id of the grid event.
    */
   public String getID() {
      return (String) get("__ID__");
   }

   /**
    * Set the grid id to the grid event.
    * @param id the specified grid id.
    */
   public void setID(String id) {
      put("__ID__", id);
   }

   /**
    * Check if requires reset when undo.
    */
   public boolean requiresReset() {
      return false;
   }

   /**
    * Process this sheet event.
    * @param command the specified asset command.
    * @param rs the specified runtime sheet as the context.
    */
   public abstract void process(RuntimeSheet rs, AssetCommand command)
      throws Exception;
}
