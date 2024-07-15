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
package inetsoft.graph.mxgraph.layout.hierarchical.model;

import java.util.LinkedHashSet;

/**
 * An abstraction of a rank in the hierarchy layout. Should be ordered, perform
 * remove in constant time and contains in constant time
 */
public class mxGraphHierarchyRank extends LinkedHashSet<mxGraphAbstractHierarchyCell> {

   /**
    *
    */
   private static final long serialVersionUID = 1L;
}
