version_name=`grep 'versionName' app/build.gradle | awk '{for(i=1;i<=NF;i++){ if($i ~ /[0-9].*[0-9]/){print $i} } }' | tr -d \"`
./gradlew :app:assembleRelease
. keystore.properties
apksigner sign --ks $storeFile --ks-pass pass:$storePassword --ks-key-alias $keyAlias --key-pass pass:$keyPassword --out $PWD/app/build/outputs/apk/release/dev.corruptedark.diditakemymeds-$version_name-release.apk $PWD/app/build/outputs/apk/release/dev.corruptedark.diditakemymeds-$version_name-release-unsigned.apk
rm $PWD/app/build/outputs/apk/release/dev.corruptedark.diditakemymeds-$version_name-release-unsigned.apk
notes_file=`mktemp XXXXXXXXXX.v$version_name.md`
cp release-template.md $notes_file
vim $notes_file
title_file=`mktemp XXXXXXXXXX.release_title.txt`
vim $title_file
release_title=`cat $title_file`
echo "$release_title"
gh release create v$version_name --target `git branch --show-current` -F $notes_file -t "$release_title" "$PWD/app/build/outputs/apk/release/dev.corruptedark.diditakemymeds-$version_name-release.apk"
rm $notes_file
rm $title_file
