rm -rf bin gen
#ant -f pre-build.xml && ant debug && mv bin/Anymime-debug.apk bin/Anymime.apk
ant -Ddbglog=true -f pre-build.xml && ant release && mv bin/Anymime-release.apk bin/Anymime.apk

