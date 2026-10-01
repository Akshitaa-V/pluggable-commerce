import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Extension } from '../models';
import { ExtensionsPage } from './extensions';

const existing: Extension = {
  id: 'ext-1',
  name: 'Rust discount rules',
  hook: 'cart.price',
  url: 'http://discount-extension:8081/hooks/cart-price',
  enabled: true,
  timeoutMs: 800,
  failurePolicy: 'SKIP',
};

describe('ExtensionsPage', () => {
  let fixture: ComponentFixture<ExtensionsPage>;
  let http: HttpTestingController;
  let el: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ExtensionsPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(ExtensionsPage);
    http = TestBed.inject(HttpTestingController);
    el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    http.expectOne('/api/extensions').flush([existing]);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function button(label: string): HTMLButtonElement {
    const found = Array.from(el.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === label,
    );
    if (!found) throw new Error(`No button "${label}"`);
    return found;
  }

  function type(selector: string, value: string): void {
    const input = el.querySelector(selector) as HTMLInputElement | HTMLSelectElement;
    input.value = value;
    input.dispatchEvent(new Event(input instanceof HTMLSelectElement ? 'change' : 'input'));
  }

  it('lists registered extensions', () => {
    const rows = el.querySelectorAll('tbody tr');
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('Rust discount rules');
    expect(rows[0].textContent).toContain('Enabled');
  });

  it('disables an extension and updates the row', async () => {
    button('Disable').click();
    const req = http.expectOne('/api/extensions/ext-1');
    expect(req.request.body).toEqual({ enabled: false });
    req.flush({ ...existing, enabled: false });
    await fixture.whenStable();
    expect(el.querySelector('tbody tr')?.textContent).toContain('Disabled');
  });

  it('does not submit an invalid form', async () => {
    type('input[formControlName=name]', 'No URL');
    button('Register').click();
    await fixture.whenStable();
    http.expectNone('/api/extensions');
    expect(el.textContent).toContain('Give a name, an http(s) URL');
  });

  it('registers a validator with the chosen failure policy', async () => {
    type('input[formControlName=name]', 'Quantity limit');
    type('select[formControlName=hook]', 'order.validate');
    await fixture.whenStable();
    type('input[formControlName=url]', 'http://localhost:8081/hooks/order-validate');
    type('select[formControlName=failurePolicy]', 'BLOCK');
    button('Register').click();

    const req = http.expectOne((r) => r.method === 'POST' && r.url === '/api/extensions');
    expect(req.request.body).toEqual({
      name: 'Quantity limit',
      hook: 'order.validate',
      url: 'http://localhost:8081/hooks/order-validate',
      timeoutMs: 1000,
      failurePolicy: 'BLOCK',
    });
    req.flush({ ...existing, id: 'ext-2', name: 'Quantity limit', hook: 'order.validate' });
    await fixture.whenStable();
    expect(el.querySelectorAll('tbody tr').length).toBe(2);
  });

  it('shows the error from the core when registration fails', async () => {
    type('input[formControlName=name]', 'Bad');
    type('input[formControlName=url]', 'http://x');
    button('Register').click();
    http
      .expectOne((r) => r.method === 'POST')
      .flush({ error: 'url must be an absolute http or https URL' }, {
        status: 400,
        statusText: 'Bad Request',
      });
    await fixture.whenStable();
    expect(el.querySelector('[role=alert]')?.textContent).toContain('absolute http or https URL');
  });
});
