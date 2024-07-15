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
package com.inetsoft.build.tern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

final class ClassDef {
   public ClassDef(String name, String url) {
      this.name = name;
      this.url = url;
      this.type = "fn()";
   }

   public String getName() {
      return name;
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public String getUrl() {
      return url;
   }

   public Collection<FieldDef> getStaticFields() {
      return staticFields.values();
   }

   public void addStaticField(FieldDef field) {
      staticFields.put(field.getName(), field);
   }

   public Collection<MethodDef> getStaticMethods() {
      return staticMethods.values();
   }

   public void addStaticMethod(MethodDef method) {
      staticMethods.put(method.getName(), method);
   }

   public Collection<FieldDef> getMemberFields() {
      return memberFields.values();
   }

   public void addMemberField(FieldDef field) {
      memberFields.put(field.getName(), field);
   }

   public Collection<MethodDef> getMemberMethods() {
      return memberMethods.values();
   }

   public void addMemberMethod(MethodDef method) {
      memberMethods.put(method.getName(), method);
   }

   public Collection<ClassDef> getNestedClasses() {
      return nestedClasses.values();
   }

   public void addNestedClass(ClassDef nested) {
      nestedClasses.put(nested.getName(), nested);
   }

   public ObjectNode toJson(ObjectMapper mapper) {
      ObjectNode node = mapper.createObjectNode();
      node.put("!type", type);

      if(url != null) {
         node.put("!url", url);
      }

      for(MethodDef method : getStaticMethods()) {
         node.set(method.getName(), method.toJson(mapper));
      }

      for(FieldDef field : getStaticFields()) {
         node.set(field.getName(), field.toJson(mapper));
      }

      ObjectNode prototype = mapper.createObjectNode();
      node.set("prototype", prototype);

      for(MethodDef method : getMemberMethods()) {
         prototype.set(method.getName(), method.toJson(mapper));
      }

      for(FieldDef field : getMemberFields()) {
         prototype.set(field.getName(), field.toJson(mapper));
      }

      for(ClassDef nested : getNestedClasses()) {
         node.set(nested.getName(), nested.toJson(mapper));
      }

      return node;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      ClassDef classDef = (ClassDef) o;
      return Objects.equals(name, classDef.name) && Objects.equals(type, classDef.type) &&
         Objects.equals(url, classDef.url) && Objects.equals(staticFields, classDef.staticFields) &&
         Objects.equals(staticMethods, classDef.staticMethods) &&
         Objects.equals(memberFields, classDef.memberFields) &&
         Objects.equals(memberMethods, classDef.memberMethods) &&
         Objects.equals(nestedClasses, classDef.nestedClasses);
   }

   @Override
   public int hashCode() {
      return Objects.hash(
         name, type, url, staticFields, staticMethods, memberFields, memberMethods, nestedClasses);
   }

   @Override
   public String toString() {
      return "ClassDef{" +
         "name='" + name + '\'' +
         ", type='" + type + '\'' +
         ", url='" + url + '\'' +
         ", staticFields=" + staticFields +
         ", staticMethods=" + staticMethods +
         ", memberFields=" + memberFields +
         ", memberMethods=" + memberMethods +
         ", nestedClasses=" + nestedClasses +
         '}';
   }

   private final String name;
   private String type;
   private final String url;
   private final Map<String, FieldDef> staticFields = new TreeMap<>();
   private final Map<String, MethodDef> staticMethods = new TreeMap<>();
   private final Map<String, FieldDef> memberFields = new TreeMap<>();
   private final Map<String, MethodDef> memberMethods = new TreeMap<>();
   private final Map<String, ClassDef> nestedClasses = new TreeMap<>();
}
