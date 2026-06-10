export interface QuarterReportResponse {
  period: string;
  periodStart: string;
  periodEnd: string;
  daysInOffice: number;
  averageDaysPerWeek: number;
  isCompliant: boolean;
  weeksRemaining: number;
  daysStillNeeded: number;
  requiredAvgForRemainder: number | null;
  requiredDaysPerWeek: number;
  monthlyBreakdown: MonthlyBreakdown[];
}

export interface MonthlyBreakdown {
  month: string;
  daysInOffice: number;
  averageDaysPerWeek: number;
}
