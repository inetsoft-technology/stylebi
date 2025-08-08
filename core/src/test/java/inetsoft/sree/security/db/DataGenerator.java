/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.security.db;

import at.favre.lib.crypto.bcrypt.BCrypt;
import net.datafaker.Faker;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonFactory;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonGenerator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class DataGenerator {
   private final Faker faker = new Faker();
   private static final String[] GROUPS = {
      "Sales", "Marketing", "Customer Support", "IT", "QA", "Development", "Finance", "HR", "Legal",
      "Operations", "Accounting", "Procurement", "Shipping", "Purchasing"
   };

   public void generate() throws Exception{
      File dir = new File("community/core/src/test/resources/inetsoft/sree/security/db");
      File sqlFile = new File(dir, "multitenant.sql");
      File jsonFile = new File(dir, "multitenant.json");

      try(PrintWriter writer = new PrintWriter(Files.newBufferedWriter(sqlFile.toPath(), StandardCharsets.UTF_8));
          BufferedWriter json = new BufferedWriter(Files.newBufferedWriter(jsonFile.toPath(), StandardCharsets.UTF_8));
          JsonGenerator generator = new JsonFactory().createGenerator(json).useDefaultPrettyPrinter())
      {
         generator.writeStartObject();

         writeDDL(writer);
         writeHostOrg(generator, writer);

         for(int i = 0; i < 5; i++) {
            generateOrganization(generator, writer);
         }

         generator.writeEndObject();
      }
   }

   public static void main(String[] args) {
      try {
         new DataGenerator().generate();
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
   }

   private void writeDDL(PrintWriter writer) {
      writer.println("""
                        CREATE TABLE INETSOFT_ORG (
                          ORG_ID VARCHAR(255) PRIMARY KEY,
                          ORG_NAME VARCHAR(255) NOT NULL);""");
      writer.println();
      writer.println("""
                        CREATE TABLE INETSOFT_USER (
                          ORG_ID VARCHAR(255) NOT NULL,
                          USER_NAME VARCHAR(255) NOT NULL,
                          EMAIL VARCHAR(255),
                          PW_HASH VARCHAR(255) NOT NULL,
                          PRIMARY KEY (ORG_ID, USER_NAME),
                          FOREIGN KEY (ORG_ID) REFERENCES INETSOFT_ORG (ORG_ID));""");
      writer.println();
      writer.println("""
                        CREATE TABLE INETSOFT_ROLE (
                          ORG_ID VARCHAR(255) NOT NULL,
                          ROLE_NAME VARCHAR(255) NOT NULL,
                          PRIMARY KEY (ORG_ID, ROLE_NAME),
                          FOREIGN KEY (ORG_ID) REFERENCES INETSOFT_ORG (ORG_ID));""");
      writer.println();
      writer.println("""
                        CREATE TABLE INETSOFT_GROUP (
                          ORG_ID VARCHAR(255) NOT NULL,
                          GROUP_NAME VARCHAR(255) NOT NULL,
                          PRIMARY KEY (ORG_ID, GROUP_NAME),
                          FOREIGN KEY (ORG_ID) REFERENCES INETSOFT_ORG (ORG_ID));""");
      writer.println();
      writer.println("""
                        CREATE TABLE INETSOFT_USER_ROLE (
                          ORG_ID VARCHAR(255) NOT NULL,
                          USER_NAME VARCHAR(255) NOT NULL,
                          ROLE_NAME VARCHAR(255),
                          PRIMARY KEY (ORG_ID, USER_NAME, ROLE_NAME),
                          FOREIGN KEY (ORG_ID) REFERENCES INETSOFT_ORG (ORG_ID),
                          FOREIGN KEY (ORG_ID, USER_NAME) REFERENCES INETSOFT_USER (ORG_ID, USER_NAME),
                          FOREIGN KEY (ORG_ID, ROLE_NAME) REFERENCES INETSOFT_ROLE (ORG_ID, ROLE_NAME));""");
      writer.println();
      writer.println("""
                        CREATE TABLE INETSOFT_GROUP_USER (
                          ORG_ID VARCHAR(255) NOT NULL,
                          GROUP_NAME VARCHAR(255) NOT NULL,
                          USER_NAME VARCHAR(255),
                          PRIMARY KEY (ORG_ID, GROUP_NAME, USER_NAME),
                          FOREIGN KEY (ORG_ID) REFERENCES INETSOFT_ORG (ORG_ID),
                          FOREIGN KEY (ORG_ID, GROUP_NAME) REFERENCES INETSOFT_GROUP (ORG_ID, GROUP_NAME),
                          FOREIGN KEY (ORG_ID, USER_NAME) REFERENCES INETSOFT_USER (ORG_ID, USER_NAME));""");
      writer.println();
   }

   private void writeHostOrg(JsonGenerator json, PrintWriter writer) throws Exception {
      String id = "host-org";
      String name = "Host Organization";

      writeOrganization(id, name, writer);

      json.writeObjectFieldStart(id);
      json.writeStringField("id", id);
      json.writeStringField("name", name);

      json.writeObjectFieldStart("users");
      writeUser(id, "admin", "admin@inetsoft.com", "admin", hashPassword("admin"), json, writer);
      json.writeEndObject();

      json.writeObjectFieldStart("roles");
      writeRole(id, "Site Admin", json, writer);
      writeRole(id, "Org Admin", json, writer);
      json.writeEndObject();

      json.writeObjectFieldStart("groups");
      json.writeEndObject();

      json.writeObjectFieldStart("userRoles");
      writeUserRoles(id, "admin", json, writer, "Site Admin");
      json.writeEndObject();

      json.writeObjectFieldStart("groupUsers");
      json.writeEndObject();

      json.writeEndObject();
      writer.println();
   }

   private void generateOrganization(JsonGenerator json, PrintWriter writer) throws Exception {
      String name = faker.company().name();
      String id = toId(name);
      writeOrganization(id, name, writer);
      json.writeObjectFieldStart(id);
      json.writeStringField("id", id);
      json.writeStringField("name", name);

      json.writeObjectFieldStart("users");
      List<String> users = new ArrayList<>();
      String adminUser = generateUser(id, json, writer);

      for(int i = 0; i < 12; i++) {
         users.add(generateUser(id, json, writer));
      }

      json.writeEndObject();

      json.writeObjectFieldStart("roles");
      List<String> roles = new ArrayList<>();
      writeRole(id, "Org Admin", json, writer);

      for(int i = 0; i < 4; i++) {
         roles.add(generateRole(id, roles, json, writer));
      }

      json.writeEndObject();

      json.writeObjectFieldStart("groups");
      List<String> groups = new ArrayList<>();

      for(int i = 0; i < 3; i++) {
         groups.add(generateGroup(id, groups, json, writer));
      }

      json.writeEndObject();

      json.writeObjectFieldStart("userRoles");
      writeUserRoles(id, adminUser, json, writer, "Org Admin");

      int i = 0;

      for(String user : users) {
         String role1 = roles.get(i);
         i = i == roles.size() - 1 ? 0 : i + 1;
         String role2 = roles.get(i);
         i = i == roles.size() - 1 ? 0 : i + 1;
         writeUserRoles(id, user, json, writer, role1, role2);
      }

      json.writeEndObject();

      Map<String, List<String>> groupUsers = new HashMap<>();
      json.writeObjectFieldStart("groupUsers");

      i = 0;

      for(String user : users) {
         String group = groups.get(i);
         groupUsers.computeIfAbsent(group, k -> new ArrayList<>()).add(user);
         i = i == groups.size() - 1 ? 0 : i + 1;
      }

      for(Map.Entry<String, List<String>> entry : groupUsers.entrySet()) {
         writeGroupUsers(id, entry.getKey(), entry.getValue(), json, writer);
      }

      json.writeEndObject();
      json.writeEndObject();
      writer.println();
   }

   private void writeOrganization(String id, String name, PrintWriter writer) {
      writer.print("INSERT INTO INETSOFT_ORG (ORG_ID, ORG_NAME) VALUES ('");
      writer.print(id);
      writer.print("', '");
      escape(name, writer);
      writer.println("');");
   }

   private void writeUserRoles(String orgId, String user, JsonGenerator json, PrintWriter writer,
                               String... roles) throws Exception
   {
      for(String role : roles) {
         writer.print("INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('");
         writer.print(orgId);
         writer.print("', '");
         escape(user, writer);
         writer.print("', '");
         escape(role, writer);
         writer.println("');");
      }

      json.writeArrayFieldStart(user);

      for(String role : roles) {
         json.writeString(role);
      }

      json.writeEndArray();
   }

   private void writeGroupUsers(String orgId, String group, List<String> users, JsonGenerator json,
                                PrintWriter writer) throws Exception
   {
      for(String user : users) {
         writer.print("INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('");
         writer.print(orgId);
         writer.print("', '");
         escape(group, writer);
         writer.print("', '");
         escape(user, writer);
         writer.println("');");
      }

      json.writeArrayFieldStart(group);

      for(String user : users) {
         json.writeString(user);
      }

      json.writeEndArray();
   }

   private String generateUser(String orgId, JsonGenerator json, PrintWriter writer) throws Exception {
      String name = faker.internet().username();
      String email = faker.internet().emailAddress();
      String password = faker.internet().password();
      String hash = hashPassword(password);
      writeUser(orgId, name, email, password, hash, json, writer);
      return name;
   }

   private String hashPassword(String password) {
      return new String(
         BCrypt.withDefaults().hashToChar(10, password.toCharArray()));
   }

   private void writeUser(String orgId, String name, String email, String password,
                          String hash, JsonGenerator json, PrintWriter writer) throws Exception
   {
      writer.print("INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('");
      writer.print(orgId);
      writer.print("', '");
      escape(name, writer);
      writer.print("', '");
      escape(email, writer);
      writer.print("', '");
      escape(hash, writer);
      writer.println("');");

      json.writeObjectFieldStart(name);
      json.writeStringField("name", name);
      json.writeStringField("email", email);
      json.writeStringField("password", password);
      json.writeStringField("passwordHash", hash);
      json.writeEndObject();
   }

   private String generateRole(String orgId, List<String> existing, JsonGenerator json,
                               PrintWriter writer) throws Exception
   {
      String name = faker.job().position();

      while(existing.contains(name)) {
         name = faker.job().position();
      }

      writeRole(orgId, name, json, writer);
      return name;
   }

   private void writeRole(String orgId, String name, JsonGenerator json, PrintWriter writer)
      throws Exception
   {
      writer.print("INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('");
      writer.print(orgId);
      writer.print("', '");
      escape(name, writer);
      writer.println("');");

      json.writeObjectFieldStart(name);
      json.writeStringField("name", name);
      json.writeEndObject();
   }

   private String generateGroup(String orgId, List<String> existing, JsonGenerator json,
                                PrintWriter writer) throws Exception
   {
      String groupName = groupName();

      while(existing.contains(groupName)) {
         groupName = groupName();
      }

      writeGroup(orgId, groupName, json, writer);
      return groupName;
   }

   private void writeGroup(String orgId, String name, JsonGenerator json, PrintWriter writer)
      throws Exception
   {
      writer.print("INSERT INTO INETSOFT_GROUP (ORG_ID, GROUP_NAME) VALUES ('");
      writer.print(orgId);
      writer.print("', '");
      escape(name, writer);
      writer.println("');");

      json.writeObjectFieldStart(name);
      json.writeStringField("name", name);
      json.writeEndObject();
   }

   private String groupName() {
      return GROUPS[faker.random().nextInt(GROUPS.length)];
   }

   private void escape(String s, PrintWriter writer) {
      for(int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);

         if(c == '\'') {
            writer.write("''");
         }
         else {
            writer.write(c);
         }
      }
   }

   private String toId(String s) {
      boolean isFirst = true;
      boolean lastWasDash = false;
      StringBuilder id = new StringBuilder();

      for(char c : s.toLowerCase().toCharArray()) {
         if(Character.isAlphabetic(c) || Character.isDigit(c)) {
            isFirst = false;
            lastWasDash = false;
            id.append(c);
         }
         else {
            if(!isFirst && !lastWasDash) {
               id.append('-');
               lastWasDash = true;
            }
         }
      }

      return id.toString();
   }
}
