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

package inetsoft.cluster.apt;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.google.auto.common.MoreElements;
import com.google.auto.service.AutoService;
import inetsoft.cluster.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.JavaFileObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@SuppressWarnings("UnstableApiUsage")
@SupportedAnnotationTypes("inetsoft.cluster.ClusterProxy")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@AutoService(Processor.class)
public class ClusterAnnotationProcessor extends AbstractProcessor {
   @Override
   public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if(!roundEnv.processingOver()) {
         processAnnotations(roundEnv);

         try {
            generateJavaSources();
         }
         catch(IOException e) {
            throw new RuntimeException("Failed to generate Java sources", e);
         }
      }

      return false;
   }

   private void processAnnotations(RoundEnvironment roundEnv) {
      for(Element element : roundEnv.getElementsAnnotatedWith(ClusterProxy.class)) {
         String targetServiceClass = MoreElements.asType(element).getQualifiedName().toString();
         String[] targetServiceNameParts = getNameParts(targetServiceClass);
         String packageName = targetServiceNameParts[0];
         String proxySimpleName = targetServiceNameParts[1] + "Proxy";
         String proxyClassName =
            packageName.isEmpty() ? proxySimpleName : packageName + "." + proxySimpleName;
         ProxyClass proxyClass =
            new ProxyClass(targetServiceClass, packageName, proxyClassName, proxySimpleName);
         proxyClasses.put(targetServiceClass, proxyClass);

         Set<String> callableClasses = new HashSet<>();
         MoreElements.asType(element).getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.METHOD)
            .filter(e -> MoreElements.getAnnotationMirror(e, ClusterProxyMethod.class).isPresent())
            .map(MoreElements::asExecutable)
            .forEach(e -> addMethod(proxyClass, e, callableClasses));
      }
   }

   private void addMethod(ProxyClass proxyClass, ExecutableElement methodElement,
                          Set<String> callableClasses)
   {
      String methodName = methodElement.getSimpleName().toString();
      String callableClassName = getCallableClassName(methodName, callableClasses);
      String returnType = methodElement.getReturnType().toString();
      String cacheName = methodElement.getAnnotation(ClusterProxyMethod.class).value();
      String keyParam = null;
      List<ProxyParameter> params = new ArrayList<>();

      for(VariableElement paramElement : MoreElements.asExecutable(methodElement).getParameters()) {
         String paramName = paramElement.getSimpleName().toString();
         String paramType = paramElement.asType().toString();
         String paramInternalType;
         String paramInitializer;
         String paramGetter;

         if("inetsoft.web.viewsheet.model.RuntimeViewsheetRef".equals(paramType)) {
            paramInternalType = "java.lang.Object";
            paramInitializer = "null";
            paramGetter = "serviceProxyContext.createRuntimeViewsheetRef()";
         }
         else if("inetsoft.web.viewsheet.service.CommandDispatcher".equals(paramType)) {
            paramInternalType = "java.lang.Object";
            paramInitializer = "null";
            paramGetter = "serviceProxyContext.createCommandDispatcher()";
         }
         else {
            paramInternalType = paramType;
            paramInitializer = paramName;
            paramGetter = paramName;
         }

         params.add(new ProxyParameter(
            paramName, paramType, paramInternalType, paramInitializer, paramGetter));

         if(MoreElements.isAnnotationPresent(paramElement, ClusterProxyKey.class)) {
            keyParam = paramName;
         }
      }

      ProxyMethod proxyMethod = new ProxyMethod(
         methodName, callableClassName, returnType, cacheName, keyParam, params);
      proxyClass.getMethods().add(proxyMethod);
   }

   private void generateJavaSources() throws IOException {
      Mustache mustache;

      try(Reader reader = openMustacheTemplate()) {
         mustache = new DefaultMustacheFactory().compile(reader, "proxy");
      }

      for(ProxyClass proxyClass : proxyClasses.values()) {
         if(written.contains(proxyClass.getTargetClass())) {
            continue;
         }

         written.add(proxyClass.getTargetClass());
         JavaFileObject file =
            processingEnv.getFiler().createSourceFile(proxyClass.getProxyClass());

         try(Writer writer = file.openWriter()) {
            mustache.execute(writer, proxyClass);
         }
      }
   }

   private String[] getNameParts(String fqn) {
      int index = fqn.lastIndexOf('.');
      String packageName = index < 0 ? "" : fqn.substring(0, index);
      String simpleName = index < 0 ? fqn : fqn.substring(index + 1);
      return new String[] { packageName, simpleName };
   }

   private String getCallableClassName(String methodName, Set<String> callableClasses) {
      String baseName = methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
      String callableName = baseName;

      for(int i = 1; callableClasses.contains(callableName); i++) {
         callableName = baseName + i;
      }

      callableClasses.add(callableName);
      return callableName;
   }

   private Reader openMustacheTemplate() {
      return new InputStreamReader(
         Objects.requireNonNull(getClass().getResourceAsStream("Proxy.java.mustache")),
         StandardCharsets.UTF_8);
   }

   private final Map<String, ProxyClass> proxyClasses = new HashMap<>();
   private final Set<String> written = new HashSet<>();
}
