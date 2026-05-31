export interface AuditResponse {
  startDate: string;
  endDate: string;
  totalOfficeDays: number;
  days: AuditDayEntry[];
}

export interface AuditDayEntry {
  date: string;
  officesVisited: string[];
  totalOfficeTime: string;
  totalOfficeTimeSeconds: number;
  firstOfficeEntry: string | null;
  lastOfficeExit: string | null;
}
