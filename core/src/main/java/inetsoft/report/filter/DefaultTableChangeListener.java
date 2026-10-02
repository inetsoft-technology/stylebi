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
package inetsoft.report.filter;

import inetsoft.report.TableFilter;
import inetsoft.report.event.TableChangeEvent;
import inetsoft.report.event.TableChangeListener;

import java.io.*;
import java.lang.ref.WeakReference;

/**
 * Define a default object which listens for TableChangeEvents. When it is
 * notified that the source table changes, it will invalidate the specified
 * table filter for the table filter to recalculate.
 * <p>
 * The listener holds its table filter weakly, so a base table that keeps this
 * listener does not keep the filter alive. The base table (see
 * AbstractTableLens.addChangeListener) keeps the listener itself strongly, so
 * the filter receives change events for as long as the filter is alive.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public class DefaultTableChangeListener implements TableChangeListener, Serializable {
   /**
    * Create a default table change listener.
    *
    * @param filter the specified table filter
    */
   public DefaultTableChangeListener(TableFilter filter) {
      this.target = new WeakReference<>(filter);
   }

   /**
    * Create a default table change listener.
    *
    * @param filter the specified table filter
    */
   public DefaultTableChangeListener(BinaryTableFilter filter) {
      this.target = new WeakReference<>(filter);
   }

   /**
    * Get the table filter this listener invalidates.
    *
    * @return the TableFilter or BinaryTableFilter, or null if it has been
    * garbage collected.
    */
   public Object getTarget() {
      return target.get();
   }

   /**
    * Invoked when the target of the listener has changed its data.
    *
    * @param event a TableChangeEvent Object.
    */
   @Override
   public void tableChanged(TableChangeEvent event) {
      Object filter = target.get();

      if(filter instanceof TableFilter) {
         ((TableFilter) filter).invalidate();
      }
      else if(filter instanceof BinaryTableFilter) {
         ((BinaryTableFilter) filter).invalidate();
      }
   }

   // write the filter strongly, so a serialized table that holds this listener
   // (SetTableLens) keeps notifying the filter after it is read back
   @Serial
   private void writeObject(ObjectOutputStream out) throws IOException {
      out.defaultWriteObject();
      out.writeObject(target.get());
   }

   @Serial
   private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      target = new WeakReference<>(in.readObject());
   }

   private transient WeakReference<Object> target;
}
