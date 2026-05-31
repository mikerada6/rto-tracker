export interface QuarterReportResponse {
  period: string;
  periodStart: string;
  periodEnd: string;
  daysInOffice: number;
  averageDaysPerWeek: number;
  isCompliant: boolean;
  monthlyBreakdown: MonthlyBreakdown[];
}

export interface MonthlyBreakdown {
  month: string;
  daysInOffice: number;
  averageDaysPerWeek: number;
}
