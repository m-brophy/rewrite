parser grammar VersionRangeParser;

options { tokenVocab=VersionRangeLexer; }

versionRequirement
    : range (COMMA range)*;

range
    : (OPEN_RANGE_OPEN | CLOSED_RANGE_OPEN) bounds (OPEN_RANGE_CLOSE | CLOSED_RANGE_CLOSE)
    ;

bounds
    : boundedLower
    | unboundedLower
    | exactly
    ;

exactly
    : Version
    ;

boundedLower
    : (Version COMMA Version?)
    ;

unboundedLower
    : (COMMA Version?)
    ;

version
    : Version
    | (PROPERTY_OPEN Version PROPERTY_CLOSE)
    ;
