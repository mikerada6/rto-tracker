import { Component, OnInit, signal, computed, HostListener, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DatePipe, NgTemplateOutlet, DecimalPipe } from '@angular/common';
import { CalendarService } from '../../core/services/calendar.service';
import { CommuteAnnotationService } from '../../core/services/commute-annotation.service';
import { AuditDayEntry } from '../../core/models/audit.model';
import { DayResponse } from '../../core/models/day.model';
import {
  CommuteAnnotation,
  categoryMeta,
} from '../../core/models/commute-annotation.model';
import {
  format, startOfMonth, endOfMonth, addMonths, subMonths,
  eachDayOfInterval, getDay, startOfWeek, endOfWeek,
  isSameMonth, isToday, isFuture, getISOWeek, parseISO
} from 'date-fns';
import { BottomSheetComponent } from '../../shared/components/bottom-sheet.component';
import { DayTimelineBarComponent } from '../../shared/components/day-timeline-bar.component';
import { buildSegments, buildJourneyPhases, formatSegmentDuration, TimelineSegment, JourneyPhase, JourneyStop } from '../../core/utils/timeline.utils';
import { CommuteAnnotationDialogComponent, CommuteAnnotationDialogResult } from './components/commute-annotation-dialog.component';

interface CalendarDay {
  date: Date;
  dateStr: string;
  dayNum: number;
  isCurrentMonth: boolean;
  isToday: boolean;
  isFuture: boolean;
  isOfficeDay: boolean;
  isSelected: boolean;
  isWeekend: boolean;
  officeTime: string | null;
  officeTimeSeconds: number;
  weekNum: number;
}

@Component({
  selector: 'app-calendar',
  imports: [DatePipe, NgTemplateOutlet, DecimalPipe, BottomSheetComponent, DayTimelineBarComponent, CommuteAnnotationDialogComponent],
  styles: [`
    .month-slide-enter { animation: slideInRight 0.22s ease-out; }
    .month-slide-back  { animation: slideInLeft  0.22s ease-out; }
    @keyframes slideInRight {
      from { opacity: 0; transform: translateX(24px); }
      to   { opacity: 1; transform: translateX(0); }
    }
    @keyframes slideInLeft {
      from { opacity: 0; transform: translateX(-24px); }
      to   { opacity: 1; transform: translateX(0); }
    }
  `],
  template: `
    <div class="space-y-4">

      <!-- ── Header ── -->
      <div class="flex items-center gap-2">
        <button (click)="prevMonth()"
          class="p-2 rounded-xl hover:bg-gray-100 active:scale-95 transition-all text-gray-600 hover:text-gray-900">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/>
          </svg>
        </button>

        <h2 class="flex-1 text-center text-lg font-bold text-gray-900 tracking-tight">
          {{ currentMonth() | date:'MMMM yyyy' }}
        </h2>

        <button (click)="nextMonth()" [disabled]="isCurrentMonthOrFuture()"
          class="p-2 rounded-xl hover:bg-gray-100 active:scale-95 transition-all text-gray-600 hover:text-gray-900 disabled:opacity-30 disabled:pointer-events-none">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"/>
          </svg>
        </button>

        @if (!isCurrentMonthOrFuture()) {
          <button (click)="goToToday()"
            class="ml-1 px-3 py-1.5 rounded-lg text-xs font-semibold bg-blue-600 text-white hover:bg-blue-700 active:scale-95 transition-all shadow-sm">
            Today
          </button>
        }

        <!-- Weekends toggle -->
        <button (click)="showWeekends.set(!showWeekends())"
          [class]="showWeekends()
            ? 'ml-1 px-3 py-1.5 rounded-lg text-xs font-semibold bg-gray-200 text-gray-700 hover:bg-gray-300 active:scale-95 transition-all'
            : 'ml-1 px-3 py-1.5 rounded-lg text-xs font-semibold bg-gray-100 text-gray-500 hover:bg-gray-200 active:scale-95 transition-all'"
          title="Toggle weekend columns">
          {{ showWeekends() ? 'Full week' : 'Mon–Fri' }}
        </button>
      </div>

      <!-- ── Month Stats Bar ── -->
      @if (monthStats()) {
        <div class="grid grid-cols-3 gap-2 sm:gap-3">
          <div class="bg-white rounded-xl border border-gray-100 shadow-sm px-3 py-2.5 text-center">
            <p class="text-2xl font-bold text-gray-900">{{ monthStats()!.officeDays }}</p>
            <p class="text-[11px] text-gray-500 mt-0.5">office days</p>
          </div>
          <div class="bg-white rounded-xl border border-gray-100 shadow-sm px-3 py-2.5 text-center">
            <p class="text-2xl font-bold text-gray-900">{{ monthStats()!.workWeeks | number:'1.1-1' }}</p>
            <p class="text-[11px] text-gray-500 mt-0.5">work weeks</p>
          </div>
          <div class="bg-white rounded-xl border border-gray-100 shadow-sm px-3 py-2.5 text-center">
            <p class="text-2xl font-bold"
               [class]="monthStats()!.avgPerWeek >= 3 ? 'text-green-600' : 'text-amber-500'">
              {{ monthStats()!.avgPerWeek | number:'1.1-1' }}
            </p>
            <p class="text-[11px] text-gray-500 mt-0.5">avg / week</p>
          </div>
        </div>
      } @else if (monthLoading()) {
        <div class="grid grid-cols-3 gap-2">
          @for (i of [1,2,3]; track i) {
            <div class="bg-gray-100 rounded-xl h-16 animate-pulse"></div>
          }
        </div>
      }

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-4">

        <!-- ── Calendar Grid ── -->
        <div class="lg:col-span-2 bg-white rounded-2xl shadow-sm border border-gray-100 p-4 overflow-hidden"
             (touchstart)="onGridTouchStart($event)"
             (touchend)="onGridTouchEnd($event)">

          <!-- Day headers -->
          <div [class]="gridCols() + ' mb-1'">
            <div></div><!-- week number spacer -->
            @for (day of weekDays(); track day) {
              <div class="text-center text-[11px] font-semibold text-gray-400 py-1 uppercase tracking-wide">
                {{ day }}
              </div>
            }
          </div>

          <!-- Day cells -->
          @if (monthLoading()) {
            <div [class]="gridCols() + ' gap-1'">
              @for (i of skeletonCells(); track i) {
                <div [class]="i % (showWeekends() ? 8 : 6) === 0 ? 'h-10 rounded' : 'aspect-square rounded-lg bg-gray-100 animate-pulse'"></div>
              }
            </div>
          } @else {
            <div [class]="gridCols() + ' gap-1 ' + slideClass()">
              @for (row of calendarWeekRows(); track row.weekNum) {
                <!-- Week number -->
                <div class="flex items-center justify-center">
                  <span class="text-[10px] font-medium text-gray-300 leading-none">W{{ row.weekNum }}</span>
                </div>

                <!-- Days in this week -->
                @for (day of row.days; track day.dateStr) {
                  <button
                    (click)="day.isCurrentMonth && !day.isFuture && selectDay(day.dateStr)"
                    [attr.aria-label]="day.dateStr"
                    [attr.aria-disabled]="!day.isCurrentMonth || day.isFuture"
                    class="relative aspect-square rounded-xl text-sm flex flex-col items-center justify-center gap-0.5 transition-all duration-150 select-none"
                    [class]="getDayCellClasses(day)"
                  >
                    <span class="font-medium leading-none z-10">{{ day.dayNum }}</span>

                    <!-- Office time label (desktop) -->
                    @if (day.isOfficeDay && day.officeTime && day.isCurrentMonth) {
                      <span class="hidden lg:block text-[9px] font-semibold leading-none z-10 opacity-90 mt-0.5">
                        {{ formatCellTime(day.officeTime) }}
                      </span>
                    }

                    <!-- Office dot (mobile only) -->
                    @if (day.isOfficeDay && day.isCurrentMonth) {
                      <span class="lg:hidden w-1 h-1 rounded-full z-10"
                            [class]="day.isSelected ? 'bg-white' : 'bg-green-500'"></span>
                    }

                    <!-- Today ring highlight -->
                    @if (day.isToday) {
                      <span class="absolute inset-0 rounded-xl ring-2 ring-blue-500 ring-offset-1 pointer-events-none"></span>
                    }
                  </button>
                }

              }
            </div>
          }

          <!-- ── Legend ── -->
          <div class="mt-3 pt-3 border-t border-gray-50 flex flex-wrap items-center gap-x-4 gap-y-1.5">
            <div class="flex items-center gap-1.5">
              <span class="w-3.5 h-3.5 rounded bg-green-100 border border-green-200 inline-block"></span>
              <span class="text-[11px] text-gray-500">&lt; 4 h</span>
            </div>
            <div class="flex items-center gap-1.5">
              <span class="w-3.5 h-3.5 rounded bg-green-300 inline-block"></span>
              <span class="text-[11px] text-gray-500">4 – 6 h</span>
            </div>
            <div class="flex items-center gap-1.5">
              <span class="w-3.5 h-3.5 rounded bg-green-500 inline-block"></span>
              <span class="text-[11px] text-gray-500">6 – 8 h</span>
            </div>
            <div class="flex items-center gap-1.5">
              <span class="w-3.5 h-3.5 rounded bg-green-700 inline-block"></span>
              <span class="text-[11px] text-gray-500">8 h+</span>
            </div>
            <div class="flex items-center gap-1.5 ml-auto">
              <span class="w-3.5 h-3.5 rounded ring-2 ring-blue-500 inline-block"></span>
              <span class="text-[11px] text-gray-500">Today</span>
            </div>
          </div>
        </div>

        <!-- ── Desktop side panel ── -->
        @if (selectedDateStr() || selectedDayLoading()) {
          <div class="hidden lg:block lg:col-span-1">
            <ng-container *ngTemplateOutlet="dayDetailTpl"/>
          </div>
        } @else {
          <div class="hidden lg:flex lg:col-span-1 items-center justify-center">
            <div class="text-center text-gray-300 py-10">
              <svg class="w-10 h-10 mx-auto mb-2 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                  d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/>
              </svg>
              <p class="text-xs">Click a day to see details</p>
            </div>
          </div>
        }
      </div>
    </div>

    <!-- ── Mobile bottom sheet ── -->
    <app-bottom-sheet [open]="!!selectedDay() || selectedDayLoading() || selectedDayError()" (close)="clearSelection()">
      <ng-container *ngTemplateOutlet="dayDetailTpl"/>
    </app-bottom-sheet>

    <!-- ── Commute annotation dialog ── -->
    <app-commute-annotation-dialog
      [open]="annotationDialogOpen()"
      [start]="annotationDialogStart()"
      [end]="annotationDialogEnd()"
      [existing]="annotationDialogExisting()"
      (save)="saveAnnotation($event)"
      (delete)="deleteAnnotation()"
      (cancel)="cancelAnnotationDialog()" />


    <!-- ── Shared day detail template ── -->
    <ng-template #dayDetailTpl>
      @if (selectedDayLoading()) {
        <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 space-y-4">
          <div class="h-5 bg-gray-100 rounded animate-pulse w-2/3"></div>
          <div class="h-4 bg-gray-100 rounded animate-pulse w-full"></div>
          <div class="h-4 bg-gray-100 rounded animate-pulse w-5/6"></div>
          <div class="h-4 bg-gray-100 rounded animate-pulse w-4/6"></div>
        </div>
      } @else if (selectedDay()) {
        <div class="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">

          <!-- Detail header -->
          <div class="px-5 py-4 border-b border-gray-50 flex items-center justify-between">
            <div>
              <p class="text-xs text-gray-400 font-medium uppercase tracking-wide">
                {{ selectedDay()!.date | date:'EEEE' }}
              </p>
              <h3 class="font-bold text-gray-900 leading-tight">
                {{ selectedDay()!.date | date:'MMMM d, yyyy' }}
              </h3>
            </div>
            <span class="px-2.5 py-1 rounded-full text-xs font-semibold"
                  [class]="selectedDay()!.officeDay
                    ? 'bg-green-100 text-green-700'
                    : 'bg-gray-100 text-gray-500'">
              {{ selectedDay()!.officeDay ? '🏢 Office' : '🏠 WFH' }}
            </span>
          </div>

          @if (selectedDay()!.officeDay) {
            <!-- Timeline bar -->
            @if (journeyPhases().length > 0) {
              <app-day-timeline-bar [phases]="journeyPhases()" />
            }

            <div class="px-5 py-4 space-y-4">

              <!-- Key metrics -->
              <div class="grid grid-cols-3 gap-3">
                <div class="bg-green-50 rounded-xl p-3 col-span-2">
                  <p class="text-[11px] text-green-600 font-medium uppercase tracking-wide">Time in Office</p>
                  @if (officeOngoing()) {
                    <p class="text-2xl font-bold text-green-800 mt-0.5">In progress<span class="text-sm font-normal text-green-500 ml-1.5">🟢</span></p>
                  } @else {
                    <p class="text-2xl font-bold text-green-800 mt-0.5">{{ selectedDay()!.totalOfficeTime || '—' }}</p>
                  }
                </div>
                <div class="bg-blue-50 rounded-xl p-3">
                  <p class="text-[11px] text-blue-600 font-medium uppercase tracking-wide">Commute</p>
                  @if (selectedDay()!.outboundCommute || selectedDay()!.inboundCommute) {
                    <div class="mt-1 space-y-0.5">
                      @if (selectedDay()!.outboundCommute) {
                        <p class="text-sm font-bold text-blue-800 flex items-center gap-1">
                          <span class="text-[10px] font-normal opacity-60">Morning</span>
                          {{ selectedDay()!.outboundCommute }}
                        </p>
                      }
                      @if (selectedDay()!.inboundCommute) {
                        <p class="text-sm font-bold text-blue-800 flex items-center gap-1">
                          <span class="text-[10px] font-normal opacity-60">Evening</span>
                          {{ selectedDay()!.inboundCommute }}
                        </p>
                      }
                    </div>
                  } @else {
                    <p class="text-lg font-bold text-blue-800 mt-0.5">—</p>
                  }
                </div>
              </div>

              <!-- Journey phases -->
              @if (journeyPhases().length > 0) {
                <div class="border-t border-gray-50 pt-3 space-y-3">
                  <div class="flex items-center justify-between">
                    <h4 class="text-[11px] font-semibold text-gray-400 uppercase tracking-wide">Journey</h4>
                    <button (click)="toggleRawEvents()"
                      class="text-[10px] text-gray-400 hover:text-gray-600 transition-colors underline underline-offset-2">
                      {{ showRawEvents() ? 'Journey view' : 'Raw events' }}
                    </button>
                  </div>

                  @if (showRawEvents()) {
                    <!-- Raw flat list -->
                    <div class="relative pl-4">
                      <div class="absolute left-1.5 top-2 bottom-2 w-px bg-gray-100"></div>
                      <div class="space-y-2.5">
                        @for (event of selectedDay()!.events; track $index) {
                          <div class="flex items-center gap-3 text-sm relative">
                            <span class="absolute -left-2.5 w-2 h-2 rounded-full border-2 border-white"
                                  [class]="event.type === 'ENTER' ? 'bg-green-500' : 'bg-rose-400'"></span>
                            <span class="font-mono text-xs text-gray-400 w-10 flex-shrink-0">
                              {{ event.timestamp | date:'HH:mm' }}
                            </span>
                            <span class="text-xs px-2 py-0.5 rounded-full font-medium"
                                  [class]="event.type === 'ENTER' ? 'bg-green-50 text-green-700' : 'bg-rose-50 text-rose-600'">
                              {{ event.type === 'ENTER' ? '→ Enter' : '← Exit' }}
                            </span>
                            <span class="text-xs text-gray-600 truncate">{{ event.zone }}</span>
                          </div>
                        }
                      </div>
                    </div>
                  } @else {
                    <!-- Journey view -->
                    @for (phase of journeyPhases(); track $index) {
                      @if (phase.type === 'office') {
                        <!-- Office: a single "stayed at" block, not a journey. Distinct from commute cards. -->
                        <div class="rounded-xl overflow-hidden bg-green-600/95 text-white shadow-sm">
                          <div class="flex items-stretch">
                            <div class="w-1.5 bg-green-300/70 flex-shrink-0"></div>
                            <div class="flex-1 px-3 py-2.5">
                              <div class="flex items-center justify-between gap-2">
                                <div class="flex items-center gap-2 min-w-0">
                                  <span class="text-base">🏢</span>
                                  <span class="text-[11px] font-semibold uppercase tracking-wider text-green-50">
                                    {{ phase.label }}
                                  </span>
                                </div>
                                <span class="text-sm font-bold tabular-nums">
                                  {{ formatDur(phase.totalDurationSeconds) }}
                                </span>
                              </div>
                              <div class="mt-1.5 space-y-1">
                                @for (stop of phase.stops; track $index) {
                                  @if (!stop.isMicroVisit) {
                                    <div class="flex items-center justify-between gap-2">
                                      <p class="text-xs font-semibold text-white truncate">
                                        <span class="mr-1">{{ zoneTypeIcon(stop.zoneType) }}</span>{{ stop.zone }}
                                      </p>
                                      <p class="text-[10px] font-mono text-green-100">
                                        @if (stop.isImplicitArrival) {
                                          <span class="italic text-green-200">departed </span>{{ stop.departureTime | date:'HH:mm' }}
                                        } @else {
                                          {{ stop.arrivalTime | date:'HH:mm' }}
                                          @if (stop.departureTime) {
                                            <span class="text-green-200"> → </span>{{ stop.departureTime | date:'HH:mm' }}
                                          } @else {
                                            <span class="italic text-green-200"> ongoing</span>
                                          }
                                        }
                                      </p>
                                    </div>
                                  }
                                }
                              </div>
                            </div>
                          </div>
                        </div>
                      } @else {
                      <div class="rounded-xl border overflow-hidden"
                           [class]="phaseContainerClass(phase.type)">

                        <!-- Phase header -->
                        <div class="flex items-center justify-between px-3 py-2"
                             [class]="phaseHeaderBgClass(phase.type)">
                          <div class="flex items-center gap-2">
                            <span class="text-sm">{{ phaseIcon(phase.type, phase) }}</span>
                            <span class="text-[11px] font-semibold uppercase tracking-wide"
                                  [class]="phaseHeaderTextClass(phase.type)">
                              {{ phase.label }}
                            </span>
                          </div>
                          <div class="flex items-center gap-2">
                            @if (phase.totalDurationSeconds > 0) {
                              <span class="text-xs font-bold"
                                    [class]="phaseHeaderTextClass(phase.type)">
                                {{ formatDur(phase.totalDurationSeconds) }}
                              </span>
                            }
                            @if (phase.type === 'annotation') {
                              <button type="button"
                                      (click)="openExistingAnnotationDialog(phase)"
                                      class="text-[10px] font-medium text-purple-700 underline underline-offset-2 hover:text-purple-900">
                                Edit
                              </button>
                            }
                          </div>
                        </div>

                        @if (phase.type === 'annotation' && phase.annotation?.note) {
                          <div class="px-3 py-1.5 text-[11px] text-purple-700 italic bg-purple-50/50 border-t border-purple-100">
                            "{{ phase.annotation?.note }}"
                          </div>
                        }

                        <!-- Phase stops with subway-map rail (inline segments — rail is bounded by first & last dot) -->
                        <div class="py-1">
                          @for (stop of phase.stops; track $index; let isFirst = $first; let isLast = $last) {
                            @if (!stop.isMicroVisit) {
                              <!-- Rail / transit row between stops -->
                              @if (!isFirst) {
                                @if (stop.isAnomalousGap) {
                                  <div class="flex items-center gap-2 px-3 py-1">
                                    <div class="w-5 flex justify-center flex-shrink-0">
                                      <div class="w-0.5 h-4 border-l-2 border-dotted border-purple-400"></div>
                                    </div>
                                    <button type="button"
                                            (click)="openAnnotationDialog(stop)"
                                            class="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-purple-100 text-purple-700 hover:bg-purple-200 transition-colors">
                                      🏷️ Label this · {{ formatDur(stop.transitFromPrevSeconds) }}
                                    </button>
                                  </div>
                                } @else if (stop.transitFromPrevSeconds > 120) {
                                  <div class="flex items-center gap-2 px-3 py-1">
                                    <div class="w-5 flex justify-center flex-shrink-0">
                                      <div class="w-0.5 h-4 border-l-2 border-dotted"
                                           [class]="phaseConnectorClass(phase.type)"></div>
                                    </div>
                                    <span class="text-[10px] font-medium px-2 py-0.5 rounded-full bg-gray-100 text-gray-500">
                                      {{ formatDur(stop.transitFromPrevSeconds) }}
                                    </span>
                                  </div>
                                } @else {
                                  <div class="flex px-3">
                                    <div class="w-5 flex justify-center flex-shrink-0">
                                      <div class="w-0.5 h-3" [class]="phaseRailBgClass(phase.type)"></div>
                                    </div>
                                  </div>
                                }
                              }

                              <!-- Stop row -->
                              <div class="flex items-center gap-2 px-3"
                                   [class]="isLast ? 'py-2.5' : 'py-1.5'">
                                <div class="w-5 flex justify-center flex-shrink-0">
                                  @if (stop.isPhaseEndpoint) {
                                    <div class="w-3 h-3 rounded-full border-2 bg-white"
                                         [class]="phaseRailBorderClass(phase.type)"></div>
                                  } @else {
                                    <div class="w-3 h-3 rounded-full border-2 border-white shadow-sm"
                                         [class]="phaseRailBgClass(phase.type)"></div>
                                  }
                                </div>

                                <div class="flex-1 min-w-0">
                                  <p class="text-xs font-semibold truncate text-gray-800">
                                    <span class="mr-1">{{ zoneTypeIcon(stop.zoneType) }}</span>{{ stop.zone }}
                                  </p>
                                  <p class="text-[10px] text-gray-400 font-mono mt-0.5">
                                    @if (stop.isPhaseEndpoint && isLast) {
                                      <span class="italic text-gray-300">arrived </span>{{ stop.arrivalTime | date:'HH:mm' }}
                                    } @else if (stop.isPhaseEndpoint && isFirst) {
                                      <span class="italic text-gray-300">departed </span>{{ stop.departureTime | date:'HH:mm' }}
                                    } @else if (stop.isImplicitArrival) {
                                      <span class="italic text-gray-300">departed </span>{{ stop.departureTime | date:'HH:mm' }}
                                    } @else {
                                      {{ stop.arrivalTime | date:'HH:mm' }}
                                      @if (stop.departureTime && stop.durationSeconds > 0) {
                                        <span class="text-gray-300"> → </span>
                                        {{ stop.departureTime | date:'HH:mm' }}
                                      } @else if (!stop.departureTime) {
                                        <span class="italic text-gray-300"> ongoing</span>
                                      }
                                    }
                                  </p>
                                </div>

                                @if (stop.durationSeconds >= 60 && !stop.isPhaseEndpoint) {
                                  <span class="text-[11px] font-bold flex-shrink-0"
                                        [class]="phaseHeaderTextClass(phase.type)">
                                    {{ formatDur(stop.durationSeconds) }}
                                  </span>
                                }
                              </div>
                            }
                          }
                        </div>
                      </div>
                      }
                    }
                  }
                </div>
              }
            </div>
          } @else {
            <div class="px-5 py-10 text-center">
              <div class="w-12 h-12 rounded-2xl bg-gray-50 flex items-center justify-center mx-auto mb-3">
                <svg class="w-6 h-6 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                    d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"/>
                </svg>
              </div>
              <p class="text-sm font-medium text-gray-400">Work from home day</p>
              <p class="text-xs text-gray-300 mt-1">No office activity recorded</p>
            </div>
          }
        </div>
      } @else {
        <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 text-center">
          <div class="w-12 h-12 rounded-2xl bg-red-50 flex items-center justify-center mx-auto mb-3">
            <svg class="w-6 h-6 text-red-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                d="M12 9v2m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/>
            </svg>
          </div>
          <p class="text-sm font-medium text-gray-500">Couldn't load day</p>
          <p class="text-xs text-gray-400 mt-1">{{ selectedDateStr() }}</p>
          <button (click)="selectDay(selectedDateStr())"
            class="mt-3 px-3 py-1.5 text-xs rounded-lg bg-gray-100 hover:bg-gray-200 text-gray-600 transition-colors">
            Retry
          </button>
        </div>
      }
    </ng-template>
  `
})
export class CalendarComponent implements OnInit {
  readonly showWeekends = signal(false);
  readonly currentMonth = signal(new Date());
  readonly auditDays = signal<Map<string, AuditDayEntry>>(new Map());
  readonly selectedDay = signal<DayResponse | null>(null);
  readonly selectedDayLoading = signal(false);
  readonly selectedDateStr = signal('');
  readonly selectedDayError = signal(false);
  readonly monthLoading = signal(false);
  readonly slideClass = signal('');
  readonly showRawEvents = signal(false);

  readonly weekDays = computed(() =>
    this.showWeekends() ? ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] : ['Mon', 'Tue', 'Wed', 'Thu', 'Fri']
  );

  readonly skeletonCells = computed(() =>
    Array.from({ length: this.showWeekends() ? 48 : 36 }, (_, i) => i)
  );

  readonly gridCols = computed(() =>
    this.showWeekends() ? 'grid grid-cols-[2rem_repeat(7,1fr)]' : 'grid grid-cols-[2rem_repeat(5,1fr)]'
  );

  private gridTouchStartX = 0;

  readonly calendarDays = computed((): CalendarDay[] => {
    const month = this.currentMonth();
    const monthStart = startOfMonth(month);
    const monthEnd = endOfMonth(month);
    const calStart = startOfWeek(monthStart, { weekStartsOn: 1 });
    const calEnd = endOfWeek(monthEnd, { weekStartsOn: 1 });
    const days = eachDayOfInterval({ start: calStart, end: calEnd });
    const auditMap = this.auditDays();

    return days.map(d => {
      const dateStr = format(d, 'yyyy-MM-dd');
      const audit = auditMap.get(dateStr);
      return {
        date: d,
        dateStr,
        dayNum: d.getDate(),
        isCurrentMonth: isSameMonth(d, month),
        isToday: isToday(d),
        isFuture: isFuture(d),
        isOfficeDay: !!audit,
        isSelected: dateStr === this.selectedDateStr(),
        isWeekend: getDay(d) === 0 || getDay(d) === 6,
        officeTime: audit?.totalOfficeTime ?? null,
        officeTimeSeconds: audit?.totalOfficeTimeSeconds ?? 0,
        weekNum: getISOWeek(d),
      };
    });
  });

  readonly calendarWeekRows = computed(() => {
    const days = this.calendarDays();
    const showWeekends = this.showWeekends();
    const rows: { weekNum: number; days: CalendarDay[] }[] = [];
    for (let i = 0; i < days.length; i += 7) {
      const week = days.slice(i, i + 7);
      const visibleDays = showWeekends ? week : week.slice(0, 5);
      rows.push({ weekNum: week[0].weekNum, days: visibleDays });
    }
    return rows;
  });

  readonly monthStats = computed(() => {
    const auditMap = this.auditDays();
    if (auditMap.size === 0) return null;

    const month = this.currentMonth();
    let officeDays = 0;
    let workWeekdayCount = 0;

    const monthStart = startOfMonth(month);
    const today = new Date();
    // Only count up to today if viewing the current month, otherwise count the full month
    const countEnd = isSameMonth(month, today) && !isFuture(monthStart)
      ? today
      : endOfMonth(month);
    eachDayOfInterval({ start: monthStart, end: countEnd }).forEach(d => {
      const dow = getDay(d);
      if (dow !== 0 && dow !== 6) workWeekdayCount++;
      const dateStr = format(d, 'yyyy-MM-dd');
      if (auditMap.has(dateStr)) officeDays++;
    });

    const workWeeks = workWeekdayCount / 5;
    const avgPerWeek = workWeeks > 0 ? officeDays / workWeeks : 0;

    return { officeDays, workWeeks, avgPerWeek };
  });

  readonly daySegments = computed((): TimelineSegment[] => {
    const day = this.selectedDay();
    if (!day || !day.events.length) return [];
    return buildSegments(day.events);
  });

  readonly dayMaxSegmentSeconds = computed((): number => {
    const segs = this.daySegments();
    if (!segs.length) return 0;
    return Math.max(...segs.map(s => s.durationSeconds));
  });

  readonly journeyPhases = computed((): JourneyPhase[] => {
    const segs = this.daySegments();
    if (!segs.length) return [];
    const day = this.selectedDay();
    const annotations = day?.commuteAnnotations ?? [];
    const threshold = day?.anomalyThresholdMinutes ?? 45;
    return buildJourneyPhases(segs, annotations, threshold);
  });

  // ── Commute annotation dialog state ──
  readonly annotationDialogOpen = signal(false);
  readonly annotationDialogStart = signal<Date>(new Date());
  readonly annotationDialogEnd = signal<Date>(new Date());
  readonly annotationDialogExisting = signal<CommuteAnnotation | null>(null);

  /** True when the user is currently at the office (no departure yet) */
  readonly officeOngoing = computed((): boolean => {
    return this.daySegments().some(s => s.zoneType === 'OFFICE' && !s.isMicroVisit && s.departureTime === null);
  });

  private annotationService = inject(CommuteAnnotationService);

  constructor(
    private calendarService: CalendarService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const dateParam = params.get('date');
      if (dateParam) {
        const parsed = new Date(dateParam + 'T00:00:00');
        if (!isSameMonth(parsed, this.currentMonth())) {
          this.currentMonth.set(parsed);
          this.loadMonth();
        }
        this.selectDay(dateParam);
      }
    });

    if (!this.route.snapshot.paramMap.get('date')) {
      this.loadMonth();
    }
  }

  loadMonth(): void {
    const month = this.currentMonth();
    const startDate = format(startOfMonth(month), 'yyyy-MM-dd');
    const endDate = format(endOfMonth(month), 'yyyy-MM-dd');
    this.monthLoading.set(true);
    this.auditDays.set(new Map());

    this.calendarService.getAudit(startDate, endDate).subscribe({
      next: (audit) => {
        const map = new Map<string, AuditDayEntry>();
        audit.days.forEach(d => map.set(d.date, d));
        this.auditDays.set(map);
        this.monthLoading.set(false);
      },
      error: () => { this.monthLoading.set(false); }
    });
  }

  selectDay(dateStr: string): void {
    this.selectedDateStr.set(dateStr);
    this.selectedDayError.set(false);
    this.selectedDayLoading.set(true);
    this.calendarService.getDayDetail(dateStr).subscribe({
      next: (day) => {
        this.selectedDay.set(day);
        this.selectedDayLoading.set(false);
      },
      error: () => {
        this.selectedDay.set(null);
        this.selectedDayError.set(true);
        this.selectedDayLoading.set(false);
      }
    });
  }

  clearSelection(): void {
    this.selectedDay.set(null);
    this.selectedDateStr.set('');
    this.selectedDayError.set(false);
    this.showRawEvents.set(false);
  }

  toggleRawEvents(): void {
    this.showRawEvents.set(!this.showRawEvents());
  }

  // ── Commute annotation actions ──

  openAnnotationDialog(stop: JourneyStop): void {
    if (!stop.gapStartTime || !stop.gapEndTime) return;
    this.annotationDialogExisting.set(null);
    this.annotationDialogStart.set(stop.gapStartTime);
    this.annotationDialogEnd.set(stop.gapEndTime);
    this.annotationDialogOpen.set(true);
  }

  openExistingAnnotationDialog(phase: JourneyPhase): void {
    if (!phase.annotation) return;
    this.annotationDialogExisting.set(phase.annotation);
    this.annotationDialogStart.set(new Date(phase.annotation.startTime));
    this.annotationDialogEnd.set(new Date(phase.annotation.endTime));
    this.annotationDialogOpen.set(true);
  }

  cancelAnnotationDialog(): void {
    this.annotationDialogOpen.set(false);
    this.annotationDialogExisting.set(null);
  }

  saveAnnotation(result: CommuteAnnotationDialogResult): void {
    const date = this.selectedDateStr();
    if (!date) return;
    const existing = this.annotationDialogExisting();
    const obs = existing
      ? this.annotationService.update(date, existing.id, { category: result.category, note: result.note })
      : this.annotationService.create(date, {
          startTime: this.annotationDialogStart().toISOString(),
          endTime: this.annotationDialogEnd().toISOString(),
          category: result.category,
          note: result.note,
        });
    obs.subscribe({
      next: () => {
        this.annotationDialogOpen.set(false);
        this.annotationDialogExisting.set(null);
        this.selectDay(date);
      },
      error: () => {
        this.annotationDialogOpen.set(false);
      },
    });
  }

  deleteAnnotation(): void {
    const date = this.selectedDateStr();
    const existing = this.annotationDialogExisting();
    if (!date || !existing) return;
    this.annotationService.delete(date, existing.id).subscribe({
      next: () => {
        this.annotationDialogOpen.set(false);
        this.annotationDialogExisting.set(null);
        this.selectDay(date);
      },
    });
  }

  annotationCategoryIcon(category: string): string {
    return categoryMeta(category as any).icon;
  }

  zoneTypeIcon(type: string): string {
    switch (type) {
      case 'HOME':          return '🏠';
      case 'OFFICE':        return '🏢';
      case 'TRAIN_STATION': return '🚉';
      default:              return '📍';
    }
  }

  segmentBgClass(type: string): string {
    switch (type) {
      case 'HOME':          return 'bg-gray-50';
      case 'OFFICE':        return 'bg-green-50';
      case 'TRAIN_STATION': return 'bg-blue-50';
      default:              return 'bg-purple-50';
    }
  }

  segmentBorderClass(type: string): string {
    switch (type) {
      case 'HOME':          return 'border-gray-200';
      case 'OFFICE':        return 'border-green-200';
      case 'TRAIN_STATION': return 'border-blue-200';
      default:              return 'border-purple-200';
    }
  }

  segmentTextClass(type: string): string {
    switch (type) {
      case 'HOME':          return 'text-gray-700';
      case 'OFFICE':        return 'text-green-700';
      case 'TRAIN_STATION': return 'text-blue-700';
      default:              return 'text-purple-700';
    }
  }

  segmentSubTextClass(type: string): string {
    switch (type) {
      case 'HOME':          return 'text-gray-500';
      case 'OFFICE':        return 'text-green-600';
      case 'TRAIN_STATION': return 'text-blue-600';
      default:              return 'text-purple-600';
    }
  }

  segmentBarBgClass(type: string): string {
    switch (type) {
      case 'HOME':          return 'bg-gray-100';
      case 'OFFICE':        return 'bg-green-100';
      case 'TRAIN_STATION': return 'bg-blue-100';
      default:              return 'bg-purple-100';
    }
  }

  segmentBarFillClass(type: string): string {
    switch (type) {
      case 'HOME':          return 'bg-gray-400';
      case 'OFFICE':        return 'bg-green-500';
      case 'TRAIN_STATION': return 'bg-blue-400';
      default:              return 'bg-purple-400';
    }
  }

  formatDur(seconds: number): string {
    return formatSegmentDuration(seconds);
  }

  phaseIcon(type: string, phase?: JourneyPhase): string {
    if (type === 'annotation' && phase?.annotation) {
      return categoryMeta(phase.annotation.category).icon;
    }
    switch (type) {
      case 'morning_commute': return '🌅';
      case 'office':          return '🏢';
      case 'evening_commute': return '🌇';
      case 'home':            return '🏠';
      default:                return '📍';
    }
  }

  phaseContainerClass(type: string): string {
    switch (type) {
      case 'morning_commute': return 'border-blue-200';
      case 'office':          return 'border-green-200';
      case 'evening_commute': return 'border-amber-200';
      case 'home':            return 'border-gray-200';
      case 'annotation':      return 'border-purple-200';
      default:                return 'border-gray-200';
    }
  }

  phaseHeaderBgClass(type: string): string {
    switch (type) {
      case 'morning_commute': return 'bg-blue-50';
      case 'office':          return 'bg-green-50';
      case 'evening_commute': return 'bg-amber-50';
      case 'home':            return 'bg-gray-50';
      case 'annotation':      return 'bg-purple-50';
      default:                return 'bg-gray-50';
    }
  }

  phaseHeaderTextClass(type: string): string {
    switch (type) {
      case 'morning_commute': return 'text-blue-700';
      case 'office':          return 'text-green-700';
      case 'evening_commute': return 'text-amber-700';
      case 'home':            return 'text-gray-600';
      case 'annotation':      return 'text-purple-700';
      default:                return 'text-gray-600';
    }
  }

  phaseConnectorClass(type: string): string {
    switch (type) {
      case 'morning_commute': return 'border-blue-300';
      case 'office':          return 'border-green-300';
      case 'evening_commute': return 'border-amber-300';
      case 'annotation':      return 'border-purple-300';
      default:                return 'border-gray-300';
    }
  }

  phaseRailBgClass(type: string): string {
    switch (type) {
      case 'morning_commute': return 'bg-blue-300';
      case 'office':          return 'bg-green-300';
      case 'evening_commute': return 'bg-amber-300';
      case 'home':            return 'bg-gray-300';
      case 'annotation':      return 'bg-purple-300';
      default:                return 'bg-gray-300';
    }
  }

  phaseRailBorderClass(type: string): string {
    switch (type) {
      case 'morning_commute': return 'border-blue-300';
      case 'office':          return 'border-green-300';
      case 'evening_commute': return 'border-amber-300';
      case 'home':            return 'border-gray-300';
      case 'annotation':      return 'border-purple-300';
      default:                return 'border-gray-300';
    }
  }

  prevMonth(): void {
    this.slideClass.set('month-slide-back');
    const newMonth = subMonths(this.currentMonth(), 1);
    this.currentMonth.set(newMonth);
    this.clearSelection();
    this.loadMonth();
    this.updateUrl();
  }

  nextMonth(): void {
    this.slideClass.set('month-slide-enter');
    const newMonth = addMonths(this.currentMonth(), 1);
    this.currentMonth.set(newMonth);
    this.clearSelection();
    this.loadMonth();
    this.updateUrl();
  }

  goToToday(): void {
    const now = new Date();
    if (!isSameMonth(now, this.currentMonth())) {
      this.slideClass.set('month-slide-enter');
      this.currentMonth.set(now);
      this.clearSelection();
      this.loadMonth();
    }
    this.updateUrl();
  }

  isCurrentMonthOrFuture(): boolean {
    const now = new Date();
    const m = this.currentMonth();
    return m.getFullYear() === now.getFullYear() && m.getMonth() === now.getMonth();
  }

  private updateUrl(): void {
    const dateStr = format(this.currentMonth(), 'yyyy-MM-dd');
    this.router.navigate(['/calendar', dateStr], { replaceUrl: false });
  }

  getDayCellClasses(day: CalendarDay): string {
    const classes: string[] = [];

    if (!day.isCurrentMonth) {
      classes.push('opacity-20 pointer-events-none text-gray-400');
      return classes.join(' ');
    }

    if (day.isFuture) {
      classes.push('text-gray-300 cursor-default');
    } else if (day.isOfficeDay) {
      // Heat-map intensity based on seconds in office
      const hrs = day.officeTimeSeconds / 3600;
      if (day.isSelected) {
        classes.push('bg-blue-600 text-white shadow-md scale-105 ring-2 ring-blue-400 ring-offset-1');
      } else if (hrs >= 8) {
        classes.push('bg-green-700 text-white hover:bg-green-800 cursor-pointer shadow-sm');
      } else if (hrs >= 6) {
        classes.push('bg-green-500 text-white hover:bg-green-600 cursor-pointer shadow-sm');
      } else if (hrs >= 4) {
        classes.push('bg-green-300 text-green-900 hover:bg-green-400 cursor-pointer');
      } else {
        classes.push('bg-green-100 text-green-800 hover:bg-green-200 cursor-pointer border border-green-200');
      }
    } else {
      if (day.isWeekend) {
        classes.push('text-gray-400 hover:bg-gray-50 cursor-pointer');
      } else {
        classes.push('text-gray-600 hover:bg-gray-50 cursor-pointer');
      }
      if (day.isSelected) {
        classes.push('!bg-blue-50 !text-blue-700 ring-1 ring-blue-200');
      }
    }

    return classes.join(' ');
  }

  formatCellTime(time: string): string {
    return time.replace(/\s+/g, '').replace(/^0h/, '').replace(/0m$/, '') || time;
  }

  onGridTouchStart(e: TouchEvent): void {
    this.gridTouchStartX = e.touches[0].clientX;
  }

  onGridTouchEnd(e: TouchEvent): void {
    const deltaX = e.changedTouches[0].clientX - this.gridTouchStartX;
    if (Math.abs(deltaX) > 50) {
      deltaX > 0 ? this.prevMonth() : this.nextMonth();
    }
  }

  @HostListener('window:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) return;
    switch (event.key) {
      case 'ArrowLeft':  event.preventDefault(); this.prevMonth(); break;
      case 'ArrowRight': event.preventDefault(); this.nextMonth(); break;
      case 'Escape':     this.clearSelection(); break;
    }
  }
}
