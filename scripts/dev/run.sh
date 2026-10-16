#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
port="${1:-8080}"
mvn -B -ntp -f backend/pom.xml -Pdev -DskipTests clean package
mvn -B -ntp -f backend/pom.xml -Pdev org.apache.maven.plugins:maven-dependency-plugin:3.10.0:build-classpath \
  -DincludeScope=test -DincludeGroupIds=org.apache.tomcat.embed,org.apache.tomcat,org.eclipse.jdt -Dmdep.outputFile=target/dev-classpath.txt
exec java -Dfile.encoding=UTF-8 -cp "backend/target/test-classes:$(cat backend/target/dev-classpath.txt)" \
  com.foodordering.dev.DevServer backend/target/crave.war "$port"
