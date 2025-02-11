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

package inetsoft.cluster;

import com.google.auto.common.MoreElements;
import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

@SuppressWarnings("UnstableApiUsage")
@SupportedAnnotationTypes("inetsoft.cluster.ClusterProxy")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@AutoService(Processor.class)
public class ClusterAnnotationProcessor extends AbstractProcessor {
   @Override
   public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if(!roundEnv.processingOver()) {
         try {
            processAnnotations(roundEnv);
         }
         catch(IOException e) {
            throw new RuntimeException("Failed to generate Java sources", e);
         }
      }

      return false;
   }

   private void processAnnotations(RoundEnvironment roundEnv) throws IOException {
      for(Element element : roundEnv.getElementsAnnotatedWith(ClusterProxy.class)) {
         String targetServiceClass = MoreElements.asType(element).getQualifiedName().toString();

         if(processedClasses.contains(targetServiceClass)) {
            continue;
         }

         processedClasses.add(targetServiceClass);
         String proxyClass = targetServiceClass + "ProxyTest";
         JavaFileObject file = processingEnv.getFiler().createSourceFile(proxyClass);

         try(PrintWriter out = new PrintWriter(file.openWriter())) {
            String simpleName = writePackage(proxyClass, out);
            out.println("@javax.annotation.processing.Generated(\"com.inetsoft.build.crd.GenerateCRDProcessor\")");
            out.println("@org.springframework.stereotype.Service");
            out.format("public class %s {%n", simpleName);

            TypeElement targetClass = MoreElements.asType(element);
            targetClass.getEnclosedElements().stream()
               .filter(e -> e.getKind() == ElementKind.METHOD)
               .filter(e -> MoreElements.getAnnotationMirror(e, ClusterProxyMethod.class).isPresent())
               .forEach(e -> writeMethod(targetServiceClass, proxyClass, e, out));

            out.println("}");
         }
      }
   }

   private void writeMethod(String targetServiceName, String proxyClassName, Element methodElement, PrintWriter out) {
      String methodName = methodElement.getSimpleName().toString();
      String callableClass = Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
      String originalCallableClass = callableClass;
      Set<String> callableClassNames = callableClasses.computeIfAbsent(proxyClassName, k -> new HashSet<>());

      for(int i = 1; callableClassNames.contains(callableClass); i++) {
         callableClass = originalCallableClass + i;
      }

      callableClassNames.add(callableClass);
      String returnTypeName = MoreElements.asExecutable(methodElement).getReturnType().toString();

      out.format("   public static final class %s implements org.apache.ignite.lang.IgniteCallable<%s> {%n", callableClass, returnTypeName);
      out.println("      @org.apache.ignite.resources.SpringApplicationContextResource");
      out.println("      private org.springframework.context.ApplicationContext applicationContext;");

      for(VariableElement param : MoreElements.asExecutable(methodElement).getParameters()) {
         out.format("      private final %s %s;%n", param.asType(), param.getSimpleName().toString());
      }

      out.println();
      out.format("      public %s(", callableClass);
      boolean first = true;

      for(VariableElement param : MoreElements.asExecutable(methodElement).getParameters()) {
         if(first) {
            first = false;
         }
         else {
            out.print(", ");
         }

         out.format("%s %s", param.asType(), param.getSimpleName().toString());
      }

      out.println(") {");

      for(VariableElement param : MoreElements.asExecutable(methodElement).getParameters()) {
         out.format("           this.%s = %s;%n", param.getSimpleName(), param.getSimpleName());
      }

      out.println("      }");
      out.println();

      out.println("      @Override");
      out.format("      public %s call() throws Exception {%n", returnTypeName);
      out.format("         %s service = applicationContext.getBean(%s.class);%n", targetServiceName, targetServiceName);
      out.format("         return service.%s(", methodElement.getSimpleName());
      first = true;

      for(VariableElement param : MoreElements.asExecutable(methodElement).getParameters()) {
         if(first) {
            first = false;
         }
         else {
            out.print(", ");
         }

         out.print(param.getSimpleName());
      }

      out.println(");");
      out.println("      }");
      out.println("   }");
      out.println();
      out.format("   public %s %s(", returnTypeName, methodElement.getSimpleName());
      first = true;

      for(VariableElement param : MoreElements.asExecutable(methodElement).getParameters()) {
         if(first) {
            first = false;
         }
         else {
            out.print(", ");
         }

         out.format("%s %s", param.asType(), param.getSimpleName().toString());
      }

      out.println(") {");
      out.println("      org.apache.ignite.Ignite ignite = ((inetsoft.sree.internal.cluster.ignite.IgniteCluster) inetsoft.sree.internal.cluster.Cluster.getInstance()).getIgniteInstance();");

      ClusterProxyMethod methodAnno = MoreElements.asExecutable(methodElement).getAnnotation(ClusterProxyMethod.class);
      String cacheName = methodAnno.value();

      String keyParam = MoreElements.asExecutable(methodElement).getParameters().stream()
         .filter(v -> MoreElements.isAnnotationPresent(v, ClusterProxyKey.class))
         .map(VariableElement::getSimpleName)
         .map(Name::toString)
         .findFirst()
         .orElseThrow();

      out.format("      return ignite.compute().affinityCall(\"%s\", %s, new %s(", cacheName, keyParam, callableClass);
      first = true;

      for(VariableElement param : MoreElements.asExecutable(methodElement).getParameters()) {
         if(first) {
            first = false;
         }
         else {
            out.print(", ");
         }

         out.print(param.getSimpleName());
      }

      out.println("));");

      out.println("   }");
   }

   private String getPackageName(String className) {
      int index = className.lastIndexOf('.');
      return index < 0 ? "" : className.substring(0, index);
   }

   private String writePackage(String name, PrintWriter out) {
      int index = name.lastIndexOf(".");
      String simpleName;

      if(index >= 0) {
         out.format("package %s;%n", name.substring(0, index));
         out.println();
         simpleName = name.substring(index + 1);
      }
      else {
         simpleName = name;
      }

      return simpleName;
   }

   private final Set<String> processedClasses = new HashSet<>();
   private final Map<String, Set<String>> callableClasses = new HashMap<>();
}
