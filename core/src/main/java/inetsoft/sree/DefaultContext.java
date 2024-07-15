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
package inetsoft.sree;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Default context stores basic information about repository.
 */
public class DefaultContext implements java.io.Serializable {
   /**
    * Create a default context.
    * @param name the name of folder/replet
    */
   public DefaultContext(String name) {
      this(name, "");
   }
   
   public DefaultContext(String name, String description) {
      this.name = name;
      this.description = description;
   }
   
   /**
    * Get the replet name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the replet name.
    */
   public void setName(String name) {
      this.name = name;
   }
   
   /**
    * Get replet description name.
    */
   public String getDescription() {
      return description;
   }

   /**
    * Set replet description name.
    */
   public void setDescription(String description) {
      this.description = description;
   }

   /**
    * Read object.
    */
   private void readObject(ObjectInputStream stream)
      throws ClassNotFoundException, java.io.IOException {
      stream.defaultReadObject();
   }

   /**
    * Write object.
    */
   private void writeObject(ObjectOutputStream stream) throws IOException {
      stream.defaultWriteObject();
   }

   /**
    * Get FavoritesUser in context.
    */
   public String getFavoritesUser() {
      StringBuilder users = new StringBuilder();

      for(int i = 0; i < favoritesUser.size(); i++) {
         String user = favoritesUser.get(i);

         if(i == 0) {
            users.append(user);
            continue;
         }

         users.append("^_^").append(user);
      }

      return users.toString();
   }

   /**
    * Add favoritesUser to FavoritesUser.
    */
   public void addFavoritesUser(String favoritesUser0) {
      if(favoritesUser0 == null) {
         return;
      }

      String[] users = favoritesUser0.split("\\^_\\^");

      for(String user: users) {
         if(!favoritesUser.contains(user)) {
            favoritesUser.add(user);
         }
      }
   }

   /**
    * delete favorites user.
    */
   public void deleteFavoritesUser(String favoritesUser0) {
      favoritesUser.remove(favoritesUser0);
   }

   protected String name;
   protected String description;
   private List<String> favoritesUser = new ArrayList<>();
}
