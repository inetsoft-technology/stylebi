/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */
package inetsoft.storage.mapdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mapdb.*;

import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
public class MapDBKeyValueEngineTests {
   @TempDir
   Path dir;

   private MapDBKeyValueEngine engine;
   private static Map<String, TestData> expectedUsers;

   @BeforeAll
   static void readUsers() throws Exception {
      expectedUsers = new HashMap<>();

      try(InputStream input = MapDBKeyValueEngineTests.class.getResourceAsStream("users.json")) {
         ObjectMapper objectMapper = new ObjectMapper();
         ObjectNode root = (ObjectNode) objectMapper.readTree(input);

         for(Iterator<String> i = root.fieldNames(); i.hasNext();) {
            String name = i.next();
            TestData value = objectMapper.convertValue(root.get(name), TestData.class);
            expectedUsers.put(name, value);
         }
      }
   }

   @BeforeEach
   void createEngine() {
      createCollection(dir.resolve("users.db"));
      engine = new MapDBKeyValueEngine(dir);
   }

   @AfterEach
   void closeEngine() throws Exception {
      engine.close();
      FileUtils.deleteDirectory(dir.toFile());
   }

   @Test
   void containsShouldReturnTrueWhenExists() {
      assertTrue(engine.contains("users", "maia.vonrueden"));
   }

   @Test
   void containsShouldReturnFalseWhenMissing() {
      assertFalse(engine.contains("users", "john.smith"));
   }

   @Test
   void getShouldReturnValueWhenExists() {
      TestData expected = expectedUsers.get("maia.vonrueden");
      assertEquals(expected, engine.get("users", "maia.vonrueden"));
   }

   @Test
   void getShouldReturnNullWhenMissing() {
      assertNull(engine.get("users", "john.smith"));
   }

   @Test
   void putShouldReturnValueWhenExists() {
      TestData expected = expectedUsers.get("maia.vonrueden");
      TestData updated = new TestData();
      updated.setUsername("maia.vonrueden");
      updated.setFirstName("John");
      updated.setLastName("Smith");
      updated.setEmail("john.smith@example.com");
      TestData actual = engine.put("users", "maia.vonrueden", updated);
      assertEquals(expected, actual);
      actual = engine.get("users", "maia.vonrueden");
      assertEquals(updated, actual);
   }

   @Test
   void putShouldReturnNullWhenMissing() {
      TestData expected = new TestData();
      expected.setUsername("john.smith");
      expected.setFirstName("John");
      expected.setLastName("Smith");
      expected.setEmail("john.smith@example.com");
      assertNull(engine.put("users", "john.smith", expected));
      TestData actual = engine.get("users", "john.smith");
      assertEquals(expected, actual);
   }

   @Test
   void removeShouldReturnValueWhenExists() {
      TestData expected = expectedUsers.get("maia.vonrueden");
      TestData actual = engine.remove("users", "maia.vonrueden");
      assertEquals(expected, actual);
      assertFalse(engine.contains("id", "maia.vonrueden"));
   }

   @Test
   void removeShouldReturnNullWhenMissing() {
      assertNull(engine.remove("users", "john.smith"));
   }

   @Test
   void streamShouldReturnValues() {
      Map<String, TestData> actualUsers = new HashMap<>();
      engine.<TestData>stream("users").forEach(p -> actualUsers.put(p.getKey(), p.getValue()));
      MapDifference<String, TestData> diff = Maps.difference(expectedUsers, actualUsers);
      assertTrue(diff.areEqual(), () -> "Differing entries: " + diff.entriesDiffering());
   }

   @Test
   void idStreamShouldReturnValues() {
      List<String> ids = engine.idStream().collect(Collectors.toList());
      assertEquals(1, ids.size());
      assertTrue(ids.contains("users"));
   }

   @Test
   void shouldCreateCollectionIfMissing() throws Exception {
      try(MapDBKeyValueEngine temp = new MapDBKeyValueEngine(dir.resolve("temp.db"))) {
         TestData expected = new TestData();
         expected.setUsername("john.smith");
         expected.setFirstName("John");
         expected.setLastName("Smith");
         expected.setEmail("john.smith@example.com");
         assertNull(temp.put("users", "john.smith", expected));
         TestData actual = temp.get("users", "john.smith");
         assertEquals(expected, actual);
      }
   }

   @SuppressWarnings({ "rawtypes", "unchecked" })
   private static void createCollection(Path file) {
      try(DB db = DBMaker.fileDB(file.toFile()).transactionEnable().fileChannelEnable().make()) {
         ConcurrentMap map =
            db.hashMap("users", Serializer.STRING, Serializer.ELSA).createOrOpen();
         map.putAll(expectedUsers);
         db.commit();
         db.getStore().compact();
      }
   }

   @SuppressWarnings("unused")
   static final class TestData implements Serializable {
      public String getUsername() {
         return username;
      }

      public void setUsername(String username) {
         this.username = username;
      }

      public String getFirstName() {
         return firstName;
      }

      public void setFirstName(String firstName) {
         this.firstName = firstName;
      }

      public String getLastName() {
         return lastName;
      }

      public void setLastName(String lastName) {
         this.lastName = lastName;
      }

      public String getEmail() {
         return email;
      }

      public void setEmail(String email) {
         this.email = email;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         TestData testData = (TestData) o;
         return Objects.equals(username, testData.username) &&
            Objects.equals(firstName, testData.firstName) &&
            Objects.equals(lastName, testData.lastName) &&
            Objects.equals(email, testData.email);
      }

      @Override
      public int hashCode() {
         return Objects.hash(username, firstName, lastName, email);
      }

      @Override
      public String toString() {
         return "TestData{" +
            "username='" + username + '\'' +
            ", firstName='" + firstName + '\'' +
            ", lastName='" + lastName + '\'' +
            ", email='" + email + '\'' +
            '}';
      }

      private String username;
      private String firstName;
      private String lastName;
      private String email;
   }
}
