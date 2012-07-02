#!/usr/bin/perl

###############################################################################
#    Copyright (C) 2012 Younghyung Cho. <yhcting77@gmail.com>
#
#    This file is part of Feeder.
#
#    This program is free software: you can redistribute it and/or modify
#    it under the terms of the GNU Lesser General Public License as
#    published by the Free Software Foundation either version 3 of the
#    License, or (at your option) any later version.
#
#    This program is distributed in the hope that it will be useful,
#    but WITHOUT ANY WARRANTY; without even the implied warranty of
#    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#    GNU Lesser General Public License
#    (<http://www.gnu.org/licenses/lgpl.html>) for more details.
#
#    You should have received a copy of the GNU General Public License
#    along with this program.  If not, see <http://www.gnu.org/licenses/>.
###############################################################################

###############################################################################
# Programming Policy
# ------------------
# DO NOT use any module that is not included as default!
# (I don't want to do additional efforts to run this script!)
###############################################################################

###############################################################################
# Really Simple Preprocessor
#
# ========================================
# DB Script Guide (Preprocessor mechanism)
# ========================================
#
# Syntax
# ------
# Script code only can be located right after script comment following by !.
# (Any other non-white-space character is not allowed before '--')
# ex.
#   --!<script code> xxxx
#
# Comment is started with '#'
# ex.
#   --!#xxxx

# Variable name whould not include white spaces.
#
# Script variable can be used out of sql comment and has following form.
# !<xxx>
# ex.
#   !<size>, !<table_name>
#
#
# Directives
# ----------
# SET <variable> <value>
#     <value> : leading / trailing white spaces are NOT included.
# SET_OFFSET <value> : set current offset to <value>
#     <value> : should be decimal number.
#
# Predefined Variables
# --------------------
# !<#> : return current offset and increase current offset by 1.
#
###############################################################################

##################################
# Running environment setup
##################################
use strict;
use warnings;
use constant {
    true    => 1,
    false   => 0,
    debug   => 0,
};

# Promote all warnings to fatal error!
$SIG{__WARN__} = sub { die @_; };

##################################
# Constant Static Data
##################################
my %pred_vars = (
    '#'    => \&predv_sharp
);

my %directives = (
    'SET'         => \&cmd_set,
    'SET_OFFSET'  => \&cmd_set_offset
);


##################################
# Utility Functions
##################################
sub dpr {
    if (0 != debug) {
	print @_;
    }
}

sub usage {
    print "./pp-dbscropt.pl <dbscript.in>\n";
    exit 1;
}

sub remove_leading_whitespaces {
    my $r = shift;
    $r =~ s/^\s+//;
    return $r;
}

sub remove_trailing_whitespaces {
    my $r = shift;
    $r =~ s/\s+$//;
    return $r;
}

##################################
# Command Functions
##################################
sub cmd_set {
    my $smap = shift;
    my $arg  = shift;
    my $ln   = shift;
    my ($var, $val);

    if ($arg =~ /^(\w+)(.+)$/) {
	$var = $1;
	$val = $2;
	$val = remove_leading_whitespaces($val);
	$smap->{$var} = $val;
    } else {
	die "ERROR : Invalid syntax : $arg (line #$ln)\n";
    }
}

sub cmd_set_offset {
    my $smap = shift;
    my $arg  = shift;
    my $ln   = shift;

    unless ($arg =~ /^[0-9]+$/) {
	die "ERROR : invalid arguement : $arg (line #$ln)\n";
    }
    $smap->{'!CUR'} = int($arg);
}

##################################
# Directive Handlers
##################################
sub substitution {
    my $smap = shift;
    my $text = shift;
    my $ln   = shift;

    my ($k, $v);
    # check predefined special variable !<#>
    while ($text =~ /(\!\<\#\>)/) {
	$text =~ s/$1/$smap->{'!CUR'}/g;
	$smap->{'!CUR'}++;
    }

    while ($text =~ /(\!\<(\w+)\>)/) {
	if (defined $smap->{$2}) {
	    $k = $1;
	    $v = $smap->{$2};
	    $text =~ s/$k/$v/g;
	} else {
	    die "ERROR : Undefined variable : $2 (line #$ln)\n";
	}
    }
    return $text;
}

sub process_code {
    my $smap = shift;
    my $code = shift;
    my $ln   = shift;
    my $arg;
    $code = remove_leading_whitespaces($code);
    $code = remove_trailing_whitespaces($code);
    if ($code =~ /^(\w+)(.*)$/) {
	if (defined $directives{$1}) {
	    if (defined $2) {
		$arg = remove_leading_whitespaces($2);
	    } else {
		$arg = "";
	    }
	    $arg = substitution($smap, $arg, $ln);
	    $directives{$1}($smap, $arg, $ln);
	} else {
	    die "ERROR : Unknown directive : $1 (line #$ln)\n";
	}
    }
}

##################################
#
# Main
#
##################################

# preprocessor symbol map
my %smap = (
    # predefined current offset (reserved variable)
    '!CUR'   => 0
);

usage unless (0 == $#ARGV);

my (@scin, $filepp);
$filepp = $ARGV[0].".sql";

open (FI, "<$ARGV[0]")
    or die "ERROR : Can't open input script file\n$!";
@scin = <FI>;
close(FI);

my ($ln, $var, $val, $text);
$ln = 0;
open (FO, ">$filepp")
    or die "ERROR : Can't open output file : $filepp\n$!";
foreach (@scin) {
    $ln++;
    if (/^\s*\-\-\!(\#?)(.+)$/) {
	# check comment.
	unless ("#" eq $1) {
	    if ("" ne $2) {
		process_code(\%smap, $2, $ln);
	    }
	}
	next;
    }
    $text = substitution(\%smap, $_, $ln);
    print FO $text;
}
close(FO);
