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
package inetsoft.web.composer.model.vs;

import inetsoft.web.adhoc.model.FontInfo;

public class HighlightModel {
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getForeground() {
        return foreground;
    }

    public void setForeground(String foreground) {
        this.foreground = foreground;
    }

    public String getBackground() {
        return background;
    }

    public void setBackground(String background) {
        this.background = background;
    }

    public FontInfo getFontInfo() {
        return fontInfo;
    }

    public void setFontInfo(FontInfo fontInfo) {
        this.fontInfo = fontInfo;
    }

    public VSConditionDialogModel getVsConditionDialogModel() {
        return vsConditionDialogModel;
    }

    public void setVsConditionDialogModel(VSConditionDialogModel vsConditionDialogModel) {
        this.vsConditionDialogModel = vsConditionDialogModel;
    }

    public boolean isApplyRow() {
        return applyRow;
    }

    public void setApplyRow(boolean applyRow) {
        this.applyRow = applyRow;
    }

    private String name;
    private String foreground;
    private String background;
    private FontInfo fontInfo;
    private VSConditionDialogModel vsConditionDialogModel;
    private boolean applyRow;
}
