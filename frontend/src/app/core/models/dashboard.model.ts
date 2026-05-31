export interface DashboardSummary {
  asOf: string;
  requiredAveragePerWeek: number;
  week: PeriodStats;
  month: PeriodStats;
  quarter: PeriodStats;
  year: PeriodStats;
  recentCommutes: RecentCommute[];
}

export interface PeriodStats {
  periodStart: string;
  periodEnd: string;
  daysInOffice: number;
  averageDaysPerWeek: number;
  weeksRemaining: number;
  daysStillNeeded: number;
  requiredAvgForRemainder: number | null;
  isCompliant?: boolean;
}

export interface RecentCommute {
  date: string;
  duration: string;
  outboundDuration: string | null;
  inboundDuration: string | null;
  route: string;
}
