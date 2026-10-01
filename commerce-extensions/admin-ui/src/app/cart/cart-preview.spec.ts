import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Quote } from '../models';
import { CartPreviewPage } from './cart-preview';

const quote: Quote = {
  currency: 'EUR',
  lines: [
    {
      sku: 'MUG-CER',
      name: 'Ceramic mug',
      quantity: 5,
      unitPriceCents: 1290,
      lineTotalCents: 6450,
    },
  ],
  subtotalCents: 6450,
  adjustments: [
    {
      extensionId: 'ext-1',
      extensionName: 'Rust discount rules',
      sku: 'MUG-CER',
      label: 'Bulk 10% (5+ units)',
      amountCents: -645,
    },
  ],
  totalCents: 5805,
  warnings: ["Extension 'Slow' timed out after 100 ms; its adjustments were skipped."],
};

describe('CartPreviewPage', () => {
  let fixture: ComponentFixture<CartPreviewPage>;
  let http: HttpTestingController;
  let el: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CartPreviewPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(CartPreviewPage);
    http = TestBed.inject(HttpTestingController);
    el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    http.expectOne('/api/products').flush([
      { sku: 'MUG-CER', name: 'Ceramic mug', unitPriceCents: 1290 },
      { sku: 'GRINDER-H', name: 'Hand grinder', unitPriceCents: 5990 },
    ]);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function button(label: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === label,
    ) as HTMLButtonElement;
  }

  async function setQuantity(index: number, value: string): Promise<void> {
    const input = el.querySelectorAll('tbody input')[index] as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  it('keeps the buttons disabled while the cart is empty', () => {
    expect(button('Get quote').disabled).toBe(true);
    expect(button('Place order').disabled).toBe(true);
  });

  it('sends only lines with a quantity and shows the extension adjustments', async () => {
    await setQuantity(0, '5');
    button('Get quote').click();

    const req = http.expectOne('/api/quotes');
    expect(req.request.body).toEqual({ lines: [{ sku: 'MUG-CER', quantity: 5 }] });
    req.flush(quote);
    await fixture.whenStable();

    const text = el.querySelector('.quote')?.textContent?.replace(/\s+/g, ' ') ?? '';
    expect(text).toContain('Bulk 10% (5+ units)');
    expect(text).toContain('by Rust discount rules');
    expect(text).toContain('58,05');
    expect(text).toContain('timed out');
  });

  it('clamps quantities to the allowed range', async () => {
    await setQuantity(1, '5000');
    button('Get quote').click();
    const req = http.expectOne('/api/quotes');
    expect(req.request.body).toEqual({ lines: [{ sku: 'GRINDER-H', quantity: 999 }] });
    req.flush(quote);
  });

  it('shows which validator rejected an order', async () => {
    await setQuantity(0, '25');
    button('Place order').click();
    http.expectOne('/api/orders').flush(
      { error: 'At most 20 units of MUG-CER per order', rejectedBy: 'Rust rules' },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    await fixture.whenStable();
    expect(el.querySelector('[role=alert]')?.textContent).toContain(
      'Rust rules: At most 20 units of MUG-CER per order',
    );
  });

  it('confirms a placed order', async () => {
    await setQuantity(0, '1');
    button('Place order').click();
    http.expectOne('/api/orders').flush({ id: 'ord-7', status: 'PLACED', quote, warnings: [] });
    await fixture.whenStable();
    expect(el.querySelector('[role=status]')?.textContent).toContain('Order ord-7 placed.');
  });
});
