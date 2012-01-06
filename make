rm -rf bin gen
ant -Ddbglog=true -f pre-build.xml && ant release && mv bin/Anymime-release.apk bin/Anymime.apk

