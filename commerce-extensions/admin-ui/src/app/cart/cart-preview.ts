import { Component, OnInit, computed, inject, signal } from '@angular/core';

import { CommerceApi, describeError } from '../commerce-api';
import { EuroPipe } from '../euro.pipe';
import { CartLine, Order, Product, Quote } from '../models';

/**
 * Builds a cart from the catalog and shows how the registered extensions change its price,
 * so an admin can check an extension before customers see it.
 */
@Component({
  selector: 'app-cart-preview',
  imports: [EuroPipe],
  templateUrl: './cart-preview.html',
})
export class CartPreviewPage implements OnInit {
  private readonly api = inject(CommerceApi);

  protected readonly products = signal<Product[]>([]);
  protected readonly quantities = signal<Partial<Record<string, number>>>({});
  protected readonly quote = signal<Quote | null>(null);
  protected readonly order = signal<Order | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly lines = computed<CartLine[]>(() =>
    Object.entries(this.quantities())
      .filter((entry): entry is [string, number] => (entry[1] ?? 0) > 0)
      .map(([sku, quantity]) => ({ sku, quantity })),
  );

  ngOnInit(): void {
    this.api.products().subscribe({
      next: (list) => this.products.set(list),
      error: (err) => this.error.set(describeError(err)),
    });
  }

  protected setQuantity(sku: string, raw: string): void {
    const parsed = Number.parseInt(raw, 10);
    const quantity = Number.isFinite(parsed) ? Math.min(Math.max(parsed, 0), 999) : 0;
    this.quantities.update((q) => ({ ...q, [sku]: quantity }));
    // A changed cart makes the old quote and order outcome stale.
    this.quote.set(null);
    this.order.set(null);
  }

  protected requestQuote(): void {
    this.run(() =>
      this.api.quote(this.lines()).subscribe({
        next: (quote) => this.finish(() => this.quote.set(quote)),
        error: (err) => this.fail(err),
      }),
    );
  }

  protected placeOrder(): void {
    this.run(() =>
      this.api.placeOrder(this.lines()).subscribe({
        next: (order) =>
          this.finish(() => {
            this.order.set(order);
            this.quote.set(order.quote);
          }),
        error: (err) => this.fail(err),
      }),
    );
  }

  private run(action: () => void): void {
    this.busy.set(true);
    this.error.set(null);
    this.order.set(null);
    action();
  }

  private finish(update: () => void): void {
    update();
    this.busy.set(false);
  }

  private fail(err: unknown): void {
    this.error.set(describeError(err));
    this.busy.set(false);
  }
}
