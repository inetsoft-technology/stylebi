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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
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
         ternClasses.add(fullClassName);

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
            for(ClassDef classDef : getEnclosingClasses(element)) {
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
            for(ClassDef classDef : getEnclosingClasses(element)) {
               classDef.addMemberField(fieldDef);
            }
         }
      }

      definitions.keySet().removeIf(key -> key.contains("."));
   }

   private void generateDefinitionFile() {
      ObjectMapper mapper = new ObjectMapper();

      try {
         FileObject file = processingEnv.getFiler().createResource(
            StandardLocation.CLASS_OUTPUT, "",
            processingEnv.getOptions().get("tern.outputFile"));

         // Incremental compilation only presents the classes recompiled in this round,
         // so this processor would otherwise overwrite the definition file with just that
         // subset, dropping definitions for every class that wasn't recompiled (breaking
         // script auto-complete for them). Seed the output with whatever was generated on a
         // prior (e.g. clean) build and overlay the current round's classes on top so
         // definitions accumulate instead of being clobbered. createResource() does not
         // truncate the file until openOutputStream() is called, so the previous content is
         // still readable here; read it with plain java.io to avoid the Filer restriction
         // against reading and (re)creating the same path in one round. A clean build has no
         // prior file and simply starts fresh. Trade-off: a class removed or renamed leaves a
         // stale entry in an incrementally-built file until the next clean build.
         ObjectNode definitionRoot = readExistingDefinitions(mapper, file);
         Set<String> priorUnions = readUnionMarkers(definitionRoot);

         for(Map.Entry<String, ClassDef> e : definitions.entrySet()) {
            definitionRoot.set(e.getKey(), e.getValue().toJson(mapper));
         }

         addUnionDefinitions(mapper, definitionRoot, priorUnions);

         try(OutputStream output = file.openOutputStream()) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(output, definitionRoot);
         }

         logInfo("Wrote " + file.toUri());
      }
      catch(Exception e) {
         logError("Failed to create definition file", e);
      }
   }

   private ObjectNode readExistingDefinitions(ObjectMapper mapper, FileObject file) {
      try {
         File existing = new File(file.toUri());

         if(existing.isFile()) {
            JsonNode node = mapper.readTree(existing);

            if(node instanceof ObjectNode) {
               return (ObjectNode) node;
            }
         }
      }
      catch(Exception e) {
         // no readable prior file (e.g. clean build) - start fresh
      }

      return mapper.createObjectNode();
   }

   /**
    * Emit a synthetic definition for each unannotated base class that is referenced as a
    * type, merging into it the members of every @TernClass subclass. Without this a method
    * declared to return an abstract base - EGraph.getCoordinate(), RectCoord.getXScale() -
    * is typed "?", so tern resolves nothing past it and auto-complete breaks for the whole
    * chain (Bug #75694). Members that do not apply to the concrete instance are included by
    * design: the alternative is offering nothing at all.
    *
    * This runs against the merged definition set (the previously generated definitions plus
    * this round's) so an incremental compile cannot shrink a union down to only the
    * subclasses that happened to be recompiled.
    */
   private void addUnionDefinitions(ObjectMapper mapper, ObjectNode definitionRoot,
                                    Set<String> priorUnions)
   {
      Set<String> unions = new TreeSet<>(priorUnions);

      for(String base : unionBases) {
         // a real @TernClass definition always takes precedence over a synthetic union
         if(isTernClass(base, definitionRoot, priorUnions)) {
            continue;
         }

         Set<String> subclasses = superClasses.get(base);

         if(subclasses == null) {
            continue;
         }

         ObjectNode prototype = mapper.createObjectNode();

         // sorted so that which subclass wins a name collision stays stable between builds
         for(String subclass : new TreeSet<>(subclasses)) {
            JsonNode subclassNode = definitionRoot.get(subclass);

            if(subclassNode == null) {
               continue;
            }

            JsonNode members = subclassNode.get("prototype");

            if(!(members instanceof ObjectNode)) {
               continue;
            }

            for(Iterator<Map.Entry<String, JsonNode>> it = members.fields(); it.hasNext(); ) {
               Map.Entry<String, JsonNode> member = it.next();

               if(!prototype.has(member.getKey())) {
                  prototype.set(member.getKey(), member.getValue().deepCopy());
               }
            }
         }

         if(prototype.size() == 0) {
            continue;
         }

         JsonNode existing = definitionRoot.get(base);
         ObjectNode node;

         if(existing instanceof ObjectNode) {
            node = (ObjectNode) existing;
         }
         else {
            node = mapper.createObjectNode();
            node.put("!type", "fn()");
            definitionRoot.set(base, node);
         }

         JsonNode declared = node.get("prototype");

         // anything already declared on the base itself wins over a subclass member
         if(declared instanceof ObjectNode) {
            prototype.setAll((ObjectNode) declared);
         }

         node.set("prototype", prototype);
         unions.add(base);
      }

      if(!unions.isEmpty()) {
         ArrayNode marker = mapper.createArrayNode();
         unions.forEach(marker::add);
         definitionRoot.set(UNION_MARKER, marker);
      }
   }

   /**
    * Check whether a name belongs to a class declared with @TernClass, whose own definition
    * must never be replaced by a synthetic union.
    *
    * @TernClass has SOURCE retention, so a base class whose source was not recompiled in this
    * round cannot be tested for the annotation directly - it is not even in this round's
    * definitions. A definition carried over from a previous build that this processor did not
    * synthesize is therefore treated as a real @TernClass definition and left alone; without
    * that check an incremental compile could overwrite, say, LinearScale with a union built
    * from whichever of its subclasses happened to be recompiled.
    */
   private boolean isTernClass(String base, ObjectNode definitionRoot, Set<String> priorUnions) {
      if(ternClasses.contains(base)) {
         return true;
      }

      if(definitions.containsKey(base)) {
         // known this round, and not annotated: the entry exists only because it was
         // auto-created to carry static members (@TernField on an unannotated base)
         return false;
      }

      return definitionRoot.has(base) && !priorUnions.contains(base);
   }

   /**
    * Read back the names this processor previously emitted as synthetic unions. tern ignores
    * the marker, which has to live in the definition file itself so that the distinction
    * between a synthesized definition and a real @TernClass one survives across builds.
    */
   private Set<String> readUnionMarkers(ObjectNode definitionRoot) {
      Set<String> markers = new TreeSet<>();
      JsonNode marker = definitionRoot.get(UNION_MARKER);

      if(marker != null && marker.isArray()) {
         marker.forEach(name -> markers.add(name.asText()));
      }

      return markers;
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
      // Delegate to getClassName so nested (member) enclosing classes are qualified
      // parent-first (e.g. "TreemapElement.Orientation"), matching the keys registered
      // for @TernClass. Building the name child-first here would mis-key static members
      // of nested classes (e.g. enum constants).
      return getClassName(element.getEnclosingElement());
   }

   private Set<ClassDef> getEnclosingClasses(Element element) {
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
         if(definitions.containsKey(name)) {
            return "+" + name;
         }

         // The type has no @TernClass of its own - typically an abstract base such as
         // Scale or Coordinate - so it would be emitted as "?", leaving tern unable to
         // infer anything past it and killing auto-complete for the rest of the call
         // chain (Bug #75694). If annotated classes extend it, record it here so a
         // synthetic union definition carrying every subclass member is emitted for it,
         // making it a real tern type.
         if(!isPlatformType(mirror) && superClasses.containsKey(name)) {
            unionBases.add(name);
            return "+" + name;
         }

         return "?";
      }
   }

   /**
    * Check whether a type is a JDK type. Unioning the annotated subclasses of, say,
    * java.lang.Object would produce a meaningless definition holding every annotated
    * class in the project.
    */
   private boolean isPlatformType(TypeMirror mirror) {
      String name = MoreTypes.asTypeElement(mirror).getQualifiedName().toString();
      return name.startsWith("java.") || name.startsWith("javax.");
   }

   private final Map<String, ClassDef> definitions = new TreeMap<>();
   // supertype simple name -> full names of the @TernClass classes extending it
   private final Map<String, Set<String>> superClasses = new HashMap<>();
   // names declared with @TernClass, as opposed to entries auto-created for static members
   private final Set<String> ternClasses = new HashSet<>();
   // unannotated supertypes referenced as a type, emitted as synthetic union definitions
   private final Set<String> unionBases = new TreeSet<>();

   private static final String UNION_MARKER = "!unions";
}
