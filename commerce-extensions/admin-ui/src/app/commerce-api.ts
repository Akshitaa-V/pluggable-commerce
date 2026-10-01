import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiError, CartLine, Extension, NewExtension, Order, Product, Quote } from './models';

/**
 * Talks to the commerce core. All paths are relative, so the same build works behind the
 * dev-server proxy, docker-compose and the Kubernetes ingress (nginx forwards /api).
 */
@Injectable({ providedIn: 'root' })
export class CommerceApi {
  private readonly http = inject(HttpClient);

  products(): Observable<Product[]> {
    return this.http.get<Product[]>('/api/products');
  }

  extensions(): Observable<Extension[]> {
    return this.http.get<Extension[]>('/api/extensions');
  }

  registerExtension(extension: NewExtension): Observable<Extension> {
    return this.http.post<Extension>('/api/extensions', extension);
  }

  setEnabled(id: string, enabled: boolean): Observable<Extension> {
    return this.http.patch<Extension>(`/api/extensions/${encodeURIComponent(id)}`, { enabled });
  }

  removeExtension(id: string): Observable<void> {
    return this.http.delete<void>(`/api/extensions/${encodeURIComponent(id)}`);
  }

  quote(lines: CartLine[]): Observable<Quote> {
    return this.http.post<Quote>('/api/quotes', { lines });
  }

  placeOrder(lines: CartLine[]): Observable<Order> {
    return this.http.post<Order>('/api/orders', { lines });
  }
}

/** Turns any HTTP failure into one readable sentence for the UI. */
export function describeError(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as Partial<ApiError> | null;
    if (body && typeof body.error === 'string') {
      return body.rejectedBy ? `${body.rejectedBy}: ${body.error}` : body.error;
    }
    if (err.status === 0) {
      return 'The commerce core is not reachable.';
    }
    return `Request failed with HTTP ${err.status}.`;
  }
  return 'Something went wrong.';
}
