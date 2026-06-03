import { CommuteAnnotation } from './commute-annotation.model';

export interface DayResponse {
  date: string;
  officeDay: boolean;
  officesVisited: string[];
  totalOfficeTime: string;
  firstOfficeEntry: string | null;
  lastOfficeExit: string | null;
  commuteDuration: string;
  outboundCommute: string | null;
  inboundCommute: string | null;
  commuteRoute: string | null;
  events: DayEventEntry[];
  commuteAnnotations: CommuteAnnotation[];
  anomalyThresholdMinutes: number;
}

export interface DayEventEntry {
  zone: string;
  zoneType: 'HOME' | 'TRAIN_STATION' | 'OFFICE' | 'OTHER';
  type: 'ENTER' | 'EXIT';
  timestamp: string;
}
