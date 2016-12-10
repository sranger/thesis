@ECHO OFF

set CLASSPATH=%CLASSPATH%;../dist/com.stephenwranger.thesis.jar

pushd ..\lib
for /r %%j IN ("*.jar") DO (call :sub_append "%%j")
popd

REM set ANT_HOME="/path/to/ant/bin"
REM set JAVA_HOME="/path/to/java/bin"
REM set PATH=%PATH%;%ANT_HOME%;%JAVA_HOME%

set DEM_DIRECTORY="D:/GISData/dem/viewfinderpanoramas.org/"
set ICOSATREE_DIRECTORY="D:/GISData/point_cloud/SanSimeon_small_icosatree_triangularCoordinates_100x100/"
set IMAGE_CACHE="D:/GISData/imageCache"
set STAMEN_SERVER="http://{a,b,c,d}.tile.stamen.com/terrain"

java -Ddem3.directory=%DEM_DIRECTORY% -Dimage.cache=%IMAGE_CACHE% -Dstamen.server=%STAMEN_SERVER% -Djava.net.useSystemProxies=true -Xmx6g com.stephenwranger.thesis.visualization.ThesisVisualization %ICOSATREE_DIRECTORY% FILESYSTEM %*

REM Do not process the subroutines underneath - the program is finished.
GOTO :eof

:DeQuote
SET DeQuote.Variable=%1
CALL Set DeQuote.Contents=%%%DeQuote.Variable%%%
Echo.%DeQuote.Contents%|FindStr/brv ""^">NUL:&&Goto :EOF
Echo.%DeQuote.Contents%|FindStr/erv ""^">NUL:&&Goto :EOF

Set DeQuote.Contents=####%DeQuote.Contents%####
Set DeQuote.Contents=%DeQuote.Contents:####"=%
Set DeQuote.Contents=%DeQuote.Contents:"####=%
Set %DeQuote.Variable%=%DeQuote.Contents%

Set DeQuote.Variable=
Set DeQuote.Contents=
Goto :eof

REM Append a path to classpath
:sub_append
SET entry=%1
CALL :DeQuote entry
set classpath=%entry%;%classpath%
REM End of subroutine, skip everything underneath
GOTO :eof