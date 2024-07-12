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
package inetsoft.report.filter;

import inetsoft.report.TableFilter;
import inetsoft.report.event.TableChangeEvent;
import inetsoft.report.event.TableChangeListener;

/**
 * Define a default object which listens for TableChangeEvents. When it is
 * notified that the source table changes, it will invalidate the specified
 * table filter for the table filter to recalculate.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public class DefaultTableChangeListener implements TableChangeListener {
   /**
    * Create a default table change listener.
    *
    * @param filter the specified table filter
    */
   public DefaultTableChangeListener(TableFilter filter) {
      this.filter = filter;
   }

   /**
    * Create a default table change listener.
    *
    * @param filter the specified table filter
    */
   public DefaultTableChangeListener(BinaryTableFilter filter) {
      this.bfilter = filter;
   }

   /**
    * Invoked when the target of the listener has changed its data.
    *
    * @param event a TableChangeEvent Object.
    */
   @Override
   public void tableChanged(TableChangeEvent event) {
      if(filter != null) {
         filter.invalidate();
      }
      else if(bfilter != null) {
         bfilter.invalidate();
      }
   }

   private TableFilter filter;
   private BinaryTableFilter bfilter;
}
