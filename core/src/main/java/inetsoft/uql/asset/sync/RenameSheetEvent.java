/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.asset.sync;

import inetsoft.uql.asset.AssetObject;

import java.io.Serializable;

public class RenameSheetEvent implements Serializable{
    public RenameSheetEvent(){
    }

    public RenameSheetEvent(AssetObject entry){
        this.entry = entry;
    }

    public AssetObject getEntry(){
        return entry;
    }

    public void setEntry(AssetObject entry){
        this.entry = entry;
    }

    public RenameInfo getRenameInfo(){
        return renameInfo;
    }

    public void setRenameInfo(RenameInfo renameInfo){
        this.renameInfo = renameInfo;
    }

    // old entry.
    private AssetObject entry;
    private RenameInfo renameInfo;
}
