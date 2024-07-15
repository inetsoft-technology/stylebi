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
package inetsoft.web.factory;

import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;

/**
 * Factory for the {@link AssetRepository} singleton instance.
 */
@Component
@Lazy
@Scope(
   scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE,
   proxyMode = ScopedProxyMode.TARGET_CLASS)
public class AssetRepositoryFactory extends AbstractFactoryBean<AssetRepository> {
   @Override
   public Class<?> getObjectType() {
      return AssetRepository.class;
   }

   @Override
   protected AssetRepository createInstance() throws Exception {
      return AssetUtil.getAssetRepository(false);
   }

   @Override
   public boolean isSingleton() {
      return false;
   }
}
