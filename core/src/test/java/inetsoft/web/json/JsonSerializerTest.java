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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.security.ResourceType;
import inetsoft.test.SreeHome;
import inetsoft.web.WebConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SreeHome()
class JsonSerializerTest {
   @BeforeEach
   void setup() {
      WebConfig webConfig = new WebConfig();
      objectMapper = webConfig.objectMapper();
   }

   @Test
   void testSerializeTimestamp() throws Exception {
      SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      Timestamp timestamp = new Timestamp(new Date().getTime());

      // Jackson object mapper adds double quotes (") to output
      assertEquals(
         objectMapper.writeValueAsString(timestamp),
         "\"" + simpleDateFormat.format(timestamp) + "\"");
   }

   @Test
   void testSerializeDate() throws Exception {
      SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      Date date = new Date();

      // Jackson object mapper adds double quotes (") to output
      assertEquals(
         objectMapper.writeValueAsString(date),
         "\"" + simpleDateFormat.format(date) + "\"");
   }

   @Test
   void testSerializeEnumSet() throws Exception {
      EnumSetBean bean = new EnumSetBean();
      bean.setSet(EnumSet.of(ResourceType.REPORT, ResourceType.VIEWSHEET));
      String actual = objectMapper.writeValueAsString(bean);
      String expected = "{\"set\":[\"REPORT\",\"VIEWSHEET\"]}";
      assertEquals(expected, actual);
   }

   @Test
   void testDeserializeEnumSet() throws Exception {
      String json = "{\"set\":[\"REPORT\",\"VIEWSHEET\"]}";
      EnumSetBean bean = objectMapper.readValue(json, EnumSetBean.class);
      EnumSet<ResourceType> expected = EnumSet.of(ResourceType.REPORT, ResourceType.VIEWSHEET);
      assertEquals(expected, bean.getSet());
   }

   private ObjectMapper objectMapper;

   public static final class EnumSetBean {
      public EnumSet<ResourceType> getSet() {
         return set;
      }

      public void setSet(EnumSet<ResourceType> set) {
         this.set = set;
      }

      private EnumSet<ResourceType> set;
   }
}
