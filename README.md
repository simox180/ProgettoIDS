# ProgettoIDS — HackHub

HackHub è una piattaforma web per la gestione di hackathon. Un hackathon attraversa quattro stati: **REGISTRATION**, **RUNNING**, **REVIEW**, **CLOSED**.
La piattaforma supporta creazione hackathon, gestione team e inviti, registrazione team, submission, supporto mentor (call + booking), valutazione judge, proclamazione vincitore con pagamento premio.

## Struttura repository
- `hackhub/` → progetto Spring Boot (codice, risorse, script smoke)

## Requisiti
- Java 17+  
- Maven (oppure Maven Wrapper incluso: `mvnw` / `mvnw.cmd`)

## Avvio applicazione
Da dentro `hackhub/`:
```bash
./mvnw spring-boot:run