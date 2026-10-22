/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.viewsheet.model;

import java.util.List;

/**
 * One assembly's placement in a viewsheet, as the agent sees it.
 *
 * <p>{@code annotationParts} is populated only on an annotation, naming the line and
 * rectangle it owns. An annotation is three linked assemblies, and listed flat they read as
 * three unrelated ones — so the read model groups them and the subordinate parts are omitted
 * from the top level.
 *
 * <p>{@code selectedPage} is populated only on a Tab, naming the member it currently shows; a
 * member's own {@code visible} does not tell, since a page hidden by not being selected is still
 * visible in its own right.
 */
public record AssemblyNode(String name, String type, int x, int y, int width, int height,
                           int zIndex, String container, boolean visible,
                           List<String> annotationParts, String annotationContent,
                           String selectedPage) {
   public AssemblyNode(String name, String type, int x, int y, int width, int height,
                       int zIndex, String container, boolean visible,
                       List<String> annotationParts, String annotationContent)
   {
      this(name, type, x, y, width, height, zIndex, container, visible, annotationParts,
           annotationContent, null);
   }

   public AssemblyNode(String name, String type, int x, int y, int width, int height,
                       int zIndex, String container, boolean visible)
   {
      this(name, type, x, y, width, height, zIndex, container, visible, null, null, null);
   }
}
