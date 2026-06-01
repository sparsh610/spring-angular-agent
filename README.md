# Spring Boot Angular Agent

This project demonstrates a traceable AI-agent workflow with a Spring Boot backend and an Angular frontend.

## What It Includes

- Spring Boot REST API at `/api/agent/chat`
- Angular chat UI with a tool trace panel
- Tool registry for backend actions
- Built-in tools for time, calculation, project file listing, and project code description
- Guardrails for tool execution and max agent steps
- Demo LLM mode that works without an API key
- Optional OpenAI-compatible Chat Completions mode
- Angular proxy for local development
- One-port production mode where Spring Boot serves the Angular build

## Run In Development

Start Spring Boot:

```powershell
mvn spring-boot:run
```

Start Angular:

```powershell
cd frontend
npm install
npm start
```

Open:

```text
http://localhost:4200
```

Angular proxies `/api` calls to Spring Boot at `http://localhost:8080`.

## Run On One Port

Build Angular:

```powershell
cd frontend
npm install
npm run build
```

Copy `frontend/dist/agent-ui/browser` into `src/main/resources/static`, then run:

```powershell
mvn spring-boot:run
```

Open:

```text
http://localhost:8080
```

## Optional OpenAI Mode

By default the backend uses a deterministic demo model so the project works locally.

To call an OpenAI-compatible Chat Completions API:

```powershell
$env:AGENT_PROVIDER="openai"
$env:OPENAI_API_KEY="your-api-key"
$env:OPENAI_MODEL="gpt-4.1-mini"
mvn spring-boot:run
```

If no API key is configured, keep `AGENT_PROVIDER=demo` or leave it unset.
