/** Types matching the JSON the commerce core returns. Amounts are integer cents. */

export type HookId = 'cart.price' | 'order.validate';
export type FailurePolicy = 'SKIP' | 'BLOCK';

export interface Product {
  sku: string;
  name: string;
  unitPriceCents: number;
}

export interface Extension {
  id: string;
  name: string;
  hook: HookId;
  url: string;
  enabled: boolean;
  timeoutMs: number;
  failurePolicy: FailurePolicy;
}

export interface NewExtension {
  name: string;
  hook: HookId;
  url: string;
  timeoutMs: number;
  failurePolicy: FailurePolicy;
}

export interface CartLine {
  sku: string;
  quantity: number;
}

export interface PricedLine extends CartLine {
  name: string;
  unitPriceCents: number;
  lineTotalCents: number;
}

export interface Adjustment {
  extensionId: string;
  extensionName: string;
  sku: string | null;
  label: string;
  amountCents: number;
}

export interface Quote {
  currency: string;
  lines: PricedLine[];
  subtotalCents: number;
  adjustments: Adjustment[];
  totalCents: number;
  warnings: string[];
}

export interface Order {
  id: string;
  status: string;
  quote: Quote;
  warnings: string[];
}

/** Error body of the core API; rejectedBy is set when a validator refused an order. */
export interface ApiError {
  error: string;
  rejectedBy?: string;
}
