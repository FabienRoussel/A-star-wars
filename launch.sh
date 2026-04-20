#!/bin/bash
export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
java -cp "$DIR/out:$DIR/jars/lwjgl.jar:$DIR/jars/lwjgl-glfw.jar:$DIR/jars/lwjgl-opengl.jar:$DIR/jars/lwjgl-openal.jar" -Djava.library.path="$DIR/natives" MainClass
