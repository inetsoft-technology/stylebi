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
 * Redo undo manager for WSContext.
 *
 * @version 8.0, 05/22/2006
 * @author InetSoft Technology Corp
 */
public class RedoUndoManager {
   /**
    * Get the undo count.
    * @return the undo count.
    */
   public int getUndoCount() {
      return ucount;
   }

   /**
    * Set the undo count.
    * @param count the specified undo count.
    */
   public void setUndoCount(int count) {
      this.ucount = count;
   }

   /**
    * Get current undo point.
    * @return current undo point.
    */
   public int getCurrent() {
      return this.upoint;
   }

   /**
    * Set current undo point.
    * @param curr the specified undo point.
    */
   public void setCurrent(int curr) {
      this.upoint = curr;
   }

   /**
    * Check if is undoable.
    * @return <tt>true</tt> if undoable.
    */
   public boolean isUndoable() {
      return upoint - 1 >= 0 && upoint - 1 < ucount;
   }

   /**
    * Check if is redoable.
    * @return <tt>true</tt> if redoable.
    */
   public boolean isRedoable() {
      return upoint + 1 >= 0 && upoint + 1 < ucount;
   }

   /**
    * Get the undo name.
    * @return the undo name.
    */
   public String getUndoName() {
      return undoName;
   }

   /**
    * Set the undo name.
    * @param name the specified undo name.
    */
   public void setUndoName(String name) {
      this.undoName = name;
   }

   /**
    * Get the redo name.
    * @return the redo name.
    */
   public String getRedoName() {
      return redoName;
   }

   /**
    * Set the redo name.
    * @param name the specified redo name.
    */
   public void setRedoName(String name) {
      this.redoName = name;
   }

   private int ucount = 0;
   private int upoint = -1;
   private String undoName = "";
   private String redoName = "";
}