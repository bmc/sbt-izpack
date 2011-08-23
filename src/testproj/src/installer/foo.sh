#!/bin/sh
#
# Dummy front-end shell script

vm_opts=
while [ $# -gt 0 ]
do
    case "$1" in
        -D*|-X*)
            vm_opts="$vm_opts $1"
	    shift
	    ;;
        *)
	    break
	    ;;
    esac
done

if [ "$SQLSHELL_SCALA_OPTS" != "" ]
then
    vm_opts="$vm_opts $SQLSHELL_SCALA_OPTS"
fi

_CP=
_sep=
for i in $INSTALL_PATH/lib/*
do
    _CP="${_CP}${_sep}${i}"
    _sep=":"
done

if [ -n "$CLASSPATH" ]
then
    _CP="$_CP:$_sep:$CLASSPATH"
fi

exec java -cp "$_CP" $vm_opts org.example.bar.foo.Foo "${@}"
