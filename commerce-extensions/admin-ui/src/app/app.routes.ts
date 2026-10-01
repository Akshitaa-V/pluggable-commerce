import { Routes } from '@angular/router';

import { CartPreviewPage } from './cart/cart-preview';
import { ExtensionsPage } from './extensions/extensions';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'extensions' },
  { path: 'extensions', component: ExtensionsPage, title: 'Extensions' },
  { path: 'cart', component: CartPreviewPage, title: 'Cart preview' },
  { path: '**', redirectTo: 'extensions' },
];
