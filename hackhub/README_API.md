# HackHub REST API (Quick Guide)

## Avvio applicazione
Da root progetto (`hackhub/`):

```powershell
mvn spring-boot:run
```

L'app espone le API su `http://localhost:8080`.

## Autenticazione e token
1. Registra utente (solo USER):

```http
POST /api/auth/register
Content-Type: application/json

{"username":"A","password":"A"}
```

2. Login USER o STAFF:

```http
POST /api/auth/login
Content-Type: application/json

{"type":"USER","identifier":"A","password":"A"}
```

Oppure STAFF:

```http
{"type":"STAFF","identifier":"organizer1","password":"organizer1pass"}
```

3. Usa il token ricevuto nel header:

```http
X-Session-Token: <token>
```

4. Logout:

```http
POST /api/auth/logout
X-Session-Token: <token>
```

## Endpoint principali

### Pubblici
- `GET /api/hackathons`
- `GET /api/hackathons/{id}`

### Auth
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`

### USER (minimo)
- `POST /api/me/team`
- `GET /api/me/team`

## Script smoke test

### Auth smoke
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\auth-smoke.ps1
```
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\e2e-smoke.ps1
```
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\e2e-full.ps1
```

Parametri opzionali comuni:
- `-BaseUrl http://localhost:8080`
- `-Username <user>`
- `-Password <password>`
