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
package inetsoft.web.composer.model.condition;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SreeHome()
class ConditionValueModelTest {
   private ObjectMapper mapper;
   private ConditionValueModel.Serializer serializer;
   private ConditionValueModel.Deserializer deserializer;

   @BeforeEach
   void setup() {
      mapper = new ObjectMapper();
      serializer = new ConditionValueModel.Serializer();
      deserializer = new ConditionValueModel.Deserializer();
   }

   @Test
   void canSerializeBasicValues() throws Exception {
      testBasicValue(true);
      testBasicValue("ABC");
      testBasicValue(1.23e6);
      testBasicValue(3.45d);
      testBasicValue(0xdeadbeef);
      testBasicValue(Integer.MAX_VALUE);
   }

   private void testBasicValue(Object value) throws Exception {
      ConditionValueModel model = new ConditionValueModel();
      model.setType(ConditionValueModel.VALUE);
      model.setValue(value);

      assertEquals(value, deserializeModel(serializeModel(model)).getValue());
   }

   private String serializeModel(ConditionValueModel model) throws Exception {
      StringWriter stringWriter = new StringWriter();
      JsonGenerator jsonGenerator = mapper.getFactory().createGenerator(stringWriter);
      serializer.serialize(model, jsonGenerator, mapper.getSerializerProvider());
      jsonGenerator.flush();

      return stringWriter.toString();
   }

   private ConditionValueModel deserializeModel(String json) throws Exception {
      InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
      JsonParser parser = mapper.getFactory().createParser(stream);
      return deserializer.deserialize(parser, mapper.getDeserializationContext());
   }
}
