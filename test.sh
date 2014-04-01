#!/bin/sh
protoc --java_out=./src/test/java --foo_out=./src/test/java --plugin=protoc-gen-foo src/test/resources/sample.proto
