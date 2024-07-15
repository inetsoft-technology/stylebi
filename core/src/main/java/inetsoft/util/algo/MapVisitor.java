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
package inetsoft.util.algo;

import java.util.Map;

/**
 * Interface for classes that act as a visitor for <tt>VisitableMap</tt>
 * objects. This interface follows the GOF visitor pattern.
 *
 * @author InetSoft Technology
 * @since  9.5
 */
public interface MapVisitor {
   /**
    * Performs an operation on a map entry.
    *
    * @param entry the map entry.
    */
   void visit(Map.Entry entry);
}
