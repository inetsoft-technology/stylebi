/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.composition;

import java.security.Principal;

/**
 * Interface for classes that intercept the processing of asset events.
 */
public interface AssetEventInterceptor {
   /**
    * Processes an event.
    *
    * @param event    the event to process.
    * @param commands the container for the commands to be sent to the client.
    * @param user     the principal that identifies the current user.
    *
    * @return <tt>true</tt> if the default event handling should be executed;
    *         <tt>false</tt> otherwise.
    */
   boolean process(AssetEvent event, AssetCommand commands, Principal user);
}
