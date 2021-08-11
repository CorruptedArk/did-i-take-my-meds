version_name=`grep 'versionName' app/build.gradle | awk '{for(i=1;i<=NF;i++){ if($i ~ /[0-9].*[0-9]/){print $i} } }' | tr -d \"`
./gradlew :app:bundlePlayRelease
. google-keystore.properties
jarsigner -keystore $storeFile -storepass $storePassword -keypass $keyPassword -signedjar $PWD/app/build/outputs/bundle/playRelease/dev.corruptedark.diditakemymeds-$version_name-playRelease-signed.aab $PWD/app/build/outputs/bundle/playRelease/dev.corruptedark.diditakemymeds-$version_name-playRelease.aab $keyAlias
rm $PWD/app/build/outputs/bundle/playRelease/dev.corruptedark.diditakemymeds-$version_name-playRelease.aab
