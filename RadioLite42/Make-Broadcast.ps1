param([Parameter(Mandatory=$true)][string]$Manifest,[Parameter(Mandatory=$true)][string]$OutputZip,[Parameter(Mandatory=$true)][string]$Ffmpeg)
$ErrorActionPreference='Stop'
$manifestPath=(Resolve-Path -LiteralPath $Manifest).Path
$base=Split-Path $manifestPath
$data=Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if (-not $data.opening -or -not $data.segments) { throw 'Require opening audio and timed speech segments.' }
$tmp=New-Item -ItemType Directory -Path (Join-Path ([IO.Path]::GetTempPath()) ('radio-'+[guid]::NewGuid()))
function To-Stamp([long]$ms) { '{0:00}:{1:00}:{2:00},{3:000}' -f ([math]::Floor($ms/3600000)),([math]::Floor($ms/60000)%60),([math]::Floor($ms/1000)%60),($ms%1000) }
try {
    $items=@(@{audio=$data.opening;text='';opening=$true})+@($data.segments)
    $list=[Collections.Generic.List[string]]::new(); $srt=[Collections.Generic.List[string]]::new(); $total=0.0; $number=0
    foreach($item in $items) {
        $source=Join-Path $base $item.audio
        $part=Join-Path $tmp.FullName ('p'+$list.Count+'.pcm')
        $args2=@('-hide_banner','-loglevel','error','-y','-i',$source)
        if($item.opening){$args2+=@('-t','6','-af','apad=whole_dur=6')}
        $args2+=@('-ac','1','-ar','24000','-f','s16le',$part)
        & $Ffmpeg @args2
        if($LASTEXITCODE -ne 0){throw 'Audio conversion failed'}
        $ms=(Get-Item -LiteralPath $part).Length/48.0
        if($ms -le 0){throw 'Empty audio segment'}
        if(-not $item.opening){if([string]::IsNullOrWhiteSpace($item.text)){throw 'Missing caption text'};$number++;$srt.Add("$number`n$(To-Stamp ([long]$total)) --> $(To-Stamp ([long]($total+$ms)))`n$($item.text.Trim())`n")}
        $total+=$ms;$list.Add($part)
    }
    $joined=Join-Path $tmp.FullName 'joined.pcm';$out=[IO.File]::Create($joined)
    try{foreach($part in $list){$inputStream=[IO.File]::OpenRead($part);try{$inputStream.CopyTo($out)}finally{$inputStream.Dispose()}}}finally{$out.Dispose()}
    $mp3=Join-Path $tmp.FullName 'broadcast.mp3';$sub=Join-Path $tmp.FullName 'broadcast.srt'
    & $Ffmpeg -hide_banner -loglevel error -y -f s16le -ar 24000 -ac 1 -i $joined -codec:a libmp3lame -b:a 48k $mp3
    if($LASTEXITCODE -ne 0){throw 'MP3 encoding failed'}
    [IO.File]::WriteAllText($sub,($srt -join "`n"),[Text.UTF8Encoding]::new($false))
    Compress-Archive -LiteralPath $mp3,$sub -DestinationPath $OutputZip -Force
    Get-Item -LiteralPath $OutputZip | Select-Object Name,Length,@{n='DurationSeconds';e={[math]::Round($total/1000,2)}}
} finally {
    # Temporary data only, beneath the uniquely created directory.
    Get-ChildItem -LiteralPath $tmp.FullName -File | Remove-Item
    Remove-Item -LiteralPath $tmp.FullName
}
