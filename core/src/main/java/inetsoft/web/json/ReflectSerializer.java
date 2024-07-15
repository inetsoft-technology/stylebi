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
package inetsoft.web.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayDeque;
import java.util.Deque;

public abstract class ReflectSerializer<T> extends StdSerializer<T> {
   public ReflectSerializer(Class<T> t) {
      super(t);
   }

   @Override
   public void serialize(T value, JsonGenerator gen, SerializerProvider provider) throws IOException {
      gen.writeNull();
      Deque<String> stack = new ArrayDeque<>();
      JsonStreamContext context = gen.getOutputContext();
      Object currentValue = value;

      while(context != null) {
         String currentName = context.getCurrentName();
         String currentClass = currentValue == null ? null : currentValue.getClass().getName();
         String label = "";

         if(currentName != null) {
            label = currentName + ": ";
         }

         label += currentClass;
         stack.addLast(label);
         currentValue = context.getCurrentValue();
         context = context.getParent();
      }

      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         String valueClass = value == null ? "null" : value.getClass().getName();
         writer.format("Attempted to write %s as JSON:%n", valueClass);
         String indent = "";

         while(!stack.isEmpty()) {
            String line = stack.removeLast();
            writer.format("%s%s%n", indent, line);
            indent += "  ";
         }
      }

      LOG.error(buffer.toString().trim());
   }

   private static final Logger LOG = LoggerFactory.getLogger(ReflectSerializer.class);
}
