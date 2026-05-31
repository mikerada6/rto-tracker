import { Component, input } from '@angular/core';

/**
 * Reusable skeleton placeholder for loading states.
 * Usage: <app-skeleton [class]="'h-4 w-full'" />
 *        <app-skeleton shape="circle" [class]="'h-12 w-12'" />
 */
@Component({
  selector: 'app-skeleton',
  template: `
    <div
      class="animate-pulse bg-gray-200 rounded"
      [class]="shapeClass() + ' ' + extraClass()"
      [attr.aria-hidden]="true"
    ></div>
  `
})
export class SkeletonComponent {
  readonly shape = input<'line' | 'circle' | 'rect'>('rect');
  readonly extraClass = input('', { alias: 'class' });

  shapeClass(): string {
    return this.shape() === 'circle' ? 'rounded-full' : 'rounded';
  }
}

