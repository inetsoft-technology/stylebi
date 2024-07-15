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

# create makefile for java
$excluded{'tool'} = 1;
$excluded{'soap'} = 1;
$excluded{'dataserv'} = 1;
$excluded{'agile'} = 1;

if($ARGV[0] ne "fl") {
    $excluded{'fl'} = 1;
    $excluded{'mobile'} = 1;
    $excluded{'air'} = 1;
}

createmk(".", ".");

sub createmk {
    my $path = $_[0];
    my $rootdir = $_[1];
    opendir DIR, $path;
    my @list = readdir DIR;
    my @javafiles = ();
    my @scalafiles = ();
    my @dirs = ();
    my $file;
    my $mk;
    my $classdir = $rootdir . "/target/classes";

    closedir DIR;

    for(my $i = 0; $i >= 0; ) {
        my $slash = index($path, '/', $i);

        if($slash > 0) {
            my $prefix = substr($path, 0, $slash);

            if($plugin{$path}) {
                $classdir = $rootdir . "/build/plugins/" . $plugin{$path} . "/classes";
            }
            elsif($plugin{$prefix}) {
                $classdir = $rootdir . "/build/plugins/" . $plugin{$prefix} . "/classes";
            }
        }
        else {
            last;
        }

        $i = $slash + 1;
    }

    foreach $file (@list) {
        if($file eq ".." || $file eq "." || exists $excluded{$file}) {
            next;
        }

        if(-d $path . "/" . $file) {
            $rc = createmk($path . "/" . $file, "../" . $rootdir);

	    if($rc && !exists $excluded{$file}) {
		push @dirs, $file;
	    }
        }
        elsif($file =~ /\.java$/) {
            push @javafiles, $file;
        }
        elsif($file =~ /\.scala$/) {
            push @scalafiles, $file;
        }
        elsif($file =~ /\.mk$/) {
            $mk = $file;
        }
    }

    if($#javafiles >= 0 || $#scalafiles >= 0 || $#dirs >= 0 || $mk) {
        open MAKEFILE, ">" . $path . "/Makefile";

        if($mk) {
            open MK, "<" . $path . "/" . $mk;

            while(<MK>) {
                print MAKEFILE $_;
            }

            close MK;
            print MAKEFILE "\n";
        }

	print MAKEFILE ".SUFFIXES: .java .class .g .jj\n";
        my $inetsoftPath = substr($path, index($path, "inetsoft"));
	print MAKEFILE "CLASSDIR=" . $classdir . "/" . $inetsoftPath . "\n\n";

        print MAKEFILE "CLASSES=";

        $jcnt = 0;

        foreach $file (@scalafiles) {
	    $classfile = $file;
            $classfile =~ s/.scala$/.class/;
            print MAKEFILE "\$(CLASSDIR)/" . $classfile . " ";
            $jcnt++;

            if($jcnt % 2 == 0) {
                print MAKEFILE "\\\n\t";
            }
        }

        foreach $file (@javafiles) {
	    $classfile = $file;
            $classfile =~ s/.java$/.class/;
            print MAKEFILE "\$(CLASSDIR)/" . $classfile . " ";
            $jcnt++;

            if($jcnt % 2 == 0) {
                print MAKEFILE "\\\n\t";
            }
        }

        print MAKEFILE "\n\n";
	print MAKEFILE "all: \$(CLASSES)\n\n";

	foreach $file (@javafiles) {
	    $classfile = $file;
            $classfile =~ s/.java$/.class/;
	    print MAKEFILE "\$(CLASSDIR)/$classfile: $file\n";
	    print MAKEFILE "\tjavac -g -target 1.8 -source 1.8 -d " . $rootdir . "/target/classes -J-Xmx1024m $file\n\n";
	}

	foreach $file (@scalafiles) {
	    $classfile = $file;
            $classfile =~ s/.scala$/.class/;
	    print MAKEFILE "\$(CLASSDIR)/$classfile: $file\n";
	    print MAKEFILE "\tscalac -d " . $rootdir . "/target/classes -J-Xmx1024m $file\n\n";
	}

	print MAKEFILE "tree: all\n";

	foreach $file (@dirs) {
	    print MAKEFILE "\tmake -C " . $file . " tree\n";
	}

        close MAKEFILE;
	return 1;
    }

    return 0;
}
