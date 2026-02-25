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
package inetsoft.web.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TypedPropertyMapSerializer and TypedPropertyMapDeserializer.
 * These tests verify round-trip serialization of the RuntimeSheet prop map
 * with embedded type information.
 */
class TypedPropertyMapSerializerTest {
   @BeforeEach
   void setup() {
      objectMapper = new ObjectMapper();
      objectMapper.registerModule(new ThirdPartySupportModule());
   }

   @Test
   void testRoundTripDimension() throws Exception {
      Map<String, Object> original = new HashMap<>();
      original.put("size", new Dimension(800, 600));

      String json = objectMapper.writeValueAsString(new TypedPropertyMapWrapper(original));
      TypedPropertyMapWrapper result = objectMapper.readValue(json, TypedPropertyMapWrapper.class);

      assertEquals(original.get("size"), result.getValues().get("size"));
   }

   @Test
   void testRoundTripPoint() throws Exception {
      Map<String, Object> original = new HashMap<>();
      original.put("position", new Point(100, 200));

      String json = objectMapper.writeValueAsString(new TypedPropertyMapWrapper(original));
      TypedPropertyMapWrapper result = objectMapper.readValue(json, TypedPropertyMapWrapper.class);

      assertEquals(original.get("position"), result.getValues().get("position"));
   }

   @Test
   void testRoundTripPoint2DDouble() throws Exception {
      Map<String, Object> original = new HashMap<>();
      original.put("scale", new Point2D.Double(1.5, 2.0));

      String json = objectMapper.writeValueAsString(new TypedPropertyMapWrapper(original));
      TypedPropertyMapWrapper result = objectMapper.readValue(json, TypedPropertyMapWrapper.class);

      assertEquals(original.get("scale"), result.getValues().get("scale"));
   }

   @Test
   void testRoundTripInsets() throws Exception {
      Map<String, Object> original = new HashMap<>();
      original.put("padding", new Insets(10, 20, 30, 40));

      String json = objectMapper.writeValueAsString(new TypedPropertyMapWrapper(original));
      TypedPropertyMapWrapper result = objectMapper.readValue(json, TypedPropertyMapWrapper.class);

      assertEquals(original.get("padding"), result.getValues().get("padding"));
   }

   @Test
   void testRoundTripRectangle() throws Exception {
      Map<String, Object> original = new HashMap<>();
      original.put("bounds", new Rectangle(10, 20, 300, 400));

      String json = objectMapper.writeValueAsString(new TypedPropertyMapWrapper(original));
      TypedPropertyMapWrapper result = objectMapper.readValue(json, TypedPropertyMapWrapper.class);

      assertEquals(original.get("bounds"), result.getValues().get("bounds"));
   }

   @Test
   void testRoundTripString() throws Exception {
      Map<String, Object> original = new HashMap<>();
      original.put("name", "test value");

      String json = objectMapper.writeValueAsString(new TypedPropertyMapWrapper(original));
      TypedPropertyMapWrapper result = objectMapper.readValue(json, TypedPropertyMapWrapper.class);

      assertEquals(original.get("name"), result.getValues().get("name"));
   }

   @Test
   void testRoundTripNullValue() throws Exception {
      Map<String, Object> original = new HashMap<>();
      original.put("nullKey", null);
      original.put("nonNullKey", "value");

      String json = objectMapper.writeValueAsString(new TypedPropertyMapWrapper(original));
      TypedPropertyMapWrapper result = objectMapper.readValue(json, TypedPropertyMapWrapper.class);

      assertTrue(result.getValues().containsKey("nullKey"));
      assertNull(result.getValues().get("nullKey"));
      assertEquals("value", result.getValues().get("nonNullKey"));
   }

   @Test
   void testRoundTripMixedTypes() throws Exception {
      Map<String, Object> original = new HashMap<>();
      original.put("dimension", new Dimension(100, 200));
      original.put("point", new Point(50, 75));
      original.put("scale", new Point2D.Double(1.25, 1.5));
      original.put("insets", new Insets(5, 10, 15, 20));
      original.put("rect", new Rectangle(0, 0, 500, 400));
      original.put("text", "hello");
      original.put("number", 42);
      original.put("empty", null);

      String json = objectMapper.writeValueAsString(new TypedPropertyMapWrapper(original));
      TypedPropertyMapWrapper result = objectMapper.readValue(json, TypedPropertyMapWrapper.class);

      assertEquals(original.get("dimension"), result.getValues().get("dimension"));
      assertEquals(original.get("point"), result.getValues().get("point"));
      assertEquals(original.get("scale"), result.getValues().get("scale"));
      assertEquals(original.get("insets"), result.getValues().get("insets"));
      assertEquals(original.get("rect"), result.getValues().get("rect"));
      assertEquals(original.get("text"), result.getValues().get("text"));
      assertEquals(original.get("number"), result.getValues().get("number"));
      assertNull(result.getValues().get("empty"));
   }

   @Test
   void testDeserializeMissingClassField() throws Exception {
      // JSON with missing @class field - should return null for that entry
      String json = "{\"key1\":{\"value\":123}}";
      TypedPropertyMapWrapper result = objectMapper.readValue(json, TypedPropertyMapWrapper.class);

      assertNull(result.getValues().get("key1"));
   }

   @Test
   void testDeserializeMissingValueField() throws Exception {
      // JSON with missing value field - should return null for that entry
      String json = "{\"key1\":{\"@class\":\"java.lang.Integer\"}}";
      TypedPropertyMapWrapper result = objectMapper.readValue(json, TypedPropertyMapWrapper.class);

      assertNull(result.getValues().get("key1"));
   }

   @Test
   void testDeserializeUnknownClass() throws Exception {
      // JSON with unknown class name - should return null (logged as warning)
      String json = "{\"key1\":{\"@class\":\"com.nonexistent.FakeClass\",\"value\":123}}";
      TypedPropertyMapWrapper result = objectMapper.readValue(json, TypedPropertyMapWrapper.class);

      // Unknown classes are silently skipped (with warning log)
      assertNull(result.getValues().get("key1"));
   }

   @Test
   void testDeserializeDisallowedClass() throws Exception {
      // JSON with class not in allowlist - should return null (logged as warning)
      String json = "{\"key1\":{\"@class\":\"java.util.ArrayList\",\"value\":[]}}";
      TypedPropertyMapWrapper result = objectMapper.readValue(json, TypedPropertyMapWrapper.class);

      // Disallowed classes are silently skipped (with warning log)
      assertNull(result.getValues().get("key1"));
   }

   @Test
   void testDeserializeFieldOrderReversed() throws Exception {
      // JSON with value before @class - should still work (field order independent)
      String json = "{\"key1\":{\"value\":{\"width\":100,\"height\":200},\"@class\":\"java.awt.Dimension\"}}";
      TypedPropertyMapWrapper result = objectMapper.readValue(json, TypedPropertyMapWrapper.class);

      assertEquals(new Dimension(100, 200), result.getValues().get("key1"));
   }

   @Test
   void testEmptyMap() throws Exception {
      Map<String, Object> original = new HashMap<>();

      String json = objectMapper.writeValueAsString(new TypedPropertyMapWrapper(original));
      TypedPropertyMapWrapper result = objectMapper.readValue(json, TypedPropertyMapWrapper.class);

      assertTrue(result.getValues().isEmpty());
   }

   private ObjectMapper objectMapper;
}
