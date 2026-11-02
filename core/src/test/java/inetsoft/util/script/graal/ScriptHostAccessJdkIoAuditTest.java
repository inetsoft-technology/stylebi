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
package inetsoft.util.script.graal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.lang.module.*;
import java.lang.reflect.*;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channel;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79846 tripwire: the JDK packages the script class filter admits broadly
 * ({@code java.awt}, {@code java.util}, {@code java.text}, and {@code java.sql}
 * when form write-back is enabled, which is the default) must not offer a script a
 * file or stream primitive. Walks every public class of those packages in the
 * running JDK that the filter admits and {@link ScriptHostAccess#isTypeDenied}
 * does not deny, and fails on any public constructor or method that takes or
 * returns a file, path, URL/URI, byte/char stream or channel, unless the member is
 * in the reviewed {@link #REVIEWED} set. A JDK upgrade or a change to
 * {@code DEFAULT_JAVA_PKGS} that adds such a member then fails here instead of
 * shipping silently.
 *
 * <p>A member that takes only a {@code String} path is not recognizable by its
 * signature, so the known ones are pinned in {@link #stringPathPrimitivesAreRefused}.
 * Classes are loaded without initialization.
 */
@Tag("core")
class ScriptHostAccessJdkIoAuditTest {
   private static final List<String> PACKAGES =
      List.of("java.awt.", "java.util.", "java.text.", "java.sql.");

   /** File, path, URL and stream types a script must never get or hand to a sink. */
   private static final List<Class<?>> IO_TYPES = List.of(
      File.class, Path.class, URL.class, URI.class, InputStream.class, OutputStream.class,
      Reader.class, Writer.class, Channel.class);

   /**
    * Reviewed members (declaring class#name). Each is a stream/file sink or a stream
    * wrapper: it needs a File, Path, URL or stream the script cannot name or obtain
    * (java.io/java.nio/java.net are blocked by name, File/URL also by type, and with
    * java.util.Formatter denied no admitted JDK member returns a raw stream to wrap),
    * or it is unusable on a headless server. Add a member here only after checking
    * that a script cannot reach a matching source.
    */
   private static final Set<String> REVIEWED = new HashSet<>();

   static {
      // sinks: need a stream/File/Path/channel argument
      Collections.addAll(REVIEWED,
         "java.util.Scanner#<init>",
         "java.util.Properties#load", "java.util.Properties#store",
         "java.util.Properties#loadFromXML", "java.util.Properties#storeToXML",
         "java.util.Properties#list",
         "java.util.PropertyResourceBundle#<init>",
         "java.util.ResourceBundle$Control#newBundle",
         "java.util.Base64$Encoder#wrap", "java.util.Base64$Decoder#wrap",
         "java.awt.Font#createFont", "java.awt.Font#createFonts",
         "java.awt.Component#list", "java.awt.Container#list",
         "java.awt.MenuComponent#list",
         "java.awt.SplashScreen#setImageURL", "java.awt.SplashScreen#getImageURL",
         "java.awt.Taskbar#setIconImage",
         "java.util.Properties#save",
         "java.lang.Throwable#printStackTrace");
      // headless server: a FileDialog can't be constructed (HeadlessException)
      REVIEWED.add("java.awt.FileDialog#getFiles");
      // a URI value only; a script can't name java.net.URI, and URL is type-denied
      Collections.addAll(REVIEWED, "java.awt.desktop.OpenURIEvent#<init>",
                         "java.awt.desktop.OpenURIEvent#getURI");
      // peer interface: its only factory is Toolkit.createDesktopPeer, and
      // java.awt.Toolkit and java.awt.Desktop are denied by type (#78490)
      REVIEWED.add("java.awt.peer.DesktopPeer#*");
      // java.sql LOB/UDT interfaces: a script can't implement them (not functional
      // interfaces), and Connection, which creates them, is denied by type. Their
      // streams carry database LOB content, not server files.
      Collections.addAll(REVIEWED, "java.sql.Blob#*", "java.sql.Clob#*", "java.sql.NClob#*",
                         "java.sql.SQLXML#*", "java.sql.SQLInput#*", "java.sql.SQLOutput#*");
   }

   private static boolean isReviewed(Class<?> owner, String key) {
      return REVIEWED.contains(key) || REVIEWED.contains(owner.getName() + "#*");
   }

   @Test
   void admittedJdkClassesOfferNoFileOrStreamMember() throws Exception {
      Predicate<String> filter = ScriptHostAccess.classFilter(Set.of(), new String[0], false);
      ClassLoader loader = ScriptHostAccessJdkIoAuditTest.class.getClassLoader();
      List<String> hits = new ArrayList<>();
      int scanned = 0;

      for(String name : jdkClassNames()) {
         // java.sql is admitted whole when form write-back is on (the default); the
         // filter can't read that setting without SreeEnv, so assume it is on
         if(!filter.test(name) && !name.startsWith("java.sql.")) {
            continue;
         }

         Class<?> type;

         try {
            type = Class.forName(name, false, loader);
         }
         catch(ClassNotFoundException | LinkageError ignore) {
            continue;
         }

         if(!Modifier.isPublic(type.getModifiers()) || ScriptHostAccess.isTypeDenied(type)) {
            continue;
         }

         scanned++;
         List<Executable> members = new ArrayList<>(Arrays.asList(type.getConstructors()));
         members.addAll(Arrays.asList(type.getMethods()));

         for(Executable member : members) {
            Class<?> owner = member.getDeclaringClass();

            if(ScriptHostAccess.isTypeDenied(owner) || owner == Object.class) {
               continue;
            }

            String key = owner.getName() + "#" +
               (member instanceof Constructor ? "<init>" : member.getName());

            if(!isReviewed(owner, key) && touchesIo(member)) {
               hits.add(type.getName() + ": " + member.toGenericString());
            }
         }
      }

      assertTrue(scanned > 500, "too few admitted JDK classes scanned: " + scanned);
      assertEquals(List.of(), hits.stream().distinct().sorted().toList(),
                   "an admitted, non-denied JDK class offers a script a file/stream member; " +
                   "block the class in ScriptHostAccess.BLOCKED_CLASSES or review it");
   }

   /**
    * Members that take only a String path, so the signature scan can't see them.
    * Each declaring class must be refused by name and denied by type.
    */
   @Test
   void stringPathPrimitivesAreRefused() throws Exception {
      Predicate<String> filter = ScriptHostAccess.classFilter(Set.of(), new String[0], false);
      List<Executable> primitives = List.of(
         java.util.Formatter.class.getConstructor(String.class),
         java.util.Formatter.class.getConstructor(String.class, String.class),
         java.util.Formatter.class.getConstructor(String.class, String.class, Locale.class),
         java.awt.color.ICC_Profile.class.getMethod("getInstance", String.class),
         java.awt.color.ICC_Profile.class.getMethod("write", String.class));
      List<Class<?>> types = List.of(java.util.Formatter.class, java.awt.color.ICC_Profile.class,
                                     java.awt.color.ICC_ProfileRGB.class,
                                     java.awt.color.ICC_ProfileGray.class);

      for(Executable member : primitives) {
         assertTrue(ScriptHostAccess.isTypeDenied(member.getDeclaringClass()),
                    member + " must be denied by type");
      }

      for(Class<?> type : types) {
         assertFalse(filter.test(type.getName()), type + " must be refused by name");
         assertTrue(ScriptHostAccess.isTypeDenied(type), type + " must be denied by type");
      }
   }

   private static boolean touchesIo(Executable member) {
      if(member instanceof Method m && isIoType(m.getReturnType())) {
         return true;
      }

      for(Class<?> param : member.getParameterTypes()) {
         if(isIoType(param)) {
            return true;
         }
      }

      return false;
   }

   private static boolean isIoType(Class<?> type) {
      while(type.isArray()) {
         type = type.getComponentType();
      }

      for(Class<?> io : IO_TYPES) {
         if(io.isAssignableFrom(type)) {
            return true;
         }
      }

      // any other java.io / java.nio Closeable (e.g. a byte channel)
      String pkg = type.getPackageName();
      return Closeable.class.isAssignableFrom(type) &&
         (pkg.startsWith("java.io") || pkg.startsWith("java.nio"));
   }

   private static List<String> jdkClassNames() throws IOException {
      List<String> names = new ArrayList<>();

      for(ModuleReference ref : ModuleFinder.ofSystem().findAll()) {
         String module = ref.descriptor().name();

         if(!module.equals("java.base") && !module.equals("java.desktop") &&
            !module.equals("java.sql"))
         {
            continue;
         }

         try(ModuleReader reader = ref.open(); Stream<String> entries = reader.list()) {
            entries
               .filter(e -> e.endsWith(".class") && !e.endsWith("module-info.class") &&
                  !e.endsWith("package-info.class"))
               .map(e -> e.substring(0, e.length() - ".class".length()).replace('/', '.'))
               .filter(n -> PACKAGES.stream().anyMatch(n::startsWith))
               .forEach(names::add);
         }
      }

      return names;
   }
}
