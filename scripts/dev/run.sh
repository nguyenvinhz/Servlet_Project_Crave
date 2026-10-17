#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
port="${1:-8080}"
mvn -B -ntp -Pdev -DskipTests package
mvn -B -ntp -Pdev org.apache.maven.plugins:maven-dependency-plugin:3.10.0:build-classpath \
  -DincludeScope=test -DincludeGroupIds=org.apache.tomcat.embed,org.apache.tomcat,org.eclipse.jdt -Dmdep.outputFile=target/dev-classpath.txt
exec java -Dfile.encoding=UTF-8 -cp "target/test-classes:$(cat target/dev-classpath.txt)" \
  vn.edu.crave.util.DevServer target/crave.war "$port"
