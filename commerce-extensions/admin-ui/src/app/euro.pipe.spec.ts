import { EuroPipe } from './euro.pipe';

describe('EuroPipe', () => {
  const pipe = new EuroPipe();

  it('formats cents as euros, including discounts', () => {
    // Intl uses a narrow no-break space before the euro sign; normalise it for the test.
    const clean = (s: string) => s.replace(/\s/g, ' ');
    expect(clean(pipe.transform(2580))).toBe('25,80 €');
    expect(clean(pipe.transform(-290))).toBe('-2,90 €');
  });

  it('returns an empty string for missing values', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
  });
});
