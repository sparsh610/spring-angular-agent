import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface AgentResponse {
  answer: string;
  provider: string;
  trace: ToolTrace[];
}

export interface ToolTrace {
  step: number;
  type: string;
  tool: string;
  arguments: Record<string, unknown>;
  result: string;
}

@Injectable({ providedIn: 'root' })
export class AgentApiService {
  constructor(private readonly http: HttpClient) {}

  chat(message: string, trace: boolean): Observable<AgentResponse> {
    return this.http.post<AgentResponse>('/api/agent/chat', { message, trace });
  }
}
