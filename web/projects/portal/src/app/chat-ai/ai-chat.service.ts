import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
   providedIn: 'root'
})
export class ChatService {
   private apiUrl = 'http://localhost:3001/api/chat';

   constructor(private http: HttpClient) {}

   sendMessage(question: string, context?: string): Observable<any> {
      return this.http.post(this.apiUrl, { question, context });
   }
}