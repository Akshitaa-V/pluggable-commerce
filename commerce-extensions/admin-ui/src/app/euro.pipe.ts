import { Pipe, PipeTransform } from '@angular/core';

const formatter = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' });

/** Formats integer cents as euros, e.g. -290 becomes "-2,90 €". */
@Pipe({ name: 'euro' })
export class EuroPipe implements PipeTransform {
  transform(cents: number | null | undefined): string {
    return cents == null ? '' : formatter.format(cents / 100);
  }
}
