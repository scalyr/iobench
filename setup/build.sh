#!/bin/bash
#usage: ./build.sh build|clean
#                  build is the default

function build() {
	[ -d build ] || mkdir build
	javac -d build ../src/*.java
	( cd build && jar -cf ../iobench.jar * )
}
function clean() {
	rm -rf iobench.jar build
}
if [ $# -eq 0 ]; then build; else $1; fi
