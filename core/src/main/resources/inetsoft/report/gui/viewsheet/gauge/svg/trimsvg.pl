#
# inetsoft-core - StyleBI is a business intelligence web application.
# Copyright © 2024 InetSoft Technology (info@inetsoft.com)
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affrero General Public License
# along with this program. If not, see <http://www.gnu.org/licenses/>.
#

opendir DIR, ".";

my @list = readdir DIR;

foreach $file (@list) {
    if($file =~ /.*\.svg/) {
	printf "%s\n", $file;
	trimsvg($file);
    }
}

close DIR;

sub trimsvg {
    my $path = $_[0];
    my $infile = "<" . $path;
    my $outfile = ">" . $path . ".tmp";
    my $discard = 0;

    open INP, $infile;
    open OUT, $outfile;
    
    while(<INP>) {
	if(($_ =~ / *<metadata>/)) {
	    $discard = 1;
	}
	
	if(!$discard) {
	    print OUT $_;
	}
	elsif(($_ =~ / *<\/metadata>/)) {
	    $discard = 0;
	}
    }

    close INP;
    close OUT;

    unlink $path;
    rename $path . ".tmp", $path;
}
