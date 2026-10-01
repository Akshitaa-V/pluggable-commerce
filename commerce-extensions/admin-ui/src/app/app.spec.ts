import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';

describe('App', () => {
  it('renders the title and both navigation links', async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('h1')?.textContent).toContain('Commerce Extensions Admin');
    const links = Array.from(el.querySelectorAll('nav a')).map((a) => a.textContent?.trim());
    expect(links).toEqual(['Extensions', 'Cart preview']);
  });
});
