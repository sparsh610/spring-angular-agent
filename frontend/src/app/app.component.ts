import { CommonModule } from '@angular/common';
import { Component, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideBot, LucideLoaderCircle, LucideSend, LucideWrench } from '@lucide/angular';
import { AgentApiService, AgentResponse, ToolTrace } from './agent-api.service';

interface ChatMessage {
  role: 'user' | 'agent';
  text: string;
  provider?: string;
  trace?: ToolTrace[];
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideBot, LucideLoaderCircle, LucideSend, LucideWrench],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  readonly message = signal('Calculate 12 * 8 and show the trace.');
  readonly traceEnabled = signal(true);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly messages = signal<ChatMessage[]>([
    {
      role: 'agent',
      text: 'Ask for the current time, a calculation, project files, or a description of this code. The backend will show tool calls when trace is enabled.',
      provider: 'system',
      trace: []
    }
  ]);
  readonly latestTrace = computed(() => {
    const reversed = [...this.messages()].reverse();
    return reversed.find((item) => item.trace && item.trace.length)?.trace ?? [];
  });

  constructor(private readonly agentApi: AgentApiService) {}

  send(): void {
    const text = this.message().trim();
    if (!text || this.loading()) {
      return;
    }

    this.error.set('');
    this.loading.set(true);
    this.messages.update((items) => [...items, { role: 'user', text }]);
    this.message.set('');

    this.agentApi.chat(text, this.traceEnabled()).subscribe({
      next: (response: AgentResponse) => {
        this.messages.update((items) => [
          ...items,
          {
            role: 'agent',
            text: response.answer,
            provider: response.provider,
            trace: response.trace
          }
        ]);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('The agent request failed. Check that Spring Boot is running on port 8080.');
        this.loading.set(false);
      }
    });
  }

  setExample(text: string): void {
    this.message.set(text);
  }

  formatArguments(value: Record<string, unknown>): string {
    return JSON.stringify(value, null, 2);
  }
}
