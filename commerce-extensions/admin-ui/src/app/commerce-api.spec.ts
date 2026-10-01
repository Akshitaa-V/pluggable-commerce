import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CommerceApi, describeError } from './commerce-api';

describe('CommerceApi', () => {
  let api: CommerceApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(CommerceApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends cart lines to the quote endpoint', () => {
    api.quote([{ sku: 'MUG-CER', quantity: 2 }]).subscribe();
    const req = http.expectOne('/api/quotes');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ lines: [{ sku: 'MUG-CER', quantity: 2 }] });
    req.flush({});
  });

  it('patches only the enabled flag and encodes the id', () => {
    api.setEnabled('ext 1', false).subscribe();
    const req = http.expectOne('/api/extensions/ext%201');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ enabled: false });
    req.flush({});
  });

  it('deletes an extension', () => {
    api.removeExtension('ext-3').subscribe();
    const req = http.expectOne('/api/extensions/ext-3');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });
});

describe('describeError', () => {
  it('names the validator that rejected an order', () => {
    const err = new HttpErrorResponse({
      status: 422,
      error: { error: 'At most 20 units of MUG-CER per order', rejectedBy: 'Rust rules' },
    });
    expect(describeError(err)).toBe('Rust rules: At most 20 units of MUG-CER per order');
  });

  it('uses the API error message when there is one', () => {
    const err = new HttpErrorResponse({ status: 400, error: { error: 'The cart is empty' } });
    expect(describeError(err)).toBe('The cart is empty');
  });

  it('explains an unreachable backend and other failures', () => {
    expect(describeError(new HttpErrorResponse({ status: 0 }))).toBe(
      'The commerce core is not reachable.',
    );
    expect(describeError(new HttpErrorResponse({ status: 503, error: 'down' }))).toBe(
      'Request failed with HTTP 503.',
    );
    expect(describeError(new Error('x'))).toBe('Something went wrong.');
  });
});
