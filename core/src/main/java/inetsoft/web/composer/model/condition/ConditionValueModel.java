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
package inetsoft.web.composer.model.condition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import inetsoft.web.binding.drm.DataRefModel;

import java.io.IOException;

@JsonSerialize(using = ConditionValueModel.Serializer.class)
@JsonDeserialize(using = ConditionValueModel.Deserializer.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConditionValueModel {
   public static final String VALUE = "VALUE";
   public static final String VARIABLE = "VARIABLE";
   public static final String EXPRESSION = "EXPRESSION";
   public static final String FIELD = "FIELD";
   public static final String SUBQUERY = "SUBQUERY";
   public static final String SESSION_DATA = "SESSION_DATA";

   public Object getValue() {
      return value;
   }

   public void setValue(Object value) {
      this.value = value;
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public void setChoiceQuery(String choiceQuery) {
      this.choiceQuery = choiceQuery;
   }

   public String getChoiceQuery() {
      return choiceQuery;
   }

   private Object value;
   private String type;
   private String choiceQuery;

   public static final class Serializer extends StdSerializer<ConditionValueModel> {
      public Serializer() {
         super(ConditionValueModel.class);
      }

      @Override
      public void serialize(
         ConditionValueModel model, JsonGenerator generator,
         SerializerProvider provider) throws IOException
      {
         generator.writeStartObject();
         generator.writeStringField("type", model.getType());
         generator.writeStringField("choiceQuery", model.getChoiceQuery());
         generator.writeObjectField("value", model.getValue());
         generator.writeEndObject();
      }
   }

   public static final class Deserializer extends StdDeserializer<ConditionValueModel> {
      public Deserializer() {
         super(ConditionValueModel.class);
      }

      @Override
      public ConditionValueModel deserialize(
         JsonParser parser, DeserializationContext context)
         throws IOException
      {
         ConditionValueModel model = new ConditionValueModel();
         JsonNode node = parser.getCodec().readTree(parser);
         model.setType(node.get("type").textValue());

         if(node.has("choiceQuery")) {
            model.setChoiceQuery(node.get("choiceQuery").textValue());
         }

         JsonNode valueNode = node.get("value");

         if(EXPRESSION.equals(model.getType())) {
            model.setValue(parser.getCodec()
               .readValue(valueNode.traverse(), ExpressionValueModel.class));
         }
         else if(SUBQUERY.equals(model.getType())) {
            model.setValue(parser.getCodec()
               .readValue(valueNode.traverse(), SubqueryValueModel.class));
         }
         else if(FIELD.equals(model.getType())) {
            model.setValue(
               parser.getCodec().readValue(valueNode.traverse(), DataRefModel.class));
         }
         else {
            if(valueNode.get("n") != null) {
               model.setValue(parser.getCodec()
                  .readValue(valueNode.traverse(), RankingValueModel.class));
            }
            else {
               model.setValue(
                  parser.getCodec().readValue(valueNode.traverse(), Object.class));
            }
         }

         return model;
      }
   }
}
