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
package inetsoft.uql.schema;

import inetsoft.uql.XNode;
import inetsoft.util.Tool;

/**
 * Time type node.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TimeType extends DateBaseType {
   /**
    * Create a time type node.
    */
   public TimeType() {
      setFormat(DEFAULT);
   }

   /**
    * Create a time type node.
    */
   public TimeType(String name) {
      super(name);
      setFormat(DEFAULT);
   }

   /**
    * Get the type of this node. The types are defined in XSchema class.
    */
   @Override
   public String getType() {
      return XSchema.TIME;
   }

   /**
    * Create a value tree corresponding to the data type defined
    * by this type.
    */
   @Override
   public XNode newInstance() {
      TimeValue node = new TimeValue();

      node.setName(getName());
      applyFormat(node);

      return node;
   }

   static final String DEFAULT = Tool.DEFAULT_TIME_PATTERN;
}

