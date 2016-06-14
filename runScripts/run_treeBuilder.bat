@ECHO OFF
set CLASSPATH=%CLASSPATH%;../lib/com.stephenwranger.graphics.jar;../dist/com.stephenwranger.thesis.jar

REM set ANT_HOME="/path/to/ant/bin"
REM set JAVA_HOME="/path/to/java/bin"
REM set PATH=%PATH%;%ANT_HOME%;%JAVA_HOME%

java -Djava.net.useSystemProxies=true -XX:MaxPermSize=256m -Xmx4096m com.stephenwranger.thesis.TreeBuilder %*