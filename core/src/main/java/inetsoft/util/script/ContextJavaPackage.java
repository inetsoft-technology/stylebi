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
package inetsoft.util.script;

import inetsoft.sree.SreeEnv;
import org.mozilla.javascript.NativeJavaPackage;
import org.mozilla.javascript.Scriptable;

import java.util.*;

/**
 * This class manages the java package namespace in both restricted and
 * unrestricted environment. We don't use ClassShutter because the 
 * default NativeJavaPackage caches result so the restricted status can't
 * be changed once it's set. This class allows the mode to switch dynamically.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ContextJavaPackage extends NativeJavaPackage {
   /**
    * Create a java package that could restrict access to java classes.
    * @param unrestricted name of unrestricted packages.
    */
   public ContextJavaPackage(String packageName, String... unrestricted) {
      super(packageName);
      this.pkg = packageName;

      for(String name : unrestricted) {
         this.unrestricted.add(name);
      }
   }

   /**
    * Get the root package name, e.g. java.
    */
   public String getRootName() {
      return pkg;
   }

   /**
    * Add a package name to allow unrestricted access.
    * @param pkg package name, e.g. java.net
    */
   public void addUnrestricted(String pkg) {
      unrestricted.add(pkg);
   }

   /**
    * Create a restricted package.
    */
   private ContextJavaPackage(String packageName, Set unrestricted) {
      this(packageName);
      this.unrestricted = unrestricted;
   }
   
   /**
    * This method is called by NativeJavaPackage to cache a package lookup 
    * result. We must cache it in different maps otherwise when the 
    * restriction is changed, the previous result will be used.
    */
   @Override
   public void put(String name, Scriptable start, Object value) {
      Map map = FormulaContext.isRestricted() ? rmap : jmap;
      map.put(name, value);
   }
   
   /**
    * Get a cached package.
    */
   @Override
   public Object get(String name, Scriptable start) {
      Map map = FormulaContext.isRestricted() ? rmap : jmap;
      Object obj = map.get(name);

      if(obj != null) {
         return obj;
      }

      String newPackage = pkg.length() == 0 ? name : pkg + '.' + name;

      if(FormulaContext.isRestricted()) {
         if(!unrestricted.contains(newPackage)) {
            ContextJavaPackage newpkg = new ContextJavaPackage(newPackage, 
							       unrestricted);
            newpkg.setParentScope(this);
            map.put(name, newpkg);
            
            return newpkg;
         }
      }

      final String sreePackage = SreeEnv.class.getPackage().getName();

      if(sreePackage.equals(newPackage)) {
         final String javaPackages = SreeEnv.getProperty("javascript.java.packages", "");
         final List<String> packages = Arrays.asList(javaPackages.split(","));
         final String sreeSubPackage = sreePackage + ".";
         boolean find = packages.stream().anyMatch(pkg -> pkg.equals(sreePackage) || pkg.startsWith(sreeSubPackage));

         if(!find) {
            return Scriptable.NOT_FOUND;
         }
      }

      Object rc = super.get(name, start);
      map.put(name, rc);
      
      return rc;
   }

   public String toString() {
      return "[ContextJavaPackage " + pkg + "]";
   }

   private String pkg;
   private Map jmap = new HashMap(); // normal environment
   private Map rmap = new HashMap(); // restricted environment
   private Set unrestricted = new HashSet(); // unrestricted packages
}
