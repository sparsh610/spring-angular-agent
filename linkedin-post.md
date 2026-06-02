# LinkedIn Post Draft: Spring Boot + Angular AI Agent

I built a small AI agent application using Spring Boot and Angular.

The goal was not just to call an LLM from a backend.

The goal was to make the agent workflow visible, controllable, and closer to how a real enterprise application would need to behave.

What I built:

- Spring Boot REST API for agent chat
- Angular chat UI for user interaction
- tool trace panel to show what the agent did
- backend tool registry for controlled actions
- tools for time, calculation, file listing, code description, and app-flow explanation
- max-step guardrails to avoid uncontrolled agent loops
- local Ollama support by default
- deterministic demo mode when no LLM is available
- optional OpenAI-compatible and Qwen provider support
- one-port production mode where Spring Boot serves the Angular build

The biggest lesson:

an AI agent is not only a prompt.

Once an agent can use tools, the engineering questions become more important:

- Which tools is the model allowed to call?
- How many steps can it take?
- How do we show the user what happened?
- How do we validate and log tool execution?
- How do we keep the system useful even when a model provider is unavailable?

This is where my Java, Spring Boot, and Angular experience connects directly with AI engineering.

In production software, we already care about APIs, validation, state, observability, fallbacks, and user experience. AI agents need the same discipline.

For this project, I intentionally kept the scope practical:

- Spring Boot for the backend boundary
- Angular for the user-facing workflow
- local LLM support for experimentation
- provider abstraction for different model backends
- traceable tool execution instead of hidden agent behavior

My main takeaway:

the future of AI apps will not be just better prompts. It will be better systems around the model.

#AIAgents #AIEngineering #SpringBoot #Angular #Java #FullStackDevelopment #Ollama #OpenAI #SoftwareEngineering

## Short Version

I built a Spring Boot + Angular AI agent app.

It includes:

- REST API for agent chat
- Angular chat UI
- backend tool registry
- tool trace panel
- guardrails for max agent steps
- Ollama local LLM support
- demo mode without a real LLM
- optional OpenAI-compatible and Qwen providers

The key lesson:

an AI agent is not just a prompt. Once it can use tools, we need backend engineering discipline around permissions, validation, logging, fallbacks, and user visibility.

#AIAgents #AIEngineering #SpringBoot #Angular #Java #SoftwareEngineering
