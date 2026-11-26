# Matrix-stack balance check.
#
# WHY this exists: an unbalanced glPushMatrix/glPopMatrix does not look like a transform bug. It leaks one
# entry of the fixed-function matrix stack (32 deep) per frame, the stack fills within seconds, every later
# push reports GL_STACK_OVERFLOW (1283), and because a FAILED push makes the following glScaled()/glTranslatef()
# write into the previous matrix, affected content collapses to nothing while everything drawn in another
# matrix state keeps painting normally. The visible symptom is a blank panel or a "GUI that closes itself",
# reported at "Post render" in run/logs/latest.log, and it is easy to misread as an animation or alpha bug.
#
# Usage:  & .\tools\verify\matrix_balance.ps1 [-Root src\main\java]
#
# Known false positives: vanilla renderers that push into GL_TEXTURE (matrixMode 5890) in one branch and into
# GL_MODELVIEW in another (e.g. TileEntityChestRenderer) are balanced per matrix mode even though the raw
# counts differ. Treat any file reported here as a question to answer, not automatically as a defect.

param([string]$Root = "src\main\java")

# Balanced per matrix mode, not per raw count: the texture-matrix push (matrixMode 5890) sits in one branch and
# is popped by the conditional pop in the same mode, while the modelview push sits in another. Vanilla code.
$KnownFalsePositive = @("TileEntityChestRenderer.java")

$files = Get-ChildItem -Path $Root -Recurse -File -Filter *.java
$bad = @()
$known = @()
foreach ($f in $files) {
    $text = Get-Content -Raw $f.FullName
    $push = ([regex]::Matches($text, '[Pp]ushMatrix\s*\(')).Count
    $pop = ([regex]::Matches($text, '[Pp]opMatrix\s*\(')).Count
    if ($push -ne $pop) {
        $row = [pscustomobject]@{ File = $f.FullName.Substring((Get-Location).Path.Length + 1); Push = $push; Pop = $pop }
        if ($KnownFalsePositive -contains $f.Name) { $known += $row } else { $bad += $row }
    }
}
foreach ($k in $known) { "known false positive (per-matrix-mode balance): {0}  push={1} pop={2}" -f $k.File, $k.Push, $k.Pop }
if ($bad.Count -eq 0) {
    "OK: every other .java file pushes and pops the same number of matrices ($($files.Count) files scanned)."
    exit 0
}
foreach ($b in $bad) { "UNBALANCED: {0}  push={1} pop={2}" -f $b.File, $b.Push, $b.Pop }
exit 1