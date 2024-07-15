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
package inetsoft.sree.security;

/**
 * This interface defines some common graphical user interface for handling
 * the login, logout and changing password.
 *
 * @author Helen Chen
 * @version 5.1, 9/20/2003
 */
public interface SecurityGui {
   /**
    * Used to authenticate the end user. It allows the user to enter some
    * credential informaton.
    *
    * @return the credential information about the entity
    */
   Object login(Object obj);

   /**
    * Used to authenticate the end user. It allows the user to enter some
    * credential informaton.
    *
    * @return the credential information about the entity
    */
   Object vpmLogin(Object obj);

   /**
    * Change the entity password. It allows user to enter new password
    *
    * @return the new password
    */
   String changePassword(Object obj);

   /**
    * ask uers some confirmation information
    *
    * @return true if the user want to logout, false otherwise.
    */
   boolean logout(Object obj);

   /**
    * Set the default user to show on the login dialog.
    */
   void setDefaultUser(String user);

   /**
    * Set the default password to show on the login dialog.
    */
   void setDefaultPassword(String passwd);
}

