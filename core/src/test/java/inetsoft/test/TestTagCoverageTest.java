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
package inetsoft.test;

import org.junit.jupiter.api.*;
import org.junit.platform.commons.support.*;

import java.lang.reflect.*;
import java.net.URI;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Core surefire runs only the {@code core} tag group, so a test class without any JUnit tag
 * silently never runs in CI (Bug #77104). This guard fails when a concrete test class in this
 * module's test classes has a test method that carries no tag, neither on the method nor on
 * its class, a superclass or an enclosing class. Test methods inherited from superclasses and
 * test methods in {@link Nested} classes are included.
 */
@Tag("core")
class TestTagCoverageTest {
   /**
    * Test classes that are known to be untagged. Every entry must still be untagged; remove an
    * entry as soon as the class is tagged.
    */
   private static final Set<String> KNOWN_UNTAGGED = Set.of();

   @Test
   void everyTestClassHasATag() throws Exception {
      Set<String> untagged = findUntaggedTestClasses();
      Set<String> unexpected = new TreeSet<>(untagged);
      unexpected.removeAll(KNOWN_UNTAGGED);
      Set<String> stale = new TreeSet<>(KNOWN_UNTAGGED);
      stale.removeAll(untagged);

      assertTrue(unexpected.isEmpty(),
                 "Test classes with untagged test methods never run in CI. Add @Tag(\"core\") " +
                 "(and the Spring test context header if the test needs it): " + unexpected);
      assertTrue(stale.isEmpty(),
                 "KNOWN_UNTAGGED entries are tagged or no longer exist, remove them: " + stale);
   }

   static Set<String> findUntaggedTestClasses() throws Exception {
      URI root = TestTagCoverageTest.class.getProtectionDomain().getCodeSource()
         .getLocation().toURI();
      List<Class<?>> classes = ReflectionSupport.findAllClassesInClasspathRoot(
         root, TestTagCoverageTest::isConcreteTopLevelClass,
         TestTagCoverageTest::isSurefireTestClassName);
      Set<String> untagged = new TreeSet<>();

      for(Class<?> c : classes) {
         try {
            if(hasUntaggedTestMethod(c, false)) {
               untagged.add(c.getName());
            }
         }
         catch(LinkageError e) {
            // a class that cannot be introspected cannot be discovered by JUnit either
            untagged.add(c.getName() + " (" + e + ")");
         }
      }

      return untagged;
   }

   /**
    * @param enclosingTagged true when an enclosing class of a {@link Nested} class is tagged, in
    *                        which case its tags apply to the nested tests too.
    */
   private static boolean hasUntaggedTestMethod(Class<?> c, boolean enclosingTagged) {
      boolean classTagged = enclosingTagged || isTagged(c);

      if(!classTagged) {
         List<Method> tests = ReflectionSupport.findMethods(
            c, TestTagCoverageTest::isTestMethod, HierarchyTraversalMode.TOP_DOWN);

         for(Method m : tests) {
            if(!isTagged(m)) {
               return true;
            }
         }
      }

      for(Class<?> nested : ReflectionSupport.findNestedClasses(
         c, n -> AnnotationSupport.isAnnotated(n, Nested.class)))
      {
         if(hasUntaggedTestMethod(nested, classTagged)) {
            return true;
         }
      }

      return false;
   }

   // finds direct, repeated (@Tags), meta-annotated and @Inherited (superclass) tags
   private static boolean isTagged(AnnotatedElement e) {
      return !AnnotationSupport.findRepeatableAnnotations(e, Tag.class).isEmpty();
   }

   private static boolean isTestMethod(Method m) {
      return AnnotationSupport.isAnnotated(m, Test.class) ||
         AnnotationSupport.isAnnotated(m, TestTemplate.class) ||
         AnnotationSupport.isAnnotated(m, TestFactory.class);
   }

   private static boolean isConcreteTopLevelClass(Class<?> c) {
      return c.getName().startsWith("inetsoft.") && !c.isInterface() && !c.isAnnotation() &&
         !c.isEnum() && !c.isRecord() && !Modifier.isAbstract(c.getModifiers()) &&
         c.getEnclosingClass() == null;
   }

   // surefire's default includes: **/Test*.java, **/*Test.java, **/*Tests.java, **/*TestCase.java
   private static boolean isSurefireTestClassName(String name) {
      String simple = name.substring(name.lastIndexOf('.') + 1);
      return !simple.contains("$") && (simple.startsWith("Test") || simple.endsWith("Test") ||
         simple.endsWith("Tests") || simple.endsWith("TestCase"));
   }
}
