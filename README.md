# ProgettoIDS — HackHub (Spring Boot REST + CLI)

HackHub è una piattaforma per la gestione di hackathon. Ogni hackathon attraversa 4 stati: **REGISTRATION**, **RUNNING**, **REVIEW**, **CLOSED**.

Questa repository contiene:
- un backend **Spring Boot REST** (persistenza su DB H2 file)
- una **CLI Java** per prove rapide (demo)

> Nota importantissima: in questa versione la **CLI è standalone/in-memory** (non usa il DB di Spring e non condivide utenti/password con la REST).  
> Puoi comunque usare **REST + CLI insieme** avviandoli in parallelo (2 terminali): è il modo più comodo per “provare subito” con la CLI e, allo stesso tempo, testare/mostrare le API REST.

---

## Struttura repository

- `hackhub/` → progetto Spring Boot (codice, risorse, scripts smoke test, sorgenti CLI)
- `ProgettoIDS.vpp` → progetto Visual Paradigm (file binario)

---

## Requisiti

- **Java 17+**
- Non serve Maven installato: c’è il **Maven Wrapper** (`mvnw` / `mvnw.cmd`)

---

# Avvio rapido (REST + CLI)

## A) Avvio REST (Spring Boot)
Apri un terminale nella root del repo, poi:

### Windows (PowerShell)
```powershell
cd .\hackhub
.\mvnw.cmd spring-boot:run 
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--hackhub.cli.enabled=true"(avvio con CLI per test manuali veloci)
```

### Linux/macOS
```bash
cd ./hackhub
./mvnw spring-boot:run
```

Base URL:
- `http://localhost:8080`

---

## B) Avvio CLI (demo rapida)
### Metodo consigliato (IDE)
Esegui la classe:
- `it.unicam.hackhub.cli.Main`

### Da terminale (Maven Wrapper)
Da dentro `hackhub/`:
```powershell
cd .\hackhub
.\mvnw.cmd -q org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass=it.unicam.hackhub.cli.Main
```

---

## C) Avvio REST + CLI insieme (consigliato)
### Metodo semplice (2 terminali)
1) Terminale A (REST):
```powershell
cd .\hackhub
.\mvnw.cmd spring-boot:run
```

2) Terminale B (CLI):
```powershell
cd .\hackhub
.\mvnw.cmd -q org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass=it.unicam.hackhub.cli.Main
```

### One-liner PowerShell (apre 2 finestre)
Da root repo:
```powershell
Start-Process powershell "-NoExit","-Command","cd .\hackhub; .\mvnw.cmd spring-boot:run"
Start-Process powershell "-NoExit","-Command","cd .\hackhub; .\mvnw.cmd -q org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass=it.unicam.hackhub.cli.Main"
```

---

# Account già presenti (seed)

## Seed account — Spring Boot (REST)
Account STAFF creati automaticamente al boot (usabili per login REST):

- ORGANIZER: `organizer1 / organizer1pass`
- JUDGE: `judge1 / judge1pass`
- MENTOR: `mentor1 / mentor1pass`

> USER non sono pre-seedati: vanno creati con `POST /api/auth/register`.

## Seed account — CLI (standalone/in-memory)
La CLI ha un seed separato (in-memory). Credenziali STAFF:

- organizer: `organizer1 / organizer1`
- judge: `judge1 / judge1`
- mentor: `mentor1 / mentor1`
- mentor: `mentor2 / mentor2`

Hackathon demo già presenti in CLI:
- `HackHub Starter` (REGISTRATION)
- `HackHub Pro` (RUNNING)

---

# Come usare la REST manualmente (senza smoke test)

## Autenticazione REST
### 1) Register USER
`POST /api/auth/register`
```json
{"username":"u1","password":"Pwd_12345"}
```

### 2) Login USER o STAFF
`POST /api/auth/login` (USER)
```json
{"type":"USER","identifier":"u1","password":"Pwd_12345"}
```

`POST /api/auth/login` (STAFF)
```json
{"type":"STAFF","identifier":"organizer1","password":"organizer1pass"}
```

### 3) Token
Usa l’header:
- `X-Session-Token: <token>`

### 4) Logout
`POST /api/auth/logout` con header `X-Session-Token`

---

## Esempi super rapidi (PowerShell)
```powershell
$base="http://localhost:8080"

# Login STAFF (organizer)
$login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType "application/json" `
  -Body '{"type":"STAFF","identifier":"organizer1","password":"organizer1pass"}'
$h = @{ "X-Session-Token" = $login.token }

# Lista hackathon pubblica
Invoke-RestMethod "$base/api/hackathons"

# Lista staff (serve spesso per ottenere staffId)
Invoke-RestMethod -Headers $h -Uri "$base/api/staff/members"
```

---

# API REST (orientamento)

## Pubbliche
- `GET /api/hackathons`
- `GET /api/hackathons/{id}`

## Auth
- `POST /api/auth/register` (USER)
- `POST /api/auth/login` (USER/STAFF)
- `POST /api/auth/logout`

## USER (principali)
- `POST /api/me/team`
- `POST /api/me/team/invitations`
- `GET /api/me/invitations`
- `POST /api/me/invitations/{invitationId}` (action: ACCEPT/DECLINE)
- `GET /api/me/registerable-hackathons`
- `POST /api/me/registration`
- `POST /api/me/submission`
- `PUT /api/me/submission`
- `GET /api/me/submission`
- `POST /api/me/support/requests`
- `GET /api/me/calls/proposals`
- `POST /api/me/calls/proposals/{proposalId}/book`

## STAFF (principali)
- `GET /api/staff/members`
- Organizer:
  - `POST /api/staff/organizer/hackathons`
  - `POST /api/staff/organizer/hackathons/{hackathonId}/advance`
  - `POST /api/staff/organizer/hackathons/{hackathonId}/winner`
  - `POST /api/staff/organizer/hackathons/{hackathonId}/pay-prize`
  - `GET /api/staff/organizer/hackathons/{hackathonId}/violation-reports/pending`
  - `POST /api/staff/organizer/violation-reports/{reportId}/decision`
- Judge:
  - `POST /api/staff/hackathons/{hackathonId}/submissions/{submissionId}/evaluation`
- Mentor:
  - `GET /api/staff/mentor/hackathons/{hackathonId}/support-requests`
  - `POST /api/staff/mentor/support-requests/{requestId}/call-proposals`
  - `POST /api/staff/mentor/violation-reports`

---

# Smoke test (consigliati)

Esegui questi comandi **dentro `hackhub/`** con Spring già avviato su `http://localhost:8080`:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\auth-smoke.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\e2e-smoke.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\e2e-full.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\persistence-smoke.ps1
```

Parametro comune:
- `-BaseUrl http://localhost:8080`

---

# Come usare la CLI (demo rapida)

Prompt:
- `hackhub(guest)>` (non autenticato)
- `hackhub(user)>` (USER autenticato)
- `hackhub(staff)>` (STAFF autenticato)

Comandi generali:
- `help` → mostra i comandi disponibili per lo stato corrente (guest/user/staff + ruoli)
- `exit` / `quit` → esce

---

## Lista comandi CLI + descrizione

### Guest
- `register` → registra un nuovo USER (solo nella CLI)
- `login` → login USER o STAFF (CLI)

### User (dopo login USER)
- `logout` → logout
- `list-hackathons` → lista hackathon (id, nome, stato, location)
- `view-hackathon` → dettaglio hackathon (richiede hackathonId)
- `create-team` → crea un team (user diventa owner/creator)
- `invite-user` → invita un utente nel tuo team
- `view-invites` → visualizza inviti ricevuti e gestiscili (ACCEPT/DECLINE)
- `register-team` → registra il team a un hackathon (mostra solo quelli registrabili)
- `my-registration` → mostra la registrazione corrente del tuo team (se presente)
- `submit` → crea una submission per il team registrato
- `update-submission` → aggiorna la submission esistente
- `my-submission` → mostra la submission corrente
- `create-support-request` → crea richiesta di supporto mentor
- `list-call-proposals` → lista proposte call disponibili (per le tue richieste)
- `book-call` → prenota una call a partire da una proposal

### Staff (dopo login STAFF)

#### Organizer
- `create-hackathon` → crea hackathon e assegna organizer/judge/mentor (selezione guidata)
- `add-mentor` → aggiunge mentor a un hackathon
- `advance-hackathon` → avanza lo stato dell’hackathon (REGISTRATION→RUNNING→REVIEW→CLOSED)
- `set-winner` → imposta il team vincitore
- `proclaim-winner` → proclamazione vincitore (se prevista dal flusso)
- `pay-prize` → pagamento premio (se previsto dal flusso / disponibilità)
- `list-reports` → lista segnalazioni (violation reports) per hackathon
- `manage-report` → gestisce una segnalazione (decision)

#### Judge
- `list-submissions` → lista submission accessibili (hackathon assegnati)
- `evaluate-submission` → valuta una submission (score/comment)

#### Staff (generico)
- `view-evaluation` → visualizza valutazione di una submission

#### Mentor
- `list-support-requests` → lista richieste supporto per hackathon assegnati
- `create-call-proposal` → crea proposta call per una richiesta supporto
- `create-report` → crea segnalazione (violation report) su un team/hackathon

---

# Visual Paradigm (.vpp)

Il file `ProgettoIDS.vpp` è binario: GitHub non lo previsualizza.
Per scaricarlo:
1) apri `ProgettoIDS.vpp` su GitHub
2) clicca **View raw** (download)
3) aprilo con Visual Paradigm

---

# Note utili / troubleshooting

## ID “strani” (es. 34, 35) in Spring
È normale: sono chiavi del DB con auto-increment e possono dipendere dallo storico del DB su file.

## Reset DB Spring (ripartire pulito)
1) ferma Spring
2) elimina:
   - `hackhub/data/hackhub.mv.db`
   - `hackhub/data/hackhub.trace.db` (se presente)
3) riavvia Spring: il DataSeeder ricrea i dati demo

## Dove eseguire i comandi
- comandi `mvnw` e scripts: **dentro `hackhub/`**
- file `ProgettoIDS.vpp` e questo README: root repo