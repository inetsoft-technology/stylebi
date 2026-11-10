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
import org.junit.platform.commons.support.AnnotationSupport;

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the tags of every test class in this module, so that a class that was added without
 * a speed tag is not silently left out of the {@code surefire.groups} selection.
 * <ul>
 *   <li>A test method has exactly one of {@code core} (under 10 s) and {@code slow}
 *       (10 s or more), from its class, a class around it, or itself.</li>
 *   <li>Every other tag is one of {@link #OTHER_TAGS}.</li>
 * </ul>
 * This only checks that the tags are present and well formed. It cannot tell that a class is
 * tagged {@code core} but takes 10 s or more; find those from the surefire report times.
 */
@Tag("core")
class TestTagCoverageTest {
   private static final String CORE = "core";
   private static final String SLOW = "slow";
   private static final Set<String> OTHER_TAGS = Set.of(
      "integration", "known-bug", "known-deadlock", "docker", "cluster", "database");

   @Test
   void everyTestMethodHasOneSpeedTagAndOnlyKnownTags() throws Exception {
      List<String> violations = new ArrayList<>();
      int[] checked = { 0 };

      for(Class<?> cls : testClasses()) {
         check(cls, cls, tagsOf(cls), violations, checked);
      }

      assertTrue(checked[0] > 0, "no test method was found, the class scan is broken");
      assertTrue(violations.isEmpty(),
                 violations.size() + " test class(es) have a wrong tag:\n" +
                    String.join("\n", violations));
   }

   /**
    * Checks the test methods of {@code cls}, those of its superclasses, which run with the tags
    * of {@code cls}, and those of its nested classes, which add to the tags of the class around
    * them. The tags of a method are the tags of the class it runs in plus its own.
    */
   private static void check(Class<?> cls, Class<?> owner, Set<String> classTags,
                             List<String> violations, int[] checked)
   {
      Map<String, Set<String>> bad = new TreeMap<>();

      for(Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
         for(Method method : c.getDeclaredMethods()) {
            if(!isTest(method)) {
               continue;
            }

            checked[0]++;
            Set<String> tags = new TreeSet<>(classTags);
            tags.addAll(tagsOf(method));
            String problem = problem(tags);

            if(problem != null) {
               bad.computeIfAbsent(problem, k -> new TreeSet<>()).add(method.getName());
            }
         }
      }

      bad.forEach((problem, methods) -> violations.add(
         owner.getName() + " " + problem + ", at " + methods.size() + " test method(s) such as " +
            methods.iterator().next()));

      for(Class<?> nested : cls.getDeclaredClasses()) {
         Set<String> tags = new TreeSet<>(classTags);
         tags.addAll(tagsOf(nested));
         check(nested, nested, tags, violations, checked);
      }
   }

   private static String problem(Set<String> tags) {
      boolean core = tags.contains(CORE);
      boolean slow = tags.contains(SLOW);

      if(!core && !slow) {
         return "has no speed tag, add @Tag(\"core\") or @Tag(\"slow\")";
      }

      if(core && slow) {
         return "has both core and slow, keep only one";
      }

      for(String tag : tags) {
         if(!CORE.equals(tag) && !SLOW.equals(tag) && !OTHER_TAGS.contains(tag)) {
            return "has the unknown tag \"" + tag + "\", the known tags are core, slow and " +
               new TreeSet<>(OTHER_TAGS);
         }
      }

      return null;
   }

   private static Set<String> tagsOf(AnnotatedElement element) {
      Set<String> tags = new TreeSet<>();
      AnnotationSupport.findRepeatableAnnotations(element, Tag.class)
         .forEach(tag -> tags.add(tag.value()));
      return tags;
   }

   /**
    * The top-level, non-abstract classes under the test class output directory. A nested class
    * takes the tags of the class around it.
    */
   private static List<Class<?>> testClasses()
      throws IOException, URISyntaxException, ClassNotFoundException
   {
      Path root = Paths.get(TestTagCoverageTest.class.getProtectionDomain().getCodeSource()
                               .getLocation().toURI());
      ClassLoader loader = TestTagCoverageTest.class.getClassLoader();
      List<Class<?>> classes = new ArrayList<>();

      try(Stream<Path> files = Files.walk(root)) {
         for(Path file : (Iterable<Path>) files::iterator) {
            String name = root.relativize(file).toString().replace('\\', '/');

            if(!name.endsWith(".class") || name.contains("$") ||
               name.equals("module-info.class") || name.endsWith("package-info.class"))
            {
               continue;
            }

            String className = name.substring(0, name.length() - ".class".length())
               .replace('/', '.');

            try {
               Class<?> cls = Class.forName(className, false, loader);

               if(!Modifier.isAbstract(cls.getModifiers()) && !cls.isInterface()) {
                  classes.add(cls);
               }
            }
            catch(LinkageError ignore) {
               // a helper class that cannot be linked here is not a test class
            }
         }
      }

      return classes;
   }

   private static boolean isTest(Method method) {
      return AnnotationSupport.isAnnotated(method, Test.class) ||
         AnnotationSupport.isAnnotated(method, RepeatedTest.class) ||
         AnnotationSupport.isAnnotated(method, TestFactory.class) ||
         AnnotationSupport.isAnnotated(method, TestTemplate.class);
   }
}
