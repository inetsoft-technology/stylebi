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
package inetsoft.web.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import inetsoft.util.Tool;

import java.io.IOException;

public class LabelSerializer extends StdSerializer<Object> {
   public LabelSerializer() {
      super(Object.class);
   }

   @Override
   public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException
   {
      if(value instanceof Object[]) {
         Object[] arr = (Object[]) value;

         gen.writeStartArray();

         for(Object obj : arr) {
            serialize(obj, gen, serializers);
         }

         gen.writeEndArray();
      }
      else if(value == null) {
         gen.writeNull();
      }
      else if(value instanceof java.sql.Time || value instanceof java.sql.Timestamp) {
         gen.writeString(value.toString());
      }
      else if(value instanceof java.util.Date) {
         gen.writeString(Tool.getDateFormat().format(value));
      }
      else {
         gen.writeString(value.toString());
      }
   }
}
