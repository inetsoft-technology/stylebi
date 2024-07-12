/*
 * tern-annotations - StyleBI is a business intelligence web application.
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
package com.inetsoft.build.tern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auto.common.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.*;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("UnstableApiUsage")
@SupportedOptions({ "tern.baseUrl", "tern.outputFile", "tern.skip" })
@SupportedAnnotationTypes({ "com.inetsoft.build.tern.*" })
public class TernAnnotationProcessor extends AbstractProcessor {
   @Override
   public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
   }

   @Override
   public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if(!"true".equals(processingEnv.getOptions().get("tern.skip"))) {
         try {
            if(roundEnv.processingOver()) {
               generateDefinitionFile();
            }
            else {
               processAnnotations(roundEnv);
            }
         }
         catch(Exception e) {
            logError(e);
         }
      }

      return false;
   }

   private void processAnnotations(RoundEnvironment roundEnv) {
      String baseUrl = processingEnv.getOptions().get("tern.baseUrl");
      Map<String, Set<String>> superClasses = new HashMap<>();

      for(Element element : roundEnv.getElementsAnnotatedWith(TernClass.class)) {
         TypeElement classType = MoreElements.asType(element);
         String className = classType.getSimpleName().toString();
         String fullClassName = getClassName(classType);

         AnnotationMirror annotation =
            MoreElements.getAnnotationMirror(element, TernClass.class).get();
         String url = (String)
            AnnotationMirrors.getAnnotationValue(annotation, "url").getValue();

         ClassDef classDef = new ClassDef(className, url.isEmpty() ? null : baseUrl + url);
         definitions.put(fullClassName, classDef);

         TypeMirror superType = classType.getSuperclass();

         while(superType.getKind() != TypeKind.NONE) {
            TypeElement superElement = MoreTypes.asTypeElement(superType);
            String superName = superElement.getSimpleName().toString();
            superClasses.computeIfAbsent(superName, k -> new HashSet<>()).add(fullClassName);
            superType = superElement.getSuperclass();
         }
      }

      Set<String> nestedNames = definitions.keySet().stream()
         .filter(s -> s.contains("."))
         .collect(Collectors.toSet());

      for(String nestedName : nestedNames) {
         int index = nestedName.indexOf('.');
         String parentName = nestedName.substring(0, index);
         ClassDef child = definitions.get(nestedName);
         ClassDef parent = definitions.computeIfAbsent(parentName, k -> new ClassDef(k, null));
         parent.addNestedClass(child);
      }

      for(Element element : roundEnv.getElementsAnnotatedWith(TernConstructor.class)) {
         ExecutableElement cstrElement = MoreElements.asExecutable(element);
         ClassDef classDef = definitions.get(getEnclosingClassName(cstrElement));

         if(classDef != null) {
            classDef.setType(getType(cstrElement));
         }
      }

      for(Element element : roundEnv.getElementsAnnotatedWith(TernMethod.class)) {
         ExecutableElement methodElement = MoreElements.asExecutable(element);
         String methodName = methodElement.getSimpleName().toString();

         AnnotationMirror annotation =
            MoreElements.getAnnotationMirror(element, TernMethod.class).get();
         String url = (String)
            AnnotationMirrors.getAnnotationValue(annotation, "url").getValue();

         MethodDef methodDef =
            new MethodDef(methodName, getType(methodElement), url.isEmpty() ? null : baseUrl + url);

         if(methodElement.getModifiers().contains(Modifier.STATIC)) {
            String className = getEnclosingClassName(element);
            ClassDef classDef = definitions.computeIfAbsent(className, k -> new ClassDef(k, null));
            classDef.addStaticMethod(methodDef);
         }
         else {
            for(ClassDef classDef : getEnclosingClasses(element, superClasses)) {
               classDef.addMemberMethod(methodDef);
            }
         }
      }

      for(Element element : roundEnv.getElementsAnnotatedWith(TernField.class)) {
         VariableElement fieldElement = MoreElements.asVariable(element);
         String fieldName = fieldElement.getSimpleName().toString();

         AnnotationMirror annotation =
            MoreElements.getAnnotationMirror(element, TernField.class).get();
         String url = (String)
            AnnotationMirrors.getAnnotationValue(annotation, "url").getValue();

         FieldDef fieldDef = new FieldDef(
            fieldName, getType(fieldElement.asType()), url.isEmpty() ? null : baseUrl + url);

         if(fieldElement.getModifiers().contains(Modifier.STATIC)) {
            String className = getEnclosingClassName(element);
            ClassDef classDef = definitions.computeIfAbsent(className, k -> new ClassDef(k, null));
            classDef.addStaticField(fieldDef);
         }
         else {
            for(ClassDef classDef : getEnclosingClasses(element, superClasses)) {
               classDef.addMemberField(fieldDef);
            }
         }
      }

      definitions.keySet().removeIf(key -> key.contains("."));
   }

   private void generateDefinitionFile() {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode definitionRoot = mapper.createObjectNode();

      for(Map.Entry<String, ClassDef> e : definitions.entrySet()) {
         definitionRoot.set(e.getKey(), e.getValue().toJson(mapper));
      }

      try {
         FileObject file = processingEnv.getFiler().createResource(
            StandardLocation.CLASS_OUTPUT, "",
            processingEnv.getOptions().get("tern.outputFile"));

         try(OutputStream output = file.openOutputStream()) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(output, definitionRoot);
         }

         logInfo("Wrote " + file.toUri());
      }
      catch(Exception e) {
         logError("Failed to create definition file", e);
      }
   }

   private void logInfo(String message) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
   }

   private void logWarning(String message, Element element, AnnotationMirror annotation) {
      processingEnv.getMessager()
         .printMessage(Diagnostic.Kind.WARNING, message, element, annotation);
   }

   private void logError(Throwable e) {
      logError(getStackTrace(e));
   }

   private void logError(String message, Throwable e) {
      logError(String.format("%s%n%s", message, getStackTrace(e)));
   }

   private void logError(String message) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
   }

   private void logError(String message, Element element, AnnotationMirror annotation) {
      processingEnv.getMessager()
         .printMessage(Diagnostic.Kind.ERROR, message, element, annotation);
   }

   private String getStackTrace(Throwable e) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      e.printStackTrace(writer);
      writer.flush();
      return buffer.toString();
   }

   private String getClassName(Element element) {
      TypeElement typeElement = MoreElements.asType(element);
      String name = typeElement.getSimpleName().toString();

      if(typeElement.getNestingKind() == NestingKind.MEMBER) {
         name = getEnclosingClassName(typeElement) + "." + name;
      }

      return name;
   }

   private String getEnclosingClassName(Element element) {
      Element classElement = element.getEnclosingElement();
      TypeElement classType = MoreElements.asType(classElement);
      String name = classType.getSimpleName().toString();

      if(classType.getNestingKind() == NestingKind.MEMBER) {
         name = name + "." + getEnclosingClassName(classType);
      }

      return name;
   }

   private Set<ClassDef> getEnclosingClasses(Element element, Map<String, Set<String>> superClasses) {
      Set<ClassDef> results = new HashSet<>();

      String className = getEnclosingClassName(element);

      if(className != null) {
         ClassDef classDef = definitions.get(className);

         if(classDef != null) {
            results.add(classDef);
         }

         if(superClasses.containsKey(className)) {
            for(String subclassName : superClasses.get(className)) {
               classDef = definitions.get(subclassName);

               if(classDef != null) {
                  results.add(classDef);
               }
            }
         }
      }

      return results;
   }

   private String getType(ExecutableElement element) {
      StringBuilder type = new StringBuilder().append("fn(");
      type.append(element.getParameters().stream()
                     .map(this::getType)
                     .collect(Collectors.joining(", ")));

      type.append(')');
      TypeMirror returnMirror = element.getReturnType();

      if(returnMirror.getKind() != TypeKind.NONE && returnMirror.getKind() != TypeKind.VOID) {
         type.append(" -> ").append(getType(returnMirror));
      }

      return type.toString();
   }

   private String getType(VariableElement element) {
      return element.getSimpleName().toString() +
         ": " +
         getType(element.asType());
   }

   private String getType(TypeMirror typeMirror) {
      switch(typeMirror.getKind()) {
      case BOOLEAN:
         return "bool";
      case BYTE:
      case SHORT:
      case INT:
      case LONG:
      case CHAR:
      case FLOAT:
      case DOUBLE:
         return "number";
      case ARRAY:
         ArrayType arrayType = MoreTypes.asArray(typeMirror);
         return "[" + getType(arrayType.getComponentType()) + "]";
      case DECLARED:
         return getDeclaredType(typeMirror);
      default:
         return "?";
      }
   }

   private String getDeclaredType(TypeMirror mirror) {
      String name = MoreTypes.asTypeElement(mirror).getSimpleName().toString();

      switch(name) {
      case "String":
         return "string";
      case "Boolean":
         return "bool";
      case "Byte":
      case "Short":
      case "Integer":
      case "Long":
      case "Float":
      case "Double":
         return "number";
      default:
         return definitions.containsKey(name) ? "+" + name : "?";
      }
   }

   private final Map<String, ClassDef> definitions = new TreeMap<>();
}
