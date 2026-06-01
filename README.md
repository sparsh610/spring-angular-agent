# Spring Boot Angular Agent

This project demonstrates a traceable AI-agent workflow with a Spring Boot backend and an Angular frontend.

## What It Includes

- Spring Boot REST API at `/api/agent/chat`
- Angular chat UI with a tool trace panel
- Tool registry for backend actions
- Built-in tools for time, calculation, project file listing, project code description, and app-flow explanation
- Guardrails for tool execution and max agent steps
- Ollama local LLM mode by default
- Qwen OpenAI-compatible API provider
- Demo mode that works without a real LLM
- Optional OpenAI-compatible Chat Completions mode
- Angular proxy for local development
- One-port production mode where Spring Boot serves the Angular build

## Run In Development

Start Spring Boot:

```powershell
ollama pull qwen2.5-coder:1.5b
ollama serve
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

## LLM Providers

By default the backend uses Ollama at `http://localhost:11434` with `qwen2.5-coder:1.5b`.

Use a different local model:

```powershell
$env:AGENT_PROVIDER="ollama"
$env:OLLAMA_MODEL="qwen3:8b"
mvn spring-boot:run
```

Use deterministic demo mode without any real LLM:

```powershell
$env:AGENT_PROVIDER="demo"
mvn spring-boot:run
```

Use the Qwen API endpoint:

```powershell
$env:AGENT_PROVIDER="qwen"
$env:QWEN_API_KEY="your-qwen-key"
$env:QWEN_BASE_URL="http://103.42.50.41:443/api1"
$env:QWEN_MODEL="Qwen/Qwen2.5-7B-Instruct-AWQ"
mvn spring-boot:run
```

To call an OpenAI-compatible Chat Completions API:

```powershell
$env:AGENT_PROVIDER="openai"
$env:OPENAI_API_KEY="your-api-key"
$env:OPENAI_MODEL="gpt-4.1-mini"
mvn spring-boot:run
```

If no API key is configured, use `AGENT_PROVIDER=ollama` or `AGENT_PROVIDER=demo`.
