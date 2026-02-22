param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$OrganizerUsername = "organizer1",
    [string]$OrganizerPassword = "organizer1pass",
    [string]$JudgeUsername = "judge1",
    [string]$JudgePassword = "judge1pass",
    [string]$MentorUsername = "mentor1",
    [string]$MentorPassword = "mentor1pass"
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")

function Invoke-Json {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )

    $url = if ($Path.StartsWith("/")) { "$BaseUrl$Path" } else { "$BaseUrl/$Path" }

    try {
        $params = @{
            Method  = $Method
            Uri     = $url
            Headers = $Headers
        }

        if ($null -ne $Body) {
            $params["ContentType"] = "application/json"
            $params["Body"] = ($Body | ConvertTo-Json -Depth 10 -Compress)
        }

        $data = Invoke-RestMethod @params
        return [pscustomobject]@{
            Ok     = $true
            Status = $null
            Data   = $data
            Error  = $null
        }
    }
    catch {
        $status = $null
        $responseBody = $null
        $errorMessage = $_.Exception.Message

        if ($_.Exception -and $_.Exception.Response) {
            $response = $_.Exception.Response
            try {
                if ($response.StatusCode) {
                    $status = [int]$response.StatusCode
                }
            }
            catch {
                $status = $null
            }

            try {
                $stream = $response.GetResponseStream()
                if ($stream) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    $responseBody = $reader.ReadToEnd()
                    $reader.Dispose()
                    $stream.Dispose()
                }
            }
            catch {
                $responseBody = $null
            }
        }

        if (-not $responseBody -and $_.ErrorDetails -and $_.ErrorDetails.Message) {
            $responseBody = $_.ErrorDetails.Message
        }

        $printStatus = if ($null -eq $status) { "n/a" } else { "$status" }
        Write-Host "HTTP ERROR [$Method $Path] Status=$printStatus" -ForegroundColor Red
        if ($responseBody) {
            Write-Host "Response: $responseBody" -ForegroundColor Red
        }
        else {
            Write-Host "Response: $errorMessage" -ForegroundColor Red
        }

        $finalError = if ($responseBody) { $responseBody } else { $errorMessage }
        return [pscustomobject]@{
            Ok     = $false
            Status = $status
            Data   = $null
            Error  = $finalError
        }
    }
}

function Assert-Ok {
    param(
        [Parameter(Mandatory = $true)][string]$Step,
        [Parameter(Mandatory = $true)]$Result
    )

    if (-not $Result.Ok) {
        $code = if ($null -eq $Result.Status) { "n/a" } else { $Result.Status }
        throw "$Step failed (HTTP $code): $($Result.Error)"
    }
}

function Get-Field {
    param(
        [Parameter(Mandatory = $true)]$Object,
        [Parameter(Mandatory = $true)][string[]]$Names
    )

    foreach ($name in $Names) {
        if ($null -eq $Object) { continue }

        if ($Object -is [System.Collections.IDictionary]) {
            if ($Object.Contains($name)) { return $Object[$name] }
            if ($Object.ContainsKey($name)) { return $Object[$name] }
        }

        $prop = $Object.PSObject.Properties[$name]
        if ($null -ne $prop) { return $prop.Value }
    }

    return $null
}

function Require-Field {
    param(
        [Parameter(Mandatory = $true)]$Object,
        [Parameter(Mandatory = $true)][string[]]$Names,
        [Parameter(Mandatory = $true)][string]$Label
    )

    $value = Get-Field -Object $Object -Names $Names
    if ($null -eq $value -or ($value -is [string] -and [string]::IsNullOrWhiteSpace($value))) {
        throw "Missing required field '$Label'"
    }
    return $value
}

function To-Array {
    param([object]$InputObject)
    if ($null -eq $InputObject) { return @() }
    if ($InputObject -is [string]) { return @($InputObject) }
    if ($InputObject -is [System.Collections.IEnumerable]) { return @($InputObject) }
    return @($InputObject)
}

function New-AuthHeaders {
    param([Parameter(Mandatory = $true)][string]$Token)
    return @{ "X-Session-Token" = $Token }
}

function Login-Principal {
    param(
        [Parameter(Mandatory = $true)][string]$Type,
        [Parameter(Mandatory = $true)][string]$Identifier,
        [Parameter(Mandatory = $true)][string]$Password
    )

    $res = Invoke-Json -Method "POST" -Path "/api/auth/login" -Body @{
        type       = $Type
        identifier = $Identifier
        password   = $Password
    }
    Assert-Ok -Step "Login $Type/$Identifier" -Result $res

    $token = [string](Require-Field -Object $res.Data -Names @("token") -Label "token")
    $id = [long](Require-Field -Object $res.Data -Names @("id", "principalId") -Label "id")

    return [pscustomobject]@{
        Token = $token
        Id    = $id
    }
}

function Register-User-IfNeeded {
    param(
        [Parameter(Mandatory = $true)][string]$Username,
        [Parameter(Mandatory = $true)][string]$Password
    )

    $res = Invoke-Json -Method "POST" -Path "/api/auth/register" -Body @{
        username = $Username
        password = $Password
    }

    if ($res.Ok) {
        $newUserId = Get-Field -Object $res.Data -Names @("userId", "id")
        Write-Host "Register OK: $Username (userId=$newUserId)"
        return
    }

    if ($res.Status -eq 409 -or $res.Status -eq 400) {
        Write-Host "Register $Username skipped (HTTP $($res.Status)), continuo."
        return
    }

    Assert-Ok -Step "Register $Username" -Result $res
}

try {
    $ts = Get-Date -Format "yyyyMMddHHmmss"
    $userPassword = "Pwd_12345"
    $user1 = "full_u1_$ts"
    $user2 = "full_u2_$ts"

    $hackathonId = $null
    $teamId = $null
    $registrationId = $null
    $submissionId = $null
    $requestId = $null
    $proposalId = $null
    $callId = $null
    $reportId = $null

    Write-Host "Step 1: Login organizer/judge/mentor"
    $organizer = Login-Principal -Type "STAFF" -Identifier $OrganizerUsername -Password $OrganizerPassword
    $judge = Login-Principal -Type "STAFF" -Identifier $JudgeUsername -Password $JudgePassword
    $mentor = Login-Principal -Type "STAFF" -Identifier $MentorUsername -Password $MentorPassword

    Write-Host "Step 2: Resolve judgeId and mentorId from /api/staff/members"
    $membersRes = Invoke-Json -Method "GET" -Path "/api/staff/members" -Headers (New-AuthHeaders -Token $organizer.Token)
    Assert-Ok -Step "GET /api/staff/members" -Result $membersRes
    $members = To-Array -InputObject $membersRes.Data

    $judgeMember = $members | Where-Object { (Get-Field -Object $_ -Names @("username", "staffUsername")) -eq $JudgeUsername } | Select-Object -First 1
    $mentorMember = $members | Where-Object { (Get-Field -Object $_ -Names @("username", "staffUsername")) -eq $MentorUsername } | Select-Object -First 1
    if ($null -eq $judgeMember) { throw "Judge '$JudgeUsername' non trovato in /api/staff/members" }
    if ($null -eq $mentorMember) { throw "Mentor '$MentorUsername' non trovato in /api/staff/members" }

    $judgeId = [long](Require-Field -Object $judgeMember -Names @("staffId", "id") -Label "judgeId")
    $mentorId = [long](Require-Field -Object $mentorMember -Names @("staffId", "id") -Label "mentorId")

    Write-Host "Step 3: Create hackathon"
    $now = Get-Date
    $createHackathonBody = @{
        name                 = "Hackathon-FULL-$ts"
        regulation           = "Regolamento FULL $ts"
        registrationDeadline = $now.AddDays(3).ToString("yyyy-MM-ddTHH:mm:ss")
        startDate            = $now.AddDays(4).ToString("yyyy-MM-ddTHH:mm:ss")
        submissionDeadline   = $now.AddDays(5).ToString("yyyy-MM-ddTHH:mm:ss")
        endDate              = $now.AddDays(6).ToString("yyyy-MM-ddTHH:mm:ss")
        location             = "Camerino"
        prizeAmount          = 1000.00
        maxTeamSize          = 5
        judgeId              = $judgeId
        mentorIds            = @($mentorId)
    }
    $createHackathonRes = Invoke-Json -Method "POST" -Path "/api/staff/organizer/hackathons" -Body $createHackathonBody -Headers (New-AuthHeaders -Token $organizer.Token)
    Assert-Ok -Step "Create hackathon" -Result $createHackathonRes
    $hackathonId = [long](Require-Field -Object $createHackathonRes.Data -Names @("hackathonId", "id") -Label "hackathonId")

    Write-Host "Step 4: Register 2 users"
    Register-User-IfNeeded -Username $user1 -Password $userPassword
    Register-User-IfNeeded -Username $user2 -Password $userPassword

    Write-Host "Step 5: Login user1 and user2"
    $u1 = Login-Principal -Type "USER" -Identifier $user1 -Password $userPassword
    $u2 = Login-Principal -Type "USER" -Identifier $user2 -Password $userPassword

    Write-Host "Step 6: user1 creates team"
    $teamRes = Invoke-Json -Method "POST" -Path "/api/me/team" -Body @{ teamName = "Team-FULL-$ts" } -Headers (New-AuthHeaders -Token $u1.Token)
    Assert-Ok -Step "Create team" -Result $teamRes
    $teamId = [long](Require-Field -Object $teamRes.Data -Names @("teamId", "id") -Label "teamId")

    Write-Host "Step 7: user1 invites user2, user2 accepts"
    $inviteRes = Invoke-Json -Method "POST" -Path "/api/me/team/invitations" -Body @{ invitedUsername = $user2 } -Headers (New-AuthHeaders -Token $u1.Token)
    Assert-Ok -Step "Invite user2" -Result $inviteRes
    $invitationId = [long](Require-Field -Object $inviteRes.Data -Names @("invitationId", "id") -Label "invitationId")

    $acceptRes = Invoke-Json -Method "POST" -Path "/api/me/invitations/$invitationId" -Body @{ action = "ACCEPT" } -Headers (New-AuthHeaders -Token $u2.Token)
    Assert-Ok -Step "Accept invitation" -Result $acceptRes

    Write-Host "Step 8: user1 registers team to hackathon"
    $regRes = Invoke-Json -Method "POST" -Path "/api/me/registration" -Body @{ hackathonId = $hackathonId } -Headers (New-AuthHeaders -Token $u1.Token)
    Assert-Ok -Step "Register team to hackathon" -Result $regRes
    $registrationId = [long](Require-Field -Object $regRes.Data -Names @("registrationId", "id") -Label "registrationId")

    Write-Host "Step 9: organizer advance to RUNNING"
    $advance1Res = Invoke-Json -Method "POST" -Path "/api/staff/organizer/hackathons/$hackathonId/advance" -Body @{} -Headers (New-AuthHeaders -Token $organizer.Token)
    Assert-Ok -Step "Advance hackathon to RUNNING" -Result $advance1Res

    Write-Host "Step 10: user1 submits project"
    $submissionRes = Invoke-Json -Method "POST" -Path "/api/me/submission" -Body @{ content = "FULL submission content $ts" } -Headers (New-AuthHeaders -Token $u1.Token)
    Assert-Ok -Step "Submit project" -Result $submissionRes
    $submissionId = [long](Require-Field -Object $submissionRes.Data -Names @("submissionId", "id") -Label "submissionId")

    Write-Host "Step 11: user1 creates support request"
    $supportRes = Invoke-Json -Method "POST" -Path "/api/me/support/requests" -Body @{ message = "Need support $ts" } -Headers (New-AuthHeaders -Token $u1.Token)
    Assert-Ok -Step "Create support request" -Result $supportRes
    $requestId = [long](Require-Field -Object $supportRes.Data -Names @("requestId", "id") -Label "requestId")

    Write-Host "Step 12: mentor creates call proposal"
    $proposalStart = (Get-Date).AddHours(2).ToString("yyyy-MM-ddTHH:mm:ss")
    $proposalEnd = (Get-Date).AddHours(2.5).ToString("yyyy-MM-ddTHH:mm:ss")
    $proposalRes = Invoke-Json -Method "POST" -Path "/api/staff/mentor/support-requests/$requestId/call-proposals" -Body @{
        proposedStart = $proposalStart
        proposedEnd   = $proposalEnd
    } -Headers (New-AuthHeaders -Token $mentor.Token)
    Assert-Ok -Step "Create call proposal" -Result $proposalRes
    $proposalId = [long](Require-Field -Object $proposalRes.Data -Names @("proposalId", "id") -Label "proposalId")

    Write-Host "Step 13: user1 books call proposal"
    $bookRes = Invoke-Json -Method "POST" -Path "/api/me/calls/proposals/$proposalId/book" -Body @{} -Headers (New-AuthHeaders -Token $u1.Token)
    Assert-Ok -Step "Book call proposal" -Result $bookRes
    $callId = [long](Require-Field -Object $bookRes.Data -Names @("callId", "id") -Label "callId")

    Write-Host "Step 14: mentor creates violation report"
    $reportRes = Invoke-Json -Method "POST" -Path "/api/staff/mentor/violation-reports" -Body @{
        hackathonId  = $hackathonId
        teamId       = $teamId
        description  = "Violation report FULL $ts"
    } -Headers (New-AuthHeaders -Token $mentor.Token)
    Assert-Ok -Step "Create violation report" -Result $reportRes
    $reportId = [long](Require-Field -Object $reportRes.Data -Names @("reportId", "id") -Label "reportId")

    Write-Host "Step 15: organizer lists pending reports and rejects"
    $pendingRes = Invoke-Json -Method "GET" -Path "/api/staff/organizer/hackathons/$hackathonId/violation-reports/pending" -Headers (New-AuthHeaders -Token $organizer.Token)
    Assert-Ok -Step "List pending reports" -Result $pendingRes
    $pendingList = To-Array -InputObject $pendingRes.Data
    $pendingItem = $pendingList | Where-Object { [string](Get-Field -Object $_ -Names @("reportId", "id")) -eq [string]$reportId } | Select-Object -First 1
    if ($null -eq $pendingItem) {
        throw "Report $reportId non trovato nella lista pending"
    }
    $decisionRes = Invoke-Json -Method "POST" -Path "/api/staff/organizer/violation-reports/$reportId/decision" -Body @{ decision = "REJECT" } -Headers (New-AuthHeaders -Token $organizer.Token)
    Assert-Ok -Step "Reject violation report" -Result $decisionRes

    Write-Host "Step 16: organizer advance to REVIEW"
    $advance2Res = Invoke-Json -Method "POST" -Path "/api/staff/organizer/hackathons/$hackathonId/advance" -Body @{} -Headers (New-AuthHeaders -Token $organizer.Token)
    Assert-Ok -Step "Advance hackathon to REVIEW" -Result $advance2Res

    Write-Host "Step 17: judge evaluates submission"
    $evalRes = Invoke-Json -Method "POST" -Path "/api/staff/hackathons/$hackathonId/submissions/$submissionId/evaluation" -Body @{
        score   = 9
        comment = "FULL evaluation"
    } -Headers (New-AuthHeaders -Token $judge.Token)
    Assert-Ok -Step "Evaluate submission" -Result $evalRes

    Write-Host "Step 18: organizer sets winner"
    $winnerRes = Invoke-Json -Method "POST" -Path "/api/staff/organizer/hackathons/$hackathonId/winner" -Body @{ teamId = $teamId } -Headers (New-AuthHeaders -Token $organizer.Token)
    Assert-Ok -Step "Set winner" -Result $winnerRes

    Write-Host "Step 19: organizer advance to CLOSED"
    $advance3Res = Invoke-Json -Method "POST" -Path "/api/staff/organizer/hackathons/$hackathonId/advance" -Body @{} -Headers (New-AuthHeaders -Token $organizer.Token)
    Assert-Ok -Step "Advance hackathon to CLOSED" -Result $advance3Res

    Write-Host "Step 20: organizer pays prize"
    $payRes = Invoke-Json -Method "POST" -Path "/api/staff/organizer/hackathons/$hackathonId/pay-prize" -Body @{} -Headers (New-AuthHeaders -Token $organizer.Token)
    Assert-Ok -Step "Pay prize" -Result $payRes

    Write-Host ""
    Write-Host "SUMMARY"
    Write-Host "hackathonId=$hackathonId"
    Write-Host "teamId=$teamId"
    Write-Host "registrationId=$registrationId"
    Write-Host "submissionId=$submissionId"
    Write-Host "requestId=$requestId"
    Write-Host "proposalId=$proposalId"
    Write-Host "callId=$callId"
    Write-Host "reportId=$reportId"
    Write-Host "E2E FULL OK"
    exit 0
}
catch {
    Write-Host "E2E FULL FAIL: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
