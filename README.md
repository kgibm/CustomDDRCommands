# CustomDDRCommands

1. Install a Java 8 j9ddr.jar into the local repository: `mvn install:install-file -Dfile=jre/lib/ddr/j9ddr.jar -DgroupId=org.openj9 -DartifactId=j9ddr -Dversion=1.0-SNAPSHOT -Dpackaging=jar -DgeneratePom=true`
1. Compile: `mvn clean install`
1. Add to jdmpview and run the command: `jdmpview -J-Dplugins=target/customddrcommands-1.0-SNAPSHOT.jar -core $CORE`
