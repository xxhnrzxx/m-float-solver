$ErrorActionPreference = 'Stop'
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$bt  = "$sdk\build-tools\36.0.0"
$aj  = "$sdk\platforms\android-36\android.jar"
$proj = "C:\Users\m\Documents\Trae-CN\temp_20261122_03\android-float-solver"
$out = "$proj\build"
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Path "$out\res","$out\gen","$out\classes","$out\dex" -Force | Out-Null

Write-Output "== aapt2 compile =="
& "$bt\aapt2.exe" compile --dir "$proj\res" -o "$out\res"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Output "== aapt2 link =="
$flat = (Get-ChildItem "$out\res\*.flat" | ForEach-Object { $_.FullName })
& "$bt\aapt2.exe" link -o "$out\base.apk" -I $aj --manifest "$proj\AndroidManifest.xml" `
    --java "$out\gen" --min-sdk-version 30 --target-sdk-version 33 --auto-add-overlay -R $flat
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Output "== javac =="
$srcs = (Get-ChildItem -Recurse "$proj\src\*.java" | ForEach-Object { $_.FullName })
$rj = (Get-ChildItem -Recurse "$out\gen\*.java" | ForEach-Object { $_.FullName })
& javac --release 11 -encoding UTF-8 -nowarn -classpath $aj -d "$out\classes" $srcs $rj
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Output "== d8 =="
& "$bt\d8.bat" --release --lib $aj --min-api 30 --output "$out\dex" (Get-ChildItem -Recurse "$out\classes\*.class" | ForEach-Object { $_.FullName })
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Output "== package =="
$py = @'
import zipfile, shutil, sys
base, dex, out = sys.argv[1], sys.argv[2], sys.argv[3]
shutil.copyfile(base, out)
with zipfile.ZipFile(out, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
print("packed")
'@
$pyPath = "$out\pack.py"
[IO.File]::WriteAllText($pyPath, $py)
& python $pyPath "$out\base.apk" "$out\dex\classes.dex" "$out\unsigned.apk"
if ($LASTEXITCODE -ne 0) { throw "pack failed" }

Write-Output "== zipalign =="
& "$bt\zipalign.exe" -f -p 4 "$out\unsigned.apk" "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Output "== apksigner =="
$rel = "$proj\release.keystore"
if (Test-Path $rel) {
    & "$bt\apksigner.bat" sign --ks $rel --ks-pass pass:mfloat2026 --key-pass pass:mfloat2026 --ks-key-alias mfloat --out "$out\app-release.apk" "$out\aligned.apk"
    if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }
    $final = "$out\app-release.apk"
} else {
    $ks = "$env:USERPROFILE\.android\debug.keystore"
    & "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out "$out\app-debug.apk" "$out\aligned.apk"
    if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }
    $final = "$out\app-debug.apk"
}

Write-Output "== verify =="
& "$bt\apksigner.bat" verify --print-certs $final
& "$bt\aapt2.exe" dump badging $final | Select-Object -First 6
Write-Output ("APK: " + $final + "  " + (Get-Item $final).Length + " bytes")
