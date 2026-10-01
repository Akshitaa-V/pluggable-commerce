import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { CommerceApi, describeError } from '../commerce-api';
import { Extension, NewExtension } from '../models';

/** Lists registered extensions and lets an admin add, switch on/off and remove them. */
@Component({
  selector: 'app-extensions',
  imports: [ReactiveFormsModule],
  templateUrl: './extensions.html',
})
export class ExtensionsPage implements OnInit {
  private readonly api = inject(CommerceApi);
  private readonly fb = inject(FormBuilder);

  protected readonly extensions = signal<Extension[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(60)]],
    hook: ['cart.price' as NewExtension['hook'], Validators.required],
    url: ['', [Validators.required, Validators.pattern(/^https?:\/\/\S+$/)]],
    timeoutMs: [1000, [Validators.required, Validators.min(50), Validators.max(5000)]],
    failurePolicy: ['SKIP' as NewExtension['failurePolicy'], Validators.required],
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.api.extensions().subscribe({
      next: (list) => this.extensions.set(list),
      error: (err) => this.error.set(describeError(err)),
    });
  }

  protected register(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    // The core only accepts BLOCK for validators; keep the form from sending a bad combination.
    const request: NewExtension = {
      ...value,
      failurePolicy: value.hook === 'cart.price' ? 'SKIP' : value.failurePolicy,
    };
    this.saving.set(true);
    this.error.set(null);
    this.api.registerExtension(request).subscribe({
      next: (created) => {
        this.extensions.update((list) => [...list, created]);
        this.form.reset();
        this.saving.set(false);
      },
      error: (err) => {
        this.error.set(describeError(err));
        this.saving.set(false);
      },
    });
  }

  protected toggle(ext: Extension): void {
    this.api.setEnabled(ext.id, !ext.enabled).subscribe({
      next: (updated) =>
        this.extensions.update((list) => list.map((e) => (e.id === updated.id ? updated : e))),
      error: (err) => this.error.set(describeError(err)),
    });
  }

  protected remove(ext: Extension): void {
    this.api.removeExtension(ext.id).subscribe({
      next: () => this.extensions.update((list) => list.filter((e) => e.id !== ext.id)),
      error: (err) => this.error.set(describeError(err)),
    });
  }
}
