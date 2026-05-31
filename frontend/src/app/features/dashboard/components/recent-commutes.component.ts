import { Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RecentCommute } from '../../../core/models/dashboard.model';

@Component({
  selector: 'app-recent-commutes',
  imports: [DatePipe],
  template: `
    <div class="bg-white rounded-2xl p-6 shadow-sm border border-gray-100">
      <h3 class="text-sm font-medium text-gray-500 mb-4">Recent Commutes</h3>
      <div class="space-y-3">
        @for (commute of commutes(); track commute.date) {
          <div class="py-2 border-b border-gray-50 last:border-0">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <div class="w-10 h-10 bg-blue-50 rounded-lg flex items-center justify-center flex-shrink-0">
                  <svg class="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                      d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
                  </svg>
                </div>
                <div>
                  <p class="text-sm font-medium text-gray-900">{{ commute.route }}</p>
                  <p class="text-xs text-gray-500">{{ commute.date | date:'EEE, MMM d' }}</p>
                </div>
              </div>
              <span class="text-xs text-gray-400 flex-shrink-0">{{ commute.duration }} total</span>
            </div>
            @if (commute.outboundDuration || commute.inboundDuration) {
              <div class="mt-1.5 ml-13 flex gap-4 pl-[52px]">
                @if (commute.outboundDuration) {
                  <span class="inline-flex items-center gap-1 text-xs text-gray-500">
                    <svg class="w-3 h-3 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7l5 5m0 0l-5 5m5-5H6" />
                    </svg>
                    To office: <span class="font-medium text-gray-700">{{ commute.outboundDuration }}</span>
                  </span>
                }
                @if (commute.inboundDuration) {
                  <span class="inline-flex items-center gap-1 text-xs text-gray-500">
                    <svg class="w-3 h-3 text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 17l-5-5m0 0l5-5m-5 5h12" />
                    </svg>
                    To home: <span class="font-medium text-gray-700">{{ commute.inboundDuration }}</span>
                  </span>
                }
              </div>
            }
          </div>
        }
      </div>
    </div>
  `
})
export class RecentCommutesComponent {
  readonly commutes = input.required<RecentCommute[]>();
}
