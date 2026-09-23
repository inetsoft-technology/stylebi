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
package inetsoft.graph.script;

import inetsoft.graph.EGraph;
import inetsoft.graph.element.GraphElement;

/**
 * Test-only Java entry points that take graph objects, used to check how a
 * script-held {@code HostBeanProxy} is converted at hand-offs whose receiver is
 * not itself a {@code HostBeanProxy}. It lives under {@code inetsoft.graph.} so
 * the script class filter lets scripts reach it. (#76969)
 */
public class GraphArgumentSink {
   public GraphArgumentSink() {
   }

   public GraphArgumentSink(GraphElement elem, int n) {
      this.desc = name(elem) + ":" + n;
   }

   public String getDesc() {
      return desc;
   }

   public static String element(GraphElement elem) {
      return name(elem);
   }

   public static String graph(EGraph graph) {
      return "EGraph:" + graph.getElementCount();
   }

   public String instanceElement(GraphElement elem) {
      return name(elem);
   }

   public static String varargs(GraphElement... elems) {
      StringBuilder sb = new StringBuilder();

      for(GraphElement elem : elems) {
         sb.append(name(elem)).append(',');
      }

      return sb.toString();
   }

   public static String array(GraphElement[] elems) {
      return varargs(elems);
   }

   public static String overload(Object value) {
      return "Object";
   }

   public static String overload(GraphElement elem) {
      return "GraphElement:" + name(elem);
   }

   private static String name(GraphElement elem) {
      return elem.getClass().getSimpleName();
   }

   private String desc;
}
