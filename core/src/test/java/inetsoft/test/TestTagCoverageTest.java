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

   /** The tags allowed next to the speed tag {@code core} or {@code slow}. */
   private static final Set<String> OTHER_TAGS = Set.of(
      "integration", "known-bug", "known-deadlock", "docker", "cluster", "database");

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

   /**
    * Beyond having a tag, a test method has exactly one speed tag: {@code core} (under 10 s) or
    * {@code slow} (10 s or more). Every other tag is one of {@link #OTHER_TAGS}, so a typo does
    * not create a tag that no group selects. The tags of a method are the tags of its class, of
    * a class around it, and its own. This cannot tell that a class tagged {@code core} takes
    * 10 s or more; find those from the surefire report times.
    */
   @Test
   void everyTestMethodHasOneSpeedTagAndOnlyKnownTags() throws Exception {
      URI root = TestTagCoverageTest.class.getProtectionDomain().getCodeSource()
         .getLocation().toURI();
      List<Class<?>> classes = ReflectionSupport.findAllClassesInClasspathRoot(
         root, TestTagCoverageTest::isConcreteTopLevelClass,
         TestTagCoverageTest::isSurefireTestClassName);
      List<String> violations = new ArrayList<>();
      int[] checked = { 0 };

      for(Class<?> c : classes) {
         try {
            checkSpeedTags(c, Set.of(), violations, checked);
         }
         catch(LinkageError e) {
            // reported by everyTestClassHasATag
         }
      }

      assertTrue(checked[0] > 0, "no test method was found, the class scan is broken");
      assertTrue(violations.isEmpty(),
                 violations.size() + " test class(es) have a wrong tag:\n" +
                 String.join("\n", violations));
   }

   private static void checkSpeedTags(Class<?> c, Set<String> enclosingTags,
                                      List<String> violations, int[] checked)
   {
      Set<String> classTags = new TreeSet<>(enclosingTags);
      classTags.addAll(tagsOf(c));
      Map<String, Set<String>> bad = new TreeMap<>();

      for(Method m : ReflectionSupport.findMethods(
         c, TestTagCoverageTest::isTestMethod, HierarchyTraversalMode.TOP_DOWN))
      {
         checked[0]++;
         Set<String> tags = new TreeSet<>(classTags);
         tags.addAll(tagsOf(m));
         String problem = speedTagProblem(tags);

         if(problem != null) {
            bad.computeIfAbsent(problem, k -> new TreeSet<>()).add(m.getName());
         }
      }

      bad.forEach((problem, methods) -> violations.add(
         c.getName() + " " + problem + ", at " + methods.size() + " test method(s) such as " +
         methods.iterator().next()));

      for(Class<?> nested : ReflectionSupport.findNestedClasses(
         c, n -> AnnotationSupport.isAnnotated(n, Nested.class)))
      {
         checkSpeedTags(nested, classTags, violations, checked);
      }
   }

   private static String speedTagProblem(Set<String> tags) {
      boolean core = tags.contains("core");
      boolean slow = tags.contains("slow");

      if(!core && !slow) {
         return "has no speed tag, add @Tag(\"core\") or @Tag(\"slow\")";
      }

      if(core && slow) {
         return "has both core and slow, keep only one";
      }

      for(String tag : tags) {
         if(!"core".equals(tag) && !"slow".equals(tag) && !OTHER_TAGS.contains(tag)) {
            return "has the unknown tag \"" + tag + "\", the known tags are core, slow and " +
               new TreeSet<>(OTHER_TAGS);
         }
      }

      return null;
   }

   private static Set<String> tagsOf(AnnotatedElement e) {
      Set<String> tags = new TreeSet<>();
      AnnotationSupport.findRepeatableAnnotations(e, Tag.class)
         .forEach(tag -> tags.add(tag.value()));
      return tags;
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
