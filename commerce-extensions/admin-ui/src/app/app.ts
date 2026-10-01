import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header>
      <h1>Commerce Extensions Admin</h1>
      <nav>
        <a routerLink="/extensions" routerLinkActive="active">Extensions</a>
        <a routerLink="/cart" routerLinkActive="active">Cart preview</a>
      </nav>
    </header>
    <main>
      <router-outlet />
    </main>
  `,
})
export class App {}
