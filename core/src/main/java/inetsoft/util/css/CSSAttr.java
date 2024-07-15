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
package inetsoft.util.css;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.io.Serializable;
import java.util.Map;

/**
 * CSS attributes.
 *
 * @version 13.2
 * @author InetSoft Technology Corp
 */
public class CSSAttr extends Object2ObjectOpenHashMap<String, String> implements Serializable {
   public CSSAttr() {
      super(1);
   }

   public CSSAttr(String ...attrs) {
      super(attrs.length / 2);

      for(int i = 0; i + 1 < attrs.length; i += 2) {
         put(attrs[i], attrs[i + 1]);
      }
   }

   public CSSAttr(Map<String, String> attrs) {
      super(attrs.size());

      // avoid np exception by ignoring null values (49423).
      attrs.entrySet().stream()
         .filter(e -> e.getValue() != null)
         .forEach(e -> put(e.getKey(), e.getValue()));
   }
}
