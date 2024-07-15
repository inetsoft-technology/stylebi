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
package inetsoft.web.factory;

import inetsoft.util.DataSpace;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;

/**
 * Factory for the {@link DataSpace} singleton instance.
 */
@Component
@Lazy
@Scope(
   scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE,
   proxyMode = ScopedProxyMode.TARGET_CLASS)
public class DataSpaceFactory extends AbstractFactoryBean<DataSpace> {
   @Override
   public Class<?> getObjectType() {
      return DataSpace.class;
   }

   @Override
   protected DataSpace createInstance()  {
      return DataSpace.getDataSpace();
   }

   @Override
   public boolean isSingleton() {
      return false;
   }
}
