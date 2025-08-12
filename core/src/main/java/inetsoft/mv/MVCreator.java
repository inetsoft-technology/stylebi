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
package inetsoft.mv;

/**
 * Service interface for classes that create materialized views.
 */
public interface MVCreator {
   /**
    * Cancel this task.
    */
   void cancel();

   /**
    * Creates the materialized view.
    *
    * @return <tt>true</tt> if the view was created.
    *
    * @throws Exception if an error prevented the view from being created.
    */
   boolean create() throws Exception;

   void removeMessageListener();
}