#!/bin/bash
#
# inetsoft-web - StyleBI is a business intelligence web application.
# Copyright © 2024 InetSoft Technology (info@inetsoft.com)
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program. If not, see <http://www.gnu.org/licenses/>.
#


mkdir toolbox

convert folder.gif -resize '15x13>' toolbox/folder.png
convert table.gif -resize '15x13>' toolbox/table.png
convert treechart.gif -resize '15x13>' toolbox/chart.png
convert crosstab.gif -resize '15x13>' toolbox/crosstab.png
convert formula_table.gif -resize '15x13>' toolbox/freehand_table.png
convert selectionlist.gif -resize '15x13>' toolbox/selection_list.png
convert selectiontree.gif -resize '15x13>' toolbox/selection_tree.png
convert timeslider.gif -resize '15x13>' toolbox/range_slider.png
convert calendar.png -resize '15x13>' toolbox/calendar.png
convert selectioncontainer.png -resize '15x13>' toolbox/selection_container.png
convert text.png -resize '15x13>' toolbox/text.png
convert image.gif -resize '15x13>' toolbox/image.png
convert gauge.gif -resize '15x13>' toolbox/gauge.png
convert slider.gif -resize '15x13>' toolbox/slider.png
convert spinner.gif -resize '15x13>' toolbox/spinner.png
convert checkbox.gif -resize '15x13>' toolbox/checkbox.png
convert radiobutton.gif -resize '15x13>' toolbox/radiobutton.png
convert combobox.gif -resize '15x13>' toolbox/combobox.png
convert textinput.gif -resize '15x13>' toolbox/textinput.png
convert submit.png -resize '15x13>' toolbox/submit.png
convert upload.png -resize '15x13>' toolbox/upload.png
convert shape_line.png -resize '15x13>' toolbox/line.png
convert shape_rectangle.png -resize '15x13>' toolbox/rectangle.png
convert shape_oval.png -resize '15x13>' toolbox/oval.png
convert tab.gif -resize '15x13>' toolbox/tab.png

pushd toolbox
  for file in *.png
  do
    convert $file -background none -gravity center -extent 15x13 $file
  done
popd # toolbox

sprity create -n composer-toolbox-images -s composer-toolbox-images.css \
  -c assets/sprites --margin 0 --prefix composer-toolbox-image sprites toolbox/*.png

rm -rf toolbox
