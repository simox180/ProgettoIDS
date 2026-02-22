param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$Username = "smokeuser",
  [string]$Password = "smokepass",
  [string]$OrganizerUsername = "organizer1",
  [string]$OrganizerPassword = "organizer1pass",
  [string]$JudgeUsername = "judge1",
  [string]$JudgePassword = "judge1pass",
  [string]$MentorUsername = "mentor1",
  [string]$MentorPassword = "mentor1pass"
)

# Esecuzione:
# powershell -ExecutionPolicy Bypass -File .\scripts\e2e-smoke.ps1

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")

function As-Array($Value) {
  if ($null -eq $Value) {
    return @()
  }
  if ($Value -is [System.Array]) {
    return $Value
  }
  return @($Value)
}

function Get-HttpStatusCode([System.Management.Automation.ErrorRecord]$Err) {
  if ($Err.Exception.Response -and $Err.Exception.Response.StatusCode) {
    return [int]$Err.Exception.Response.StatusCode
  }
  return $null
}

function Get-ErrorBody([System.Management.Automation.ErrorRecord]$Err) {
  if (-not $Err.Exception.Response) {
    return $null
  }

  try {
    $stream = $Err.Exception.Response.GetResponseStream()
    if (-not $stream) {
      return $null
    }

    $reader = New-Object System.IO.StreamReader($stream)
    try {
      return $reader.ReadToEnd()
    } finally {
      $reader.Dispose()
    }
  } catch {
    return $null
  }
}

function Get-ErrorMessage($ErrorPayload) {
  if ($null -eq $ErrorPayload) {
    return "errore sconosciuto"
  }
  if ($ErrorPayload -is [string]) {
    return $ErrorPayload
  }
  if ($ErrorPayload.PSObject.Properties["error"]) {
    return [string]$ErrorPayload.error
  }
  if ($ErrorPayload.PSObject.Properties["message"]) {
    return [string]$ErrorPayload.message
  }
  return ($ErrorPayload | ConvertTo-Json -Compress)
}

function Invoke-Json([string]$Method,
                     [string]$Path,
                     $Body = $null,
                     [hashtable]$Headers = @{},
                     [switch]$Quiet) {
  $params = @{
    Method      = $Method
    Uri         = "$BaseUrl$Path"
    Headers     = $Headers
    ErrorAction = "Stop"
  }

  if ($null -ne $Body) {
    $params.ContentType = "application/json"
    $params.Body = $Body | ConvertTo-Json -Depth 10 -Compress
  }

  try {
    $data = Invoke-RestMethod @params
    return [pscustomobject]@{
      Ok     = $true
      Status = 200
      Data   = $data
      Error  = $null
    }
  } catch {
    $status = Get-HttpStatusCode $_
    $rawBody = Get-ErrorBody $_
    $parsedError = $rawBody
    if ($rawBody) {
      try {
        $parsedError = $rawBody | ConvertFrom-Json -ErrorAction Stop
      } catch {
        $parsedError = $rawBody
      }
    }

    if (-not $Quiet) {
      $statusLabel = "?"
      if ($null -ne $status) {
        $statusLabel = [string]$status
      }
      Write-Host ("[HTTP {0}] {1} {2} -> {3}" -f $statusLabel, $Method, $Path, (Get-ErrorMessage $parsedError))
    }

    return [pscustomobject]@{
      Ok     = $false
      Status = $status
      Data   = $null
      Error  = $parsedError
    }
  }
}

function Assert-Ok([string]$Step, $Result) {
  if ($Result.Ok) {
    return
  }
  $statusLabel = "?"
  if ($null -ne $Result.Status) {
    $statusLabel = [string]$Result.Status
  }
  throw ("{0} fallito (HTTP {1}): {2}" -f $Step, $statusLabel, (Get-ErrorMessage $Result.Error))
}

function Login([string]$Type, [string]$Identifier, [string]$Secret) {
  $login = Invoke-Json -Method "POST" -Path "/api/auth/login" -Body @{
    type = $Type
    identifier = $Identifier
    password = $Secret
  }
  Assert-Ok "Login $Type/$Identifier" $login
  if ([string]::IsNullOrWhiteSpace($login.Data.token)) {
    throw "Login $Type/$Identifier senza token"
  }
  return $login.Data
}

function Get-AssignedHackathon([string]$StaffToken, [long]$HackathonId) {
  $result = Invoke-Json -Method "GET" -Path "/api/staff/me/hackathons" -Headers @{ "X-Session-Token" = $StaffToken }
  Assert-Ok "Lettura hackathon assegnati staff" $result

  foreach ($entry in (As-Array $result.Data)) {
    if ([long]$entry.hackathonId -eq $HackathonId) {
      return $entry
    }
  }
  return $null
}

try {
  Write-Host "== E2E Smoke REST =="

  $register = Invoke-Json -Method "POST" -Path "/api/auth/register" -Body @{
    username = $Username
    password = $Password
  } -Quiet
  if ($register.Ok) {
    Write-Host ("USER registrato: userId={0}" -f $register.Data.userId)
  } elseif ($register.Status -in @(400, 409)) {
    Write-Host "USER gia presente, continuo."
  } else {
    Assert-Ok "Registrazione USER" $register
  }

  $userLogin = Login -Type "USER" -Identifier $Username -Secret $Password
  $organizerLogin = Login -Type "STAFF" -Identifier $OrganizerUsername -Secret $OrganizerPassword
  $judgeLogin = Login -Type "STAFF" -Identifier $JudgeUsername -Secret $JudgePassword
  $mentorLogin = Login -Type "STAFF" -Identifier $MentorUsername -Secret $MentorPassword

  $userToken = $userLogin.token
  $organizerToken = $organizerLogin.token
  $judgeToken = $judgeLogin.token
  $mentorToken = $mentorLogin.token

  $userHeaders = @{ "X-Session-Token" = $userToken }
  $organizerHeaders = @{ "X-Session-Token" = $organizerToken }
  $judgeHeaders = @{ "X-Session-Token" = $judgeToken }
  $mentorHeaders = @{ "X-Session-Token" = $mentorToken }

  Write-Host ("Token USER={0}" -f $userToken)
  Write-Host ("Token ORGANIZER={0}" -f $organizerToken)
  Write-Host ("Token JUDGE={0}" -f $judgeToken)
  Write-Host ("Token MENTOR={0}" -f $mentorToken)

  $myTeam = Invoke-Json -Method "GET" -Path "/api/me/team" -Headers $userHeaders -Quiet
  if ($myTeam.Ok) {
    $teamId = [long]$myTeam.Data.teamId
    Write-Host ("Team gia esistente: teamId={0}" -f $teamId)
  } elseif ($myTeam.Status -eq 400) {
    $teamName = ("Team-{0}-{1}" -f $Username, (Get-Date -Format "HHmmss"))
    $teamCreate = Invoke-Json -Method "POST" -Path "/api/me/team" -Headers $userHeaders -Body @{ teamName = $teamName }
    Assert-Ok "Creazione team" $teamCreate
    $teamId = [long]$teamCreate.Data.teamId
    Write-Host ("Team creato: teamId={0}, teamName={1}" -f $teamId, $teamCreate.Data.teamName)
  } else {
    Assert-Ok "Lettura team utente" $myTeam
  }

  $myRegistration = Invoke-Json -Method "GET" -Path "/api/me/registration" -Headers $userHeaders -Quiet
  if ($myRegistration.Ok) {
    $registrationId = [long]$myRegistration.Data.registrationId
    $hackathonId = [long]$myRegistration.Data.hackathonId
    Write-Host ("Registrazione gia esistente: registrationId={0}, hackathonId={1}" -f $registrationId, $hackathonId)
  } elseif ($myRegistration.Status -eq 400) {
    $registerable = Invoke-Json -Method "GET" -Path "/api/me/registerable-hackathons" -Headers $userHeaders
    Assert-Ok "Lista hackathon registrabili" $registerable
    $options = As-Array $registerable.Data
    if ($options.Count -eq 0) {
      throw "Nessun hackathon registrabile disponibile"
    }

    $selected = $options | Where-Object { $_.status -eq "REGISTRATION" } | Select-Object -First 1
    if ($null -eq $selected) {
      $selected = $options[0]
    }
    $hackathonId = [long]$selected.id

    $registerTeam = Invoke-Json -Method "POST" -Path "/api/me/registration" -Headers $userHeaders -Body @{
      hackathonId = $hackathonId
    }
    Assert-Ok "Registrazione team ad hackathon" $registerTeam
    $registrationId = [long]$registerTeam.Data.registrationId
    Write-Host ("Registrazione creata: registrationId={0}, hackathonId={1}" -f $registrationId, $hackathonId)
  } else {
    Assert-Ok "Lettura registrazione team" $myRegistration
  }

  $assignedForOrganizer = Get-AssignedHackathon -StaffToken $organizerToken -HackathonId $hackathonId
  if ($null -eq $assignedForOrganizer) {
    throw ("Organizer non assegnato ad hackathonId={0}" -f $hackathonId)
  }

  if ($assignedForOrganizer.status -eq "REGISTRATION") {
    $advanceToRunning = Invoke-Json -Method "POST" -Path ("/api/staff/organizer/hackathons/{0}/advance" -f $hackathonId) -Headers $organizerHeaders
    Assert-Ok "Advance hackathon a RUNNING" $advanceToRunning
    $assignedForOrganizer = Get-AssignedHackathon -StaffToken $organizerToken -HackathonId $hackathonId
  }
  if ($assignedForOrganizer.status -ne "RUNNING") {
    throw ("Hackathon {0} non in RUNNING (stato attuale: {1})" -f $hackathonId, $assignedForOrganizer.status)
  }
  Write-Host ("Hackathon {0} in stato RUNNING" -f $hackathonId)

  $submissionContent = ("Smoke submission {0}" -f (Get-Date -Format "s"))
  $submitResult = Invoke-Json -Method "POST" -Path "/api/me/submission" -Headers $userHeaders -Body @{ content = $submissionContent } -Quiet
  if (-not $submitResult.Ok -and $submitResult.Status -eq 409) {
    $submitResult = Invoke-Json -Method "PUT" -Path "/api/me/submission" -Headers $userHeaders -Body @{ content = $submissionContent }
    Assert-Ok "Update submission utente" $submitResult
  } else {
    Assert-Ok "Create submission utente" $submitResult
  }
  $submissionId = [long]$submitResult.Data.submissionId
  Write-Host ("Submission pronta: submissionId={0}" -f $submissionId)

  $staffDirectory = Invoke-Json -Method "GET" -Path "/api/staff/members" -Headers $organizerHeaders
  Assert-Ok "Lettura staff directory" $staffDirectory
  Write-Host ("Staff members: {0}" -f ((As-Array $staffDirectory.Data).Count))

  $judgeAssignments = Invoke-Json -Method "GET" -Path "/api/staff/me/hackathons" -Headers $judgeHeaders
  Assert-Ok "Lista hackathon assegnati (judge)" $judgeAssignments
  Write-Host ("Hackathon assegnati a judge: {0}" -f ((As-Array $judgeAssignments.Data).Count))

  $submissionsForJudge = Invoke-Json -Method "GET" -Path ("/api/staff/hackathons/{0}/submissions" -f $hackathonId) -Headers $judgeHeaders
  Assert-Ok "Lista submission per hackathon (judge)" $submissionsForJudge
  $submissionList = As-Array $submissionsForJudge.Data
  if ($submissionList.Count -eq 0) {
    throw "Nessuna submission trovata per la valutazione"
  }
  $submissionId = [long]$submissionList[0].submissionId
  Write-Host ("Submission selezionata per valutazione: submissionId={0}" -f $submissionId)

  $supportCreate = Invoke-Json -Method "POST" -Path "/api/me/support/requests" -Headers $userHeaders -Body @{
    message = ("Smoke support request {0}" -f (Get-Date -Format "s"))
  }
  Assert-Ok "Creazione support request utente" $supportCreate
  $supportRequestId = [long]$supportCreate.Data.requestId
  Write-Host ("Support request creata: requestId={0}" -f $supportRequestId)

  $mentorRequests = Invoke-Json -Method "GET" -Path ("/api/staff/mentor/hackathons/{0}/support-requests" -f $hackathonId) -Headers $mentorHeaders
  Assert-Ok "Lista support request mentor" $mentorRequests
  $requests = As-Array $mentorRequests.Data
  if ($requests.Count -eq 0) {
    throw "Nessuna support request disponibile per il mentor"
  }
  $matchingRequest = $requests | Where-Object { [long]$_.requestId -eq $supportRequestId } | Select-Object -First 1
  if ($null -eq $matchingRequest) {
    $supportRequestId = [long]$requests[0].requestId
  }

  $proposalStart = (Get-Date).AddHours(1).ToString("yyyy-MM-ddTHH:mm:ss")
  $proposalEnd = (Get-Date).AddHours(1).AddMinutes(45).ToString("yyyy-MM-ddTHH:mm:ss")
  $createProposal = Invoke-Json -Method "POST" `
    -Path ("/api/staff/mentor/support-requests/{0}/call-proposals" -f $supportRequestId) `
    -Headers $mentorHeaders `
    -Body @{
      proposedStart = $proposalStart
      proposedEnd = $proposalEnd
    }
  Assert-Ok "Creazione call proposal mentor" $createProposal
  $proposalId = [long]$createProposal.Data.proposalId
  Write-Host ("Call proposal creata: proposalId={0}" -f $proposalId)

  $userProposals = Invoke-Json -Method "GET" -Path "/api/me/calls/proposals" -Headers $userHeaders
  Assert-Ok "Lista call proposals user" $userProposals
  $proposalList = As-Array $userProposals.Data
  if ($proposalList.Count -eq 0) {
    throw "Nessuna call proposal disponibile per l'utente"
  }
  $selectedProposal = $proposalList | Where-Object { [long]$_.proposalId -eq $proposalId } | Select-Object -First 1
  if ($null -eq $selectedProposal) {
    $selectedProposal = $proposalList[0]
    $proposalId = [long]$selectedProposal.proposalId
  }

  $bookCall = Invoke-Json -Method "POST" -Path ("/api/me/calls/proposals/{0}/book" -f $proposalId) -Headers $userHeaders
  Assert-Ok "Booking call utente" $bookCall
  $callId = [long]$bookCall.Data.callId
  Write-Host ("Call prenotata: callId={0}, meetingLink={1}" -f $callId, $bookCall.Data.meetingLink)

  $advanceToReview = Invoke-Json -Method "POST" -Path ("/api/staff/organizer/hackathons/{0}/advance" -f $hackathonId) -Headers $organizerHeaders
  Assert-Ok "Advance hackathon a REVIEW" $advanceToReview

  $judgeEvaluate = Invoke-Json -Method "POST" `
    -Path ("/api/staff/hackathons/{0}/submissions/{1}/evaluation" -f $hackathonId, $submissionId) `
    -Headers $judgeHeaders `
    -Body @{
      score = 8
      comment = "Smoke test evaluation"
    }
  Assert-Ok "Valutazione submission (judge)" $judgeEvaluate
  $evaluationId = [long]$judgeEvaluate.Data.evaluationId
  Write-Host ("Valutazione creata: evaluationId={0}" -f $evaluationId)

  $checkEvaluation = Invoke-Json -Method "GET" `
    -Path ("/api/staff/hackathons/{0}/submissions/{1}/evaluation" -f $hackathonId, $submissionId) `
    -Headers $judgeHeaders
  Assert-Ok "Lettura valutazione (judge)" $checkEvaluation

  [void](Invoke-Json -Method "POST" -Path "/api/auth/logout" -Headers $userHeaders -Body @{} -Quiet)
  [void](Invoke-Json -Method "POST" -Path "/api/auth/logout" -Headers $organizerHeaders -Body @{} -Quiet)
  [void](Invoke-Json -Method "POST" -Path "/api/auth/logout" -Headers $judgeHeaders -Body @{} -Quiet)
  [void](Invoke-Json -Method "POST" -Path "/api/auth/logout" -Headers $mentorHeaders -Body @{} -Quiet)

  Write-Host ("SUMMARY teamId={0} registrationId={1} hackathonId={2} submissionId={3} requestId={4} proposalId={5} callId={6} evaluationId={7}" -f `
    $teamId, $registrationId, $hackathonId, $submissionId, $supportRequestId, $proposalId, $callId, $evaluationId)
  Write-Host "E2E smoke completato."
} catch {
  Write-Host ("E2E smoke fallito: {0}" -f $_.Exception.Message)
  exit 1
}
